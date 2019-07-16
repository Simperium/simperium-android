# Simperium for Android

Trying to make using [Simperium][Simperium.com] in your Android app dead simple.

[![Build Status](https://travis-ci.org/Simperium/simperium-android.svg?branch=develop)](https://travis-ci.org/Simperium/simperium-android)

## Using in an Android Project

Simperium for Android is configured as an [Android Library Project][].

TODO: brief code example/tutorial :)

## Contributing

To get started first clone the project:

```
git clone https://github.com/Simperium/simperium-android.git
```

Simperium Android uses [Android Studio][] and [gradle][] for development.

### Tests

Please provide unit tests for your contributions. Run tests with gradle:

```
./gradlew connectedAndroidTest
```

Unit tests use a mock networking and storage stack so that different components can be tested in isolation. The unit tests should not connect to any external services.


## Publish the library to maven central

Replace `CHANGEME` by a valid bintray user/key and run the following command line:

```
./gradlew assemble publishToMavenLocal bintrayUpload -PbintrayUser=CHANGEME -PbintrayKey=CHANGEME -PdryRun=false
```

[Android Studio]: http://developer.android.com/sdk/installing/studio.html
[Gradle]: http://www.gradleware.com
[Simperium.com]: http://simperium.com
[Android Library Project]: http://developer.android.com/tools/projects/index.html#LibraryProjects
