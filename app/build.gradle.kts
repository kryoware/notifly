plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sentry)
}

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
        buildConfigField("String", "SENTRY_DSN",
            "\"${providers.environmentVariable("SENTRY_DSN").getOrElse("")}\"")
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
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.koin.android)
}


sentry {
    autoInstallation { enabled = false }
    tracingInstrumentation { enabled = false }
    autoUploadProguardMapping = providers.environmentVariable("SENTRY_AUTH_TOKEN").isPresent
    includeProguardMapping = true
    includeSourceContext = false
    telemetry = false
    org = providers.environmentVariable("SENTRY_ORG").getOrElse("kryoware")
    projectName = providers.environmentVariable("SENTRY_PROJECT").getOrElse("fundflow-android")
}
