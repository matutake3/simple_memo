plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

/** Optional local override read from ~/.gradle/gradle.properties or ./gradle.properties:
 *  trialBypass=true  — Release でも試用期限を無視（開発者の端末のみに推奨）
 *  trialBypass=false — Debug でも試用期限を適用（期限切れUIの確認用）
 * Debug 既定: 制限オフ。false 指定時のみ Debug でも本番と同じ期限挙動。
 */
val trialBypassGradleProp: String? =
    project.findProperty("trialBypass") as? String

android {
    namespace = "jp.simplist.memo"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.simplist.memo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val trialBypassRelease =
                trialBypassGradleProp?.equals("true", ignoreCase = true) == true
            buildConfigField("boolean", "TRIAL_BYPASS", trialBypassRelease.toString())
        }
        debug {
            isMinifyEnabled = false
            val trialBypassDebug = when (trialBypassGradleProp?.lowercase()) {
                "false", "no", "0" -> false
                else -> true
            }
            buildConfigField("boolean", "TRIAL_BYPASS", trialBypassDebug.toString())
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
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES"
        )
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Lifecycle / Coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // RecyclerView (memo / checklist / tag lists)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room (memos.db)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore Preferences (app settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Biometric (privacy lock)
    implementation("androidx.biometric:biometric:1.1.0")

    // Google Play Billing (permanent_unlock IAP, ¥250)
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // WorkManager (自動バックアップの daily スケジューラ)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // DocumentFile (SAF Tree URI 配下のファイル作成・列挙に必要)
    implementation("androidx.documentfile:documentfile:1.0.1")

    // (Backup の JSON は org.json.JSONObject (Android 標準) で組み立てる)

    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
