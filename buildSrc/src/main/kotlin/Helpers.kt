import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByName
import java.util.Base64
import java.util.Properties
import kotlin.system.exitProcess

private val Project.android get() = extensions.getByName<ApplicationExtension>("android")

private val Project.androidComponents
    get() = extensions.getByName<ApplicationAndroidComponentsExtension>("androidComponents")

private lateinit var metadata: Properties
private lateinit var localProperties: Properties

fun Project.requireMetadata(): Properties {
    if (!::metadata.isInitialized) {
        metadata = Properties().apply {
            load(rootProject.file("nb4a.properties").inputStream())
        }
    }
    return metadata
}

fun Project.requireLocalProperties(): Properties {
    if (!::localProperties.isInitialized) {
        localProperties = Properties()

        val base64 = System.getenv("LOCAL_PROPERTIES")
        if (!base64.isNullOrBlank()) {

            localProperties.load(Base64.getDecoder().decode(base64).inputStream())
        } else if (project.rootProject.file("local.properties").exists()) {
            localProperties.load(rootProject.file("local.properties").inputStream())
        }
    }
    return localProperties
}

fun Project.setupCommon() {
    android.apply {
        buildToolsVersion = "36.0.0"
        compileSdk = 35
        defaultConfig {
            minSdk = 21
            targetSdk = 35
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = true
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_8
            targetCompatibility = JavaVersion.VERSION_1_8
        }
        lint {
            showAll = true
            checkAllWarnings = true
            checkReleaseBuilds = true
            warningsAsErrors = true
            textOutput = project.file("build/lint.txt")
            htmlOutput = project.file("build/lint.html")
        }
        packaging {
            resources.excludes.addAll(
                listOf(
                    "**/*.kotlin_*",
                    "/META-INF/*.version",
                    "/META-INF/native/**",
                    "/META-INF/native-image/**",
                    "/META-INF/INDEX.LIST",
                    "DebugProbesKt.bin",
                    "com/**",
                    "org/**",
                    "**/*.java",
                    "**/*.proto",
                    "okhttp3/**"
                )
            )
        }
        buildTypes {
            getByName("release") {
                isShrinkResources = true
                if (System.getenv("nkmr_minify") == "0") {
                    isShrinkResources = false
                    isMinifyEnabled = false
                }
            }
            getByName("debug") {
                isDebuggable = true
                isJniDebuggable = true
            }
        }
    }
}

fun Project.setupAppCommon() {
    setupCommon()

    val lp = requireLocalProperties()
    val nyaboxStore = System.getenv("NYABOX_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
    val keystorePwd = System.getenv("NYABOX_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: lp.getProperty("KEYSTORE_PASS")
        ?: System.getenv("KEYSTORE_PASS")
    val alias = System.getenv("NYABOX_KEY_ALIAS")?.takeIf { it.isNotBlank() }
        ?: lp.getProperty("ALIAS_NAME")
        ?: System.getenv("ALIAS_NAME")
    val pwd = System.getenv("NYABOX_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: lp.getProperty("ALIAS_PASS")
        ?: System.getenv("ALIAS_PASS")

    android.apply {
        if (nyaboxStore != null && (keystorePwd.isNullOrBlank() || alias.isNullOrBlank() || pwd.isNullOrBlank())) {
            error("NyaBox release signing is incomplete")
        }
        if (!keystorePwd.isNullOrBlank()) {
            signingConfigs {
                create("release") {
                    storeFile = if (nyaboxStore != null) file(nyaboxStore) else rootProject.file("release.keystore")
                    storePassword = keystorePwd
                    keyAlias = alias
                    keyPassword = pwd
                    if (nyaboxStore != null) {
                        storeType = "PKCS12"
                    }
                }
            }
        }
        buildTypes {
            val key = signingConfigs.findByName("release")
            if (key != null) {
                getByName("release").signingConfig = key
                getByName("debug").signingConfig = key
            }
        }
    }
}

fun Project.setupApp() {
    val pkgName = requireMetadata().getProperty("PACKAGE_NAME")
    val verName = requireMetadata().getProperty("VERSION_NAME")
    val preVersion = requireMetadata().getProperty("PRE_VERSION_NAME")
    val verCode = (requireMetadata().getProperty("VERSION_CODE").toInt()) * 5
    android.apply {
        defaultConfig {
            applicationId = pkgName
            versionCode = verCode
            versionName = verName
            buildConfigField("String", "PRE_VERSION_NAME", "\"\"")
        }
    }
    setupAppCommon()

    android.apply {
        buildTypes {
            getByName("release") {
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    file("proguard-rules.pro")
                )
            }
        }

        splits.abi {
            reset()
            isEnable = true
            isUniversalApk = false
            include("armeabi-v7a")
            include("arm64-v8a")
            include("x86")
            include("x86_64")
        }

        flavorDimensions += "vendor"
        productFlavors {
            create("oss")
            create("fdroid")
            create("play")
            create("preview") {
                buildConfigField(
                    "String",
                    "PRE_VERSION_NAME",
                    "\"${requireMetadata().getProperty("PRE_VERSION_NAME")}\""
                )
            }
        }

        for (abi in listOf("Arm64", "Arm", "X64", "X86")) {
            tasks.create("assemble" + abi + "FdroidRelease") {
                dependsOn("assembleFdroidRelease")
            }
        }

        sourceSets.getByName("main").apply {
            jniLibs.srcDir("executableSo")
        }
    }

    androidComponents.onVariants { variant ->
        val isPreview = variant.productFlavors.any { it.second == "preview" }
        val baseName = if (isPreview) "NyaBox-$preVersion" else "NyaBox-$verName"
        variant.outputs.forEach { output ->
            val abi = output.filters.firstOrNull { it.filterType.name == "ABI" }?.identifier
            output.outputFileName.set(if (abi.isNullOrEmpty()) "$baseName.apk" else "$baseName-$abi.apk")
        }
    }
}