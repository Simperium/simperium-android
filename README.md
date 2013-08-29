# Simperium Android Dependency Repository

Artifacts needed to build Simperium Android are hosted here.

## Gradle configuration

```groovy
repositories {
  maven { url "http://simperium.github.io/simperium-android" }
}
```

## Current Artifacts

- [Simperium Android][] - Android library project
  `com.simperium:simperium-android:0.1.3-alpha-SNAPSHOT`
- [Android Websockets][] - websocket client used in Simperium Android
  `com.codebutler:android-websockets:6c7c60d`
- [Android Async HTTP][] - HTTP client used in Simperium Android
  `com.loopj:android-async-http:1.4.3`

[Simperium Android]: https://github.com/Simperium/simperium-android
[Android Websockets]: https://github.com/codebutler/android-websockets
[Android Async HTTP]: https://github.com/loopj/android-async-http
