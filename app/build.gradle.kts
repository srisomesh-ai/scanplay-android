plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "in.scanplay.app"; compileSdk = 35
    defaultConfig { applicationId = "in.scanplay.app"; minSdk = 24; targetSdk = 35; versionCode = 3; versionName = "1.0.2" }
    signingConfigs { create("release") {
        val ks = System.getenv("KEYSTORE_PATH")
        if (ks != null) { storeFile = file(ks); storePassword = System.getenv("KEYSTORE_PASSWORD"); keyAlias = System.getenv("KEY_ALIAS"); keyPassword = System.getenv("KEY_PASSWORD") } } }
    buildTypes { release { isMinifyEnabled = false; signingConfig = if (System.getenv("KEYSTORE_PATH") != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1"); implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.11.0"); implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.activity:activity-ktx:1.9.1")
}
