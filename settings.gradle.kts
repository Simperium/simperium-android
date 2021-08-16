pluginManagement {
    plugins {
        id("com.android.library") version "4.2.2"
        id("com.automattic.android.publish-to-s3") version "0.6.1"
    }
    repositories {
        maven {
            url = uri("https://a8c-libs.s3.amazonaws.com/android")
            content {
                includeGroup("com.automattic.android")
                includeGroup("com.automattic.android.publish-to-s3")
            }
        }
        gradlePluginPortal()
        google()
    }
}

include(":Simperium")
