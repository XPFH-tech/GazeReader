plugins {
 id("com.android.application") version "8.7.3"
 id("org.jetbrains.kotlin.android") version "2.0.21"
}
android {
 namespace = "dev.xpfh.airgesture"
 compileSdk = 35
 signingConfigs {
  getByName("debug") {
   enableV1Signing = true
   enableV2Signing = true
   enableV3Signing = false
   enableV4Signing = false
  }
 }
 defaultConfig { applicationId = "dev.xpfh.airgesture"; minSdk = 31; targetSdk = 33; versionCode = 6; versionName = "3.0-A3"; ndk { abiFilters += "arm64-v8a" } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 androidResources { noCompress += "task" }
}
dependencies {
 implementation("androidx.core:core-ktx:1.15.0")
 implementation("androidx.lifecycle:lifecycle-service:2.8.7")
 implementation("androidx.camera:camera-core:1.4.1")
 implementation("androidx.camera:camera-camera2:1.4.1")
 implementation("androidx.camera:camera-lifecycle:1.4.1")
 implementation("com.google.mediapipe:tasks-vision:0.10.14")
 testImplementation("junit:junit:4.13.2")
}
