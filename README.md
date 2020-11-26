# IntelliJ plugin uploader

A gradle plugin to take care of uploading an IntelliJ plugin and updating updatePlugins.xml hosted on a private repository.

## Usage

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id "dev.bmac.intellij.plugin-uploader" version "1.1.0"
}

uploadPlugin {
    //Get the plugin distribution from the buildPlugin
    def archive = project.tasks.buildPlugin as Zip
    host 'https://repo.example.com/intellij/plugins/'
    pluginName 'PluginName'
    file archive.archivePath
    pluginId project.group
    version project.version
    description file('description.txt').text
    changeNotes file('change-notes.txt').text
    authentication 'Basic ' + new String(Base64.encoder.encode((username + ":" + password).bytes))
}
```

## Configuration

| Attributes | Values | 
| :------------- | :--------- |
| host | The host plus path of the repository to post the plugin to |
| pluginName | The plugin name to be used in the upload path such that host + pluginName is the folder the plugin will be uploaded to |
| file | The file to be uploaded to the repo under host + pluginName + file.getName() |
| pluginId | Plugin Id used to match in the updatePlugins.xml
| version | Plugin version used to update updatePlugins.xml | 
| description | *Optional* Plugins description to be used in updatePlugins.xml | 
| changeNotes | *Optional* Plugins change notes to be used in updatePlugins.xml |
| authentication | *Optional* Authentication string used to publish files to the private repo. Will be used as the authorization header |
| updateFile | *Optional* Overrides the default update file name. Defaults to 'updatePlugins.xml' |
| writeToUpdateXml | *Optional* Gates whether updatePlugins.xml is updated. Defaults to 'true' |
