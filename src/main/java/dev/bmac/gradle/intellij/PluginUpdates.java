package dev.bmac.gradle.intellij;


import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Created by brian.mcnamara on Jan 29 2020
 **/
@XmlRootElement(name = "plugins")
public class PluginUpdates {

    @XmlElement(name = "plugin")
    private Set<Plugin> plugins = new HashSet<>();

    public void updateOrAdd(Plugin plugin) {
        plugins.remove(plugin);
        plugins.add(plugin);
    }

    static class Plugin {
        private String id;
        private String url;
        private String version;
        private String description;
        private String changeNotes;
        private String name;
        private IdeaVersion versionInfo;

        public Plugin() {

        }

        public Plugin(UploadPluginExtension extension) {
            this.id = extension.getPluginId();
            this.name = extension.getPluginName();
            this.version = extension.getVersion();
            this.description = extension.getDescription();
            this.changeNotes = extension.getChangeNotes();
            this.url = "./" + extension.getPluginName() + "/" + extension.getFile().getName();

            if (extension.getUntilBuild() != null || extension.getSinceBuild() != null) {
                versionInfo = new IdeaVersion(extension.getSinceBuild(), extension.getUntilBuild());
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
        public IdeaVersion getVersionInfo() {
            return versionInfo;
        }

        public void setVersionInfo(IdeaVersion versionInfo) {
            this.versionInfo = versionInfo;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Plugin plugin = (Plugin) o;
            return id.equals(plugin.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        static class IdeaVersion {
            //<idea-version since-build="181.3" until-build="191.*" />
            private String sinceBuild;
            private String untilBuild;

            public IdeaVersion() {

            }

            public IdeaVersion(String since, String until) {
                this.sinceBuild = since;
                this.untilBuild = until;
            }

            @XmlAttribute(name = "since-build")
            public String getSinceBuild() {
                return sinceBuild;
            }

            public void setSinceBuild(String sinceBuild) {
                this.sinceBuild = sinceBuild;
            }
            @XmlAttribute(name = "until-build")
            public String getUntilBuild() {
                return untilBuild;
            }

            public void setUntilBuild(String untilBuild) {
                this.untilBuild = untilBuild;
            }
        }
    }

}
