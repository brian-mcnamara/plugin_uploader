package dev.bmac.gradle.intellij;

import com.github.rholder.retry.Attempt;
import com.github.rholder.retry.RetryException;
import com.github.rholder.retry.RetryListener;
import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
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
import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PluginUploader {

    static final String LOCK_FILE_EXTENSION = ".lock";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();

    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;
    private final int timeoutMs;
    private final int retryTimes;
    private final Logger logger;

    private final String url;
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

    public PluginUploader(int timeoutMs, int retryTimes, Logger logger,
                          @NotNull String url, @NotNull String pluginName, @NotNull File file, @NotNull String updateFile,
                          @NotNull String pluginId, @NotNull String version, String authentication,
                          String description, String changeNotes, @NotNull Boolean updatePluginXml,
                          String sinceBuild, String untilBuild, @NotNull UploadMethod uploadMethod) throws Exception {

        this.timeoutMs = timeoutMs;
        this.retryTimes = retryTimes;
        this.logger = logger;
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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

        postPlugin();
        if (updatePluginXml) {
            final AtomicReference<Throwable> firstException = new AtomicReference<>();
            Retryer<Void> retryer = RetryerBuilder.<Void>newBuilder()
                    .retryIfException()
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
                    postUpdateXml();
                    return null;
                });
            } catch (ExecutionException | RetryException e) {
                Throwable cause = firstException.get();
                throw new GradleException("Failed to update " + file, cause != null ? cause : e);
            }
        }
    }

    void postUpdateXml() {
        String lock = null;
        try {
            lock = getLock();
            if (lock != null) {
                lock = null;
                throw new GradleException("Lock exists on host. Can not proceed until lock file is cleared." +
                        " This could be another process currently running.");
            }
            lock = getLockId();
            setLock(lock);
            //TODO better lock safety
            if (!lock.equals(getLock())) {
                lock = null;
                throw new GradleException("Another process claimed the lock while we were trying to claim it. Please try again later.");
            }
            PluginElement plugin = new PluginElement(pluginId, version, description, changeNotes, pluginName,
                    sinceBuild, untilBuild, file);
            PluginsElement updates = getUpdates();
            PluginUpdatesUtil.updateOrAdd(plugin, updates.getPlugins(), logger);
            postUpdates(updates);
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
                        logger.error("Failed to cleanup " + file + LOCK_FILE_EXTENSION + ". File must be cleaned up manually", e);
                    }
                } else {
                    logger.error("The lock value changed during execution. This is bad! " + file + " may be invalid");
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

            FileWriter fw = new FileWriter(file);
            marshaller.marshal(updates, fw);
            fw.close();

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

    String getUrl() {
        return url;
    }

    protected String getLockId() {
        return UUID.randomUUID().toString();
    }

    public enum UploadMethod {
        POST,
        PUT;
    }
}
