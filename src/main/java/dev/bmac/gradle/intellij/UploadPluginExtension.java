package dev.bmac.gradle.intellij;

import okhttp3.internal.http.HttpMethod;

import java.io.File;

/**
 * Created by brian.mcnamara on Jan 29 2020
 **/
public class UploadPluginExtension {
    public static final String UPDATE_PLUGINS_FILENAME = "updatePlugins.xml";
    //The url of the repository where updatePlugins.xml and the plugin zips will be placed
    private String url;
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
    private boolean updatePluginXml = true;
    //Since idea build to prevent installs with earlier versions (optional)
    private String sinceBuild;
    //Until build to also prevent installs with newer versions (optional)
    private String untilBuild;
    //HTTP method used for uploading files
    private UploadMethod uploadMethod = UploadMethod.POST;

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
        return updatePluginXml;
    }

    public void setUpdatePluginXml(boolean updatePluginXml) {
        this.updatePluginXml = updatePluginXml;
    }

    public String getUrl() {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public String getUploadMethod() {
        return uploadMethod.toString();
    }

    public void setUploadMethod(UploadMethod uploadMethod) {
        this.uploadMethod = uploadMethod;
    }

    public enum UploadMethod {
        POST,
        PUT;
    }
}
