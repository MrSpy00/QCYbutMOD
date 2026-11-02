plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "com.aegissoft.qcybutmod"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aegissoft.qcybutmod"
    minSdk = 26
    targetSdk = 36
    versionCode = 100
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
    debug {
      applicationIdSuffix = ".debug"
      isDebuggable = true
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    freeCompilerArgs.addAll(
      "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
      "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
      "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
    )
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
  implementation("androidx.activity:activity-compose:1.12.2")

  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.animation:animation")
  implementation("androidx.navigation:navigation-compose:2.9.6")

  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")

  implementation("com.google.android.material:material:1.13.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
  implementation("com.google.code.gson:gson:2.11.0")
  implementation("com.squareup.okhttp3:okhttp:4.12.0")

  // ML Kit Offline AI Translation & Language Identification
  implementation("com.google.mlkit:translate:17.0.3")
  implementation("com.google.mlkit:language-id:17.0.6")

  // Glance for Modern Compose Widgets
  implementation("androidx.glance:glance:1.1.0")
  implementation("androidx.glance:glance-appwidget:1.1.0")
  implementation("androidx.glance:glance-material3:1.1.0")

  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.2.1")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
