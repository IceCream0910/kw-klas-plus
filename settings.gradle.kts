rootProject.name = "kw-klas-plus"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("android.arch")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.android.support")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("android.arch")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.android.support")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

include(":androidApp")
include(":legacyAndroidApp")
include(":shared")
