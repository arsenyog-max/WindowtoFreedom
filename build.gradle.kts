import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application") version "8.11.0"
    id("org.jetbrains.kotlin.android") version "2.2.0"
}

val abis = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) propertiesFile.inputStream().use { load(it) }
}

fun signingSecret(name: String): String? = System.getenv(name) ?: localProperties.getProperty(name)

val releaseKeystoreFile = file(signingSecret("KEYSTORE_FILE") ?: "release.keystore")
val releaseKeystorePassword = signingSecret("KEYSTORE_PASSWORD")
val releaseKeyAlias = signingSecret("KEY_ALIAS")
val releaseKeyPassword = signingSecret("KEY_PASSWORD")
val releaseSigningReady = releaseKeystoreFile.exists() &&
    releaseKeystorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null

val buildGitHash: String = runCatching {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    process.waitFor()
    output.ifEmpty { "nogit" }
}.getOrElse { "nogit" }

val buildTimeStamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    .format(Date())

val buildStamp: String = "$buildGitHash | $buildTimeStamp"

android {
    namespace = "io.github.romanvht.byedpi"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.smenley.byedpixtg"
        minSdk = 21
        targetSdk = 34
        versionCode = 29
        versionName = "2.0.9"

        buildConfigField("String", "BUILD_STAMP", "\"$buildStamp\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(abis)
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs(".")
            res.srcDirs("res", "src/main/res")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.matching { it.name == "generateDebugResources" }.configureEach {
    mustRunAfter(
        "checkDebugAarMetadata",
        "generateDebugBuildConfig",
        "mergeDebugNativeDebugMetadata",
        "dataBindingMergeDependencyArtifactsDebug"
    )
}

tasks.matching { it.name == "mergeDebugResources" }.configureEach {
    mustRunAfter("generateDebugResources")
}

tasks.matching { it.name == "packageDebugResources" }.configureEach {
    mustRunAfter("mergeDebugResources")
}

tasks.matching { it.name == "compileDebugKotlin" }.configureEach {
    mustRunAfter(
        "checkDebugAarMetadata",
        "generateDebugBuildConfig",
        "mergeDebugNativeDebugMetadata",
        "dataBindingMergeDependencyArtifactsDebug",
        "generateDebugResources",
        "mergeDebugResources",
        "packageDebugResources",
        "mergeDebugAssets",
        "compressDebugAssets",
        "desugarDebugFileDependencies",
        "mergeDebugShaders",
        "mergeDebugJniLibFolders",
        "mergeExtDexDebug",
        "checkDebugDuplicateClasses",
        "mergeLibDexDebug",
        "validateSigningDebug"
    )
}
