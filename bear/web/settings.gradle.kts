// Web preview: a standalone build so the Android project stays JVM-only.
// Build from bear/: ./gradlew -p web preview
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "bear-web"
