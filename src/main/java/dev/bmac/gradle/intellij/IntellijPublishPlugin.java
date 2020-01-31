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
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Simple gradle plugin to manage IntelliJ upload to a private repository as well as managing
 * updatePlugins.xml.
 * Created by brian.mcnamara on Jan 29 2020
 **/
public class IntellijPublishPlugin implements Plugin<Project> {
    static final String UPDATE_PLUGIN = "updatePlugins.xml";
    static final String UPDATE_PLUGIN_EAP = "updatePlugins-EAP.xml";
    static final String LOCK_FILE = "publisher.lock";
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
        String lock = null;
        try {
            lock = getLock(extension);
            if (lock != null) {
                lock = null;
                throw new GradleException("Lock exists on host. Can not proceed until lock is cleared");
            }
            lock = getLockId();
            setLock(extension, lock);
            //TODO better lock safety
            if (!lock.equals(getLock(extension))) {
                lock = null;
                throw new GradleException("Another process claimed the lock. Please try again later.");
            }
            postPlugin(extension, logger);
            PluginUpdates.Plugin plugin = new PluginUpdates.Plugin(extension);
            PluginUpdates updates = getUpdates(extension);
            updates.updateOrAdd(plugin);
            postUpdates(extension, updates);
        } finally {
            if (lock != null) {
                if (lock.equals(getLock(extension))) {
                    clearLock(extension);
                } else {
                    logger.error("The lock value changed during execution. This is bad! updatePlugins.xml may be invalid");
                }
            }
        }
    }

    void postPlugin(UploadPluginExtension extension, Logger logger) {
        String pluginEndpoint = extension.getPluginName() + "/" + extension.getFile().getName();

        RequestBody requestBody = RequestBody.create(extension.getFile(), MediaType.parse("application/zip"));

        Request.Builder requestBuilder = new Request.Builder()
                .url(extension.getHost() + pluginEndpoint)
                .post(requestBody);

        if (extension.getAuthentication() != null) {
            requestBuilder.addHeader("Authorization", extension.getAuthentication());
        }
        Request request = requestBuilder.build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to upload plugin with status: " + response.code());
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

            String fileName = extension.isProduction() ? UPDATE_PLUGIN : UPDATE_PLUGIN_EAP;

            RequestBody requestBody = RequestBody.create(file, MediaType.parse("application/xml"));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(extension.getHost() + fileName)
                    .post(requestBody);

            if (extension.getAuthentication() != null) {
                requestBuilder.addHeader("Authorization", extension.getAuthentication());
            }
            Request request = requestBuilder.build();

            try (Response response = CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Failed to upload pluginUpdates.xml with status: " + response.code());
                }
            }
        } catch (IOException | JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    PluginUpdates getUpdates(UploadPluginExtension extension) {
        Request request = new Request.Builder()
                .url(extension.getHost() + (extension.isProduction() ? UPDATE_PLUGIN : UPDATE_PLUGIN_EAP))
                .get()
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.code() == 404) {
                System.out.println("No pluginUpdates.xml found");
                return new PluginUpdates();
            } else if (response.isSuccessful()) {
                ResponseBody body = response.body();
                if (body == null) {
                    throw new RuntimeException("Body was null for updatePlugins");
                }
                return (PluginUpdates) unmarshaller.unmarshal(body.byteStream());
            } else {
                throw new RuntimeException("Received an unknown status code while retrieving pluginUpdates.xml");
            }

        } catch (IOException | JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    String getLock(UploadPluginExtension extension) {
        Request request = new Request.Builder()
                .url(extension.getHost() + LOCK_FILE)
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

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void setLock(UploadPluginExtension extension, String lockValue) {
        try {
            RequestBody requestBody = RequestBody.create(lockValue, MediaType.parse("text/plain"));

            Request.Builder requestBuilder = new Request.Builder()
                    .url(extension.getHost() + LOCK_FILE)
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
                    .url(extension.getHost() + LOCK_FILE)
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
