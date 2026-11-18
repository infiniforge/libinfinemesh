pluginManagement {
    resolutionStrategy.eachPlugin {
        if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:8.9.2")
        if (requested.id.id.startsWith("org.jetbrains.kotlin.")) useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
    }
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "libinfinemesh"
include(":mesh-core")
if (!providers.gradleProperty("meshCoreOnly").isPresent) {
    val dependencyRoot = System.getProperty("infini.dependencies")?.let(::file) ?: file("dependencies")
    System.setProperty("infini.dependencies", dependencyRoot.canonicalPath)
    includeBuild(dependencyRoot.resolve("libinfinistream"))
    include(":mesh-android")
}
