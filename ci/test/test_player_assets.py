from __future__ import annotations

import hashlib
import io
import unittest
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
EXPECTED_SHADER_HASHES = {
    "Anime4K_Clamp_Highlights.glsl":
        "e457fdb6c60cea88e195a463a3c21e72f3073e38b18ff4c8ddf7258028ed7ed5",
    "Anime4K_Restore_CNN_L.glsl":
        "bead2fca3a75eef0b8949d203ee3fcbcf8dfdb2bf3239580ec4c8eae80891f8b",
    "Anime4K_Restore_CNN_M.glsl":
        "dd515c307d97d8e5c809f263dd94174cc5667b8c1299082cdff14e6ddfc8d4bc",
    "Anime4K_Upscale_CNN_x2_L.glsl":
        "120f62fd293bb949c746d0c9986b6b6a8eecebfd49b0db8e139103b5f459234a",
    "Anime4K_Upscale_CNN_x2_M.glsl":
        "249dc3be467f556ed3361deea79f42bac1ae57456c22588c2cc3c2ee8808909c",
    "Anime4K_Upscale_CNN_x2_S.glsl":
        "90b65a4f36950852a34e5f12beb179fafed59fa8d911887e0f5f184337998edf",
}
EXPECTED_MPV_AAR_SHA256 = "fc983b7ed0c8b8be1938283fe94108dfdc593aa31608d55dd1ce119ae201c32c"
EXPECTED_FFMPEG_AAR_SHA256 = "1a30a94226bf2157927ec6edbb20154f9a1c1c53580f59cf55efe46db87a5ab3"
MPV_FFMPEG_NAMESPACE = {
    "libavcodec.so": "libmpcodec.so",
    "libavdevice.so": "libmpdevice.so",
    "libavfilter.so": "libmpfilter.so",
    "libavformat.so": "libmpformat.so",
    "libavutil.so": "libmputil.so",
    "libswresample.so": "libmpresample.so",
    "libswscale.so": "libmpscale.so",
}
REQUIRED_LIBCXX_SYMBOLS = {
    b"_ZNSt6__ndk127__from_chars_floating_pointIfEENS_19__from_chars_resultIT_EEPKcS5_NS_12chars_formatE",
    b"_ZNSt6__ndk127__from_chars_floating_pointIdEENS_19__from_chars_resultIT_EEPKcS5_NS_12chars_formatE",
}


class PlayerAssetsTest(unittest.TestCase):
    def test_video_view_intent_is_owned_by_player_activity(self) -> None:
        manifest = ET.parse(REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml")
        activities = manifest.getroot().find("application").findall("activity")
        by_name = {activity.attrib[ANDROID + "name"]: activity for activity in activities}
        player = by_name[".ui.features.player.PlayerActivity"]
        main = by_name[".ui.main.MainActivity"]

        def view_filters(activity: ET.Element) -> list[ET.Element]:
            return [
                intent_filter
                for intent_filter in activity.findall("intent-filter")
                if any(
                    action.attrib.get(ANDROID + "name") == "android.intent.action.VIEW"
                    for action in intent_filter.findall("action")
                )
            ]

        player_mime_types = {
            data.attrib[ANDROID + "mimeType"]
            for intent_filter in view_filters(player)
            for data in intent_filter.findall("data")
            if ANDROID + "mimeType" in data.attrib
        }
        main_mime_types = {
            data.attrib[ANDROID + "mimeType"]
            for intent_filter in view_filters(main)
            for data in intent_filter.findall("data")
            if ANDROID + "mimeType" in data.attrib
        }

        self.assertIn("video/*", player_mime_types)
        self.assertIn("application/vnd.apple.mpegurl", player_mime_types)
        self.assertNotIn("*/*", main_mime_types)
        self.assertNotIn("video/*", main_mime_types)

    def test_anime4k_assets_are_fixed_mit_sources(self) -> None:
        shader_root = REPO_ROOT / "app" / "src" / "main" / "assets" / "shaders"
        actual_files = {path.name for path in shader_root.glob("*.glsl")}
        self.assertEqual(set(EXPECTED_SHADER_HASHES), actual_files)
        for file_name, expected_hash in EXPECTED_SHADER_HASHES.items():
            payload = (shader_root / file_name).read_bytes()
            self.assertTrue(payload.startswith(b"// MIT License"))
            self.assertEqual(expected_hash, hashlib.sha256(payload).hexdigest())

    def test_gradle_has_no_native_packaging_selection(self) -> None:
        build_script = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertNotIn("pickFirsts", build_script)
        self.assertNotIn("pickFirst", build_script)
        self.assertIn("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7", build_script)
        self.assertIn("verifyPlayerNativeInputs", build_script)
        self.assertIn('implementation(files("libs/mpv-player-arm64.aar"))', build_script)
        self.assertIn(
            'implementation(files("libs/ffmpeg-kit-player-arm64.aar"))',
            build_script,
        )
        self.assertIn("verifyDebugPlayerRuntimePackaging", build_script)

    def test_player_aars_have_disjoint_native_ownership_and_required_runtime(self) -> None:
        mpv_aar_path = REPO_ROOT / "app" / "libs" / "mpv-player-arm64.aar"
        ffmpeg_aar_path = REPO_ROOT / "app" / "libs" / "ffmpeg-kit-player-arm64.aar"
        self.assertEqual(
            EXPECTED_MPV_AAR_SHA256,
            hashlib.sha256(mpv_aar_path.read_bytes()).hexdigest(),
        )
        self.assertEqual(
            EXPECTED_FFMPEG_AAR_SHA256,
            hashlib.sha256(ffmpeg_aar_path.read_bytes()).hexdigest(),
        )
        with zipfile.ZipFile(mpv_aar_path) as aar:
            with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as classes:
                names = set(classes.namelist())
            mpv_native_names = {
                name for name in aar.namelist() if name.startswith("jni/")
            }
            mpv_payload = aar.read("jni/arm64-v8a/libmpv.so")
            mpv_avformat_payload = aar.read("jni/arm64-v8a/libmpformat.so")
            libcxx_payload = aar.read("jni/arm64-v8a/libc++_shared.so")
        self.assertTrue(
            {
                "is/xyz/mpv/MPVLib.class",
                "is/xyz/mpv/MPVLib$EventObserver.class",
                "is/xyz/mpv/MPVLib$LogObserver.class",
                "is/xyz/mpv/MPVNode.class",
                "is/xyz/mpv/Utils.class",
            }.issubset(names)
        )
        self.assertEqual(
            {
                "jni/arm64-v8a/libc++_shared.so",
                *{
                    f"jni/arm64-v8a/{name}"
                    for name in MPV_FFMPEG_NAMESPACE.values()
                },
                "jni/arm64-v8a/libmpv.so",
                "jni/arm64-v8a/libplayer.so",
            },
            mpv_native_names,
        )
        for source_name, namespaced_name in MPV_FFMPEG_NAMESPACE.items():
            self.assertNotIn(source_name.encode("ascii"), mpv_payload)
            self.assertIn(namespaced_name.encode("ascii"), mpv_payload)
        self.assertIn(b"--enable-mbedtls", mpv_avformat_payload)
        self.assertIn(b"mbedtls_ssl_handshake", mpv_avformat_payload)
        for symbol in REQUIRED_LIBCXX_SYMBOLS:
            self.assertIn(symbol, mpv_payload)
            self.assertIn(symbol, libcxx_payload)

        with zipfile.ZipFile(ffmpeg_aar_path) as aar:
            ffmpeg_native_names = {
                name for name in aar.namelist() if name.startswith("jni/")
            }
        self.assertEqual(9, len(ffmpeg_native_names))
        self.assertTrue(
            all(name.startswith("jni/arm64-v8a/") for name in ffmpeg_native_names)
        )
        self.assertNotIn("jni/arm64-v8a/libc++_shared.so", ffmpeg_native_names)
        self.assertTrue(mpv_native_names.isdisjoint(ffmpeg_native_names))

    def test_mpv_binding_is_isolated_and_background_candidate_capture_uses_session_state(self) -> None:
        java_root = REPO_ROOT / "app" / "src" / "main" / "java"
        mpv_import_owners = []
        for source in java_root.rglob("*.kt"):
            text = source.read_text(encoding="utf-8")
            if "`is`.xyz.mpv" in text or "is.xyz.mpv" in text:
                mpv_import_owners.append(source.relative_to(REPO_ROOT).as_posix())
        self.assertEqual(
            [
                "app/src/main/java/com/ai/assistance/operit/core/player/runtime/"
                "MpvPlayerEngine.kt"
            ],
            mpv_import_owners,
        )

        candidate_source = (
            java_root
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "tools"
            / "defaultTool"
            / "websession"
            / "browser"
            / "BrowserMediaCandidate.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("session.appliedUserAgent", candidate_source)
        self.assertNotIn("session.webView.settings", candidate_source)
        self.assertNotIn("webView.settings", candidate_source)

    def test_mpv_core_initializes_before_android_surface_attach(self) -> None:
        player_root = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "player"
        )
        runtime_root = player_root / "runtime"
        engine_source = (runtime_root / "MpvPlayerEngine.kt").read_text(encoding="utf-8")
        protocol_source = (runtime_root / "PlayerRuntimeProtocolPolicy.kt").read_text(
            encoding="utf-8"
        )
        service_source = (runtime_root / "PlayerRuntimeService.kt").read_text(
            encoding="utf-8"
        )
        session_source = (player_root / "PlayerSession.kt").read_text(encoding="utf-8")
        initialize_body = engine_source[
            engine_source.index("fun initialize("):
            engine_source.index("fun load(")
        ]

        self.assertIn("MPVLib.init()", initialize_body)
        self.assertIn('setRequiredOption("msg-level", "all=v")', initialize_body)
        self.assertIn("Utils.copyAssets(appContext)", initialize_body)
        self.assertIn('setRequiredOption("tls-ca-file", tlsCaFile.absolutePath)', initialize_body)
        self.assertIn('setRequiredOption("tls-verify", "yes")', initialize_body)
        self.assertIn('setRequiredOption("ytdl", "no")', initialize_body)
        self.assertIn("MPVLib.addLogObserver(this)", initialize_body)
        self.assertNotIn("MPVLib.attachSurface", initialize_body)
        self.assertNotIn('setRequiredOption("force-window", "yes")', initialize_body)
        self.assertIn("buildPlayerMpvHttpHeaderPlan(headers)", engine_source)
        self.assertIn("rangeOwner=mpv", engine_source)
        self.assertIn('data["reason"]?.asString()', engine_source)
        self.assertIn('data["file_error"]?.asString()', engine_source)
        self.assertNotIn('data["reason"]?.asInt()', engine_source)
        self.assertIn('name.equals("Range", ignoreCase = true)', protocol_source)
        self.assertIn(
            "val created = MpvPlayerEngine(applicationContext, engineListener)",
            service_source,
        )
        self.assertIn("runtimeHandler.post", service_source)
        self.assertNotIn("MpvPlayerEngine", session_source)
        self.assertNotIn("PlayerMediaResolver", session_source)
        self.assertNotIn("MPVLib", session_source)
        self.assertIn("beginPendingPlayerSurfaceAttach", session_source)
        self.assertIn("onSurfaceAttached", session_source)

        controls_source = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "ui"
            / "features"
            / "player"
            / "PlayerControls.kt"
        ).read_text(encoding="utf-8")
        self.assertIn(".heightIn(min = 32.dp)", controls_source)
        self.assertGreaterEqual(controls_source.count("padding = 2.dp"), 4)
        self.assertGreaterEqual(controls_source.count("softWrap = false"), 2)
        self.assertGreaterEqual(controls_source.count("fontWeight = FontWeight.Bold"), 2)
        self.assertIn('val items = listOf("查看日志")', controls_source)
        self.assertNotIn(
            'listOf("解码", "投屏", "听视频", "片头片尾", "自动旋转", "查看日志")',
            controls_source,
        )
        self.assertGreaterEqual(
            controls_source.count('description = "弹幕（当前资源不支持）"'),
            2,
        )

        player_screen_source = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "ui"
            / "features"
            / "player"
            / "PlayerScreen.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("DialogProperties(usePlatformDefaultWidth = false)", player_screen_source)
        self.assertIn(".fillMaxHeight(0.9f)", player_screen_source)
        self.assertIn(".horizontalScroll(rememberScrollState())", player_screen_source)
        self.assertIn("PlayerLogFilterChip", player_screen_source)
        self.assertIn("LazyColumn(", player_screen_source)
        self.assertIn('"复制日志"', player_screen_source)
        self.assertIn('"导出文件"', player_screen_source)
        self.assertIn('"确认清空"', player_screen_source)

        gesture_source = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "ui"
            / "features"
            / "player"
            / "PlayerGestureLayer.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("awaitEachGesture", gesture_source)
        self.assertIn(".pointerInput(Unit)", gesture_source)
        self.assertNotIn("pointerInput(enabled, state.positionSeconds)", gesture_source)
        self.assertNotIn(
            "pointerInput(enabled, state.positionSeconds, state.durationSeconds",
            gesture_source,
        )

        log_buffer_source = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "player"
            / "PlayerDebugLogBuffer.kt"
        ).read_text(encoding="utf-8")
        self.assertIn('NETWORK_AND_LOADING("网络与加载")', log_buffer_source)
        self.assertIn('SURFACE_AND_RENDER("画面与 Surface")', log_buffer_source)
        self.assertIn("MutableStateFlow(0L)", log_buffer_source)

        callback_source = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "aidl"
            / "com"
            / "ai"
            / "assistance"
            / "operit"
            / "core"
            / "player"
            / "runtime"
            / "IPlayerRuntimeCallback.aidl"
        ).read_text(encoding="utf-8")
        self.assertIn("onDiagnosticLog", callback_source)


if __name__ == "__main__":
    unittest.main()
