import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
}

// Release signing is read from keystore.properties (gitignored). If that file
// is missing (e.g. a fresh clone without the keystore), release builds fall
// back to unsigned config so the project still opens — you just can't produce
// a signed release APK until you restore keystore.properties + the .jks.
// Google Maps key: read from local.properties (gitignored) or the
// MAPS_API_KEY environment variable (CI). It must NEVER live in a tracked
// file such as gradle.properties - see SPL-SEC-001 and SecretsHygieneTest.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) FileInputStream(f).use { load(it) }
}
val mapsApiKey: String = localProps.getProperty("MAPS_API_KEY")
    ?: System.getenv("MAPS_API_KEY")
    ?: ""
if (mapsApiKey.isEmpty()) {
    logger.warn("MAPS_API_KEY not set in local.properties or the environment - the Map tab will be blank.")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

android {
    namespace = "com.example.splitwiseauto"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.splitwiseauto"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Room schema export target (paired with exportSchema = true on
        // AppDatabase, starting at v9). AppDatabaseMigrationTest reads
        // app/schemas/9.json from here via MigrationTestHelper — this
        // directory is populated by kapt the next time the module builds.
        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8: shrink + optimize + obfuscate. This is the big one for
            // smoothness (debug builds run un-optimized). resource shrinking
            // removes unused resources. Keep-rules live in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only sign with the release key when keystore.properties is present.
            signingConfig = if (keystoreProps.getProperty("storeFile") != null) {
                signingConfigs.getByName("release")
            } else {
                null
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
    lint {
        // The Fragment version is already bumped in dependencies to satisfy
        // registerForActivityResult; this keeps the (now-fixed) check from
        // ever blocking a release APK. The app uses ComponentActivity's own
        // result APIs, which work regardless of the Fragment version.
        disable += "InvalidFragmentVersionForActivityResult"
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.4.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // The 16 KB "APK/ELF alignment" warning has two causes: the native
        // library's own build (CameraX 1.4.1 already fixes that) and how
        // AGP zip-aligns .so files in the APK — that second part needs AGP
        // 8.5.1+, and this project is on 8.1.4. Packaging native libs
        // compressed instead of uncompressed sidesteps that alignment
        // requirement entirely without needing an AGP bump. This is
        // Google's own documented fallback for projects that aren't on
        // 8.5.1+ yet.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    // Lets the androidTest source set (AppDatabaseMigrationTest) find the
    // schema JSON files Room's annotation processor writes to
    // app/schemas/ via the room.schemaLocation option above.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

dependencies {
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Pin a modern Fragment so lint-vital (release) accepts
    // registerForActivityResult — the transitive Fragment version was older
    // than the 1.3.0 that ActivityResult APIs require. 1.6.2 is the newest
    // line that still targets compileSdk 34.
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    implementation(platform("androidx.compose:compose-bom:2023.03.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.maps.android:maps-compose:2.11.4")

    // QR scanning for "Scan & Pay" (USSD *99# flow)
    // Pinned to 1.4.1: 1.3.x's native libimage_processing_util_jni.so
    // isn't 16 KB page-size aligned (Google fixed this in 1.4.0). Newer
    // CameraX lines (1.5.x/1.6.x) need a much newer AGP/compileSdk than
    // this project is on (AGP 8.1.4 / compileSdk 34), so 1.4.1 is the
    // version that both fixes the alignment warning AND builds cleanly
    // here without touching AGP.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.google.zxing:core:3.5.3")

    // Scan & Pay: periodic worker that marks PENDING payments with no
    // confirming bank SMS after 30 min as UNCONFIRMED. 2.9.x is the last
    // line that supports compileSdk 34 (2.10+ needs compileSdk 35).
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.compose.material:material-icons-extended")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // MigrationTestHelper for AppDatabaseMigrationTest (instrumented —
    // runs under connectedAndroidTest, not plain unit tests).
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
