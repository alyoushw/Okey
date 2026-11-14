import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
}

val voiceProperties = Properties().apply {
  rootProject.file(".env").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun voiceSetting(name: String): String = System.getenv(name) ?: voiceProperties.getProperty(name, "")
fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.okeycompanion.vjkrp"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.1"
    buildConfigField("String", "VOICE_SERVICE_URL", quoted(voiceSetting("VOICE_SERVICE_URL")))

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }

  }

  buildTypes {
    release {
      buildConfigField("String", "ELEVENLABS_API_KEY", quoted(""))
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      buildConfigField("String", "ELEVENLABS_API_KEY", quoted(voiceSetting("ELEVENLABS_API_KEY")))
      // Standard Android debug key is generated locally; no private keystore is required.
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}


dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
}

// A public release must include the complete offline game voice pack.
val bundledGameAudio = listOf("turn_start", "warning_10s", "countdown_5", "countdown_4", "countdown_3", "countdown_2", "countdown_1", "time_up", "victory")
  .map { layout.projectDirectory.file("src/main/assets/game_audio/$it.mp3").asFile }
@org.gradle.work.DisableCachingByDefault(because = "Validates release inputs without producing outputs")
abstract class VerifyGameAudio : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val audioFiles: ConfigurableFileCollection

  @TaskAction
  fun verify() {
    val missing = audioFiles.files.filter { !it.isFile || it.length() == 0L }.map { it.name }
    check(missing.isEmpty()) { "Missing bundled game audio: ${missing.joinToString()}. Run scripts/generate_game_audio.py before release." }
  }
}
val verifyGameAudio by tasks.registering(VerifyGameAudio::class) {
  audioFiles.from(bundledGameAudio)
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyGameAudio) }
