package dev.bmac.gradle.intellij;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.io.CharStreams;
import com.sun.istack.Nullable;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PluginUploader {

    static final String UNKNOWN_VERSION = "UNKNOWN";
    static final String LOCK_FILE_EXTENSION = ".lock";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();

    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;
    private final int timeoutMs;
    private final int retryTimes;
    private final Logger logger;

    private final String url;
    private final boolean absoluteDownloadUrls;
    private final String pluginName;
    private final File file;
    private final String updateFile;
    private final String pluginId;
    private final String version;
    private final String authentication;
    private final String description;
    private final String changeNotes;
    private final Boolean updatePluginXml;
    private final String sinceBuild;
    private final String untilBuild;
    private final UploadMethod uploadMethod;

    private final boolean mutableRelease = Boolean.parseBoolean(System.getProperty("dev.bmac.pluginUploader.mutableRelease", "false"));

    public PluginUploader(int timeoutMs, int retryTimes, Logger logger,
                          @NotNull String url, boolean absoluteDownloadUrls, @NotNull String pluginName,
                          @NotNull File file, @NotNull String updateFile, @NotNull String pluginId,
                          @NotNull String version, String authentication, String description, String changeNotes,
                          @NotNull Boolean updatePluginXml, String sinceBuild, String untilBuild,
                          @NotNull UploadMethod uploadMethod) throws Exception {

        this.timeoutMs = timeoutMs;
        this.retryTimes = retryTimes;
        this.logger = logger;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.absoluteDownloadUrls = absoluteDownloadUrls;
        this.pluginName = pluginName;
        this.file = file;
        this.updateFile = updateFile;
        this.pluginId = pluginId;
        this.version = version;
        this.authentication = authentication;
        this.description = description;
        this.changeNotes = changeNotes;
        this.updatePluginXml = updatePluginXml;
        this.sinceBuild = sinceBuild;
        this.untilBuild = untilBuild;
        this.uploadMethod = uploadMethod;

        JAXBContext contextObj = JAXBContext.newInstance(PluginsElement.class);

        marshaller = contextObj.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        unmarshaller = contextObj.createUnmarshaller();
    }

    void execute() {
        if (url == null || pluginName == null ||
                file == null || pluginId == null || version == null) {
            throw new RuntimeException("Must specify url, pluginName, pluginId, version and file to uploadPlugin");
        }
        if (updatePluginXml && updateFile == null) {
            throw new RuntimeException("updateFile can not be null");
        }

        if (!updatePluginXml) {
            //Prevent replacing a already published version based on the plugin xml.
            try {
                getPluginsThrowIfOverwrite();
            } catch (FatalException e) {
                throw new GradleException(e.getMessage());
            }
            postPlugin();
        } else {
            final AtomicReference<Throwable> firstException = new AtomicReference<>();
            Retryer<Void> retryer = RetryerBuilder.<Void>newBuilder()
                    .retryIfException(e -> !(e instanceof FatalException))
                    .withStopStrategy(StopStrategies.stopAfterAttempt(retryTimes))
                    .withWaitStrategy(WaitStrategies.fixedWait(timeoutMs, TimeUnit.MILLISECONDS))
                    .withRetryListener(new RetryListener() {
                        @Override
                        public <V> void onRetry(Attempt<V> attempt) {
                            if (attempt.hasException()) {
                                firstException.compareAndSet(null, attempt.getExceptionCause());
                            }
                        }
                    })
                    .build();
            try {
                retryer.call(() -> {
                    postPluginAndUpdateXml();
                    return null;
                });
            } catch (ExecutionException | RetryException e) {
                Throwable cause = firstException.get();
                if (cause instanceof FatalException) {
                    throw new GradleException(cause.getMessage(), cause);
                }
                throw new GradleException("Failed to publish plugin", cause != null ? cause : e);
            }
        }
    }

    void postPluginAndUpdateXml() throws RetryableException, FatalException {
        String lock = setLockThrows();

        try {
            PluginsElement plugins = getPluginsThrowIfOverwrite();

            postPlugin();

            PluginElement plugin = new PluginElement(pluginId, version, description, changeNotes, pluginName,
                    sinceBuild, untilBuild, file, absoluteDownloadUrls ? url : ".");
            PluginUpdatesUtil.updateOrAdd(plugin, plugins.getPlugins(), logger);

            postUpdates(plugins);
        } finally {
            if (lock != null) {
                if (lock.equals(getLock())) {
                    Retryer<Object> retryer = RetryerBuilder.newBuilder()
                            .retryIfExceptionOfType(IOException.class)
                            .withStopStrategy(StopStrategies.stopAfterAttempt(retryTimes))
                            .withWaitStrategy(WaitStrategies.fixedWait(timeoutMs, TimeUnit.MILLISECONDS))
                            .build();
                    try {
                        retryer.call(() -> {
                            clearLock();
                            return null;
                        });
                    } catch (ExecutionException | RetryException e) {
                        throw new FatalException("Failed to cleanup " + file + LOCK_FILE_EXTENSION + ". File must be cleaned up manually", e);
                    }
                } else {
                    throw new FatalException("The lock value changed during execution. This is bad! The release may be invalid");
                }
            }
        }
    }

    void postPlugin() {
        String pluginEndpoint = pluginName + "/" + file.getName();

        RequestBody requestBody = RequestBody.create(file, MediaType.parse("application/zip"));

        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url + "/" + pluginEndpoint)
                    .method(uploadMethod.name(), requestBody);

            if (authentication != null) {
                requestBuilder.addHeader("Authorization", authentication);
            }
            Request request = requestBuilder.build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to upload plugin with status: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void postUpdates(PluginsElement updates) {
        try {
            File file = File.createTempFile("updatePlugins", null);
            file.deleteOnExit();

            try (FileWriter fw = new FileWriter(file)) {

                Date now = Calendar.getInstance().getTime();
                DateFormat df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss z");
                df.setTimeZone(TimeZone.getTimeZone("GMT"));
                String dateString = df.format(now);
                String pluginVersion = getPluginVersion();
                fw.append("<!-- File updated on ")
                        .append(dateString)
                        .append(" updating '")
                        .append(pluginId)
                        .append("' using plugin uploader version ")
                        .append(pluginVersion)
                        .append(" -->\n");

                marshaller.marshal(updates, fw);
            }

            String fileName = updateFile;

            RequestBody requestBody = RequestBody.create(file, MediaType.parse("application/xml"));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(url + "/" + fileName)
                    .method(uploadMethod.name(), requestBody);

            if (authentication != null) {
                requestBuilder.addHeader("Authorization", authentication);
            }
            Request request = requestBuilder.build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to upload " + fileName + " with status: " + response.code());
                }
            }
        } catch (IOException | JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    PluginsElement getUpdates() {
        try {
            Request request = new Request.Builder()
                    .url(url + "/" + updateFile)
                    .get()
                    .build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.code() == 404) {
                    logger.info("No " + updateFile + " found. Creating new file.");
                    return new PluginsElement();
                } else if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new RuntimeException("Body was null for " + updateFile);
                    }
                    return (PluginsElement) unmarshaller.unmarshal(body.byteStream());
                } else {
                    throw new RuntimeException("Received an unknown status code while retrieving " + updateFile);
                }

            }
        } catch (IOException | JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    String getLock() {
        try {
            Request request = new Request.Builder()
                    .url(url + "/" + updateFile + LOCK_FILE_EXTENSION)
                    .get()
                    .build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.code() == 404) {
                    return null;
                } else if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body == null) {
                        return "";
                    }
                    return body.string();
                } else {
                    throw new RuntimeException("Received an unknown status code while retrieving lock file, status: " + response.code());
                }

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Upload the lock to the server.
     * Note: This does not thow exceptions. It is expected to check the lock after this method is called to verify this
     * process acquired the lock.
     */
    void setLock(String lockValue) {
        try {
            RequestBody requestBody = RequestBody.create(lockValue, MediaType.parse("text/plain"));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(url + "/" + updateFile + LOCK_FILE_EXTENSION)
                    .method(uploadMethod.name(), requestBody);

            if (authentication != null) {
                requestBuilder.addHeader("Authorization", authentication);
            }
            Request request = requestBuilder.build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to upload lock with status: " + response.code());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to upload lock file which will cause this process to fail when we read back the lock", e);
        }
    }

    void clearLock() throws IOException {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url + "/" + updateFile + LOCK_FILE_EXTENSION)
                    .delete();

            if (authentication != null) {
                requestBuilder.addHeader("Authorization", authentication);
            }
            Request request = requestBuilder.build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to delete lock with status: " + response.code());
                }
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse the host", e);
        }
    }

    /**
     * Ensure the lock does not exist, throwing if it exists, and set the lock
     * @return the lock key
     */
    String setLockThrows() throws RetryableException {
        String lock = getLock();
        if (lock != null) {
            throw new RetryableException("Lock exists on host. Can not proceed until lock file is cleared." +
                    " This could be another process currently running.");
        }
        lock = getLockId();
        setLock(lock);
        //TODO better lock safety
        if (!lock.equals(getLock())) {
            throw new RetryableException("Another process claimed the lock while we were trying to claim it. Please try again later.");
        }
        return lock;
    }

    /**
     * Returns the plugins xml from the repo checking if the plugin being published exists and throws exception if
     * allowOverwrite is set to false (default)
     * @throws if the plugin ID and plugin version being published exists in the plugin xml from the repo
     * @return the plugins xml from the repository
     */
    PluginsElement getPluginsThrowIfOverwrite() throws FatalException {
        PluginsElement plugins = getUpdates();
        boolean pluginVersionExistsInRepo = plugins.getPlugins().stream().anyMatch(plugin ->
                pluginId.equals(plugin.getId()) && version.equals(plugin.getVersion()));

        //Prevent replacing published versions.
        if (!mutableRelease && pluginVersionExistsInRepo) {
            throw new FatalException("Plugin '" + pluginId + "' with version " + version + " already published to repository." +
                    " Because `allowOverwrite` is set to false (default), this publishing attempt will be aborted.");
        }
        return plugins;
    }

    String getUrl() {
        return url;
    }

    protected String getLockId() {
        return UUID.randomUUID().toString();
    }

    static String getPluginVersion() {
        try(InputStream is = PluginUploader.class.getResourceAsStream("/project.version")) {
            if (is != null) {
                try (InputStreamReader isr = new InputStreamReader(is)) {
                    return CharStreams.toString(isr);
                }
            }
        } catch (Exception e) {
            //Ignore
        }
        return UNKNOWN_VERSION;
    }

    public enum UploadMethod {
        POST,
        PUT;
    }

    private static class FatalException extends Exception {
        public FatalException(String message) {
            super(message);
        }

        public FatalException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static class RetryableException extends Exception {
        public RetryableException(String message) {
            super(message);
        }
    }
}
