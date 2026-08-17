from __future__ import annotations

import hashlib
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"
EXPECTED_SHADER_HASHES = {
    "Anime4K_AutoDownscalePre_x2.glsl":
        "9141668ced0b26512253e6396e805820716f35b57c92950d9da489f8b96a7ba4",
    "Anime4K_AutoDownscalePre_x4.glsl":
        "dadb7b713cfa1d810c55b5deff616072f3390e546fed1e6a54f80ea555f7b95d",
    "Anime4K_Clamp_Highlights.glsl":
        "8c5fb67c76bed3021f8a27b050c3b97a6ac1b284f9ce91c04189015c354c0217",
    "Anime4K_Restore_CNN_M.glsl":
        "dd515c307d97d8e5c809f263dd94174cc5667b8c1299082cdff14e6ddfc8d4bc",
    "Anime4K_Restore_CNN_S.glsl":
        "fca48f8322be4c7c5b14393a6eb6d733bbefffea0ca29694cb6c4b0335dad5ce",
    "Anime4K_Restore_CNN_Soft_M.glsl":
        "df1cdc360d6fbfd51b6d6deec99aefb747d0de72cc1c0ff271cd48758a6a0c5a",
    "Anime4K_Restore_CNN_Soft_S.glsl":
        "17fe08df911bd7ae67235da8076701d51647a5fcacab8f1f1f042a1a85f0bb50",
    "Anime4K_Upscale_CNN_x2_M.glsl":
        "249dc3be467f556ed3361deea79f42bac1ae57456c22588c2cc3c2ee8808909c",
    "Anime4K_Upscale_CNN_x2_S.glsl":
        "90b65a4f36950852a34e5f12beb179fafed59fa8d911887e0f5f184337998edf",
    "Anime4K_Upscale_Denoise_CNN_x2_M.glsl":
        "ca51390eabca94ed3e1d9b40dc15b34045cc966f232ba9490ae0b4c1834d94f6",
}
EXPECTED_SHADER_LICENSE_MARKERS = {
    "Anime4K_AutoDownscalePre_x2.glsl": b"released into the public domain",
    "Anime4K_AutoDownscalePre_x4.glsl": b"released into the public domain",
    **{
        file_name: b"// MIT License"
        for file_name in EXPECTED_SHADER_HASHES
        if not file_name.startswith("Anime4K_AutoDownscalePre_")
    },
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

    def test_renderx_sample_launcher_is_removed_at_manifest_merge_boundary(self) -> None:
        manifest = ET.parse(REPO_ROOT / "app" / "src" / "main" / "AndroidManifest.xml")
        activities = manifest.getroot().find("application").findall("activity")
        by_name = {activity.attrib[ANDROID + "name"]: activity for activity in activities}
        removal = by_name["live.pw.renderX.LatexView"]
        self.assertEqual("remove", removal.attrib[TOOLS + "node"])
        self.assertEqual("MissingClass", removal.attrib[TOOLS + "ignore"])

        build_script = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        self.assertIn("VerifySingleDebugLauncherTask", build_script)
        self.assertIn("verifySingleDebugLauncher", build_script)
        self.assertIn("SingleArtifact.MERGED_MANIFEST", build_script)
        self.assertIn(
            'listOf("com.ai.assistance.operit.ui.main.MainActivity")',
            build_script,
        )

    def test_anime4k_assets_are_fixed_upstream_sources(self) -> None:
        attributes = (REPO_ROOT / ".gitattributes").read_text(encoding="utf-8")
        self.assertIn(
            "app/src/main/assets/shaders/*.glsl -text !eol -whitespace",
            attributes,
        )
        shader_root = REPO_ROOT / "app" / "src" / "main" / "assets" / "shaders"
        actual_files = {path.name for path in shader_root.glob("*.glsl")}
        self.assertEqual(set(EXPECTED_SHADER_HASHES), actual_files)
        for file_name, expected_hash in EXPECTED_SHADER_HASHES.items():
            payload = (shader_root / file_name).read_bytes()
            self.assertIn(EXPECTED_SHADER_LICENSE_MARKERS[file_name], payload)
            self.assertEqual(expected_hash, hashlib.sha256(payload).hexdigest())

    def test_gradle_has_no_native_pick_first_packaging_selection(self) -> None:
        build_script = (REPO_ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        jni_libs_block = build_script.split("jniLibs {", maxsplit=1)[1].split("}", maxsplit=1)[0]
        self.assertNotIn("pickFirsts", jni_libs_block)
        self.assertNotIn("pickFirst", jni_libs_block)
        self.assertIn('pickFirsts += "/META-INF/LICENSE.md"', build_script)
        self.assertIn(
            "ffmpegkit-maintained/ffmpeg@62b07bf097baf26b416c815aea514e05c9ad6d63",
            build_script,
        )
        self.assertIn(
            "FFmpeg@n9.0.1/bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa",
            build_script,
        )
        self.assertIn("verifyPlayerNativeInputs", build_script)
        self.assertIn(
            "f52aca6f35c651be7aab55f2efe6b5f40180d1ebaeb1404cc446470bf8deb6a4",
            build_script,
        )
        self.assertIn(
            "86d97cc0174ff44a8057899bef7b8e66bd976e5cfa7bba7d2a9fc819cb8efca7",
            build_script,
        )
        self.assertNotIn(
            "fc983b7ed0c8b8be1938283fe94108dfdc593aa31608d55dd1ce119ae201c32c",
            build_script,
        )
        self.assertNotIn(
            "9a73f2a9f06161fb967de47d8eafde3269e78f844e18f8f557e532378640cc5e",
            build_script,
        )
        self.assertNotIn(
            "1a30a94226bf2157927ec6edbb20154f9a1c1c53580f59cf55efe46db87a5ab3",
            build_script,
        )
        self.assertIn("requireNativeZipAlignment", build_script)
        self.assertIn("LIBAVCODEC_63", build_script)
        self.assertIn("LIBAVUTIL_61", build_script)
        self.assertIn(
            "OpenH264@v2.6.0/652bdb7719f30b52b08e506645a7322ff1b2cc6f",
            build_script,
        )
        self.assertIn("8.1.7-kiyori-n9.0.1-r4", build_script)
        self.assertIn('implementation(files("libs/mpv-player-arm64.aar"))', build_script)
        self.assertIn(
            'implementation(files("libs/ffmpeg-kit-player-arm64.aar"))',
            build_script,
        )
        self.assertIn("verifyDebugPlayerRuntimePackaging", build_script)

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
        network_source = (runtime_root / "PlayerNetworkSnapshot.kt").read_text(
            encoding="utf-8"
        )
        models_source = (player_root / "PlayerModels.kt").read_text(encoding="utf-8")
        session_source = (player_root / "PlayerSession.kt").read_text(encoding="utf-8")
        settings_page_source = (
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
            / "main"
            / "shell"
            / "KiyoriPlayerSettingsPage.kt"
        ).read_text(encoding="utf-8")
        initialize_body = engine_source[
            engine_source.index("fun initialize("):
            engine_source.index("fun load(")
        ]

        self.assertIn("MPVLib.init()", initialize_body)
        normal_log_level = 'setRequiredOption("msg-level", "all=warn,ffmpeg=info,demux=info")'
        self.assertIn(normal_log_level, initialize_body)
        self.assertLess(initialize_body.index(normal_log_level), initialize_body.index("MPVLib.init()"))
        self.assertIn("Utils.copyAssets(appContext)", initialize_body)
        self.assertIn('setRequiredOption("tls-ca-file", tlsCaFile.absolutePath)', initialize_body)
        self.assertIn('setRequiredOption("tls-verify", "yes")', initialize_body)
        self.assertIn('setRequiredOption("ytdl", "no")', initialize_body)
        self.assertIn("MPVLib.addLogObserver(this)", initialize_body)
        self.assertIn("clearStaleFullVideoCacheDirectories()", initialize_body)
        self.assertIn('requireRuntimeStringProperty("mpv-version")', engine_source)
        self.assertIn('requireRuntimeStringProperty("ffmpeg-version")', engine_source)
        self.assertIn('requireRuntimeStringArrayProperty("protocol-list")', engine_source)
        self.assertIn(
            'requireRuntimeStringArrayProperty("demuxer-lavf-list")',
            engine_source,
        )
        self.assertIn('MPVLib.getPropertyNode("decoder-list")', engine_source)
        self.assertIn(
            'MPVLib.getPropertyNode("option-info/hwdec")',
            engine_source,
        )
        self.assertIn("optionInfo.asMap()", engine_source)
        self.assertIn('optionMap["choices"]', engine_source)
        self.assertIn("PlayerRuntimeHardwareDecoderEvidence.CHOICES_ABSENT", engine_source)
        self.assertNotIn("option-info/hwdec/choices", engine_source)
        self.assertNotIn("MPVLib.attachSurface", initialize_body)
        self.assertNotIn('setRequiredOption("force-window", "yes")', initialize_body)
        self.assertIn("buildPlayerMpvHttpHeaderPlan(headers)", engine_source)
        self.assertIn("rangeOwner=mpv", engine_source)
        load_body = engine_source[
            engine_source.index("fun load("):
            engine_source.index("fun attachSurface(")
        ]
        self.assertLess(
            load_body.index("applyRenderingProfile(settings.renderingProfile)"),
            load_body.index("applyDecoderBackend(settings.decoderBackend)"),
        )
        self.assertIn('MPVLib.setPropertyString("glsl-shaders", serialized)', engine_source)
        self.assertIn('MPVLib.getPropertyString("glsl-shaders")', engine_source)
        self.assertIn("Anime4K 属性已核验", engine_source)
        settings_store_source = (player_root / "PlayerSettingsStore.kt").read_text(
            encoding="utf-8"
        )
        self.assertIn("private fun readAnime4KMode(): Anime4KMode", settings_store_source)
        self.assertIn(
            "val migratedId = migrateLegacyAnime4KPersistedId(storedId)",
            settings_store_source,
        )
        self.assertIn(
            "preferences.edit { putString(KEY_ANIME4K_MODE, migratedId) }",
            settings_store_source,
        )
        self.assertIn("anime4KMode = readAnime4KMode()", settings_store_source)
        self.assertIn('FULL_VIDEO("full_video"', models_source)
        self.assertIn('PlayerNetworkCachePolicy.FULL_VIDEO -> "完整缓存"', settings_page_source)
        for source in (
            models_source,
            settings_store_source,
            settings_page_source,
            service_source,
            engine_source,
        ):
            self.assertNotIn("fullVideoCacheEnabled", source)
        self.assertNotIn("TOGGLE_FULL_VIDEO_CACHE", settings_page_source)
        self.assertNotIn("KEY_FULL_VIDEO_CACHE_ENABLED", settings_store_source)
        self.assertNotIn('"stream-start"', engine_source)
        self.assertNotIn('"stream-end"', engine_source)
        self.assertIn('MPVLib.getPropertyNode("demuxer-cache-state")', engine_source)
        self.assertNotIn(
            'runCatching { MPVLib.getPropertyNode("demuxer-cache-state") }',
            engine_source,
        )
        self.assertNotIn('"video-params/codec-profile"', engine_source)
        self.assertIn(
            'MPVLib.getPropertyString("track-list/$index/codec-profile")',
            engine_source,
        )
        self.assertIn('data["reason"]?.asString()', engine_source)
        self.assertIn('data["file_error"]?.asString()', engine_source)
        self.assertNotIn('data["reason"]?.asInt()', engine_source)
        self.assertIn('normalizedName == "range"', protocol_source)
        self.assertIn('normalizedName.startsWith("sec-ch-ua")', protocol_source)
        self.assertIn('normalizedName.startsWith("sec-fetch-")', protocol_source)
        self.assertIn(
            "val created = MpvPlayerEngine(applicationContext, engineListener)",
            service_source,
        )
        self.assertIn("PlayerNetworkSnapshotObserver(", service_source)
        self.assertIn(".also(PlayerNetworkSnapshotObserver::start)", service_source)
        self.assertIn("networkSnapshotObserver?.stop()", service_source)
        self.assertIn(
            "connectivityManager.registerDefaultNetworkCallback(callback, handler)",
            network_source,
        )
        self.assertIn(
            "connectivityManager.unregisterNetworkCallback(callback)",
            network_source,
        )
        self.assertIn(
            "handler.postDelayed(publishRunnable, PLAYER_NETWORK_SNAPSHOT_COALESCE_MILLIS)",
            network_source,
        )
        self.assertNotIn("bindProcessToNetwork", network_source)
        apply_settings_body = service_source[
            service_source.index("override fun applySettings("):
            service_source.index("override fun applyVideoFitMode(")
        ]
        self.assertLess(
            apply_settings_body.index(
                "activeEngine.applyRenderingProfile(settings.renderingProfile)"
            ),
            apply_settings_body.index(
                "activeEngine.applyDecoderBackend(settings.decoderBackend)"
            ),
        )
        self.assertIn("runtimeHandler.post", service_source)
        self.assertNotIn("MpvPlayerEngine", session_source)
        self.assertNotIn("PlayerMediaResolver", session_source)
        self.assertNotIn("MPVLib", session_source)
        self.assertIn("beginPendingPlayerSurfaceAttach", session_source)
        self.assertIn("onSurfaceAttached", session_source)
        self.assertIn("pendingUserSeekLoadCommandId", session_source)
        self.assertIn("isPlayerUserSeekEvent(", session_source)
        self.assertIn('MPVLib.setPropertyString("vo", "null")', engine_source)
        self.assertIn('MPVLib.setPropertyString("vo", videoOutput)', engine_source)
        self.assertIn("requireFullVideoCacheRoot(create = true)", engine_source)
        self.assertIn("requireFullVideoCacheRoot(create = false)", engine_source)
        boost_body = session_source[
            session_source.index("fun beginLongPressSpeedBoost("):
            session_source.index("fun setAudioTrack(")
        ]
        self.assertIn("resolveLongPressPlayerSpeed(snapshot.speed)", boost_body)
        self.assertIn("activeLongPressSpeedBoost", boost_body)
        self.assertNotIn("setLastPlaybackSpeed", boost_body)

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
        self.assertIn('label = "自动旋转"', controls_source)
        self.assertIn('label = "查看播放日志"', controls_source)
        self.assertIn("onAutoRotateChanged(!autoRotateEnabled)", controls_source)
        self.assertIn("PLAYER_SPEED_MENU_OPTIONS", controls_source)
        self.assertIn('title = "Anime4K 模式"', controls_source)
        self.assertIn('"A+ - 双重强化"', controls_source)
        self.assertNotIn(
            'listOf("解码", "投屏", "听视频", "片头片尾", "自动旋转", "查看日志")',
            controls_source,
        )
        self.assertGreaterEqual(
            controls_source.count('description = "弹幕（当前资源不支持）"'),
            2,
        )
        popup_body = controls_source[
            controls_source.index("private fun PlayerPopupMenu("):
            controls_source.index("private fun LegacyImageButton(")
        ]
        self.assertIn("shape = RoundedCornerShape(20.dp)", popup_body)
        self.assertIn("containerColor = PlayerPopupBackground", popup_body)
        self.assertIn("tonalElevation = 0.dp", popup_body)
        self.assertNotIn(".background(PlayerPopupBackground)", popup_body)
        image_button_body = controls_source[
            controls_source.index("private fun LegacyImageButton("):
            controls_source.index("private fun LegacyTextButton(")
        ]
        self.assertNotIn(".background(", image_button_body)
        self.assertNotIn(".border(", image_button_body)
        text_button_body = controls_source[
            controls_source.index("private fun LegacyTextButton("):
            controls_source.index("private fun LegacySeekBar(")
        ]
        self.assertNotIn(".background(", text_button_body)
        self.assertNotIn(".border(", text_button_body)

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
        self.assertIn("PLAYER_LONG_PRESS_SPEED_THRESHOLD_MILLIS", gesture_source)
        self.assertIn("session.beginLongPressSpeedBoost()", gesture_source)
        self.assertIn("session.endLongPressSpeedBoost()", gesture_source)
        self.assertIn("verticalOnRight = true", gesture_source)
        self.assertIn("verticalOnRight = false", gesture_source)

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
        self.assertIn("onMediaIdentityChanged", callback_source)


if __name__ == "__main__":
    unittest.main()
