package dev.bmac.gradle.intellij.xml;

import com.intellij.openapi.util.BuildNumber;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;

public class IdeaVersionElement {
    //<idea-version since-build="181.3" until-build="191.*" />
    private BuildNumber sinceBuild;
    private BuildNumber untilBuild;

    public IdeaVersionElement() {

    }

    public IdeaVersionElement(String since, String until) {
        this(BuildNumber.fromString(since), BuildNumber.fromString(until));
    }

    public IdeaVersionElement(BuildNumber since, BuildNumber until) {
        this.sinceBuild = since;
        this.untilBuild = until;
    }

    @XmlAttribute(name = "since-build")
    public String getSinceBuildString() {
        return sinceBuild == null ? null : sinceBuild.asString();
    }

    public void setSinceBuildString(String sinceBuild) {
        this.sinceBuild = BuildNumber.fromString(sinceBuild);
    }

    @XmlAttribute(name = "until-build")
    public String getUntilBuildString() {
        return untilBuild == null ? null : untilBuild.asString();
    }

    public void setUntilBuildString(String untilBuild) {
        this.untilBuild = BuildNumber.fromString(untilBuild);
    }

    public BuildNumber getSinceBuild() {
        return sinceBuild;
    }

    public BuildNumber getUntilBuild() {
        return untilBuild;
    }

    @XmlTransient
    public void setUntilBuild(BuildNumber untilBuild) {
        this.untilBuild = untilBuild;
    }

    @XmlTransient
    void setSinceBuild(BuildNumber sinceBuild) {
        this.sinceBuild = sinceBuild;
    }

}
