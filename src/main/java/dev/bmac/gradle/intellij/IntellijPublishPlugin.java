package dev.bmac.gradle.intellij;

import com.github.rholder.retry.*;
import com.sun.istack.Nullable;
import okhttp3.*;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple gradle plugin to manage IntelliJ upload to a private repository as well as managing
 * updatePlugins.xml.
 * Created by brian.mcnamara on Jan 29 2020
 **/
public class IntellijPublishPlugin implements Plugin<Project> {
    static final String LOCK_FILE_EXTENSION = ".lock";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();

    private final Marshaller marshaller;
    private final Unmarshaller unmarshaller;
    private final int timeoutMs;
    private final int retryTimes;

    public IntellijPublishPlugin() throws Exception {
        this(1000, 5);
    }

    IntellijPublishPlugin(int timeoutMs, int retryTimes) throws Exception {
        this.timeoutMs = timeoutMs;
        this.retryTimes = retryTimes;
        JAXBContext contextObj = JAXBContext.newInstance(PluginUpdates.class);

        marshaller = contextObj.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        unmarshaller = contextObj.createUnmarshaller();
    }

    @Override
    public void apply(Project project) {
        UploadPluginExtension extension = project.getExtensions()
                .create("uploadPlugin", UploadPluginExtension.class);

        project.task("uploadPlugin").doLast(task -> {
            execute(extension, project.getLogger());
        });
    }

    void execute(UploadPluginExtension extension, Logger logger) {
        if (extension.getHost() == null || extension.getPluginName() == null ||
                extension.getFile() == null || extension.getPluginId() == null || extension.getVersion() == null) {
            throw new RuntimeException("Must specify host, pluginName, pluginId, version and file to uploadPlugin");
        }
        if (extension.writeToUpdateXml() && extension.getUpdateFile() == null) {
            throw new RuntimeException("updateFile can not be null");
        }

        postPlugin(extension, logger);
        if (extension.writeToUpdateXml()) {
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
                    postUpdateXml(extension, logger);
                    return null;
                });
            } catch (ExecutionException | RetryException e) {
                Throwable cause = firstException.get();
                throw new GradleException("Failed to update " + extension.getUpdateFile(), cause != null ? cause : e);
            }
        }
    }

    void postUpdateXml(UploadPluginExtension extension, Logger logger) {
        String lock = null;
        try {
            lock = getLock(extension);
            if (lock != null) {
                lock = null;
                throw new GradleException("Lock exists on host. Can not proceed until lock file is cleared." +
                        " This could be another process currently running.");
            }
            lock = getLockId();
            setLock(extension, lock, logger);
            //TODO better lock safety
            if (!lock.equals(getLock(extension))) {
                lock = null;
                throw new GradleException("Another process claimed the lock while we were trying to claim it. Please try again later.");
            }
            PluginUpdates.Plugin plugin = new PluginUpdates.Plugin(extension);
            PluginUpdates updates = getUpdates(extension, logger);
            updates.updateOrAdd(plugin);
            postUpdates(extension, updates);
        } finally {
            if (lock != null) {
                if (lock.equals(getLock(extension))) {
                    Retryer<Object> retryer = RetryerBuilder.newBuilder()
                            .retryIfExceptionOfType(IOException.class)
                            .withStopStrategy(StopStrategies.stopAfterAttempt(retryTimes))
                            .withWaitStrategy(WaitStrategies.fixedWait(timeoutMs, TimeUnit.MILLISECONDS))
                            .build();
                    try {
                        retryer.call(() -> {
                            clearLock(extension);
                            return null;
                        });
                    } catch (ExecutionException | RetryException e) {
                        logger.error("Failed to cleanup " + extension.getUpdateFile() + LOCK_FILE_EXTENSION + ". File must be cleaned up manually", e);
                    }
                } else {
                    logger.error("The lock value changed during execution. This is bad! " + extension.getUpdateFile() + " may be invalid");
                }
            }
        }
    }

    void postPlugin(UploadPluginExtension extension, Logger logger) {
        String pluginEndpoint = extension.getPluginName() + "/" + extension.getFile().getName();

        RequestBody requestBody = RequestBody.create(extension.getFile(), MediaType.parse("application/zip"));

        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(URI.create(extension.getHost() + "/" + pluginEndpoint).normalize().toURL())
                    .post(requestBody);

            if (extension.getAuthentication() != null) {
                requestBuilder.addHeader("Authorization", extension.getAuthentication());
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

    void postUpdates(UploadPluginExtension extension, PluginUpdates updates) {
        try {
            File file = File.createTempFile("updatePlugins", null);
            file.deleteOnExit();

            FileWriter fw = new FileWriter(file);
            marshaller.marshal(updates, fw);
            fw.close();

            String fileName = extension.getUpdateFile();

            RequestBody requestBody = RequestBody.create(file, MediaType.parse("application/xml"));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(URI.create(extension.getHost() + "/" + fileName).normalize().toURL())
                    .post(requestBody);

            if (extension.getAuthentication() != null) {
                requestBuilder.addHeader("Authorization", extension.getAuthentication());
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

    PluginUpdates getUpdates(UploadPluginExtension extension, Logger logger) {
        String updateFile = extension.getUpdateFile();
        try {
            Request request = new Request.Builder()
                    .url(URI.create(extension.getHost() + "/" + updateFile).normalize().toURL())
                    .get()
                    .build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (response.code() == 404) {
                    logger.info("No " + updateFile + " found. Creating new file.");
                    return new PluginUpdates();
                } else if (response.isSuccessful()) {
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new RuntimeException("Body was null for " + updateFile);
                    }
                    return (PluginUpdates) unmarshaller.unmarshal(body.byteStream());
                } else {
                    throw new RuntimeException("Received an unknown status code while retrieving " + updateFile);
                }

            }
        } catch (IOException | JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    String getLock(UploadPluginExtension extension) {
        try {
            Request request = new Request.Builder()
                    .url(URI.create(extension.getHost() + "/" + extension.getUpdateFile() + LOCK_FILE_EXTENSION).normalize().toURL())
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
    void setLock(UploadPluginExtension extension, String lockValue, Logger logger) {
        try {
            RequestBody requestBody = RequestBody.create(lockValue, MediaType.parse("text/plain"));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(URI.create(extension.getHost() + "/" + extension.getUpdateFile() + LOCK_FILE_EXTENSION).normalize().toURL())
                    .post(requestBody);

            if (extension.getAuthentication() != null) {
                requestBuilder.addHeader("Authorization", extension.getAuthentication());
            }
            Request request = requestBuilder.build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Failed to upload lock with status: " + response.code());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to post lock file which will cause this process to fail when we read back the lock", e);
        }
    }

    void clearLock(UploadPluginExtension extension) throws IOException {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(URI.create(extension.getHost() + "/" + extension.getUpdateFile() + LOCK_FILE_EXTENSION).normalize().toURL())
                    .delete();

            if (extension.getAuthentication() != null) {
                requestBuilder.addHeader("Authorization", extension.getAuthentication());
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

    protected String getLockId() {
        return UUID.randomUUID().toString();
    }
}
