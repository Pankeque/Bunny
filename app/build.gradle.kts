plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.bunny"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bunny"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
        buildConfigField("String", "SOCKET_URL", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "BACKEND_IP", "\"\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("String", "SOCKET_URL", "\"http://10.0.2.2:8080\"")
        }
        release {
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
            buildConfigField("String", "SOCKET_URL", "\"http://10.0.2.2:8080\"")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Lifecycle atualizado para garantir suporte ao collectAsState moderno
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.navigation:navigation-compose:2.7.6")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
