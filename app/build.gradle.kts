import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Номер версии задаёт сборка на GitHub (номер запуска). Локально — 1.
val buildNumber = (System.getenv("VERSION_CODE") ?: "1").toInt()

// Ключ подписи приходит из секретов GitHub. Без него собирается неподписанная версия.
val signingStoreFile: String? = System.getenv("SIGNING_STORE_FILE")
val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
val hasSigning = !signingStoreFile.isNullOrBlank() &&
    !signingPassword.isNullOrBlank() &&
    file(signingStoreFile!!).exists()

android {
    namespace = "io.github.cmix7777.kazhdyidnevnik"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.cmix7777.kazhdyidnevnik"
        minSdk = 29
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingPassword
                keyAlias = "kazhdyidnevnik"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Классы kotlin-stdlib-jdk7/jdk8 давно входят в обычный kotlin-stdlib. Старые версии этих
// пакетов тянет WorkManager, а Maven Central их не отдаёт, поэтому исключаем.
configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Разбор страницы расписания, кэш на телефоне, фоновые задачи
    implementation("org.jsoup:jsoup:1.20.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    testImplementation("junit:junit:4.13.2")
}
