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
    id "dev.bmac.intellij.plugin-uploader" version "1.2.0"
}

uploadPlugin {
    // Get the plugin distribution file from the buildPlugin task provided from the gradle-intellij-plugin
    def archive = project.tasks.buildPlugin as Zip
    // For security, do not hard code usernames or passwords in source control, instead load them through the gradle properties:
    // <code> findProperty('some.gradle.property') as String </code>
    // or through Environment variables:
    // <code> System.getenv('SOME_ENVIRONMENT_VARIABLE') </code>
    def username = "exampleUsername" 
    def password = "examplePassword"
    url 'https://repo.example.com/intellij/plugins/'
    pluginName 'PluginName'
    file archive.archivePath
    pluginId project.group
    version project.version
    description file('description.txt').text
    changeNotes file('change-notes.txt').text
    sinceBuild "211"
    // Example for Basic type authentication
    authentication 'Basic ' + new String(Base64.encoder.encode(("$username:$password").bytes))
}
```

## Configuration

| Attributes | Values | 
| :------------- | :--------- |
| <kbd>url</kbd> - The url plus path of the repository to post the plugin to | **Required:** true <br/> **Acceptable Values:** Any URL, for example: <ul> <li>`https://repo.example.com/intellij/plugins` </li> <li>`http://repo.example.com:4443`</li></ul> |
| <kbd>pluginName</kbd> - The plugin name to be used in the upload path such that url + pluginName is the folder the plugin will be uploaded to <br/><br/>**Note:** Name will be escaped when used as the upload path | **Required:** true <br/> **Acceptable Values:** Any String|
| <kbd>file</kbd> - The file to be uploaded to the repo under url + pluginName + file.getName() |  **Required:** true <br/> **Acceptable Values:** A existing file path, ideally should be set via `project.tasks.buildPlugin as Zip` which grabs the file from the IntelliJ gradle plugin |
| <kbd>pluginId</kbd> - Plugin Id used to match in the updatePlugins.xml | **Required:** true <br/> **Acceptable Values:** Any String|
| <kbd>version</kbd> - Plugin version used to update updatePlugins.xml | **Required:** true <br/> **Acceptable Values:** Any String| 
| <kbd>description</kbd> - Plugins description to be used in updatePlugins.xml | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** Any String| 
| <kbd>changeNotes</kbd> - Plugins change notes to be used in updatePlugins.xml | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** Any String|
| <kbd>sinceBuild</kbd> - Plugins minimum required IDE version. <br/> See [Multi-versioning](#Multi-versioning) for more info. <br/><br/><b>Note:</b> This should match the version specified in plugin.xml. The IDE will still validate the version in plugin.xml if this is excluded, but will not be until its been downloaded. | **Required:** false (required if using multi-versioning) <br/> **Default:** *none* <br/> **Acceptable Values:** A valid build number. See [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) for more info.|
| <kbd>untilBuild</kbd> - Plugins max allowed IDE version. <br/><br/><b>Note:</b> This should match the version specified in plugin.xml. The IDE will still validate the version in plugin.xml if this is excluded, but will not be until its been downloaded. | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** A valid build number. See [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) for more info.|
| <kbd>authentication</kbd> - Authentication string used to publish files to the private repo. Will be used as the authorization header | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** <ul> <li> `Basic [authenticationString]` </li> <li> `Bearer [bearerToken] ` </li> </ul>
| <kbd>updateFile</kbd> - Overrides the default updatePlugins.xml file name. <br/><br/><b>Note:</b> See [Publishing a Plugin to a Custom Plugin Repository](https://jetbrains.org/intellij/sdk/docs/basics/getting_started/update_plugins_format.html#describing-your-plugins-in-updatepluginsxml-file) for more information about updatePlugins.xml | **Required:** false <br/> **Default:** <kbd>updatePlugins.xml</kbd> <br/> **Acceptable Values:** Any String |
| <kbd>updatePluginXml</kbd> - Gates whether updatePlugins.xml is updated. | **Required:** false <br/> **Default:** <kbd>true</kbd> <br/> **Acceptable Values:** `true` / `false` |
| <kbd>uploadMethod</kbd> - Sets the HTTP method used for uploading files to the repo. | **Required:** false <br/> **Default:** <kbd>POST</kbd> <br/> **Acceptable Values:** <ul> <li>POST</li><li>PUT</li></ul> |


## Multi-versioning

As of version 1.2.0, multiple plugin entries with the same ID can be added to the <kbd>updateFile</kbd> file
as long as the since-build and until-build don't overlap. This is available since IDEA version 193.2956.37 (2019.3)
which added entry-filtering using since and until build on each entry.

The gradle plugin will check if the current upload, or an existing entry on the repository, has a since-build set before 193.2956.37 (or is not set), 
which prevents multiple entries from being added to ensure compatibility for the older IDEA versions.

Otherwise, multi-versioning is enabled which will update an existing entry if the since-build matches otherwise
a new entry is created and existing entries until-build are updated ensuring no overlap occurs. 

For best experience, it is recommended to always provide a valid build version to the <kbd>sinceBuild</kbd> parameter
and to exclude the <kbd>untilBuild</kbd> parameter as this plugin will take care of adding until-build when new entries are uploaded.
It should be noted, specifying <kbd>untilBuild</kbd> is possible while using multi-version, however the until-build version may be changed
at a later time to a lower build version (for example, if a new entry specifies a since-build which is before the current entries 
until-build, the until-build will be updated to a build before the new entries since-build)

## Notes

This plugin uses a lock file to prevent concurrent modifications to the updatePlugins.xml file.
While the lock file will be cleaned up, it could be left behind if the process is forcefully interrupted
requiring the lock to be deleted manually. The lock can be found in the <kbd>url</kbd> root and is named `updatePlugins.xml.lock`
(lock file name depends on <kbd>updateFile</kbd>)

## License

Most of this project is covered under the MIT license - located in the LICENSE file, with certain portions covered by 
Apache 2 license; all of which are clearly marked in a comment at the top of the file(s).