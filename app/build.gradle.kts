plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.powerscheduler"
    compileSdk = 34

    buildTypes {
    debug {
    isDebuggable = false
       }
    }

    defaultConfig {
        applicationId = "com.example.powerscheduler"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags += ""
                abiFilters += "arm64-v8a"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

       packaging {
       jniLibs {
           useLegacyPackaging = true
       }
    }

    buildTypes {

           debug {
        isDebuggable = false
        }   

        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.register("copyNativeBinaryToAssets") {
    dependsOn("assembleDebug")
    doLast {
        val sourceFile = file("${layout.buildDirectory.get().asFile}/intermediates/cmake/debug/obj/arm64-v8a/librtc_setter.so")
        val destDir = file("src/main/assets")
        if (sourceFile.exists()) {
            if (!destDir.exists()) destDir.mkdirs()
            sourceFile.copyTo(file("${destDir.absolutePath}/rtc_setter"), overwrite = true)
            println("Successfully copied librtc_setter.so to assets/rtc_setter")
        } else {
            println("Warning: Compiled binary not found at ${sourceFile.absolutePath}")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}