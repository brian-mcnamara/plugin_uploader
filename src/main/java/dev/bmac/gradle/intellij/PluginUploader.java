package dev.bmac.gradle.intellij;

import com.github.rholder.retry.*;
import com.google.common.io.ByteSource;
import com.google.common.io.CharStreams;
import com.sun.istack.Nullable;
import dev.bmac.gradle.intellij.repo.Repo;
import dev.bmac.gradle.intellij.repo.RestRepo;
import dev.bmac.gradle.intellij.repo.S3Repo;
import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    //System property to allow skipping the check which prevents replacing a released release.
    public static final String RELEASE_CHECK_PROPERTY = "dev.bmac.pluginUploader.skipReleaseCheck";

    static final String UNKNOWN_VERSION = "UNKNOWN";
    static final String LOCK_FILE_EXTENSION = ".lock";

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
    private final RepoType repoType;

    private final boolean skipReleaseCheck = Boolean.parseBoolean(System.getProperty(RELEASE_CHECK_PROPERTY, "false"));
    private final Repo repo;

    public PluginUploader(int timeoutMs, int retryTimes, Logger logger,
                          @NotNull String url, boolean absoluteDownloadUrls, @NotNull String pluginName,
                          @NotNull File file, @NotNull String updateFile, @NotNull String pluginId,
                          @NotNull String version, String authentication, String description, String changeNotes,
                          @NotNull Boolean updatePluginXml, String sinceBuild, String untilBuild,
                          @NotNull RepoType repoType) throws Exception {

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
        this.repoType = repoType;

        JAXBContext contextObj = JAXBContext.newInstance(PluginsElement.class);

        marshaller = contextObj.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);

        unmarshaller = contextObj.createUnmarshaller();

        this.repo = getRepoType();
    }

    /**
     * Main execution
     */
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
                throw new GradleException(e.getMessage(), e);
            }
            uploadPlugin();
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

    /**
     * Creates a lock, grabs the current updatePlugins.xml, ensures there is not a version conflict,
     * uploads the plugin file, uploads the updated updatePlugins.xml and deletes the lock at the end
     * @throws RetryableException
     * @throws FatalException
     */
    void postPluginAndUpdateXml() throws RetryableException, FatalException {
        String lock = uploadLockThrows();

        try {
            PluginsElement plugins = getPluginsThrowIfOverwrite();

            uploadPlugin();

            PluginElement plugin = new PluginElement(pluginId, version, description, changeNotes, pluginName,
                    sinceBuild, untilBuild, file, absoluteDownloadUrls ? url : ".");
            PluginUpdatesUtil.updateOrAdd(plugin, plugins.getPlugins(), logger);

            uploadUpdates(plugins);
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
                            deleteLock();
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

    /**
     * Uploads the plugin file
     */
    void uploadPlugin() {
        String pluginEndpoint = pluginName + "/" + file.getName();

        try {
            repo.upload(pluginEndpoint, file, "application/zip");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Uploads the updatePlugins.xml file adding a comment on top to indicate the time, this gradle plugins version,
     * and plugin used to update the file
     * @param updates The updatePlugins.xml POJO to marshal
     */
    void uploadUpdates(PluginsElement updates) {
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

            repo.upload(updateFile, file, "application/xml");
        } catch (IOException | JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Grabs the latest updatePlugin.xml from the repo and unmarshaling it.
     * Returns an empty POJO if the file does not exist in the repo
     * @return The unmarshaled file from the repo or an empty one if it does not exist
     */
    PluginsElement getUpdates() {
        try {
            return repo.get(updateFile, update -> {
                if (update.exists()) {
                    try {
                        return (PluginsElement) unmarshaller.unmarshal(update.getInputStream());
                    } catch (JAXBException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    logger.info("No " + updateFile + " found. Creating new file.");
                    return new PluginsElement();
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Grabs the lock file from the repo and returns the contents
     * @return The locks content or null if it does not exist.
     */
    @Nullable
    String getLock() {
        try {
            return repo.get(updateFile + LOCK_FILE_EXTENSION, l -> {
                if (l.exists()) {
                    ByteSource bs = new ByteSource() {
                        @Override
                        public InputStream openStream() {
                            return l.getInputStream();
                        }
                    };
                    try {
                        return bs.asCharSource(StandardCharsets.UTF_8).read();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    return null;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Upload the lock to the server.
     * Note: This does not throw exceptions. It is expected to check the lock after this method is called to verify this
     * process acquired the lock.
     */
    void setLock(String lockValue) {
        File lockFile = null;
        try {
            lockFile = Files.createTempFile(pluginId, "lock").toFile();
            try (FileOutputStream fos = new FileOutputStream(lockFile)) {
                fos.write(lockValue.getBytes(StandardCharsets.UTF_8));
            }
            repo.upload(updateFile + LOCK_FILE_EXTENSION, lockFile, "text/plain");
        } catch (IOException e) {
            logger.error("Failed to upload lock file which will cause this process to fail when we read back the lock", e);
        } finally {
            if (lockFile != null) {
                lockFile.delete();
            }
        }
    }

    /**
     * Deletes the lock on the server
     * @throws IOException
     */
    void deleteLock() throws IOException {
        repo.delete(updateFile + LOCK_FILE_EXTENSION);
    }

    /**
     * Ensure the lock does not exist, throwing if it exists, and sets the lock by uploading the lock file to the repo
     * @return the lock key
     */
    String uploadLockThrows() throws RetryableException {
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
        if (!skipReleaseCheck && pluginVersionExistsInRepo) {
            throw new FatalException("Plugin '" + pluginId + "' with version " + version + " already published to repository." +
                    " Because `allowOverwrite` is set to false (default), this publishing attempt will be aborted.");
        }
        return plugins;
    }

    protected String getLockId() {
        return UUID.randomUUID().toString();
    }

    protected Repo getRepoType() {
        switch (repoType) {
            case REST_POST:
            case REST_PUT:
                return new RestRepo(url, authentication, logger, repoType);
            case S3:
                return new S3Repo(url, authentication, logger);
            default:
                throw new IllegalStateException("Upload method not implemented for " + repoType.name());
        }
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

    public enum RepoType {
        REST_POST,
        REST_PUT,
        S3
    }

    /**
     * @deprecated Migrated to using RepoType
     */
    @Deprecated
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
