# Simperium for Android

Trying to make using [Simperium][Simperium.com] in your Android app dead simple.

## Using in an Android Project

Simperium for Android is configured as an [Android Library Project][].

TODO: brief code example/tutorial :)

## Contributing

To get started first clone the project:

```
git clone https://github.com/Simperium/simperium-android.git
```

Simperium Android now uses [Android Studio][] and [gradle][] for development.

### Tests

Please provide unit tests for your contributions.

Currently there are two projects for running tests: SimperiumUnitTests and SimperiumIntegrationTests.

#### Running Unit Tests

To run the unit tests use the `gradlew` command:

```
./gradlew :SimperiumUnitTests:connectedInstrumentTest
```

Unit tests use a mock networking and storage stack so that different components can be tested in isolation. The unit tests should not connect to any external services.

#### Running Integration Tests

To run the integration tests use the `gradlew` command:

```
./gradlew :SimperiumIntegrationTests:connectedInstrumentTest
```

These tests require a connection to [Simperium.com][] as well as a configured App ID and App Token which can be found on the Simperium.com dashboard for your account as well as a configured user and access token. These values should be defined in `SimperiumIntegrationTests/src/main/assets/simperium.properties`:

```
simperium.appid=APP_ID
simperium.appsecret=APP_SECRET
simperium.user.email=USER_EMAIL
simperium.user.token=USER_TOKEN
```

[Android Studio]: http://developer.android.com/sdk/installing/studio.html
[Gradle]: http://www.gradleware.com
[Simperium.com]: http://simperium.com
[Android Library Project]: http://developer.android.com/tools/projects/index.html#LibraryProjects