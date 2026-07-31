import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional release signing. Create a `keystore.properties` file in the project root
// (it is git-ignored) with storeFile / storePassword / keyAlias / keyPassword to sign
// release builds with your own key. Without it, release builds fall back to the debug
// key so `assembleRelease` still produces an installable APK for personal use.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

// The release workflow derives these from the git tag so a published APK reports the
// version it was tagged with. Local builds just use the defaults below.
val appVersionName = (project.findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() }
val appVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()

android {
    namespace = "com.firstresponder.kit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.firstresponder.kit"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode ?: 1
        versionName = appVersionName ?: "1.0"

        // No instrumentation tests are shipped; the app has no analytics or network use.
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            // The engine touches android.os.Process; stubs return defaults instead of
            // throwing, so its timing can be tested on a plain JVM.
            isReturnDefaultValues = true
        }
    }

    bundle {
        language {
            // The language is picked inside the app, not taken from the system locale, so
            // splitting translations out per-locale would leave a device set to English
            // with no Hebrew strings to switch to.
            enableSplit = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Material Icons: the extended set keeps every tool icon available as the kit grows.
    // R8 strips the unused ones from release builds.
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
