plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "android.clip.cpp"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 25

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",

                    // --- Add all of these ---
                    // Disable host-specific optimizations
                    "-DCLIP_NATIVE=OFF",

                    // Disable all x86-specific instructions
                    "-DCLIP_AVX=OFF",
                    "-DCLIP_AVX2=OFF",
                    "-DCLIP_FMA=OFF",
                    "-DCLIP_AVX512=OFF",
                    "-DCLIP_AVX512_VBMI=OFF",
                    "-DCLIP_AVX512_VNNI=OFF",
                    "-DCLIP_F16C=OFF", // Disable this too, just in case

                    // Disable OS-specific libraries
                    "-DCLIP_ACCELERATE=OFF",
                    "-DCLIP_OPENBLAS=OFF",

                    // Disable examples and extra builds
                    "-DCLIP_BUILD_EXAMPLES=OFF",
                    "-DCLIP_BUILD_IMAGE_SEARCH=OFF"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}