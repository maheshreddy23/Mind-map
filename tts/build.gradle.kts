import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val emulatorAbi = providers.gradleProperty("withX86_64").isPresent
val abis = listOf("arm64-v8a") + (if (emulatorAbi) listOf("x86_64") else emptyList())

// Prebuilt sherpa-onnx Android libraries (libsherpa-onnx-jni.so + libonnxruntime.so), Apache-2.0.
// Downloaded at build time only; the app never touches the network at runtime.
val sherpaVersion = "1.13.8"
val sherpaDir = layout.buildDirectory.dir("sherpa-onnx")
val sherpaJniLibs = layout.buildDirectory.dir("sherpa-onnx/jniLibs")

val fetchSherpaOnnx by tasks.registering {
    description = "Downloads and unpacks the prebuilt sherpa-onnx Android libraries."
    val archive = sherpaDir.map { it.file("sherpa-onnx-v$sherpaVersion-android.tar.bz2") }
    outputs.dir(sherpaJniLibs)
    inputs.property("version", sherpaVersion)
    inputs.property("abis", abis)
    doLast {
        val tarball = archive.get().asFile
        if (!tarball.isFile) {
            tarball.parentFile.mkdirs()
            val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/${tarball.name}"
            logger.lifecycle("Downloading $url")
            val tmp = File(tarball.path + ".part")
            uri(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(tarball)
        }
        val out = sherpaJniLibs.get().asFile
        out.deleteRecursively()
        val unpack = File(tarball.parentFile, "unpacked").apply { deleteRecursively(); mkdirs() }
        val proc = ProcessBuilder("tar", "xjf", tarball.absolutePath, "-C", unpack.absolutePath).inheritIO().start()
        check(proc.waitFor() == 0) { "failed to unpack ${tarball.name}" }
        val libsRoot = unpack.walkTopDown().first { it.isDirectory && it.name == "jniLibs" }
        for (abi in abis) {
            val src = File(libsRoot, abi)
            check(src.isDirectory) { "sherpa-onnx archive has no $abi libraries" }
            src.listFiles { f -> f.name.endsWith(".so") }!!.forEach { it.copyTo(File(out, "$abi/${it.name}"), overwrite = true) }
        }
    }
}

android {
    namespace = "com.freshernotes.tts"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += abis }
    }

    sourceSets {
        getByName("main").jniLibs.srcDir(sherpaJniLibs)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") { dependsOn(fetchSherpaOnnx) }

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
