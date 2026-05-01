import java.util.Properties

fun asBuildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun isGooglePlaceJwtDisabled(rootDir: File): Boolean {
    val configFile = rootDir.resolve("supabase/config.toml")
    if (!configFile.exists()) return false

    var inGooglePlaceSection = false
    for (line in configFile.readLines()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("[") -> inGooglePlaceSection = trimmed == "[functions.google-place-details]"
            inGooglePlaceSection && trimmed.startsWith("verify_jwt") -> {
                val value = trimmed.substringAfter("=", "").trim()
                return value.equals("false", ignoreCase = true)
            }
        }
    }
    return false
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.lokerlokal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lokerlokal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
        buildConfigField(
            "String",
            "SUPABASE_URL",
            asBuildConfigString(localProperties.getProperty("SUPABASE_URL", ""))
        )
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            asBuildConfigString(localProperties.getProperty("SUPABASE_ANON_KEY", ""))
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("androidx.fragment:fragment-testing:1.8.6")
    debugImplementation("androidx.fragment:fragment-testing:1.8.6")
}

tasks.matching { it.name == "preBuild" }.configureEach {
    doFirst {
        if (isGooglePlaceJwtDisabled(rootProject.projectDir)) {
            logger.warn(
                "⚠️  google-place-details Edge Function has verify_jwt=false in supabase/config.toml. " +
                    "This is okay for current testing, but remember to re-enable JWT verification before shipping a production build."
            )
        }
    }
}

