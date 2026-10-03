plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sentry)
}

fun gitValue(vararg args: String): String = providers.exec {
    workingDir(rootProject.projectDir)
    commandLine("git", *args)
    isIgnoreExitValue = true
}.standardOutput.asText.get().trim()

val sourceRef = providers.environmentVariable("NOTIFLY_SOURCE_REF")
    .orElse(providers.environmentVariable("GITHUB_HEAD_REF").filter { it.isNotBlank() })
    .orElse(providers.environmentVariable("GITHUB_REF_NAME"))
    .orElse(providers.environmentVariable("CI_COMMIT_REF_NAME"))
    .orElse(providers.environmentVariable("BUILD_SOURCEBRANCHNAME"))
    .getOrElse(gitValue("symbolic-ref", "--quiet", "--short", "HEAD").ifBlank {
        if (gitValue("rev-parse", "--verify", "HEAD").isNotBlank()) "detached" else "unknown"
    }).ifBlank { "unknown" }.removePrefix("refs/heads/")
val sourceSha = gitValue("rev-parse", "--verify", "HEAD").take(7).ifBlank { "unknown" }
fun buildStringLiteral(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "ph.notifly.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    buildFeatures { buildConfig = true }

    defaultConfig {
        applicationId = "ph.notifly.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SOURCE_REF", buildStringLiteral(sourceRef))
        buildConfigField("String", "SOURCE_SHA", buildStringLiteral(sourceSha))
        val sentryDsn = providers.gradleProperty("sentryDsn")
            .orElse(providers.environmentVariable("SENTRY_DSN"))
            .getOrElse("")
        buildConfigField("String", "SENTRY_DSN",
            "\"$sentryDsn\"")
    }

    val signingValues = listOf(
        providers.environmentVariable("KEYSTORE_FILE").orNull,
        providers.environmentVariable("KEYSTORE_PASSWORD").orNull,
        providers.environmentVariable("KEY_ALIAS").orNull,
        providers.environmentVariable("KEY_PASSWORD").orNull,
    )
    if (signingValues.all { it != null }) {
        signingConfigs.create("release") {
            storeFile = file(signingValues[0]!!)
            storePassword = signingValues[1]
            keyAlias = signingValues[2]
            keyPassword = signingValues[3]
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.android)
}


sentry {
    autoInstallation { enabled = false }
    tracingInstrumentation { enabled = true }
    autoUploadProguardMapping = providers.environmentVariable("SENTRY_AUTH_TOKEN")
        .map { it.isNotBlank() }.getOrElse(false)
    includeProguardMapping = true
    includeSourceContext = false
    telemetry = false
    org = providers.environmentVariable("SENTRY_ORG").getOrElse("kryoware")
    projectName = providers.environmentVariable("SENTRY_PROJECT").getOrElse("fundflow-android")
}
