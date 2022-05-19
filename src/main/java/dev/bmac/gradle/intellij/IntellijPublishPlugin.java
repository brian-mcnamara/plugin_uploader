package dev.bmac.gradle.intellij;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * Simple gradle plugin to manage IntelliJ upload to a private repository as well as managing
 * updatePlugins.xml.
 * Created by brian.mcnamara on Jan 29 2020
 **/
public class IntellijPublishPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        TaskProvider<UploadPluginTask> uploadPluginTaskTaskProvider =
                project.getTasks().register(PluginUploader.TASK_NAME, UploadPluginTask.class);
        uploadPluginTaskTaskProvider.configure(it -> {
            it.dependsOn(project.getTasks()
                    .named(GenerateBlockMapTask.TASK_NAME, GenerateBlockMapTask.class));
        });
        project.getTasks().register(GenerateBlockMapTask.TASK_NAME, GenerateBlockMapTask.class).configure(it -> {
            it.file.set(uploadPluginTaskTaskProvider.get().file);
        });
    }
}
