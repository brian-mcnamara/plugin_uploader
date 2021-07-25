package dev.bmac.gradle.intellij;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Simple gradle plugin to manage IntelliJ upload to a private repository as well as managing
 * updatePlugins.xml.
 * Created by brian.mcnamara on Jan 29 2020
 **/
public class IntellijPublishPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        project.getTasks().register("uploadPlugin", UploadPluginTask.class);
    }
}
