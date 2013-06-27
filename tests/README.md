# Simperium Tests

Provides a very basic app used by the integration and unit tests.

## Setting up the Test App

For integration tests the SimperiumTestApp will need a valid Simperium.com app. A file named `simperium.properties` should be placed in `app/assets` with the following properties:

    simperium.appid=simperium-app-id
    simperium.appsecret=simperium-secret
    simperium.user.email=user@example.com
    simperium.user.token=token-for-user

The integration tests will use this data to build buckets and test simperium syncing.

Once the configuration is present, install the app on a device or simulator:

    cd tests/app
    and debug install

You can now run the tests.


## Integration Tests

The integration tests run against the live simperium.com API to ensure the core syncing features are working as expected. They should match with the tests in the Simperium-iOS project.

To run the integration tests, first install the test app and then run:

    cd tests/integration_tests
    ant debug install test

## Unit Tests

Unit Tests are configured with mock data and can run in isolation. To run the unit tests, firs install the test app and then run:

    cd tests/unit_tests
    ant debug install test

