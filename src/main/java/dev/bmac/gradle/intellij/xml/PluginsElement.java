package dev.bmac.gradle.intellij.xml;

import com.google.common.annotations.VisibleForTesting;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by brian.mcnamara on Jan 29 2020
 **/
@XmlRootElement(name = "plugins")
public class PluginsElement {

    private List<PluginElement> plugins;

    public PluginsElement() {
        plugins = new LinkedList<>();
    }

    @XmlElement(name = "plugin")
    public List<PluginElement> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<PluginElement> plugins) {
        this.plugins = plugins;
    }

}
