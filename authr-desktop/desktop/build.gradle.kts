import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.compose") version "1.9.3"
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

// Repositories are declared once in settings.gradle.kts
// (RepositoriesMode.FAIL_ON_PROJECT_REPOS).

kotlin {
    jvmToolchain(21)
    sourceSets.main {
        // All sources live in a single desktop source set:
        // - com.example.*  : the ported Android app code (kept as close to 1:1 as possible)
        // - android.*, androidx.work/security/lifecycle/activity/compose shims: desktop implementations
        //   of the Android APIs the app depends on
    }
}

dependencies {
    implementation(compose.desktop.currentOs)

    // Material 3 + icons via the plugin DSL (resolves the CMP-aligned artifact;
    // compose.desktop.currentOs does not include material3 by itself).
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Lifecycle (ViewModel + viewModelScope on desktop)
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.9.6")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Networking (identical stack to the Android app)
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.12.0")
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // org.json (Android's JSON library) for the JVM
    implementation("org.json:json:20250517")

    // MP3 decoding for the MediaPlayer shim (Edge TTS / Pocket TTS playback)
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")

    // Print-interior PDF writer (engine/publishing; P5 exit gate compiles the
    // direct route against the fetched jar — D-15 remediation step: the Gradle
    // route needs this dependency declared)
    implementation("org.apache.pdfbox:pdfbox:2.0.33")

    // Room database (KMP / JVM)
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.sqlite:sqlite-bundled:2.6.2")
    ksp("androidx.room:room-compiler:2.8.5")

    // Koin DI
    implementation("io.insert-koin:koin-core:3.5.6")

    // Unit tests (P0 gates: container round-trip, plugin schema)
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

compose.desktop {
    application {
        mainClass = "com.example.MainKt"

        buildTypes.release.proguard {
            isEnabled = false
        }

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.AppImage, TargetFormat.Exe)
            packageName = "AuthrOS"
            packageVersion = "3.1.0"
            description = "Authr: The Autonomous Author Operating System - Desktop Edition"
            vendor = "Authr OS"
            copyright = "© 2026 Authr OS. All rights reserved."
            licenseFile.set(project.file("LICENSE"))
            appResourcesRootDir.set(project.layout.projectDirectory.dir("resources"))

            windows {
                menu = true
                dirChooser = true
                // 2026-11-13 (push-preflight bug #4): the old value
                // "6ac9d1e0-1f9a-4b8f-9f8f-authros20desk" is NOT parseable by
                // UUID.fromString ("authros20desk" is not hex) — the Compose
                // plugin validates upgradeUuid when generating the Windows
                // upgrade identity, so the MSI/EXE packaging task dies at
                // configuration time on the Windows runner. Deterministic v5
                // (URL namespace) so rebuilds keep the same upgrade identity.
                upgradeUuid = "f5d50191-48ab-5410-b4d0-042ebed917cb"
            }
            macOS {
                bundleID = "com.aistudio.audionovelstudio.nxkpt"
                dockName = "Authr OS"
            }
            linux {
                menuGroup = "authr"
                rpmLicenseType = "Apache-2.0"
            }
        }
    }
}
