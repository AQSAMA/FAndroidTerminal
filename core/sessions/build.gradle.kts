plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.github.aqsama.fandroidterminal.sessions"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    buildFeatures { aidl = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    api(project(":core:terminal"))
    implementation(project(":core:runtime"))
    implementation(libs.androidx.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
