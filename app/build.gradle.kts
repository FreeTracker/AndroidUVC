import java.util.zip.GZIPOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "net.d7z.net.oss.uvc"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "net.d7z.net.oss.uvc"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                // 关键：libusb 需要特定的 C 标志
                cppFlags.add("-std=c++11")
                arguments.add("-DANDROID_STL=c++_shared")
                // 这里的 abiFilters 决定了生成的 .so 支持哪些手机
            }
        }
    }

    signingConfigs {
        val keystoreFilePath = System.getenv("KEYSTORE_FILE")
        if (keystoreFilePath != null && file(keystoreFilePath).exists()) {
            create("release") {
                storeFile = file(keystoreFilePath)
                storePassword = System.getenv("KEY_STORE_PASSWORD")
                keyAlias = System.getenv("ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            val releaseConfig = signingConfigs.findByName("release")
            if (releaseConfig != null) {
                signingConfig = releaseConfig
            }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"

        }
    }
    buildFeatures {
        compose = true
    }

    sourceSets.getByName("main").res.setSrcDirs(
        listOf(
            "src/main/res",
            layout.buildDirectory.dir("generated/res/bundledLicenses").get().asFile
        )
    )
}

aboutLibraries {
    collect {
        configPath = file("../aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
}

val bundledLicenseSources = mapOf(
    "project_license.gz" to rootProject.layout.projectDirectory.file("LICENSE"),
    "libusb_notice.gz" to layout.projectDirectory.file("src/main/cpp/libusb/COPYING"),
    "libuvc_license.gz" to layout.projectDirectory.file("src/main/cpp/libuvc/LICENSE.txt")
)

val generateBundledLicenseResources by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/res/bundledLicenses/raw")

    inputs.files(bundledLicenseSources.values)
    outputs.dir(outputDir)

    doLast {
        val rawDir = outputDir.get().asFile
        if (!rawDir.exists()) {
            rawDir.mkdirs()
        }

        bundledLicenseSources.forEach { (outputName, sourceFile) ->
            val source = sourceFile.asFile
            val target = rawDir.resolve(outputName)
            source.inputStream().buffered().use { input ->
                target.outputStream().buffered().use { fileOutput ->
                    GZIPOutputStream(fileOutput).use { gzipOutput ->
                        input.copyTo(gzipOutput)
                    }
                }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateBundledLicenseResources)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.material)
    implementation(libs.nanohttpd)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
