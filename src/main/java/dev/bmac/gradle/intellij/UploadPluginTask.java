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

    //The url of the repository where updatePlugins.xml and the plugin zips will be placed
    @Input
    public final Property<String> url;
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
    //HTTP method used for uploading files
    @Input
    @Optional
    public final Property<PluginUploader.UploadMethod> uploadMethod;
    //Allows replacing an existing release which exists in the updatePluginXml file
    @Input
    @Optional
    public final Property<Boolean> allowOverwrite;

    @Inject
    public UploadPluginTask(ObjectFactory objectFactory) {
        url = objectFactory.property(String.class);
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
        uploadMethod = objectFactory.property(PluginUploader.UploadMethod.class);
        allowOverwrite = objectFactory.property(Boolean.class);
    }


    @TaskAction
    public void execute() throws Exception {
        new PluginUploader(1000, 5, getLogger(),
                url.get(),
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
                uploadMethod.getOrElse(PluginUploader.UploadMethod.POST),
                allowOverwrite.getOrElse(false)).execute();
    }

    public Property<String> getUrl() {
        return url;
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

    public Property<PluginUploader.UploadMethod> getUploadMethod() {
        return uploadMethod;
    }

    public Property<Boolean> getAllowOverwrite() {
        return allowOverwrite;
    }
}
