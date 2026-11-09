pluginManagement {
    repositories {
        // Sandbox: Maven Central rate-limits (HTTP 429) Gradle's metadata
        // bursts; artifacts fetched via curl are seeded into ~/.m2 and
        // resolved through mavenLocal() first (P2 build-repair note).
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal() // see pluginManagement note (sandbox 429 workaround)
        mavenCentral()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}

rootProject.name = "AuthrDesktop"

include(":desktop")
