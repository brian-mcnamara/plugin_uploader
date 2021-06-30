package dev.bmac.gradle.intellij.xml;

import com.google.common.net.UrlEscapers;
import dev.bmac.gradle.intellij.UploadPluginExtension;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

public class PluginElement {
    private String id;
    private String url;
    private String version;
    private String description;
    private String changeNotes;
    private String name;
    private IdeaVersionElement versionInfo;

    public PluginElement() {

    }

    public PluginElement(UploadPluginExtension extension) {
        this.id = extension.getPluginId();
        this.name = extension.getPluginName();
        this.version = extension.getVersion();
        this.description = extension.getDescription();
        this.changeNotes = extension.getChangeNotes();
        this.url = UrlEscapers.urlFragmentEscaper().escape("./" + extension.getPluginName() + "/" + extension.getFile().getName());


        if (extension.getUntilBuild() != null || extension.getSinceBuild() != null) {
            versionInfo = new IdeaVersionElement(extension.getSinceBuild(), extension.getUntilBuild());
        }
    }

    @XmlAttribute
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlAttribute
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @XmlAttribute
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @XmlElement
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "change-notes")
    public String getChangeNotes() {
        return changeNotes;
    }

    public void setChangeNotes(String changeNotes) {
        this.changeNotes = changeNotes;
    }

    @XmlElement()
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    @Nullable
    public IdeaVersionElement getVersionInfo() {
        return versionInfo;
    }

    public void setVersionInfo(IdeaVersionElement versionInfo) {
        this.versionInfo = versionInfo;
    }

}
