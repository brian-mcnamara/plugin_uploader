# Change log

## 1.3.0
### Added
* Treat versions in updatePlugins.xml as immutable. Without setting mutableRelease property to true, uploadPlugin will fail iff an entry in updatePlugins.xml matches the plugin id and version
* Added S3 support
### Changed
* Migrated `uploadMethod` to `repoType` changing the values from `POST`/`PUT` to `REST_POST` and `REST_PUT`
* Improved some error handling

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