package dev.bmac.gradle.intellij;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public class UploadPluginTask extends ConventionTask {
    public static final String UPDATE_PLUGINS_FILENAME = "updatePlugins.xml";

    //The (encoded) url of the repository where updatePlugins.xml and the plugin zips will be placed
    @Input
    public final Property<String> url;
    //Prefix of download urls in updatePlugins.xml (optional)
    @Input
    @Optional
    public final Property<String> downloadUrlPrefix;
    /**
     * Use absolute path for the download url in update plugins xml ($url/$pluginName/${file.getName})
     * @deprecated switch to downloadUrlPrefix=${url}
     */
    @Input
    @Optional
    @Deprecated()
    public final Property<Boolean> absoluteDownloadUrls;
    //The plugin name
    @Input
    public final Property<String> pluginName;
    //The plugin file to upload
    @InputFile
    public final RegularFileProperty file;
    //Name of the plugin update file.
    @Input
    @Optional
    public final Property<String> updateFile;
    //The plugin unique id
    @Input
    public final Property<String> pluginId;
    //Plugin version
    @Input
    public final Property<String> version;
    //The authentication header to add (optional)
    @Input
    @Optional
    public final Property<String> authentication;
    //Description to be added (optional)
    @Input
    @Optional
    public final Property<String> pluginDescription;
    //Change notes to be added (optional)
    @Input
    @Optional
    public final Property<String> changeNotes;
    //Whether to update {@link updateFile}
    @Input
    @Optional
    public final Property<Boolean> updatePluginXml;
    //Since idea build to prevent installs with earlier versions (optional)
    @Input
    @Optional
    public final Property<String> sinceBuild;
    //Until build to also prevent installs with newer versions (optional)
    @Input
    @Optional
    public final Property<String> untilBuild;
    //Repo type to use
    @Input
    @Optional
    public final Property<PluginUploader.RepoType> repoType;

    /**
     * @deprecated Update to use repoType
     */
    @Input
    @Optional
    @Deprecated
    public final Property<PluginUploader.UploadMethod> uploadMethod;


    /**
     * Internal inputs
     */
    @InputFile
    public final RegularFileProperty blockmapFile;
    @InputFile
    final RegularFileProperty blockmapHashFile;

    @Inject
    public UploadPluginTask(ObjectFactory objectFactory) {
        url = objectFactory.property(String.class);
        absoluteDownloadUrls = objectFactory.property(Boolean.class);
        downloadUrlPrefix = objectFactory.property(String.class);
        pluginName = objectFactory.property(String.class);
        file = objectFactory.fileProperty();
        updateFile = objectFactory.property(String.class);
        pluginId = objectFactory.property(String.class);
        version = objectFactory.property(String.class);
        authentication = objectFactory.property(String.class);
        pluginDescription = objectFactory.property(String.class);
        changeNotes = objectFactory.property(String.class);
        updatePluginXml = objectFactory.property(Boolean.class);
        sinceBuild = objectFactory.property(String.class);
        untilBuild = objectFactory.property(String.class);
        repoType = objectFactory.property(PluginUploader.RepoType.class);
        uploadMethod = objectFactory.property(PluginUploader.UploadMethod.class);
        blockmapFile = objectFactory.fileProperty();
        blockmapHashFile = objectFactory.fileProperty();
    }


    @TaskAction
    public void execute() throws Exception {
        Logger logger = getLogger();

        //TODO clean up once uploadMethod is removed
        PluginUploader.RepoType rt = repoType.getOrElse(PluginUploader.RepoType.REST_POST);
        if (uploadMethod.isPresent() && repoType.isPresent()) {
            logger.error("repoType and uploadMethod can not both be used. Remove uploadMethod as its deprecated");
        } if (uploadMethod.isPresent()) {
            logger.warn("DEPRECATED: uploadMethod has been migrated to repoType. It may be removed in future releases");
            switch (uploadMethod.get()) {
                case POST:
                    rt = PluginUploader.RepoType.REST_POST;
                    break;
                case PUT:
                    rt = PluginUploader.RepoType.REST_PUT;
                    break;
            }
        }

        new PluginUploader(1000, 5, logger,
                url.get(),
                downloadUrlPrefix.getOrNull(),
                absoluteDownloadUrls.getOrElse(false),
                pluginName.get(),
                file.get().getAsFile(),
                updateFile.getOrElse(UPDATE_PLUGINS_FILENAME),
                pluginId.get(),
                version.get(),
                authentication.getOrNull(),
                pluginDescription.getOrNull(),
                changeNotes.getOrNull(),
                updatePluginXml.getOrElse(true),
                sinceBuild.getOrNull(),
                untilBuild.getOrNull(),
                rt,
                blockmapFile.getAsFile().get(),
                blockmapHashFile.getAsFile().get()).execute();
    }

    public Property<String> getUrl() {
        return url;
    }

    public Property<String> getDownloadUrlPrefix() {
        return downloadUrlPrefix;
    }

    public Property<Boolean> getAbsoluteDownloadUrls() {
        return absoluteDownloadUrls;
    }

    public Property<String> getPluginName() {
        return pluginName;
    }

    public RegularFileProperty getFile() {
        return file;
    }

    public Property<String> getUpdateFile() {
        return updateFile;
    }

    public Property<String> getPluginId() {
        return pluginId;
    }

    public Property<String> getVersion() {
        return version;
    }

    public Property<String> getAuthentication() {
        return authentication;
    }

    public Property<String> getPluginDescription() {
        return pluginDescription;
    }

    public Property<String> getChangeNotes() {
        return changeNotes;
    }

    public Property<Boolean> getUpdatePluginXml() {
        return updatePluginXml;
    }

    public Property<String> getSinceBuild() {
        return sinceBuild;
    }

    public Property<String> getUntilBuild() {
        return untilBuild;
    }

    public Property<PluginUploader.RepoType> getRepoType() {
        return repoType;
    }

    public RegularFileProperty getBlockmapFile() {
        return blockmapFile;
    }

    public RegularFileProperty getBlockmapHashFile() {
        return blockmapHashFile;
    }

    @Deprecated
    public Property<PluginUploader.UploadMethod> getUploadMethod() {
        return uploadMethod;
    }
}
