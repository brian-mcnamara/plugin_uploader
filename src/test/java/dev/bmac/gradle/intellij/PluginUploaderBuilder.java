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

    public PluginUploaderBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    public PluginUploaderBuilder setAbsoluteDownloadUrls(final boolean absoluteDownloadUrls) {
        this.absoluteDownloadUrls = absoluteDownloadUrls;
        return this;
    }

    public PluginUploaderBuilder setPluginName(String pluginName) {
        this.pluginName = pluginName;
        return this;
    }

    public PluginUploaderBuilder setFile(File file) {
        this.file = file;
        return this;
    }

    public PluginUploaderBuilder setUpdateFile(String updateFile) {
        this.updateFile = updateFile;
        return this;
    }

    public PluginUploaderBuilder setPluginId(String pluginId) {
        this.pluginId = pluginId;
        return this;
    }

    public PluginUploaderBuilder setVersion(String version) {
        this.version = version;
        return this;
    }

    public PluginUploaderBuilder setAuthentication(String authentication) {
        this.authentication = authentication;
        return this;
    }

    public PluginUploaderBuilder setDescription(String description) {
        this.description = description;
        return this;
    }

    public PluginUploaderBuilder setChangeNotes(String changeNotes) {
        this.changeNotes = changeNotes;
        return this;
    }

    public PluginUploaderBuilder setUpdatePluginXml(Boolean updatePluginXml) {
        this.updatePluginXml = updatePluginXml;
        return this;
    }

    public PluginUploaderBuilder setSinceBuild(String sinceBuild) {
        this.sinceBuild = sinceBuild;
        return this;
    }

    public PluginUploaderBuilder setUntilBuild(String untilBuild) {
        this.untilBuild = untilBuild;
        return this;
    }

    public PluginUploaderBuilder setRepoType(PluginUploader.RepoType repoType) {
        this.repoType = repoType;
        return this;
    }

    public PluginUploaderBuilder setLogger(Logger logger) {
        this.logger = logger;
        return this;
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
