plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
val buildCode = providers.environmentVariable("VERSION_CODE")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER").map { (1000 + it.toInt()).toString() })
    .orElse("1").get().toInt().also { require(it in 1..2_100_000_000) }
val commit = providers.environmentVariable("BUILD_COMMIT").orElse("local").get()
    .also { require(it.matches(Regex("[a-zA-Z0-9-]{1,40}"))) }
val releaseVersion = providers.environmentVariable("VERSION_NAME").orElse("0.1.0").get()
    .also { require(it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")) && it.length <= 100) }
val pullRequest = providers.environmentVariable("BUILD_PR").orElse("").get()
    .also { require(it.matches(Regex("[0-9]{0,10}"))) }
android {
    namespace = "io.github.aqsama.fandroidterminal"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.aqsama.fandroidterminal"
        minSdk = 29
        targetSdk = 36
        versionCode = buildCode
        versionName = releaseVersion
        buildConfigField("String", "BUILD_COMMIT", "\"$commit\"")
        buildConfigField("String", "BUILD_PR", "\"$pullRequest\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("preview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            signingConfig = null
            matchingFallbacks += "release"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { animationsDisabled = true }
}
dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:terminal"))
    implementation(project(":core:sessions"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.coroutines.android)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    debugImplementation(libs.compose.test.manifest)
}
