plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

dependencies {
    implementation(project(":controls"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
}

// Native compilation is a separate incremental build, never a Gradle side effect.
val nativeStage = providers.gradleProperty("triaevumNativeStage")
val triaevumVersionCode = providers.gradleProperty("triaevumVersionCode").orNull?.toIntOrNull() ?: 1
val triaevumVersionName = providers.gradleProperty("triaevumVersionName").orElse("0.1-intro-dev").get()
val releaseKeystorePath = providers.environmentVariable("TRIAEVUM_RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("TRIAEVUM_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("TRIAEVUM_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("TRIAEVUM_RELEASE_KEY_PASSWORD").orNull
val releaseSigningAvailable = listOf(releaseKeystorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
android {
    namespace = "org.triaevum.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.triaevum.android"
        minSdk = 29
        targetSdk = 33
        versionCode = triaevumVersionCode
        versionName = triaevumVersionName
        ndk { abiFilters += "arm64-v8a" }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    sourceSets.getByName("main") {
        if (nativeStage.isPresent) jniLibs.srcDir(nativeStage.get())
    }
    signingConfigs {
        if (releaseSigningAvailable) {
            create("triaevumRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("triaevumRelease")
            }
        }
    }
    packaging { jniLibs.useLegacyPackaging = true }
    androidResources {
        noCompress += listOf("bin", "o3tu")
    }
}
tasks.register("checkNativeStage") {
    onlyIf {
        gradle.startParameter.taskNames.any { it.contains("assemble", ignoreCase = true) || it.contains("package", ignoreCase = true) }
    }
    doLast {
        check(nativeStage.isPresent) {
            "Supply -PtriaevumNativeStage from the Android native build."
        }
        val stageRoot = file(nativeStage.get())
        val expectedAbis = setOf("arm64-v8a")
        val actualAbis = stageRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.name }
            ?.toSet() ?: emptySet()
        check(actualAbis == expectedAbis) {
            "Native stage must contain only $expectedAbis; found $actualAbis"
        }

        val expectedLibraries = setOf(
            "libTriAevum.so",
            "libtriaevum_title_bootstrap.so",
            "libtriaevum_title_aot.so",
            "libc++_shared.so"
        )
        val abiRoot = file("${nativeStage.get()}/arm64-v8a")
        val actualLibraries = abiRoot.listFiles()
            ?.filter { it.isFile && it.extension == "so" }
            ?.map { it.name }
            ?.toSet() ?: emptySet()
        check(actualLibraries == expectedLibraries) {
            "Unexpected native product payload. Expected $expectedLibraries, found $actualLibraries"
        }
        for (name in expectedLibraries) {
            check(file("${nativeStage.get()}/arm64-v8a/$name").isFile) {
                "Missing native library: $name"
            }
        }
    }
}
tasks.named("preBuild") { dependsOn("checkNativeStage") }
