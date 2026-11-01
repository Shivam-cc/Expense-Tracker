import java.net.URI

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeCompiler)
}

val releaseApiUrlProvider = providers.gradleProperty("expenseflow.apiUrl")
val releaseApiUrl = releaseApiUrlProvider.orNull
if (releaseApiUrl != null) {
    val releaseUri = URI(releaseApiUrl)
    require(releaseUri.scheme == "https" && !releaseUri.host.isNullOrBlank() &&
        releaseUri.rawUserInfo == null && releaseUri.rawQuery == null && releaseUri.rawFragment == null) {
        "Release expenseflow.apiUrl must be an HTTPS URL without credentials, query or fragment"
    }
}

android {
    namespace = "com.expensetracker.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.expensetracker.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug { buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080\"") }
        release { buildConfigField("String", "API_BASE_URL", "\"${releaseApiUrl.orEmpty()}\"") }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

val checkReleaseApiUrl = tasks.register("checkReleaseApiUrl") {
    doLast {
        if (releaseApiUrlProvider.orNull.isNullOrBlank()) {
            error(
                "Release build requires Gradle property \"expenseflow.apiUrl\". " +
                    "Set it to the API base URL, for example -Pexpenseflow.apiUrl=https://api.example.com"
            )
        }
    }
}
tasks.configureEach {
    if (name.contains("Release") && name != "checkReleaseApiUrl") {
        dependsOn(checkReleaseApiUrl)
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar)
    implementation(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.preview)
    debugImplementation(libs.androidx.compose.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.sqldelight.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.test)
    androidTestImplementation(libs.androidx.test.junit)
    debugImplementation(libs.androidx.compose.test.manifest)
}
