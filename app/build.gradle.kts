plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "ir.bot.parsoil"
    compileSdk = 34
    defaultConfig {
        applicationId = "ir.bot.parsoil"
        minSdk = 24; targetSdk = 34
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "1.0.0"
    }
    signingConfigs { create("rel") {
        storeFile = file("release.jks"); storePassword = "parsoilbot"
        keyAlias = "bot"; keyPassword = "parsoilbot" } }
    buildTypes { release { signingConfig = signingConfigs.getByName("rel"); isMinifyEnabled = false } }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.13.1") }
