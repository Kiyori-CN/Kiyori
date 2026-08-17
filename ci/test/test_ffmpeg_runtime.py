from __future__ import annotations

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
        self.assertIn("FFmpegKitConfig.getMediaInformationExecute(", service)
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

        self.assertGreaterEqual(toolbox.count("withContext(Dispatchers.IO)"), 2)
        self.assertEqual(standard.count("withContext(Dispatchers.IO) { invoke(tool) }"), 3)
        self.assertNotIn("com.arthenica.ffmpegkit", standard)

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

    def test_media_pool_uses_one_duration_plan_and_one_explicit_encoder_command(self) -> None:
        media_pool = MEDIA_POOL_MANAGER.read_text(encoding="utf-8")
        mnn_provider = MNN_PROVIDER.read_text(encoding="utf-8")

        self.assertEqual(
            media_pool.count("FFmpegUtil.getMediaInfo(source.absolutePath)"),
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


if __name__ == "__main__":
    unittest.main()
