import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val defaultClientId = localProps.getProperty("GMAIL_CLIENT_ID", "").trim()
val reversedClientIdScheme = if (defaultClientId.endsWith(".apps.googleusercontent.com")) {
    "com.googleusercontent.apps." + defaultClientId.removeSuffix(".apps.googleusercontent.com")
} else {
    "io.github.neoruben.mercadonastats.oauth"
}

android {
    namespace = "io.github.neoruben.mercadonastats"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.neoruben.mercadonastats"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"

        buildConfigField("String", "DEFAULT_GMAIL_CLIENT_ID", "\"$defaultClientId\"")
        manifestPlaceholders["oauthRedirectScheme"] = reversedClientIdScheme
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    testImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.pdfbox:pdfbox:2.0.32")
}
