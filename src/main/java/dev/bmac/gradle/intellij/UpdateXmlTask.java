package dev.bmac.gradle.intellij;

import dev.bmac.gradle.intellij.xml.PluginElement;
import dev.bmac.gradle.intellij.xml.PluginsElement;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;

public class UpdateXmlTask extends ConventionTask {

    //Name of the plugin update file.
    @OutputFile
    public final RegularFileProperty updateFile;
    //The relative or full url where the plugin-version can be downloaded
    @Input
    public final Property<String> downloadUrl;
    //The plugin name
    @Input
    public final Property<String> pluginName;
    //The plugin unique id
    @Input
    public final Property<String> pluginId;
    //Plugin version
    @Input
    public final Property<String> version;
    //Description to be added (optional)
    @Input
    @Optional
    public final Property<String> pluginDescription;
    //Change notes to be added (optional)
    @Input
    @Optional
    public final Property<String> changeNotes;
    //Since idea build to prevent installs with earlier versions (optional)
    @Input
    @Optional
    public final Property<String> sinceBuild;
    //Until build to also prevent installs with newer versions (optional)
    @Input
    @Optional
    public final Property<String> untilBuild;

    @Inject
    public UpdateXmlTask(ObjectFactory objectFactory) {
        this.updateFile = objectFactory.fileProperty();
        this.downloadUrl = objectFactory.property(String.class);
        this.pluginName = objectFactory.property(String.class);
        this.pluginId = objectFactory.property(String.class);
        this.version = objectFactory.property(String.class);
        this.pluginDescription = objectFactory.property(String.class);
        this.changeNotes = objectFactory.property(String.class);
        this.sinceBuild = objectFactory.property(String.class);
        this.untilBuild = objectFactory.property(String.class);
    }

    @TaskAction
    public void execute() throws Exception {
        File updateFile = this.updateFile.getAsFile().get();
        PluginsElement pluginsElement = new PluginsElement();
        if (updateFile.exists()) {
             pluginsElement = (PluginsElement) PluginUpdatesUtil.UNMARSHALLER.unmarshal(updateFile);
        } else {
            getLogger().info(updateFile.getName() + " not found, creating new file");
        }

        PluginElement pluginElement = new PluginElement(pluginId.get(), version.get(), pluginDescription.getOrNull(),
                changeNotes.getOrNull(), pluginName.get(), sinceBuild.getOrNull(), untilBuild.getOrNull(), downloadUrl.get());
        PluginUpdatesUtil.updateOrAdd(pluginElement, pluginsElement.getPlugins(), getLogger());

        PluginUpdatesUtil.MARSHALLER.marshal(pluginsElement, updateFile);
    }

    public RegularFileProperty getUpdateFile() {
        return updateFile;
    }

    public Property<String> getDownloadUrl() {
        return downloadUrl;
    }

    public Property<String> getPluginName() {
        return pluginName;
    }

    public Property<String> getPluginId() {
        return pluginId;
    }

    public Property<String> getVersion() {
        return version;
    }

    public Property<String> getPluginDescription() {
        return pluginDescription;
    }

    public Property<String> getChangeNotes() {
        return changeNotes;
    }

    public Property<String> getSinceBuild() {
        return sinceBuild;
    }

    public Property<String> getUntilBuild() {
        return untilBuild;
    }
}
