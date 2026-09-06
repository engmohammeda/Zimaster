import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// ═══════════════════════════════════════════════════════════════════════════
// Supabase credential resolution
//
// BUG FIXED: CI (.github/workflows/android-release.yml) writes SUPABASE_URL and
// SUPABASE_ANON_KEY into local.properties, but this script never read that file —
// `project.findProperty` only sees gradle.properties and `-P` arguments. Every CI
// build therefore silently shipped the dummy placeholders from gradle.properties
// and the released APK could not reach the cloud.
//
// Resolution order below (first non-blank wins):
//   1. environment variable  — what GitHub Actions exports from repo secrets
//   2. local.properties      — your machine and the CI fallback (git-ignored)
//   3. gradle.properties     — committed public defaults
//   4. dummy placeholder     — fails loudly at runtime, not at compile time
// ═══════════════════════════════════════════════════════════════════════════
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** Resolve a Supabase build value from env → local.properties → gradle.properties → fallback. */
fun supabaseProperty(name: String, fallback: String): String {
    val fromEnv = System.getenv(name)?.trim()
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromLocal = localProperties.getProperty(name)?.trim()
    if (!fromLocal.isNullOrBlank()) return fromLocal
    val fromGradle = (project.findProperty(name) as? String)?.trim()
    if (!fromGradle.isNullOrBlank()) return fromGradle
    return fallback
}

// ═══════════════════════════════════════════════════════════════════════════
// Signing keystore resolution
//
// BUG FIXED: the repository intentionally ships no keystore (*.keystore is
// git-ignored), yet signingConfigs pointed at ${rootDir}/debug.keystore
// unconditionally. On every checkout except the `release` CI job — which
// generates that file with keytool — the build died at:
//
//   > Task :app:validateSigningDebug FAILED
//   Keystore file '<repo>/debug.keystore' not found for signing config 'debugConfig'
//
// so the `ci` job's `assembleDebug` could never succeed.
//
// Also: android-release.yml exports RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD
// / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD, and nothing ever read them — the
// workflow had to copy the real keystore over debug.keystore as a workaround.
// They are honoured here now.
//
// Resolution order:
//   1. RELEASE_STORE_FILE      — the CI release keystore
//   2. ${rootDir}/debug.keystore — a locally provided debug keystore
//   3. none → AGP's built-in `debug` signing config, which generates
//      ~/.android/debug.keystore on demand (how a normal project builds)
// ═══════════════════════════════════════════════════════════════════════════
val signingStoreFile: File? = listOfNotNull(
    System.getenv("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() }?.let { file(it) },
    file("${rootDir}/debug.keystore"),
).firstOrNull { it.exists() }

android {
    namespace = "com.zmastery.english"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.zmastery.english"
        minSdk = 24
        targetSdk = 36
        // CI builds use the workflow run number as a monotonically increasing
        // versionCode (required by Google Play); local builds fall back to the
        // baseline below.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2
        versionName = "1.1.0"

        val supabaseUrl: String = supabaseProperty("SUPABASE_URL", "https://dummy-project.supabase.co")
        val supabaseAnonKey: String = supabaseProperty("SUPABASE_ANON_KEY", "dummy-anon-key")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        // Fail loudly in the build log instead of shipping an APK that silently
        // cannot reach the cloud. Never prints the key itself — only its source.
        val keySource = when {
            !System.getenv("SUPABASE_ANON_KEY").isNullOrBlank() -> "environment"
            !localProperties.getProperty("SUPABASE_ANON_KEY").isNullOrBlank() -> "local.properties"
            !(project.findProperty("SUPABASE_ANON_KEY") as? String).isNullOrBlank() -> "gradle.properties"
            else -> "FALLBACK PLACEHOLDER"
        }
        val looksProvisioned = supabaseUrl.contains(".supabase.co") &&
            !supabaseUrl.contains("dummy-project") &&
            (supabaseAnonKey.startsWith("sb_publishable_") || supabaseAnonKey.startsWith("eyJ"))
        logger.lifecycle(
            "Supabase → url=$supabaseUrl | key source=$keySource | provisioned=$looksProvisioned"
        )
        logger.lifecycle(
            "Signing  → keystore=${signingStoreFile?.name ?: "AGP built-in debug (~/.android/debug.keystore)"}"
        )
        if (!looksProvisioned) {
            logger.warn(
                "⚠️  SUPABASE credentials are placeholders — this build cannot sync to the cloud. " +
                    "Set SUPABASE_URL / SUPABASE_ANON_KEY (env, local.properties or gradle.properties). " +
                    "See docs/SUPABASE_SETUP.md."
            )
        }
    }

    signingConfigs {
        // Declared only when a keystore actually exists — see the note above.
        signingStoreFile?.let { keystore ->
            create("projectSigning") {
                storeFile = keystore
                storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "android"
                keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "androiddebugkey"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "android"
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
            // Left unset when no keystore is present so AGP uses its built-in
            // `debug` config and generates ~/.android/debug.keystore itself.
            if (signingStoreFile != null) {
                signingConfig = signingConfigs.getByName("projectSigning")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName(
                if (signingStoreFile != null) "projectSigning" else "debug"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("androidx.navigation:navigation-compose:2.9.7")

    implementation("androidx.core:core-ktx:1.15.0")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // Supabase — BOM 3.2.6 is the last supabase-kt release compiled with
    // Kotlin 2.2.21 / AGP 8.10.1, i.e. exactly the toolchain this project uses.
    // (3.3.0+ requires Kotlin 2.4.0 and would force a toolchain upgrade.)
    // 3.1.1 → 3.2.6 brings: linkIdentityWithIdToken (anonymous → Google upgrade),
    // the Android auto-refresh background leak fix, and PostgREST filter escaping
    // for reserved characters. No breaking changes for the APIs used here.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.2.6"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    // Ktor OkHttp Client for Supabase — 3.3.1 is the Ktor version supabase-kt
    // 3.2.6 is built and tested against; keeping them in step avoids subtle
    // binary incompatibilities in the HTTP engine.
    implementation("io.ktor:ktor-client-okhttp:3.3.1")

    // Google Sign-In via Credential Manager & Play Services
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
