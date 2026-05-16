import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun localProperty(name: String): String? = localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val meilistsStoreFile = localProperty("meilists.signing.storeFile")?.let { file(it) }
val meilistsStorePassword = localProperty("meilists.signing.storePassword")
val meilistsKeyAlias = localProperty("meilists.signing.keyAlias")
val meilistsKeyPassword = localProperty("meilists.signing.keyPassword") ?: meilistsStorePassword
val hasMeilistsSigning = meilistsStoreFile?.isFile == true &&
        meilistsStorePassword != null &&
        meilistsKeyAlias != null &&
        meilistsKeyPassword != null

android {
    namespace = "de.haberland.meilists"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.haberland.meilists"
        minSdk = 26
        //noinspection AndroidLintEditedTargetSdkVersion
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 11
        versionName = "0.2.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasMeilistsSigning) {
            create("meilists") {
                storeFile = requireNotNull(meilistsStoreFile)
                storePassword = requireNotNull(meilistsStorePassword)
                keyAlias = requireNotNull(meilistsKeyAlias)
                keyPassword = requireNotNull(meilistsKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            if (hasMeilistsSigning) {
                signingConfig = signingConfigs.getByName("meilists")
            }
        }

        release {
            if (hasMeilistsSigning) {
                signingConfig = signingConfigs.getByName("meilists")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Play In-App Updates
    implementation(libs.play.app.update)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
