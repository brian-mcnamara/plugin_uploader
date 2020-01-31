# IntelliJ plugin uploader

A gradle plugin to take care of uploading and updating updatePlugins.xml hosted on a private repository.

## Usage

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id "dev.bmac.intellij.plugin-uploader" version "1.0.1"
}

uploadPlugin {
    def archive = project.tasks.buildPlugin as Zip
    host 'https://repo.example.com/intellij/plugins/'
    pluginName 'PluginName'
    pluginId project.group
    version project.version
    production System.getenv("IS_PRODUCTION")
    description file('description.txt').text
    changeNotes file('change-notes.txt').text
    authentication 'Basic ' + new String(Base64.encoder.encode((username + ":" + password).bytes))
    file archive.archivePath
}
```