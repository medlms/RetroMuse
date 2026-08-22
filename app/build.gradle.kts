import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing credentials live in keystore.properties, which is not checked in. Copy
// keystore.properties.example and fill it in to produce a release build.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// AdMob unit IDs default to Google's official test units so debug builds never serve
// live ads. Override them in keystore.properties before shipping.
fun adUnit(key: String, testDefault: String): String =
    keystoreProperties.getProperty(key) ?: testDefault

android {
    namespace = "com.retro.grooveplayer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.retro.retromuse"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "1.0.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        manifestPlaceholders["admobAppId"] =
            adUnit("admobAppId", "ca-app-pub-3940256099942544~3347511713")
        buildConfigField(
            "String",
            "ADMOB_BANNER_UNIT_ID",
            "\"${adUnit("admobBannerUnitId", "ca-app-pub-3940256099942544/6300978111")}\""
        )
    }

    signingConfigs {
        create("release") {
            val storeFileName = keystoreProperties.getProperty("storeFile") ?: "release-key.jks"
            val store = file(storeFileName)
            if (store.exists() && keystoreProperties.getProperty("storePassword") != null) {
                storeFile = store
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    
    // ExoPlayer & Media3
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("androidx.media:media:1.6.0")
    
    // Gson for JSON persistence (AsyncStorage replacement)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Coil for Compose image loading
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    // Google AdMob Mobile Ads SDK
    implementation("com.google.android.gms:play-services-ads:23.0.0")

    // In-app review prompt (rating count and average are heavy ranking inputs)
    implementation("com.google.android.play:review:2.0.1")
    implementation("com.google.android.play:review-ktx:2.0.1")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
