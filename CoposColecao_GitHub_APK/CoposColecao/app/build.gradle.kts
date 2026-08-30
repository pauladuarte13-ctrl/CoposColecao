plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
}plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "pt.sro.coposcolecao"
    compileSdk = 35

    defaultConfig {
        applicationId = "pt.sro.coposcolecao"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlinOptions {
    jvmTarget = "17"
}
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.mediapipe:tasks-vision:0.10.26")
}
val imageEmbedderModel = layout.projectDirectory.file(
    "src/main/assets/mobilenet_v3_small.tflite"
)

tasks.register("downloadImageEmbedderModel") {
    outputs.file(imageEmbedderModel)
    doLast {
        val target = imageEmbedderModel.asFile
        if (!target.exists()) {
            target.parentFile.mkdirs()
         val modelUrl = uri(
    "https://storage.googleapis.com/mediapipe-models/image_embedder/" +
    "mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite"
).toURL()

println("A descarregar o modelo de IA para ${target.absolutePath}")

modelUrl.openStream().use { input ->
    target.outputStream().use { output -> input.copyTo(output) }
}
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("downloadImageEmbedderModel")
}
