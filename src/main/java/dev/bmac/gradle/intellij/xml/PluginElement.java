package dev.bmac.gradle.intellij.xml;

import com.google.common.net.UrlEscapers;

import javax.annotation.Nullable;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.io.File;

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

    public PluginElement(String id, String version, String description, String changeNotes,
                         String pluginName, String sinceBuild, String untilBuild, File file) {
        this.id = id;
        this.url = UrlEscapers.urlFragmentEscaper().escape("./" + pluginName + "/" + file.getName());
        this.version = version;
        this.description = description;
        this.changeNotes = changeNotes;
        this.name = pluginName;
        if (untilBuild != null || sinceBuild != null) {
            versionInfo = new IdeaVersionElement(sinceBuild, untilBuild);
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

    @XmlElement(name = "idea-version")
    @Nullable
    public IdeaVersionElement getVersionInfo() {
        return versionInfo;
    }

    public void setVersionInfo(IdeaVersionElement versionInfo) {
        this.versionInfo = versionInfo;
    }

}
