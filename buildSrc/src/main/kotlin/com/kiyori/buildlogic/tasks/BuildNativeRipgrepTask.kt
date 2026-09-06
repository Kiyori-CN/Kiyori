package com.kiyori.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.work.DisableCachingByDefault
import javax.inject.Inject

@DisableCachingByDefault(
    because = "The output is built by the machine-local Rust and Android NDK toolchains."
)
abstract class BuildNativeRipgrepTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cargoManifestFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cargoLockFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSourceDirectory: DirectoryProperty

    @get:Input
    abstract val rustTarget: Property<String>

    @get:Input
    abstract val rustToolchain: Property<String>

    @get:Input
    abstract val androidApiLevel: Property<Int>

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Internal
    abstract val ndkDirectory: DirectoryProperty

    @get:LocalState
    abstract val cargoTargetDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val target = rustTarget.get()
        val apiLevel = androidApiLevel.get()
        val osName = System.getProperty("os.name").lowercase()
        val hostTag =
            when {
                osName.contains("windows") -> "windows-x86_64"
                osName.contains("linux") -> "linux-x86_64"
                osName.contains("mac") || osName.contains("darwin") -> "darwin-x86_64"
                else -> error("Unsupported native ripgrep build host: $osName")
            }
        val linkerName =
            "aarch64-linux-android${apiLevel}-clang" +
                if (osName.contains("windows")) ".cmd" else ""
        val linker =
            ndkDirectory.get().asFile
                .resolve("toolchains/llvm/prebuilt/$hostTag/bin/$linkerName")
                .canonicalFile
        check(linker.isFile) {
            "Android linker for native ripgrep was not found: $linker"
        }

        val cargoTargetRoot = cargoTargetDirectory.get().asFile
        val cargoExecutable = if (osName.contains("windows")) "cargo.exe" else "cargo"
        val linkerEnvironmentName =
            "CARGO_TARGET_${target.uppercase().replace('-', '_')}_LINKER"

        execOperations.exec {
            workingDir(cargoManifestFile.get().asFile.parentFile)
            executable(cargoExecutable)
            args(
                "+${rustToolchain.get()}",
                "build",
                "--manifest-path",
                cargoManifestFile.get().asFile.absolutePath,
                "--release",
                "--target",
                target,
                "--locked",
            )
            environment("CARGO_TARGET_DIR", cargoTargetRoot.absolutePath)
            environment(linkerEnvironmentName, linker.absolutePath)
        }.assertNormalExitValue()

        val builtLibrary =
            cargoTargetRoot.resolve("$target/release/liboperit_ripgrep.so")
        check(builtLibrary.isFile && builtLibrary.length() > 0L) {
            "Cargo did not produce native ripgrep: $builtLibrary"
        }

        val generatedRoot = outputDirectory.get().asFile
        check(!generatedRoot.exists() || generatedRoot.deleteRecursively()) {
            "Unable to clear generated native ripgrep directory: $generatedRoot"
        }
        val abiDirectory = generatedRoot.resolve("arm64-v8a")
        check(abiDirectory.mkdirs() || abiDirectory.isDirectory) {
            "Unable to create generated native ripgrep ABI directory: $abiDirectory"
        }
        builtLibrary.copyTo(abiDirectory.resolve("liboperit_ripgrep.so"), overwrite = true)
    }
}
