package dev.bmac.gradle.intellij;

import dev.bmac.gradle.intellij.repo.Repo;
import org.gradle.api.logging.Logger;

import java.io.File;

public class PluginUploaderBuilder {

    private String url;
    private boolean absoluteDownloadUrls = false;
    private String pluginName;
    private File file;
    private String updateFile = UploadPluginTask.UPDATE_PLUGINS_FILENAME;
    private String pluginId;
    private String version;
    private String authentication;
    private String description;
    private String changeNotes;
    private Boolean updatePluginXml = true;
    private String sinceBuild;
    private String untilBuild;
    private PluginUploader.RepoType repoType = PluginUploader.RepoType.REST_POST;
    private Logger logger;
    private Repo repo = null;

    public PluginUploaderBuilder(String url, String pluginName, File file, String pluginId, String version, Logger logger) {
        this.url = url;
        this.pluginName = pluginName;
        this.file = file;
        this.pluginId = pluginId;
        this.version = version;
        this.logger = logger;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setAbsoluteDownloadUrls(final boolean absoluteDownloadUrls) {
        this.absoluteDownloadUrls = absoluteDownloadUrls;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public void setUpdateFile(String updateFile) {
        this.updateFile = updateFile;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setChangeNotes(String changeNotes) {
        this.changeNotes = changeNotes;
    }

    public void setUpdatePluginXml(Boolean updatePluginXml) {
        this.updatePluginXml = updatePluginXml;
    }

    public void setSinceBuild(String sinceBuild) {
        this.sinceBuild = sinceBuild;
    }

    public void setUntilBuild(String untilBuild) {
        this.untilBuild = untilBuild;
    }

    public void setRepoType(PluginUploader.RepoType repoType) {
        this.repoType = repoType;
    }

    public void setRepoType(Repo repo) {
        this.repo = repo;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public String getUrl() {
        return url;
    }

    public boolean isAbsoluteDownloadUrls() {
        return absoluteDownloadUrls;
    }

    public String getPluginName() {
        return pluginName;
    }

    public File getFile() {
        return file;
    }

    public String getUpdateFile() {
        return updateFile;
    }

    public String getPluginId() {
        return pluginId;
    }

    public String getVersion() {
        return version;
    }

    public String getAuthentication() {
        return authentication;
    }

    public String getDescription() {
        return description;
    }

    public String getChangeNotes() {
        return changeNotes;
    }

    public Boolean getUpdatePluginXml() {
        return updatePluginXml;
    }

    public String getSinceBuild() {
        return sinceBuild;
    }

    public String getUntilBuild() {
        return untilBuild;
    }

    public PluginUploader.RepoType getRepoType() {
        return repoType;
    }

    public Logger getLogger() {
        return logger;
    }

    public PluginUploader build(String lockId) throws Exception {
        return new PluginUploader(1, 2, logger, url, absoluteDownloadUrls, pluginName, file,
                updateFile, pluginId, version, authentication, description, changeNotes, updatePluginXml,
                sinceBuild, untilBuild, repoType) {
            @Override
            protected String getLockId() {
                return lockId;
            }

            @Override
            protected Repo getRepoType() {
                if (repo == null) {
                    return super.getRepoType();
                } else {
                    return repo;
                }
            }
        };
    }
}
