# Change log

## 1.2.5
### Fixed
- Fixed upload plugin task calling project.tasks which causes issues in bazel cache
## 1.3.4
### Added
- Added downloadUrlPrefix to specify the plugin file download location prefix.
### Changed
- Updated lock cleanup error message to indicate correct file
- Updated error logging for repository operations
- Deprecated absoluteDownloadUrls in favor of downloadUrlPrefix
## 1.3.3
### Fixed
- Fixed `UpdatePluginsXml` incorrectly specifying @Input for an output file.
## 1.3.2
### Added
- Added `UpdatePluginsXml` task to allow updating file-based updatePlugins.xml
## 1.3.1
### Fixed
- Fixed compatibility with intellij-gradle-plugin version 1.6.0 which added strict okhttp dependency version
## 1.3.0
### Added
* Treat versions in updatePlugins.xml as immutable. uploadPlugin will fail iff an entry in updatePlugins.xml matches the plugin id and version
* Added S3-compatible repository support
* Added `generateBlockMap` task adding BlockMap and hash generation for incremental plugin updates from the IDE
### Changed
* Migrated `uploadMethod` to `repoType` changing the values from `POST`/`PUT` to `REST_POST` and `REST_PUT`
* Improved some error handling
* Added authentication to GET requests to support [fully authenticated repositories](https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/src/com/intellij/ide/plugins/auth/PluginRepositoryAuthProvider.java)

## 1.2.3
* Added `absoluteDownloadUrls` to allow absolute urls over relative path in update plugins xml

## 1.2.2
* Added comment to updates xml file specifying the latest change details

## 1.2.1
* Fix idea-version element naming

## 1.2.0
* Added multi version support
* Migrate to provider based arguments
* Added notes about plugin signing