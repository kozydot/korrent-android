pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add JitPack if needed for libraries like Marplex/CloudflareBypass
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Korrent"
include(":app")