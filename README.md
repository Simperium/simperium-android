# Simperium for Android

Trying to make using [Simperium][Simperium.com] in your Android app dead simple.

## Using in an Android Project

Simperium for Android is configured as an [Android Library Project][].

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

## Publish to S3

A new version of this library will be automatically published to S3 by CI in the following scenarios:

**Note**: `sha1` corresponds to the commit hash.

* For all tags -> Version: `{tag-name}`
* For all commits in `trunk` (so PR merges) -> Version: `trunk-{sha1}`
* For all commits for open PRs - you can open a draft PR to get it to publish -> Version: `{prNumber}-{sha1}`

## Usage

### Adding simperium as dependency

```groovy
implementation 'com.automattic:simperium:<version>'
```

[Android Studio]: http://developer.android.com/sdk/installing/studio.html
[Gradle]: http://www.gradleware.com
[Simperium.com]: http://simperium.com
[Android Library Project]: http://developer.android.com/tools/projects/index.html#LibraryProjects
