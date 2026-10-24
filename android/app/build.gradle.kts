import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kiedziedojade.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kiedziedojade.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "1.4.13"

        // JEDNO źródło adresów sieciowych — reszta kodu czyta BuildConfig.
        // raw.githubusercontent: dane są świeże od razu po commicie harvestera.
        buildConfigField(
            "String", "DATA_URL",
            "\"https://raw.githubusercontent.com/royallorde-maker/kiedy-dojade/main/public/data\""
        )
        buildConfigField(
            "String", "UPDATE_URL",
            "\"https://raw.githubusercontent.com/royallorde-maker/kiedy-dojade/main/backend/api/version.json\""
        )
        buildConfigField(
            "String", "REPO_URL",
            "\"https://github.com/royallorde-maker/kiedy-dojade\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Podpis release: keystore.properties (gitignored; w CI z sekretów).
            // Gdy klucza NIE ma — packageRelease/bundleRelease padają z jawnym
            // błędem; debug/testy działają bez klucza (jak w bs-companion).
            val ksPropsFile = rootProject.file("keystore.properties")
            if (ksPropsFile.exists()) {
                signingConfigs {
                    create("release") {
                        val ksProps = Properties()
                        FileInputStream(ksPropsFile).use { ksProps.load(it) }
                        storeFile = file(ksProps["storeFile"] as String)
                        storePassword = ksProps["storePassword"] as String
                        keyAlias = ksProps["keyAlias"] as String
                        keyPassword = ksProps["keyPassword"] as String
                    }
                }
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("Brak keystore.properties — packageRelease/bundleRelease padnie z błędem.")
                tasks.matching { it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
                    doFirst {
                        throw GradleException(
                            "Brak keystore.properties — release MUSI być podpisany tym samym kluczem " +
                                "(unsigned apk łamie aktualizacje OTA)."
                        )
                    }
                }
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Mapa: kafelki OpenStreetMap bez klucza API (własny UA w MapScreen.kt).
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.0.21")
    // org.json nie istnieje na JVM — dokładamy, żeby parser danych dał się testować.
    testImplementation("org.json:json:20240303")
}
