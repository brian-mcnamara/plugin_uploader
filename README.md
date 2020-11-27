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
    url 'https://repo.example.com/intellij/plugins/'
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
| <kbd>url</kbd> - The url plus path of the repository to post the plugin to | **Required:** true <br/> **Acceptable Values:** <ul> <li>`https://repo.example.com/intellij/plugins` </li> <li>`http://repo.example.com:4443`</li></ul> |
| <kbd>pluginName</kbd> - The plugin name to be used in the upload path such that url + pluginName is the folder the plugin will be uploaded to <br/><br/>**Note:** Name will be escaped when used as the upload path | **Required:** true <br/> **Acceptable Values:** Any String|
| <kbd>file</kbd> - The file to be uploaded to the repo under url + pluginName + file.getName() |  **Required:** true <br/> **Acceptable Values:** A existing file path, ideally should be set via `project.tasks.buildPlugin as Zip` which grabs the file from the IntelliJ gradle plugin |
| <kbd>pluginId</kbd> - Plugin Id used to match in the updatePlugins.xml | **Required:** true <br/> **Acceptable Values:** Any String|
| <kbd>version</kbd> - Plugin version used to update updatePlugins.xml | **Required:** true <br/> **Acceptable Values:** Any String| 
| <kbd>description</kbd> - Plugins description to be used in updatePlugins.xml | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** Any String| 
| <kbd>changeNotes</kbd> - Plugins change notes to be used in updatePlugins.xml | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** Any String|
| <kbd>authentication</kbd> - Authentication string used to publish files to the private repo. Will be used as the authorization header | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** <ul> <li> `Basic [authenticationString]` </li> <li> `Bearer [bearerToken] ` </li> </ul>
| <kbd>updateFile</kbd> - Overrides the default updatePlugins.xml file name. | **Required:** false <br/> **Default:** <kbd>updatePlugins.xml</kbd> <br/> **Acceptable Values:** Any String |
| <kbd>updatePluginXml</kbd> - Gates whether updatePlugins.xml is updated. | **Required:** false <br/> **Default:** <kbd>true</kbd> <br/> **Acceptable Values:** `true` / `false` |

## Notes

This plugin uses a lock file to prevent concurrent modifications to the updatePlugins.xml file.
While the lock file will be cleaned up, it could be left behind if the process is forcefully interrupted
requiring the lock to be deleted manually. The lock can be found in the <kbd>url</kbd> root and is named `updatePlugins.xml.lock`
(lock file name depends on <kbd>updateFile</kbd>)