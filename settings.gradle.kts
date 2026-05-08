pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        // Some libraries (and user forks) are published to JitPack; include it so Gradle can resolve
        // community packages such as supabase clients if they're published there.
        maven {
            url = uri("https://jitpack.io")
            // Note: credentials are intentionally not configured here because the Settings script
            // doesn't expose `findProperty` in the Kotlin DSL. If you need to provide credentials
            // for JitPack, place them in your global Gradle properties file (~/.gradle/gradle.properties)
            // as 'jitpackUser' / 'jitpackToken' or set the environment variables JITPACK_USER / JITPACK_TOKEN.
        }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add JitPack as an additional repository for resolving community artifacts
        maven {
            url = uri("https://jitpack.io")
            // If authentication is required for JitPack access, add credentials to ~/.gradle/gradle.properties
            // or set JITPACK_USER / JITPACK_TOKEN environment variables. Avoid calling findProperty from
            // the Settings script to prevent compilation errors in the Kotlin DSL.
        }
    }
}

rootProject.name = "HealthMonitor"
include(":app")
