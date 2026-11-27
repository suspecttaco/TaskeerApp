import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Única fuente de la versión: versionCode se deriva de versionName para que
// nunca diverjan y siempre crezca de forma monótona (Play lo exige).
// Supone minor y patch < 100.
val appVersionName = providers.gradleProperty("taskeer.versionName").get()
val appVersionCode = appVersionName.split(".").map { it.toInt() }
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

// Las credenciales salen de un fichero local no versionado o, en su defecto,
// del entorno; sin ninguna de las dos el release queda sin firmar.
val keystoreProperties = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText.map { text -> Properties().apply { load(text.reader()) } }
    .orNull

fun signingValue(propertyKey: String, envKey: String): String? =
    keystoreProperties?.getProperty(propertyKey) ?: providers.environmentVariable(envKey).orNull

val releaseStoreFile = signingValue("storeFile", "TASKEER_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "TASKEER_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "TASKEER_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "TASKEER_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

// ID de cliente Web de Google Cloud (no es un secreto, pero depende del entorno): vacío oculta el botón de Google.
val googleWebClientId = providers.gradleProperty("taskeer.googleWebClientId").getOrElse("").trim().replace("\"", "")

// La URL del proyecto de Supabase y la anon key son públicas por diseño, pero dependen del entorno: nunca se versionan.
// Sin ellas la app compila y el botón de Google queda oculto.
val supabaseUrl = providers.gradleProperty("taskeer.supabaseUrl").getOrElse("").trim().replace("\"", "")
val supabaseAnonKey = providers.gradleProperty("taskeer.supabaseAnonKey").getOrElse("").trim().replace("\"", "")

android {
    namespace = "com.simposio.taskeer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.simposio.taskeer"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Sin backend local por defecto: debug apunta a preproducción. Quien tenga uno propio
            // lo sobrescribe con -Ptaskeer.apiBaseUrl; 10.0.2.2 (la máquina vista desde el emulador)
            // sigue permitido en cleartext de debug.
            val apiBaseUrl = providers.gradleProperty("taskeer.apiBaseUrl")
                .getOrElse("https://backend-taskeerv3-preproduction.onrender.com/api/v1/")
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
            buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        }
        release {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://backend-taskeerv3-preproduction.onrender.com/api/v1/\"",
            )
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
            buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.googleid)
    implementation(libs.okhttp)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.androidx.datastore.preferences.core)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}