# Change log

## 1.2.3
* Treat versions in updatePlugins.xml as immutable. Without setting mutableRelease property to true, uploadPlugin will fail iff an entry in updatePlugins.xml matches the plugin id and version
* Improved some error handling

## 1.2.2
* Added comment to updates xml file specifying the latest change details

## 1.2.1
* Fix idea-version element naming

## 1.2.0
* Added multi version support
* Migrate to provider based arguments
* Added notes about plugin signing