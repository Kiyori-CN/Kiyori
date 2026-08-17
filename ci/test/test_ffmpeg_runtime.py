from __future__ import annotations

import json
import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
APP_MAIN = REPO_ROOT / "app" / "src" / "main"
RUNTIME_ROOT = (
    APP_MAIN
    / "java"
    / "com"
    / "ai"
    / "assistance"
    / "operit"
    / "core"
    / "ffmpeg"
    / "runtime"
)
MEDIA_POOL_MANAGER = (
    APP_MAIN
    / "java"
    / "com"
    / "ai"
    / "assistance"
    / "operit"
    / "util"
    / "MediaPoolManager.kt"
)
MNN_PROVIDER = (
    APP_MAIN
    / "java"
    / "com"
    / "ai"
    / "assistance"
    / "operit"
    / "api"
    / "chat"
    / "llmprovider"
    / "MNNProvider.kt"
)


class FFmpegRuntimeContractTest(unittest.TestCase):
    def test_manifest_owns_non_exported_ffmpeg_process(self) -> None:
        manifest = (APP_MAIN / "AndroidManifest.xml").read_text(encoding="utf-8")
        service = re.search(
            r"<service\s+"
            r'android:name="\.core\.ffmpeg\.runtime\.FFmpegRuntimeService"'
            r"(?P<body>[^>]*)/>",
            manifest,
            flags=re.DOTALL,
        )
        self.assertIsNotNone(service)
        body = service.group("body")
        self.assertIn('android:enabled="true"', body)
        self.assertIn('android:exported="false"', body)
        self.assertIn('android:process=":ffmpeg"', body)

    def test_ffmpegkit_java_api_is_owned_only_by_runtime_service(self) -> None:
        kotlin_sources = sorted((APP_MAIN / "java").rglob("*.kt"))
        owners = []
        for source in kotlin_sources:
            text = source.read_text(encoding="utf-8")
            if "com.arthenica.ffmpegkit" in text:
                owners.append(source.relative_to(REPO_ROOT).as_posix())
        self.assertEqual(
            owners,
            [
                "app/src/main/java/com/ai/assistance/operit/core/ffmpeg/runtime/"
                "FFmpegRuntimeService.kt"
            ],
        )

    def test_runtime_contract_keeps_logs_off_binder_and_handles_death(self) -> None:
        callback = (
            APP_MAIN
            / "aidl"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "ffmpeg"
            / "runtime"
            / "IFFmpegRuntimeCallback.aidl"
        ).read_text(encoding="utf-8")
        service = (RUNTIME_ROOT / "FFmpegRuntimeService.kt").read_text(encoding="utf-8")
        client = (RUNTIME_ROOT / "FFmpegRuntimeClient.kt").read_text(encoding="utf-8")
        contract = (
            APP_MAIN
            / "aidl"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "ffmpeg"
            / "runtime"
            / "IFFmpegRuntime.aidl"
        ).read_text(encoding="utf-8")

        self.assertNotIn("onLog", callback)
        self.assertNotIn("String output", callback)
        self.assertIn("boolean registerCallback", contract)
        self.assertIn("boolean submit", contract)
        self.assertNotIn("oneway interface IFFmpegRuntime", contract)
        self.assertIn("outputLogPath", (RUNTIME_ROOT / "FFmpegRuntimeModels.kt").read_text(encoding="utf-8"))
        self.assertIn("ConcurrentHashMap", service)
        self.assertIn("newSingleThreadExecutor", service)
        self.assertIn("executor.execute", service)
        self.assertIn("FFmpegKitConfig.ffmpegExecute(session)", service)
        self.assertIn("FFprobeSession.create(", service)
        self.assertIn("FFmpegKitConfig.ffprobeExecute(session)", service)
        self.assertIn("FFmpegRuntimeMediaInformationParser.parse(probeFile)", service)
        self.assertIn('"-o",', service)
        self.assertNotIn("FFmpegKitConfig.getMediaInformationExecute(", service)
        self.assertNotIn("asyncFFmpegExecute", service)
        self.assertNotIn("asyncGetMediaInformationExecute", service)
        self.assertIn("FFmpegKitConfig.enableLogCallback(", service)
        self.assertIn("log.sessionId == 0L", service)
        self.assertIn("activeNativeRequest", service)
        self.assertIn("FFmpegKitConfig.messagesInTransmit(0L)", service)
        self.assertIn("session.cancel()", service)
        self.assertIn("callbackBinder.linkToDeath", service)
        self.assertIn("callbackBinder.unlinkToDeath", service)
        self.assertIn("FFmpegRuntimeFailureCode.CALLBACK_REPLACED", service)
        self.assertIn("FFmpegRuntimeFailureCode.CALLBACK_DISCONNECTED", service)
        self.assertIn("synchronized(active.lifecycleLock)", service)
        self.assertIn("val lifecycleLock = Any()", service)
        self.assertIn("linkToDeath", client)
        self.assertIn("withContext(Dispatchers.IO)", client)
        self.assertIn("resolveFfmpegRuntimeLogFile", client)
        self.assertIn("FFmpegRuntimeTerminal.ProcessDied", client)
        self.assertIn("pending.terminal.complete(FFmpegRuntimeTerminal.ProcessDied)", client)
        self.assertIn("attempt.connected.completeExceptionally(error)", client)
        self.assertNotIn("FFmpegRuntimeClient.getInstance(appContext).execute(", client)

        submit_body = client[
            client.index("private suspend fun submit("):
            client.index("private suspend fun awaitConnectedRuntime(")
        ]
        self.assertEqual(submit_body.count("runtime.remote.submit("), 1)
        self.assertNotIn("repeat(", submit_body)
        self.assertNotIn("while (", submit_body)

    def test_toolbox_and_ai_tool_executor_leave_main_dispatcher(self) -> None:
        toolbox = (
            APP_MAIN
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "ui"
            / "features"
            / "toolbox"
            / "screens"
            / "ffmpegtoolbox"
            / "FFmpegToolboxScreen.kt"
        ).read_text(encoding="utf-8")
        standard = (
            APP_MAIN
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "tools"
            / "defaultTool"
            / "standard"
            / "StandardFFmpegTool.kt"
        ).read_text(encoding="utf-8")
        probe = (
            APP_MAIN
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "tools"
            / "defaultTool"
            / "standard"
            / "StandardFFmpegProbeTool.kt"
        ).read_text(encoding="utf-8")

        self.assertGreaterEqual(toolbox.count("withContext(Dispatchers.IO)"), 2)
        self.assertEqual(standard.count("withContext(Dispatchers.IO) { invoke(tool) }"), 3)
        self.assertEqual(probe.count("withContext(Dispatchers.IO) { invoke(tool) }"), 1)
        self.assertNotIn("com.arthenica.ffmpegkit", standard)
        self.assertNotIn("com.arthenica.ffmpegkit", probe)

    def test_ffmpeg_execute_prompt_is_not_described_as_a_shell(self) -> None:
        prompts = (
            APP_MAIN
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "config"
            / "SystemToolPromptsInternal.kt"
        ).read_text(encoding="utf-8")
        package = (
            APP_MAIN / "assets" / "packages" / "ffmpeg.js"
        ).read_text(encoding="utf-8")

        for source in (prompts, package):
            self.assertIn("not a shell", source)
            self.assertIn("管道、重定向或命令链", source)
            self.assertIn("com.kiyori:ffmpeg", source)
            self.assertIn("Ubuntu", source)
        self.assertIn('"ffmpeg_probe"', package)
        self.assertIn('name = "ffmpeg_probe"', prompts)
        self.assertIn('name = "section"', prompts)

    def test_media_pool_uses_one_duration_plan_and_one_explicit_encoder_command(self) -> None:
        media_pool = MEDIA_POOL_MANAGER.read_text(encoding="utf-8")
        mnn_provider = MNN_PROVIDER.read_text(encoding="utf-8")

        self.assertEqual(
            media_pool.count("FFmpegUtil.probeMedia(source.absolutePath)"),
            1,
        )
        self.assertEqual(media_pool.count("resolveMediaPoolTranscodePlan("), 2)
        self.assertEqual(media_pool.count("FFmpegUtil.executeArguments(arguments)"), 2)
        self.assertEqual(media_pool.count('"libopenh264"'), 1)
        self.assertIn('"constrained_baseline"', media_pool)
        self.assertIn('"yuv420p"', media_pool)
        self.assertIn("withContext(Dispatchers.IO)", media_pool)
        self.assertNotIn('"mpeg4"', media_pool)
        self.assertNotIn('"-preset"', media_pool)
        self.assertNotIn('"-crf"', media_pool)
        self.assertNotIn("commands = listOf", media_pool)
        self.assertNotIn("for (cmd in commands)", media_pool)

        self.assertIn("withContext(Dispatchers.IO)", mnn_provider)
        self.assertIn("preprocessMultimodalText", mnn_provider)
        self.assertEqual(mnn_provider.count("FFmpegUtil.executeArguments("), 2)
        self.assertNotIn("FFmpegUtil.executeCommand(", mnn_provider)

    def test_conversion_contract_is_deterministic_across_host_and_toolpkg(self) -> None:
        standard = (
            APP_MAIN
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "tools"
            / "defaultTool"
            / "standard"
            / "StandardFFmpegTool.kt"
        ).read_text(encoding="utf-8")
        contract = (
            APP_MAIN
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "tools"
            / "defaultTool"
            / "standard"
            / "FFmpegConversionContract.kt"
        ).read_text(encoding="utf-8")
        prompts = (
            APP_MAIN
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "config"
            / "SystemToolPromptsInternal.kt"
        ).read_text(encoding="utf-8")
        source_package = (REPO_ROOT / "examples" / "ffmpeg.ts").read_text(encoding="utf-8")
        generated_package = (REPO_ROOT / "examples" / "ffmpeg.js").read_text(encoding="utf-8")
        asset_package = (APP_MAIN / "assets" / "packages" / "ffmpeg.js").read_text(
            encoding="utf-8"
        )
        definitions = (
            REPO_ROOT / "examples" / "types" / "ffmpeg.d.ts"
        ).read_text(encoding="utf-8")
        result_definitions = (
            REPO_ROOT / "examples" / "types" / "results.d.ts"
        ).read_text(encoding="utf-8")

        self.assertIn("executeArgumentsBlocking(arguments)", standard)
        self.assertNotIn("executeBlocking(command)", standard[standard.index("class StandardFFmpegConvertToolExecutor"):])
        self.assertIn("validateRawFfmpegCommand(", standard)
        self.assertIn('H264_AAC_MP4("h264_aac_mp4")', contract)
        self.assertIn('"libopenh264"', contract)
        self.assertIn('"constrained_baseline"', contract)
        self.assertIn('add("-n")', contract)
        self.assertIn("commitFileAtomicallyWithoutReplacement", standard)
        for text in (prompts, source_package, generated_package, asset_package, definitions):
            self.assertIn("h264_aac_mp4", text)
            self.assertIn("video_bitrate", text)
            self.assertNotIn("video_codec", text)
            self.assertNotIn("audio_codec", text)
        for text in (source_package, generated_package, asset_package):
            self.assertIn("ffmpeg_probe", text)
            self.assertIn("data: result", text)
            self.assertNotIn("data: result.output", text)
            self.assertIn("isToolExecutionError(error)", text)
            self.assertNotIn("error instanceof Error", text)
            self.assertIn("Shell syntax is rejected", text)
        self.assertIn('name = "ffmpeg_probe"', prompts)
        self.assertIn("function probe(inputPath: string)", definitions)
        self.assertIn("channels?: number;", result_definitions)
        self.assertNotIn("channels?: 1 | 2 | 4 | 6 | 8;", result_definitions)
        self.assertEqual(generated_package, asset_package)

    def test_ffmpegkit_overlay_wrapper_identity_matches_manifest(self) -> None:
        manifest = json.loads(
            (
                REPO_ROOT
                / "tools"
                / "ffmpegkit_native_build"
                / "closure_manifest.json"
            ).read_text(encoding="utf-8")
        )
        expected_version = manifest["framework"]["wrapper_version"]
        overlay = (
            REPO_ROOT
            / "tools"
            / "ffmpegkit_native_build"
            / "overlay"
            / "android-8.1-lts"
        )
        identity_files = {
            "native header": (
                overlay
                / "android"
                / "ffmpeg-kit-android-lib"
                / "src"
                / "main"
                / "cpp"
                / "ffmpegkit.h"
            ),
            "Java loader": (
                overlay
                / "android"
                / "ffmpeg-kit-android-lib"
                / "src"
                / "main"
                / "java"
                / "com"
                / "arthenica"
                / "ffmpegkit"
                / "NativeLoader.java"
            ),
            "source identity": overlay / "tools" / "source" / "SOURCE",
            "Android build": overlay / "tools" / "android" / "build.gradle",
        }

        for label, path in identity_files.items():
            with self.subTest(label=label):
                self.assertIn(
                    expected_version,
                    path.read_text(encoding="utf-8"),
                )

    def test_runtime_bounds_logs_and_cancels_queued_work_without_retry(self) -> None:
        service = (RUNTIME_ROOT / "FFmpegRuntimeService.kt").read_text(encoding="utf-8")
        client = (RUNTIME_ROOT / "FFmpegRuntimeClient.kt").read_text(encoding="utf-8")
        models = (RUNTIME_ROOT / "FFmpegRuntimeModels.kt").read_text(encoding="utf-8")
        protocol = (RUNTIME_ROOT / "FFmpegRuntimeProtocol.kt").read_text(encoding="utf-8")

        self.assertIn("FFMPEG_RUNTIME_MAX_COMMAND_CHARS", models)
        self.assertIn("FFMPEG_RUNTIME_MAX_ARGUMENT_TOTAL_CHARS", models)
        self.assertIn("FFMPEG_RUNTIME_MAX_PATH_CHARS", models)
        self.assertIn("FFmpegRuntimeInformationSection", models)
        self.assertIn("pruneFfmpegRuntimeLogs(", service)
        self.assertIn("pruneFfmpegRuntimeProbeFiles(cacheDir)", service)
        self.assertIn("FFMPEG_RUNTIME_PROBE_SHOW_ENTRIES", service)
        self.assertIn("FFMPEG_RUNTIME_MAX_LOG_BYTES", service)
        self.assertIn("sessionId = 0L", service)
        self.assertIn("cancelled before native session creation", service)
        self.assertIn("discardTerminalLog(", client)
        self.assertIn("protectedLogPaths = setOf(diagnosticLogPath)", client)
        self.assertIn("processId = pending.processId.takeIf", client)
        self.assertIn("sessionId = pending.sessionId.takeIf", client)
        self.assertNotIn("UNKNOWN_RETURN_CODE", service)
        self.assertIn("FFMPEG_RUNTIME_LOG_MAX_TOTAL_BYTES", protocol)
        self.assertNotIn("repeat(", client[client.index("private suspend fun submit("):client.index("private suspend fun awaitConnectedRuntime(")])


if __name__ == "__main__":
    unittest.main()
