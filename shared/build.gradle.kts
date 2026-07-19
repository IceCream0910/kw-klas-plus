import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    android {
       namespace = "com.icecream.kwklasplus.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

val verifyCommonMainPlatformBoundaries by tasks.registering {
    val commonSources = fileTree("src/commonMain/kotlin") {
        include("**/*.kt")
    }
    inputs.files(commonSources)

    doLast {
        val forbiddenImports = listOf(
            "import android.",
            "import androidx.",
            "import org.jetbrains.compose.",
            "import platform.",
        )
        val violations = commonSources.files.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                if (forbiddenImports.any(line::startsWith)) {
                    "${source.relativeTo(projectDir)}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "commonMain에 플랫폼 UI/API import를 사용할 수 없습니다.\n${violations.joinToString("\n")}",
            )
        }
    }
}

val verifyPlatformSourceSetBoundaries by tasks.registering {
    val platformSources = files(
        fileTree("src/androidMain/kotlin") { include("**/*.kt") },
        fileTree("src/iosMain/kotlin") { include("**/*.kt") },
    )
    inputs.files(platformSources)

    doLast {
        val appImportPrefix = "import com.icecream.kwklasplus."
        val sharedImportPrefix = "import com.icecream.kwklasplus.core."
        val violations = platformSources.files.flatMap { source ->
            source.readLines().mapIndexedNotNull { index, line ->
                if (line.startsWith(appImportPrefix) && !line.startsWith(sharedImportPrefix)) {
                    "${source.relativeTo(projectDir)}:${index + 1}: $line"
                } else {
                    null
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "shared 플랫폼 source set이 플랫폼 앱 구현을 참조할 수 없습니다.\n${violations.joinToString("\n")}",
            )
        }
    }
}

tasks.named("check") {
    dependsOn(verifyCommonMainPlatformBoundaries)
    dependsOn(verifyPlatformSourceSetBoundaries)
}
