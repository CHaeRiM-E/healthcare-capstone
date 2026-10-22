import java.util.Properties

val localProperties = Properties().apply {
    val localPropertiesFile =
        rootProject.file("local.properties")

    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use {
            load(it)
        }
    }
}

val kakaoRestApiKey =
    localProperties.getProperty(
        "KAKAO_REST_API_KEY",
        ""
    )
val kakaoNativeAppKey =
    localProperties.getProperty(
        "KAKAO_NATIVE_APP_KEY",
        ""
    )
val factChatApiKey =
    localProperties.getProperty(
        "FACTCHAT_API_KEY",
        ""
    )

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        // local.properties에 저장한 카카오 키를
        // 문자열 리소스로 변환
        resValue(
            "string",
            "kakao_rest_api_key",
            kakaoRestApiKey
        )
        resValue(
            "string",
            "factchat_api_key",
            factChatApiKey
        )
        resValue(
            "string",
            "kakao_native_app_key",
            kakaoNativeAppKey
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-storage")
    // ML Kit 한국어 텍스트 인식
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation(
        "androidx.recyclerview:recyclerview:1.4.0"
    )
    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation(
        "com.google.android.gms:play-services-location:21.3.0"
    )

    implementation(
        "com.squareup.okhttp3:okhttp:4.12.0"
    )
    // 카카오 지도 SDK
    implementation("com.kakao.maps.open:android:2.12.8")

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.core.ktx)
    implementation(libs.material)

    testImplementation(libs.junit)

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )
}