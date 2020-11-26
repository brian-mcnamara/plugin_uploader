package dev.bmac.gradle.intellij;

import java.io.File;

/**
 * Created by brian.mcnamara on Jan 29 2020
 **/
public class UploadPluginExtension {
    public static final String UPDATE_PLUGINS_FILENAME = "updatePlugins.xml";
    //The host of the repository where updatePlugins.xml and the plugin zips will be placed
    private String host;
    //The plugin name
    private String pluginName;
    //The plugin file to upload
    private File file;
    //Name of the plugin update file.
    private String updateFile = UPDATE_PLUGINS_FILENAME;
    //The plugin unique id
    private String pluginId;
    //Plugin version
    private String version;
    //The authentication header to add (optional)
    private String authentication;
    //Description to be added (optional)
    private String description;
    //Change notes to be added (optional)
    private String changeNotes;
    //Whether to update {@link updateFile}
    private boolean writeToUpdateXml = true;
    //Since idea build to prevent installs with earlier versions (optional)
    private String sinceBuild;
    //Until build to also prevent installs with newer versions (optional)
    private String untilBuild;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getChangeNotes() {
        return changeNotes;
    }

    public void setChangeNotes(String changeNotes) {
        this.changeNotes = changeNotes;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public boolean writeToUpdateXml() {
        return writeToUpdateXml;
    }

    public void setWriteToUpdateXml(boolean writeToUpdateXml) {
        this.writeToUpdateXml = writeToUpdateXml;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getAuthentication() {
        return authentication;
    }

    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSinceBuild() {
        return sinceBuild;
    }

    public void setSinceBuild(String sinceBuild) {
        this.sinceBuild = sinceBuild;
    }

    public String getUntilBuild() {
        return untilBuild;
    }

    public void setUntilBuild(String untilBuild) {
        this.untilBuild = untilBuild;
    }

    public String getUpdateFile() {
        return updateFile;
    }

    public void setUpdateFile(String updateFile) {
        this.updateFile = updateFile;
    }
}
