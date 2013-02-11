# Simperium for Android

Trying to make using Simperium in your Android app dead simple.

## Install

The easiest way to install at this point is to download the project and use it as an Android libary project.

First clone the repo onto your system and pull down the submodules:

```bash
$> git clone https://github.com/Simperium/simperium-android.git --branch duo
$> cd simperium-android
$> git submodule init
```
Then in your Android project update project.properties to reference the library by adding:

```
android.library.reference.1=path/to/simperium-android-repo
```

If you're using Eclipse [follow these instructions on *Referencing a library project*][eclipse]. If you use `ant` from the command line you will need to configure the libraries correctly using the `./setup` script:
```bash
$> ./setup android-10
```
I'm using android-10 (Android 2.3.3) as the target for the libraries.

[eclipse]:http://developer.android.com/tools/projects/projects-eclipse.html#ReferencingLibraryProject

## Usage

Your `AndroidManifest.xml` will need `android.permission.INTERNET`.

```xml
<!-- snip -->
<uses-permission android:name="android.permission.INTERNET" />
<!-- snip -->
```

You will need to [signup and set up a Simperium app](https://simperium.com/signup/) to get an `app_id` and an `api_key`. Now it's time to configure Simperium.

```java
// import Simperium
import com.simperium.*;

public class MyActivity extends Activiy {

  private Simperium simperium;
  
  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    // initialize simperium
    configureSimperium();
  }
  
  public void onResume(){
  
  }
  
  // sample method for configuing simperium, most likely called in
  // an Activity's onCreate method or move to a singleton to provide
  // a sharable instance for the Application
  protected void configureSimperium()
  {
    // Simperium provides a single point of configuration
    simperium = new Simperium(
      // Your applicaton's id example: abusers-headset-237
      APP_ID,
      // Your application's secret token: a4834ksado...
      APP_TOKEN,
      // Context Simperium uses to save user prefs
      getContext(),
      // a com.simperium.Simperium.AuthenticationListener
      new Simperium.AuthenticationListener()
      {
        public void onAuthenticationStatusChange(Simperium.AuthenticationStatus status)
        {
          // here you can handle how to prompt a user to sign up or sign in
          // or show that hey are in an offline state
          switch(Simperium.AuthenticationStatus)
          {
            case AUTHENTICATED:
              // Socket is operating and the user is signed in
              break;
            case NOT_AUTHENTICATED:
              // User does not have an access token or it has expired
              // They'll need to re-auth
              break;
            case OFFLINE:
              // User has an access token but the connection to Simperium
              // is down
              break;
          }
        }
      }
    );
    
  }
}
```

### TODO

There is much to do :).

 1. Change processing: Use the [jsondiff-java][] library to process changes in the Channel.
 2. Provide a storage interface to buckets and possibly implement one [using sqlite as a key-value store][sql-keyvalue].
 3. Make buckets more intelligent on how to instatiate entites from JSON and allow applications a
    simple interface to define their own.
 4. Unit tests. Critical components 1) Channel command processing 2) Change processing 3) Bucket events

[jsondiff-java]: https://github.com/Simperium/jsondiff-java
[sql-keyvalue]: http://backchannel.org/blog/friendfeed-schemaless-mysql