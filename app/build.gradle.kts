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

// Firma de la release: local.properties indica dónde está keystore.properties (fuera del repositorio),
// con storeFile, storePassword, keyAlias y keyPassword. Sin él, la release se genera sin firmar.
val releaseKeystorePropsFile = localProps.getProperty("RELEASE_KEYSTORE_PROPERTIES")
    ?.trim()
    ?.let { File(it) }
    ?.takeIf { it.exists() }
val releaseKeystoreProps = releaseKeystorePropsFile?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}
// Si la ruta de storeFile no existe (p. ej. escrita con otro nombre de usuario), se busca junto a keystore.properties.
val releaseStoreFile = releaseKeystoreProps?.getProperty("storeFile")?.trim()?.let { path ->
    File(path).takeIf { it.exists() } ?: File(releaseKeystorePropsFile!!.parentFile, File(path).name)
}

android {
    namespace = "io.github.neoruben.mercadonastats"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.neoruben.mercadonastats"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.2.0"

        buildConfigField("String", "DEFAULT_GMAIL_CLIENT_ID", "\"$defaultClientId\"")
        manifestPlaceholders["oauthRedirectScheme"] = reversedClientIdScheme
    }

    signingConfigs {
        if (releaseKeystoreProps != null && releaseStoreFile?.exists() == true) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseKeystoreProps.getProperty("storePassword")
                keyAlias = releaseKeystoreProps.getProperty("keyAlias")
                keyPassword = releaseKeystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // La versión de pruebas se instala como app aparte, sin tocar la instalada ni sus tickets.
        // (El inicio de sesión con Google no funciona en ella; IMAP y PDF sí.)
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
            // Licencias duplicadas de Jakarta Mail / Activation
            excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md")
        }
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
    // IMAP (misma librería que la versión Docker)
    implementation("org.eclipse.angus:jakarta.mail:2.0.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.pdfbox:pdfbox:2.0.32")
}
