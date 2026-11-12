import java.util.Properties
import com.android.build.api.dsl.LibraryExtension

plugins { alias(libs.plugins.android.library) }
android {
    namespace = "io.github.aqsama.fandroidterminal.runtime"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(libs.coroutines.android)
    implementation("org.tukaani:xz:1.10")
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
}

val packagedProot = layout.buildDirectory.dir("generated/proot")
val buildProot by tasks.registering(Exec::class) {
    val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        ?: Properties().apply {
            val local = rootProject.file("local.properties")
            if (local.exists()) local.inputStream().use { load(it) }
        }.getProperty("sdk.dir", "${System.getProperty("user.home")}/Android/Sdk")
    inputs.files(rootProject.file("scripts/build-proot-runtime.sh"), rootProject.file("patches/proot-android.patch"))
    inputs.dir(rootProject.file("third_party/talloc"))
    inputs.file(rootProject.file("dependencies.lock.json"))
    outputs.dir(packagedProot)
    commandLine("bash", rootProject.file("scripts/build-proot-runtime.sh"),
        "$sdk/ndk/28.2.13676358", "29", packagedProot.get().dir("arm64-v8a").asFile)
}
extensions.configure<LibraryExtension> {
    sourceSets.getByName("main").jniLibs.srcDir(packagedProot)
    packaging.jniLibs.useLegacyPackaging = true
    // PRoot and its loader are executable ELFs installed in Android's native library directory.
    packaging.jniLibs.keepDebugSymbols += setOf("**/libproot.so", "**/libproot_loader.so")
}
tasks.named("preBuild").configure { dependsOn(buildProot) }

// Keep service-owned installer recovery checks in the existing CI verification command.
tasks.matching { it.name == "testDebugUnitTest" }.configureEach {
    finalizedBy(":core:sessions:testDebugUnitTest")
}
