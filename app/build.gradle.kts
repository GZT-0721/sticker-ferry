plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.stickerferry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stickerferry"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ------------------------------------------------------------------
    // 签名配置
    //
    // 自用的话全部留空即可 —— Debug 版不需要签名，能直接装。
    //
    // 想要 Release 版（体积更小）再配。密钥从环境变量读，
    // 绝不写进这个文件（写进去就等于泄露了）。
    //
    // 在 GitHub 上配置：Settings → Secrets and variables → Actions
    // ------------------------------------------------------------------
    signingConfigs {
        create("release") {
            val storePath = System.getenv("SIGNING_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
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
            // 只有在配了密钥时才用 release 签名，否则用 debug 签名兜底
            signingConfig = if (!System.getenv("SIGNING_STORE_FILE").isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            // 保持和 release 相同包名，避免用户困惑
            // （如果想要两个版本共存，可以打开下面这行）
            // applicationIdSuffix = ".debug"
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    // SAF 目录访问
    implementation(libs.androidx.documentfile)

    // Key 加密存储
    implementation(libs.androidx.security.crypto)

    // 图片加载（含 GIF 动图支持）
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // 网络（AI 接口 / 图库检索）
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.ui.tooling)
}
