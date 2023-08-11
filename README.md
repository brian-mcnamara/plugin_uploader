# IntelliJ plugin uploader

A gradle plugin to automate uploading an IntelliJ plugin and updating updatePlugins.xml hosted on a private repository,
including S3-compatible stores.

## Usage

```groovy
buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id "dev.bmac.intellij.plugin-uploader" version "1.3.3"
}

generateBlockMap {
  // Depend on either signPlugin or buildPlugin, depending on which task provides the file in the uploadPlugin
  dependsOn(project.tasks.named("signPlugin"))
}

uploadPlugin {
    def signPluginTask = project.tasks.named("signPlugin").get() as SignPluginTask
    // Get the plugin distribution file from the signPlugin task provided from the gradle-intellij-plugin
    def archive = signPluginTask.outputArchiveFile.asFile
    // If you do not wish to sign the plugin, you can use the buildPlugin output instead and specify `archive.archivePath` for the file argument
    // def archive = project.tasks.buildPlugin.get().archiveFile
    
    // For security, do not hard code usernames or passwords in source control, instead load them through the gradle properties:
    // <code> findProperty('some.gradle.property') as String </code>
    // or through Environment variables:
    // <code> System.getenv('SOME_ENVIRONMENT_VARIABLE') </code>
    def username = "exampleUsername" 
    def password = "examplePassword"
    url.set('https://repo.example.com/intellij/plugins/')
    pluginName.set('PluginName')
    file.set(archive.get())
    pluginId.set(project.group)
    version.set(project.version)
    pluginDescription.set(file('description.txt').text)
    changeNotes.set(file('change-notes.txt').text)
    sinceBuild.set("211")
    // Example for Basic type authentication
    authentication.set('Basic ' + new String(Base64.encoder.encode(("$username:$password").bytes)))
}
```

## Configuration

| Attributes                                                                                                                                                                                                                                                                                                                                         | Values                                                                                                                                                                                                                                               | 
|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| <kbd>url</kbd> - The url plus path of the repository to post the plugin to                                                                                                                                                                                                                                                                         | **Required:** true <br/> **Acceptable Values:** Any URL, for example: <ul> <li>`https://repo.example.com/intellij/plugins` </li> <li>`http://repo.example.com:4443`</li></ul>                                                                        |
| <kbd>pluginName</kbd> - The plugin name to be used in the upload path such that url + pluginName is the folder the plugin will be uploaded to <br/><br/>**Note:** Name will be escaped when used as the upload path                                                                                                                                | **Required:** true <br/> **Acceptable Values:** Any String                                                                                                                                                                                           |
| <kbd>file</kbd> - The file to be uploaded to the repo under url + pluginName + file.getName()                                                                                                                                                                                                                                                      | **Required:** true <br/> **Acceptable Values:** A existing file path, ideally should be set via `project.tasks.buildPlugin as Zip` which grabs the file from the IntelliJ gradle plugin                                                              |
| <kbd>pluginId</kbd> - Plugin Id used to match in the updatePlugins.xml                                                                                                                                                                                                                                                                             | **Required:** true <br/> **Acceptable Values:** Any String                                                                                                                                                                                           |
| <kbd>version</kbd> - Plugin version used to update updatePlugins.xml                                                                                                                                                                                                                                                                               | **Required:** true <br/> **Acceptable Values:** Any String                                                                                                                                                                                           | 
| <kbd>pluginDescription</kbd> - Plugins description to be used in updatePlugins.xml                                                                                                                                                                                                                                                                 | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** Any String                                                                                                                                                                | 
| <kbd>changeNotes</kbd> - Plugins change notes to be used in updatePlugins.xml                                                                                                                                                                                                                                                                      | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** Any String                                                                                                                                                                |
| <kbd>sinceBuild</kbd> - Plugins minimum required IDE version. <br/> See [Multi-versioning](#Multi-versioning) for more info. <br/><br/><b>Note:</b> This should match the version specified in plugin.xml. The IDE will still validate the version in plugin.xml if this is excluded, but will not be until its been downloaded.                   | **Required:** false (required if using multi-versioning) <br/> **Default:** *none* <br/> **Acceptable Values:** A valid build number. See [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) for more info. |
| <kbd>untilBuild</kbd> - Plugins max allowed IDE version. <br/><br/><b>Note:</b> This should match the version specified in plugin.xml. The IDE will still validate the version in plugin.xml if this is excluded, but will not be until its been downloaded.                                                                                       | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** A valid build number. See [Build Number Ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html) for more info.                                      |
| <kbd>authentication</kbd> - Authentication string used to publish files to the private repo. Will be used as the authorization header                                                                                                                                                                                                              | **Required:** false <br/> **Default:** *none* <br/> **Acceptable Values:** <ul> <li> `Basic [authenticationString]` </li> <li> `Bearer [bearerToken] ` </li> </ul>                                                                                   |
| <kbd>updateFile</kbd> - Overrides the default updatePlugins.xml file name. <br/><br/><b>Note:</b> See [Publishing a Plugin to a Custom Plugin Repository](https://jetbrains.org/intellij/sdk/docs/basics/getting_started/update_plugins_format.html#describing-your-plugins-in-updatepluginsxml-file) for more information about updatePlugins.xml | **Required:** false <br/> **Default:** <kbd>updatePlugins.xml</kbd> <br/> **Acceptable Values:** Any String                                                                                                                                          |
| <kbd>updatePluginXml</kbd> - Gates whether updatePlugins.xml is updated.                                                                                                                                                                                                                                                                           | **Required:** false <br/> **Default:** <kbd>true</kbd> <br/> **Acceptable Values:** `true` / `false`                                                                                                                                                 |
| <kbd>repoType</kbd> - Sets the type of repository operations to use.                                                                                                                                                                                                                                                                               | **Required:** false <br/> **Default:** <kbd>REST_POST</kbd> <br/> **Acceptable Values:** <ul> <li>REST_POST</li><li>REST_PUT</li><li>S3</li></ul>                                                                                                    |
| <kbd>absoluteDownloadUrls</kbd> - Use absolute url to the plugin download in update plugins xml over relative paths.                                                                                                                                                                                                                               | **Required:** false <br/> **Default:** <kbd>false</kbd> <br/> **Acceptable Values:** `true` / `false`                                                                                                                                                |


## Supported repo types

This plugin supports standard REST style repositories (Nexus, artifactory, etc) which accept uploads
via POST/PUT requests. As of version 1.3.0, the plugin now supports Amazon S3 compatible stores as well. 

### S3

S3 compatible repositories are supported with the <kbd>repoType</kbd> set to `S3`. Some requirements should be noted:

* AWS S3 should use [virtual-hosted-style endpoints](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-bucket-intro.html)
  as the <kbd>url</kbd>, for example `https://bucket-name.s3.Region.amazonaws.com/folder`
* non-AWS S3 endpoints need to specify the bucket name as the userinfo in the url, for example
  `https://bucket-name@storage.example.com/folder`
* Any path added to the <kbd>url</kbd> will be the directory structure under the bucket
* Authentication can be specified by any means accepted by the [aws sdk](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html)
  or can be passed into the <kbd>authentication</kbd> as a colon (`:`) separated list of access key, secret key, or access key, secret key, session token.

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

## Updating a local file-based updatePlugins.xml

A task is registered as part of this plugin which can be used to update a file based updatePlugins.xml
This can be useful when hosting plugins in source control over a dedicated repository.

### Usage

```groovy
task updateLocalPluginXml(type:dev.bmac.gradle.intellij.UpdateXmlTask) {
  updateFile.set(file('updatePlugins.xml'))
  downloadUrl.set('http://example.com/plugins/pluginFile.zip')
  pluginName.set('PluginName')
  pluginId.set(project.group)
  version.set(project.version)
  pluginDescription.set(file('description.txt').text)
  changeNotes.set(file('change-notes.txt').text)
  sinceBuild.set("211")
}
```

## Notes

This plugin uses a lock file to prevent concurrent modifications to the updatePlugins.xml file.
While the lock file will be cleaned up, it could be left behind if the process is forcefully interrupted
requiring the lock to be deleted manually. The lock can be found in the <kbd>url</kbd> root and is named `updatePlugins.xml.lock`
(lock file name depends on <kbd>updateFile</kbd>)

As of 1.3.0, a check will be performed to prevent replacing an existing release. This only checks updatePlugins.xml
versions, so any versions not in this file will be allowed to be replaced. This can be disabled using 
`dev.bmac.pluginUploader.skipReleaseCheck` system property set to `true`

### Plugin Signing

As of 2021.2 plugin signature are being checked during install. Private plugin can use plugin signing but require the `signPlugin`
task be implemented and public/private keys be used to sign. While not required to use this plugin, it is recommended.

## License

Most of this project is covered under the MIT license - located in the LICENSE file, with certain portions covered by 
Apache 2 license; all of which are clearly marked in a comment at the top of the file(s).
