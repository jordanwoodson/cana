import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.protobuf)
}

val keystoreProperties = Properties()
val keystorePropertiesFile: File = rootProject.file("key.properties")

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "io.github.samolego.canta"
    compileSdk = 36

    // For reproducible builds
    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    defaultConfig {
        // Own id so Cana installs next to upstream Canta
        applicationId = "io.github.jordanwoodson.cana"
        minSdk = 28 // todo - figure out a way to bypass hidden api methods on android < 9
        targetSdk = 35
        versionCode = project.property("version_code")?.toString()?.toInt() ?: 1
        versionName = project.property("version_name")?.toString() ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "APP_VERSION", "\"${project.property("version_name")}\"")
        buildConfigField("int", "VERSION_CODE", "${project.property("version_code")}")
    }

    signingConfigs {
        create("release") {
            keyAlias = keystoreProperties["keyAlias"].toString()
            keyPassword = keystoreProperties["keyPassword"].toString()
            storeFile =
                    if (keystoreProperties["storeFile"] != null)
                            keystoreProperties["storeFile"]?.let { file(it) }
                    else null
            storePassword = keystoreProperties["storePassword"].toString()
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    kotlin {
        jvmToolchain(21)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = true
    }
    // composeOptions {
    //    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    // }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3.android)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    testImplementation(libs.junit)
    // Use Android's JSON semantics (including last-key-wins) instead of the JVM stub.
    testImplementation("com.vaadin.external.google:android-json:0.0.20131108.vaadin1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.coil.compose)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.api)
    implementation(libs.provider)

    implementation(libs.androidx.datastore.core)
    implementation(libs.protobuf.javalite)
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.biometric)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
