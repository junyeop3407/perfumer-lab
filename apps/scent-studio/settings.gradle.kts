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
    }
}

rootProject.name = "scent-studio"

// 앱은 Expo(app/, npm)로 바뀌었다. Java 엔진은 TypeScript로 옮긴 뒤 지운다 (docs/design.md 1-1절)
include(":engine")
