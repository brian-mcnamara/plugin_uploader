package dev.bmac.gradle.intellij;

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
import java.net.URI;
import java.util.UUID;

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

    public IntellijPublishPlugin() throws Exception {
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
            String lock = null;
            try {
                lock = getLock(extension);
                if (lock != null) {
                    lock = null;
                    throw new GradleException("Lock exists on host. Can not proceed until lock file is cleared." +
                            " This could be another process currently running.");
                }
                lock = getLockId();
                setLock(extension, lock);
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
                        clearLock(extension);
                    } else {
                        logger.error("The lock value changed during execution. This is bad! " + extension.getUpdateFile() + " may be invalid");
                    }
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

    void setLock(UploadPluginExtension extension, String lockValue) {
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
                    throw new RuntimeException("Failed to upload lock with status: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void clearLock(UploadPluginExtension extension) {
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
                    throw new RuntimeException("Failed to delete lock with status: " + response.code());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getLockId() {
        return UUID.randomUUID().toString();
    }
}
