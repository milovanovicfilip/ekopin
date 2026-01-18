import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val geoapifyKey: String = localProperties.getProperty("GEOAPIFY_KEY") ?: ""
val mqttHost: String = localProperties.getProperty("MQTT_HOST") ?: ""
val mqttPort: String = localProperties.getProperty("MQTT_PORT") ?: ""

android {
    namespace = "com.example.mobile"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.example.mobile"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GEOAPIFY_KEY", "\"$geoapifyKey\"")
        buildConfigField("String", "MQTT_HOST", "\"$mqttHost\"")
        buildConfigField("String", "MQTT_PORT", "\"$mqttPort\"")
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

    packaging {
        resources {
            excludes += setOf("META-INF/INDEX.LIST", "META-INF/*.kotlin_module")
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }
}

// Task to copy native libraries from JARs to jniLibs
tasks.register("copyGdxNatives") {
    doLast {
        val nativesConfig = configurations.getByName("debugRuntimeClasspath")
        val jniLibsDir = file("src/main/jniLibs")
        
        nativesConfig.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            if (artifact.moduleVersion.id.group == "com.badlogicgames.gdx" &&
                artifact.moduleVersion.id.name == "gdx-platform" &&
                artifact.classifier != null && artifact.classifier!!.startsWith("natives-")) {
                
                val classifier = artifact.classifier!!
                val abiName = when (classifier) {
                    "natives-armeabi-v7a" -> "armeabi-v7a"
                    "natives-arm64-v8a" -> "arm64-v8a"
                    "natives-x86" -> "x86"
                    "natives-x86_64" -> "x86_64"
                    else -> return@forEach
                }
                
                val abiDir = file("$jniLibsDir/$abiName")
                abiDir.mkdirs()
                
                copy {
                    from(zipTree(artifact.file))
                    into(abiDir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("copyGdxNatives")
}

dependencies {
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.gridlayout:gridlayout:1.0.0")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val gdxVersion = "1.12.1"
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    implementation("org.osmdroid:osmdroid-android:6.1.18")

    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    implementation("com.github.hannesa2:paho.mqtt.android:4.2.3")
}