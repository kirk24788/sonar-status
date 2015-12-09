# Sonar Status Plugin
[![Build Status](https://travis-ci.org/kirk24788/sonar-status.svg?branch=master)](https://travis-ci.org/kirk24788/sonar-status)


Sonar "Plugin" for SonarQube 5.2 which tries to recover some functionality of the Build Breaker Plugin.
This is not a regular PlugIn since it needs to overwrite some internal SonarQube classes -thus you have to
put it into the `lib/server/` directory as `_sonar-server-5.2.jar` - this should take care that the class loader loads
our classes first and ignores the default implementation.
