# Player native stack

本文是 Android 播放器与 FFmpegKit native closure 的精确成员、SONAME、`DT_NEEDED`、ELF、哈希、
许可证和 promotion 说明。FFmpeg 三执行面、AI/内部调用边界、Binder 运行时、失败语义、Ubuntu
版本策略和后续开发门禁以 [Kiyori FFmpeg 架构与开发指南](FFMPEG_ARCHITECTURE.md) 为权威。

Kiyori has one player owner and two process-isolated arm64 FFmpeg namespaces. The non-exported `:ffmpeg` process
keeps FFmpegKit's normal `libav*.so` names for FFmpeg tools and download remuxing while the main process uses Binder.
The non-exported `:player` process loads the single
mpv core against the selected source-built `libmp*.so` FFmpeg closure. Gradle packaging selection, same-name
overwrite, an application-layer media proxy, and a second player runtime are not used.

## Selected dual M9 source closures

| Input | Source identity | SHA-256 |
| --- | --- | --- |
| Player M9 source AAR | `mpvlibAndroid@168e0a5e43b37c85509050cddcb5eaddc2e313c0`; mpv `2339eb72767517fc5a113283939f59076946fbc1`; FFmpeg `n9.0.1` / `bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`; Mbed TLS `3.6.7` / `068ff080b369adfac81509f9b57b2afabaf82dc5` | `7CB0B25DC15F21278992243CE2597193B7A54E1A488933555B572982203E2BC0` |
| Player selected aligned AAR | The preceding source closure transformed to the `libmp*.so` namespace with deterministic 16 KiB ZIP alignment | `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4` |
| FFmpegKit r6 source AAR | maintained framework `62b07bf097baf26b416c815aea514e05c9ad6d63`; wrapper `8.1.7-kiyori-n9.0.1-r6`; FFmpeg `n9.0.1` / `bf1b838f2ab88b4f8fd83443325c782ea0e0f7fa`; OpenH264 `v2.6.0` / `652bdb7719f30b52b08e506645a7322ff1b2cc6f`; Android Binder threadpool preservation, capability stdout bridge, GPL/HarfBuzz and `drawtext`/`eq`/`boxblur` | `0BD7ADDAE2D17960DB940A17A3E2450ACB83EECB46BE0D3800D051BB2075C1CE` |
| FFmpegKit r6 selected aligned AAR | The preceding full tools closure without a second `libc++_shared.so`, retaining Java/JNI, SAF, resources, GPLv3 text and notices | `7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394` |

`ci/script/build_player_native_closure.py` builds the complete player M9 source AAR in an isolated profile workspace.
`ci/script/audit_player_native_closure.py` verifies the source AAR before it can be copied to the stable `work/`
output. `ci/script/prepare_mpv_player_dependency.py` then creates and audits a deterministic thin candidate before
an expected-hash paired promotion can replace both selected product AARs. The selected
`app/libs/mpv-player-arm64.aar` keeps `classes.jar`, the AAR manifest/metadata, `assets/cacert.pem`,
`assets/subfont.ttf`, `libmpv.so`, `libplayer.so`, the matching `libc++_shared.so`, and the seven source-built
FFmpeg ELF files with Mbed TLS enabled. The historical `2026-06-25` binary-input transform remains available only
as an explicit `work/` baseline and cannot write the product path.

The script preserves every rewritten ELF's byte length and replaces only equal-length dynamic-library strings:

| Upstream name | Player namespace |
| --- | --- |
| `libavcodec.so` | `libmpcodec.so` |
| `libavdevice.so` | `libmpdevice.so` |
| `libavfilter.so` | `libmpfilter.so` |
| `libavformat.so` | `libmpformat.so` |
| `libavutil.so` | `libmputil.so` |
| `libswresample.so` | `libmpresample.so` |
| `libswscale.so` | `libmpscale.so` |

The same substitutions are applied to `libmpv.so`, `libplayer.so`, and all seven FFmpeg SONAME / `DT_NEEDED`
references. The deterministic selected player output is `50,926,119` bytes with SHA-256
`F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4`. Its C++ runtime is
`1,374,336` bytes with SHA-256 `C4C2FE5CBCB1FBA0003A31FC7AB29A9BB12DF6CC187EC45A806462540E83D93B`.
Both `libmpv.so` and this runtime identify Android Clang `21.0.0`, build `13989888`, and use `0x4000` `PT_LOAD`
alignment. Every selected player ELF has zero `RPATH` and `RUNPATH` tags, and each AAR native payload starts at a
ZIP data offset congruent to zero modulo `0x4000`.

The selected mpv `libmpformat.so` reports FFmpeg `n9.0.1`, `--enable-mbedtls`, Mbed TLS `3.6.7`, RSA-PSS, and
HTTPS, while curl is disabled. The selected FFmpegKit `libavutil.so` reports FFmpeg `n9.0.1`; the normal-name tools
closure retains the full configured codec/filter/library set, MediaCodec and zlib while OpenSSL remains disabled.
This `:ffmpeg` process configuration does not affect `:player`, whose HTTPS backend remains the namespaced Mbed TLS
closure.

A second vivo report at `2026-07-29 13:53 +08:00` confirmed that the namespaced stack now opens HTTPS, then exposed a
separate request-contract error: `Unexpected offset: expected 0, got 19890176`. The reported public resource is
`25260223` bytes, and requesting `Range: bytes=19890176-` returns the same `Content-Range` start. FFmpeg `n8.1.2`
suppresses its internally generated Range whenever custom headers already contain `Range`, but still validates the
response offset against its current `off`. Kiyori therefore retains browser Range evidence for candidate/download
identity while excluding it from mpv `http-header-fields`; mpv/FFmpeg alone owns playback open and seek offsets.

`app/libs/ffmpeg-kit-player-arm64.aar` retains the compatible Java API, proguard metadata, resources, licenses and
source notices from the fixed maintained framework. Its native set is exactly the nine arm64 FFmpegKit/FFmpeg
libraries; the source AAR's `libc++_shared.so` is omitted. The deterministic selected output is `30,486,441` bytes
with SHA-256 `7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394`. All nine native payloads are
ELF64/AArch64, use the FFmpeg `.63/.63/.12/.63/.61/.7/.10` symbol majors, have no `RPATH`/`RUNPATH`, satisfy
`PT_LOAD >= 0x4000`, and start at 16 KiB-aligned ZIP payload offsets. The selected r6 closure records
`--enable-gpl`/`--enable-libharfbuzz` in `libavutil.so`, exact `drawtext`/`eq`/`boxblur` in
`libavfilter.so`, and a byte-exact `res/raw/license_gplv3.txt`.

Existing private dependency archives can still contain the retired `app/libs/ffmpeg-kit-local.aar` and manual
`jniLibs` runtimes; `prepare_android_dependencies.py` removes those retired owners and then validates the two selected
product AARs. Gradle resolves only `app/libs/mpv-player-arm64.aar` and
`app/libs/ffmpeg-kit-player-arm64.aar`; it does not resolve a second Maven native owner or perform an unreviewed
binary overwrite.

## Dual M9 selection and historical M8 boundary

The historical fixed-input preparation path remains available only to reproduce the attributable
`2026-06-25` binary baseline under `work/`. It cannot write `app/libs/mpv-player-arm64.aar`. Replacing version
markers or individual ELF files would create an unauditable mixed closure and remains prohibited.

The selected local player closure fixes:

```text
mpv commit       = 2339eb727
FFmpeg           = n9.0.1
Mbed TLS         = 3.6.7
RSA-PSS support  = enabled
curl             = disabled
Android ABI/API  = arm64-v8a / 24+
NDK revision     = 29.0.14206865
PT_LOAD minimum  = 0x4000
RPATH/RUNPATH    = absent
```

The source builder fixes Windows host-only build-system failures before compilation while preserving the fixed
source, feature flags and native payload semantics. The protected transformations cover uppercase FFmpeg `.S`
rules, GNU Make response files and long header installation, the Windows libass single-job boundary, Lua argument
conversion, the verified NDK Shaderc short-path mirror, the mpv Android JNI source root, and Meson's generated
`libmpv.so` host RPATH token. Unknown, multiple or mismatched generated forms fail instead of being rewritten.

The source and thin auditors require exact AAR members, Java/JNI classes, ELF64 little-endian AArch64, matching
SONAME/`DT_NEEDED`, zero `RPATH`/`RUNPATH`, `PT_LOAD >= 0x4000`, FFmpeg version namespaces, FFmpeg and C++
symbol closure, float/double `__from_chars_floating_point`, the fixed mpv commit, Mbed TLS `3.6.7`, RSA-PSS,
HTTPS, curl-disabled markers, libplacebo, shaderc, and 16 KiB native ZIP payload offsets.

The major-upgrade paired promotion contract accepts only the fixed `m9_ffmpeg_major_candidate` and the exact selected
player and FFmpegKit hashes. It audits both candidates, both `app/libs`-directory temporary files, and both final
products before returning success. A patch-level FFmpegKit rebuild may use
`--promote-m9-ffmpegkit-patch-candidate`; that path requires the selected mpv AAR to retain its fixed hash, audits the
mpv closure before and after, audits the FFmpegKit candidate/temporary/final product against that unchanged
`libc++_shared.so` owner, and never rewrites the mpv product. M8 cannot be selected through either path.

The M9 source AAR is `23,380,329` bytes with SHA-256
`7CB0B25DC15F21278992243CE2597193B7A54E1A488933555B572982203E2BC0`; its deterministic aligned thin/product
AAR is `50,926,119` bytes with SHA-256
`F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4`. Both passed the source/thin closure
auditor with ten AArch64 ELF files, no `RPATH`/`RUNPATH`, `PT_LOAD >= 0x4000`, and aligned native ZIP payloads.
The selected FFmpeg
namespace is `libavcodec.so.63`, `libavdevice.so.63`, `libavfilter.so.12`, `libavformat.so.63`,
`libavutil.so.61`, `libswresample.so.7`, and `libswscale.so.10`.

The FFmpegKit r6 source AAR is `39,632,341` bytes with SHA-256
`0BD7ADDAE2D17960DB940A17A3E2450ACB83EECB46BE0D3800D051BB2075C1CE`; its deterministic aligned
thin/product AAR is `30,486,441` bytes with SHA-256
`7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394`. The fixed 33-file overlay keeps the
FFmpeg 9 command layer execution-local for session state, logging, reports, hardware devices, graph prefixes and
vstats while preserving the existing Java/JNI and SAF interfaces. The fixed source patches map FFmpeg's constrained
baseline value to OpenH264's baseline enum, skip an empty `BsFlush` word, and preserve an Android application's
already-started Binder threadpool before transcode. r5 added HarfBuzz/private FFprobe JSON; r6 additionally routes
help/capability stdout into the FFmpegKit log callback, enables GPL plus `eq/boxblur`, requires
`CONFIG_LIBHARFBUZZ=1`, `CONFIG_DRAWTEXT_FILTER=1`, `CONFIG_EQ_FILTER=1` and
`CONFIG_BOXBLUR_FILTER=1`, locks their binary markers, and packages the repository GPLv3 text. Framework patches make
the bounded job count effective and
reapply the OpenH264 patch after the Android helper's source reset. The `:ffmpeg` service uses one FIFO top-level
native owner while retaining independent request/session/result state and FFmpeg's internal worker threads; terminal
delivery drains the active session and session `0` callback queues. `libffmpegkit.so` does not import `exit`, `_exit`
or `quick_exit`.

OpenH264 `v2.6.0` predates the upstream `BsFlush` guard commit
`40555ec684ec0fede3948c8f272c04d88d05189d`, so r6 retains the locked empty-word patch instead of deleting it.
The matching FFmpeg profile patch is also retained. The patched OpenH264 v2.6.0 and FFmpeg n9.0.1 host
ASan/UBSan matrix completed `20/20` encodes and full audio/video decode reads.

FFmpegKit r3 remains the dated Android field-failure and build-chain baseline:
source `17,101,494` bytes / `48D7686C451363B2DCE936AC846D9A5F68CDF5BB5B96183143345A3CD16D5A7C`,
thin/product `30,133,939` bytes / `1E685258788D164209B2C5740F51A87E270E01A571E3428EA6BEE85EBCDF36F4`.
FFmpegKit r2 remains the earlier root-cause and Android reproducibility baseline:
source `17,100,196` bytes / `C2E35DBF0B2122361EB6BFA0B88D459A7AAD5C48951D60289E00C383D422952D`,
thin/product `30,135,977` bytes / `8FF6A8604FAE1AF0FB5DA160FF191F8D1CBAFE226630E2C04AAE3E6C5B34ED67`.
Neither historical closure is a runtime selector.

M8 (`n8.1.2` + Mbed TLS `3.6.7`) remains only a dated attribution, security-refresh and build-chain comparison
baseline. It is not a runtime selector, promotion option, or downgrade path.

The source identities and current selections are recorded in
`tools/player_native_build/closure_manifest.json` and `tools/ffmpegkit_native_build/closure_manifest.json`.
`NOTICE` describes the selected packaged payload. Device decoding, GPU performance, real-network playback and user
acceptance remain separate `verification_pending` evidence and are not implied by source, Gradle or APK checks.

## Link ownership

- The `:ffmpeg` process owns the seven normal-name FFmpeg libraries, `libffmpegkit.so`, and
  `libffmpegkit_abidetect.so`.
- The `:player` process owns `libmpv.so`, `libplayer.so`, the seven namespaced `libmp*.so` FFmpeg libraries, and the
  only `libc++_shared.so`.
- `libmpv.so` and `libplayer.so` dynamically resolve all FFmpeg symbols from the namespaced mpv `n9.0.1` set.
- `libmpv.so` dynamically resolves its C++ symbols from the Clang 21 runtime built and shipped by the same M9 source
  closure.
- Player initialization copies the fixed `cacert.pem`, sets `tls-ca-file`, keeps `tls-verify=yes`, and sets `ytdl=no`
  because browser media candidates are direct media requests and no yt-dlp binary is distributed.
- Player load keeps observed request identity fields but excludes a captured `Range` before `http-header-fields`, so
  FFmpeg can generate the correct range for its current open or seek offset.
- Kiyori's existing ExoPlayer dependency remains unrelated and is used only by its existing background-video owner.

## Reproduction

From a prepared checkout, run the project virtual environment interpreter:

```powershell
.\.venv\Scripts\python.exe -B ci/script/prepare_mpv_player_dependency.py --repository .
```

The default command only validates the already selected player and FFmpegKit product AARs; it does not
download or recreate the historical mpv product. A missing selected product must be restored through the audited
source-candidate promotion path. The explicit legacy `--mpv-input-aar` mode writes only a historical thin baseline
under `work/`.

To rebuild the selected player M9 source closure and create its aligned thin candidate, run the source builder with
the manifest profile and fixed toolchain paths:

```powershell
.\.venv\Scripts\python.exe -B ci\script\build_player_native_closure.py `
  --repository . `
  --profile m9_ffmpeg_major_candidate `
  --work-root <isolated-work-root> `
  --android-ndk <ndk-r29-path> `
  --android-sdk <android-sdk-path> `
  --bash <msys2-bash-path> `
  --build-tools <python-build-tools-path> `
  --host-toolchain <winlibs-root> `
  --jobs 8

.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --source-closure-aar <m9-source-aar> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe>
```

Build the fixed FFmpegKit M9 workspace and closure through the WSL-aware repository builder:

```powershell
.\.venv\Scripts\python.exe -B ci\script\build_ffmpegkit_native_closure.py `
  --repository . `
  --distribution Ubuntu-22.04 `
  --linux-user <wsl-user> `
  --work-root <wsl-work-root> `
  --android-ndk <wsl-ndk-r29-path> `
  --android-sdk <wsl-android-sdk-path> `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --jobs 12 `
  --candidate-output
```

After both candidates pass their independent auditors, promote the exact pair:

```powershell
.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --promote-m9-mpv-candidate <m9-player-thin-candidate> `
  --promote-m9-ffmpegkit-candidate <m9-ffmpegkit-thin-candidate> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --expected-mpv-sha256 f52aca6f35c651be7aab55f2efe6b5f40180d1ebaeb1404cc446470bf8deb6a4 `
  --expected-ffmpegkit-sha256 7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394
```

When only the FFmpegKit patch-level closure changes and the selected mpv product must remain byte-identical:

```powershell
.\.venv\Scripts\python.exe -B ci\script\prepare_mpv_player_dependency.py `
  --repository . `
  --promote-m9-ffmpegkit-patch-candidate <m9-ffmpegkit-thin-candidate> `
  --source-closure-profile m9_ffmpeg_major_candidate `
  --native-readelf <windows-host-ndk-llvm-readelf.exe> `
  --expected-ffmpegkit-sha256 7e6b4c20a93dfb3b90bc7f3c5d724cf657b70e2469ea4f2b1110396a8d345394
```

The paired command audits both candidates, both product-directory temporary files and both final product AARs. The
patch command verifies the fixed mpv hash before and after, audits its thin closure twice, and audits the FFmpegKit
candidate/temporary/final product against that unchanged C++ owner. Neither path exposes M8 selection.

## Required APK audit

Every player build records:

1. both generated AAR SHA-256 values and exact member lists;
2. final APK ABI and native entry list, with duplicate names rejected;
3. each arm64 ELF class/machine and every `PT_LOAD` alignment, with a minimum of `0x4000`;
4. old `libav*.so` / `libsw*.so` names are absent from the mpv closure, while every expected `libmp*.so` SONAME and
   `DT_NEEDED` edge is present;
5. `--enable-mbedtls`, Mbed TLS version, HTTPS marker, and complete unresolved FFmpeg symbol closure for
   `libmpv.so` / `libplayer.so`;
6. all C++ symbols required by arm64 libraries that depend on `libc++_shared.so`, including both
   `__from_chars_floating_point` instantiations required by `libmpv.so`;
7. APK time, size, SHA-256, package/version, Debug v2 signature, and `zipalign -c -P 16 -v 4` result.

The final APK hash is filled in only after the artifact has actually been built. Device decoding, GPU performance,
Anime4K output, gestures, and site behavior remain `verification_pending` until a user tests the final Debug APK on
target hardware.

## Current dual M9 selected AAR evidence (2026-08-17)

- Player source AAR: `23,380,329` bytes, SHA-256
  `7CB0B25DC15F21278992243CE2597193B7A54E1A488933555B572982203E2BC0`
- Player product AAR: `50,926,119` bytes, SHA-256
  `F52ACA6F35C651BE7AAB55F2EFE6B5F40180D1EBAEB1404CC446470BF8DEB6A4`
- FFmpegKit r6 source AAR: `39,632,341` bytes, SHA-256
  `0BD7ADDAE2D17960DB940A17A3E2450ACB83EECB46BE0D3800D051BB2075C1CE`
- FFmpegKit r6 product AAR: `30,486,441` bytes, SHA-256
  `7E6B4C20A93DFB3B90BC7F3C5D724CF657B70E2469EA4F2B1110396A8D345394`
- Both products are byte-identical to their aligned thin candidates; all 19 native payload offsets are
  `0 mod 0x4000`
- `libmpv.so` embeds `mpv v0.41.0-774-g2339eb727`, matching fixed commit `2339eb727`; the manifest's
  source-identity label remains `v0.41.0-dev-g2339eb727`. Namespaced and normal `libavutil` report FFmpeg
  `n9.0.1` with `LIBAVUTIL_61`; the FFmpegKit wrapper reports `8.1.7-kiyori-n9.0.1-r6`
- Both namespaces use FFmpeg majors `.63/.63/.12/.63/.61/.7/.10`; all selected ELF files are
  ELF64/AArch64, have no `RPATH`/`RUNPATH`, and satisfy `PT_LOAD >= 0x4000`
- The selected player supplies the only `libc++_shared.so`; normal and namespaced native basenames have zero overlap
- Historical Debug APK with FFmpegKit r4, before the r5 promotion: written `2026-08-17 19:48:41 +08:00`,
  `486,200,460` bytes, SHA-256
  `14CBE55B2BF1FC11B769D9E14267F474E41C3EF40FC115210E7A7A0CB6CC28C6`; the prescribed build
  revalidated it as `BUILD SUCCESSFUL in 59s` with 232 tasks, 19 executed and 213 up-to-date.
  Package/version/SDK:
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`, one `MainActivity` launcher, arm64-v8a only, Android Debug V2
  single signer, and `zipalign -c -P 16 -v 4` verified
- The APK contains 51 `.so` files with zero duplicate basenames plus the AArch64 shell launcher. All 52 files are
  ELF64/AArch64; 153 `PT_LOAD` entries are `0x4000 × 151` and `0x10000 × 2`
- All ten player and nine FFmpegKit native members are byte-identical to their selected AAR entries. The 19 related
  ELF files have zero `RPATH`/`RUNPATH`; normal/namespaced cross-dependencies and FFmpeg 8 markers are zero
- The selected r6 `libffmpegkit.so` contains the wrapper, capability-output bridge and
  `ABinderProcess_isThreadPoolStarted` patch markers; `libavutil.so` contains
  `--enable-gpl`/`--enable-libharfbuzz`, `libavfilter.so` contains exact `drawtext`/`eq`/`boxblur`, and the
  AAR contains the byte-exact GPLv3 resource
- The full APK audit also observes `RPATH/RUNPATH` in unrelated owners `libonnxruntime.so` and
  `libsherpa-mnn-jni.so`; they are outside the 19 player/FFmpegKit closure ELF files and are not modified by this
  native-stack stage

## Historical M8 selected AAR evidence (2026-08-16, before dual M9 promotion)

The following evidence remains the attributable M8 security-refresh baseline. It does not describe the selected
product AARs.

- Source AAR: `23,333,280` bytes, SHA-256
  `A13A3079BF9EC543C8C073C6B6D71D259C3002FA21E851DACD18E7E76254D006`
- Product AAR: `50,601,483` bytes, SHA-256
  `9A73F2A9F06161FB967DE47D8EAFDE3269E78F844E18F8F557E532378640CC5E`
- Product and audited thin candidate are byte-identical; the fixed FFmpegKit tools AAR remains
  `1A30A94226BF2157927EC6EDBB20154F9A1C1C53580F59CF55EFE46DB87A5AB3`
- Exact source/thin member sets, required binding classes, arm64 ELF64/AArch64, SONAME/`DT_NEEDED`, seven FFmpeg
  version namespaces, versioned import/provider closure, 99 C++ imports, both
  `__from_chars_floating_point` instantiations, and the single C++ runtime all pass
- `libmpv.so` reports mpv `v0.41.0-dev-g2339eb727`; `libmpformat.so` reports FFmpeg `n8.1.2`,
  `--enable-mbedtls`, Mbed TLS `3.6.7`, RSA-PSS and HTTPS, with curl disabled
- All ten player ELF files have zero `RPATH`/`RUNPATH`; every `PT_LOAD` is at least `0x4000`
- Final Debug APK: `471,063,551` bytes, SHA-256
  `6656ABC96C32A0E74FD00098478C2ACBA866EC272447F20A8CE3AAA395F6FA43`; package/version/SDK
  `com.kiyori / 45 / 0.1.0 / 26 / 34 / 37`, one `MainActivity` launcher, arm64-v8a only, Android Debug V2
  single signer, and `zipalign -P 16` verified
- The APK contains 51 `.so` files without duplicate basenames plus the AArch64 shell launcher. All 52 ELF files
  are ELF64/AArch64; 160 `PT_LOAD` entries are `0x4000 × 158` and `0x10000 × 2`. All ten M8 player and nine
  FFmpegKit native members are byte-identical to their selected AAR entries

## Historical stage-13 Debug evidence (2026-08-16, before M8 selection)

This evidence was regenerated from the stage-13 source tree before M8 selection. It remains a historical comparison
and does not describe the currently selected AAR.

- `app/libs/mpv-player-arm64.aar`: `50,543,589` bytes, SHA-256
  `FC983B7ED0C8B8BE1938283FE94108DFDC593AA31608D55DD1CE119AE201C32C`; exact native set:
  `libc++_shared.so`, `libmpv.so`, `libplayer.so`, `libmpcodec.so`, `libmpdevice.so`, `libmpfilter.so`,
  `libmpformat.so`, `libmputil.so`, `libmpresample.so`, and `libmpscale.so`
- `app/libs/ffmpeg-kit-player-arm64.aar`: `29,989,550` bytes, SHA-256
  `1A30A94226BF2157927EC6EDBB20154F9A1C1C53580F59CF55EFE46DB87A5AB3`; exact native set:
  `libavcodec.so`, `libavdevice.so`, `libavfilter.so`, `libavformat.so`, `libavutil.so`, `libswresample.so`,
  `libswscale.so`, `libffmpegkit.so`, and `libffmpegkit_abidetect.so`
- Final APK: `app/build/outputs/apk/debug/app-debug.apk`, written `2026-08-16 18:13:41 +08:00`,
  `471063551` bytes, SHA-256
  `9A02093155A717EF5018CD6A41DE192A2497F810618F51D4F38DA8C14882CF2A`
- Package: `com.kiyori`, version `45 / 0.1.0`, min/target/compile SDK `26 / 34 / 37`; the only launcher is
  `com.ai.assistance.operit.ui.main.MainActivity`
- Android Debug v2 single-signer signature verified; certificate SHA-256
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`; v1, v3 and v4 are not used
- `zipalign -c -P 16 -v 4`: `Verification successful`
- APK ABI: only `arm64-v8a`; 51 `.so` files with no duplicate basename. `assets/operit_shell_exec` is present and
  `libsudo.so` is absent
- All 51 `.so` files plus the shell launcher are AArch64 ELF64. The 160 `PT_LOAD` entries have a global minimum
  alignment of `0x4000`; none is below `0x4000`
- All 10 mpv AAR native payloads and all 9 FFmpegKit AAR native payloads are byte-identical to the selected APK
  entries
- The 44 DEX files contain `MPVLib`, `MPVNode`, `MpvPlayerEngine`, `PlayerRuntimeService`,
  `PlayerNetworkSnapshot`, `PlayerNetworkSnapshotObserver`, `PlayerRuntimeCapabilitySnapshot`,
  `PlayerRuntimeMediaIdentitySnapshot`, `PlayerFullVideoCacheStateEvidence`, and
  `PlayerRuntimeProtocolPolicyKt`; `onMediaIdentityChanged`, `fullVideoCacheStateEvidence`, `FULL_VIDEO`, and
  `demuxer-cache-state` markers are present
- `libmpv.so` has all seven expected namespaced FFmpeg `DT_NEEDED` edges; `libplayer.so` has the expected
  `libmpcodec.so`, `libmpformat.so`, `libmpscale.so`, and `libmputil.so` edges. Both have zero normal-name FFmpeg
  edges
- `libmpv.so` has 251 versioned FFmpeg imports and `libplayer.so` has 34; their unique union is 256 and all are
  provided by the namespaced closure
- `libmpv.so` and `libplayer.so` require 99 unique C++ symbols; all are provided by the selected namespaced
  FFmpeg/C++ runtime. The float and double `__from_chars_floating_point` exports are both present
- `libmpv.so` reports `mpv v0.41.0-dev-g2339eb727` and Android Clang `21.0.0` build `13989888`;
  `libmpformat.so` reports FFmpeg `n8.1.2`, `--enable-mbedtls`, Mbed TLS `3.6.6`, and
  `mbedtls_ssl_handshake`
- `assets/operit_shell_exec` depends only on `libandroid.so`, `liblog.so`, `libm.so`, `libdl.so`, and `libc.so`

That stage-13 closure remained reproducible from its fixed binary inputs and passed its historical static packaging
contract. It was security-refresh-pending and has now been superseded locally by the selected M8 source closure. The
historical record is not evidence that FFmpeg `9.0.1` has qualified or that target-device media behavior has passed.

## Historical Debug evidence (2026-07-29)

This evidence describes the `2026-07-29` artifact and is retained as a historical native-closure baseline. It is not
the current stage-13 Debug artifact. The final stage-13 hash is recorded only after its current source tree has been
built and audited.

The historical local artifact was
`app/build/outputs/apk/debug/app-debug.apk`, written `2026-07-29 14:22:10 +08:00`, size `474822245` bytes, SHA-256
`969C20C2FC6E1A401CFF51812EC1936AB674ECF5B2F51D0A7AB1589FBB35A6A7`.

- Package: `com.kiyori`, version `45 / 0.1.0`, min SDK 26, target SDK 34, compile SDK 37
- APK ABI: only `arm64-v8a`; 51 native libraries with no duplicate basename
- ELF: 52 AArch64 ELF64 files; every `PT_LOAD` minimum alignment is at least `0x4000`
- Non-ELF: inherited two-byte `libsudo.so` shell placeholder only
- Build gate: all 19 player-related native entries exist once; DEX contains `MPVLib`, `MPVNode`,
  `MpvPlayerEngine`, and `PlayerRuntimeService`
- `libmpv.so` and `libplayer.so` have zero normal-name FFmpeg `DT_NEEDED` edges
- `libmpv.so`: 251 versioned FFmpeg imports; missing from the namespaced mpv FFmpeg set: 0
- `libplayer.so`: 34 versioned FFmpeg imports; unique union with `libmpv.so`: 256; missing: 0
- `libmpv.so` and `libplayer.so` require 99 unique C++ symbols; missing after the namespaced FFmpeg and unique
  `libc++_shared.so` definitions: 0
- `libmpv.so` and the packaged C++ runtime both identify Android Clang `21.0.0`, build `13989888`; float/double
  `__from_chars_floating_point` exports found: 2
- `libmpformat.so`: FFmpeg `n8.1.2`, `--enable-mbedtls`, Mbed TLS `3.6.6`, `mbedtls_ssl_handshake`, and `https`
  markers all present
- Android Debug v2 signature verified; certificate SHA-256
  `E72AD950D07ADBEDFB9C909C48D922FDDB3560677012DA79B686A127867AE902`
- `zipalign -c -P 16 -v 4`: `Verification successful`

| APK native entry | SHA-256 |
| --- | --- |
| `libmpv.so` | `7CBF66DEB27047BDEDE563C396F99CAB0A65279CE15DDFBB19FE529F726B7275` |
| `libplayer.so` | `2EC34FABE647B4F119C0CC2433D90E9DF0514929B97DD83E6E03761FED0D84C1` |
| `libmpcodec.so` | `2BC66213F6A30E330F13324D89971C24F41F8E91A14BD1C89944B9CB5B854DB1` |
| `libmpdevice.so` | `0ABAED79E58B5626009AA37E595BA6F7AD28A96407142266F55CADF1490BF5BF` |
| `libmpfilter.so` | `B953ACC6415F3A079A1E6A3F8FE003B140E5DEB603D75591308F5542F2D39FCC` |
| `libmpformat.so` | `BF2C77C5F898A000D61FA14FA155322B182B4D1807FD961005290D6442B1F9D3` |
| `libmputil.so` | `FF034958716E6173C9539F6FB7724D428A61D6FFC9981E30DB29FFDE3D0A327A` |
| `libmpresample.so` | `B1497D565C5F919151AA58FBF54DC13FCA40504696DD89B3A4B177454BA1BCB5` |
| `libmpscale.so` | `FC4CEEBA6639BF22EDBC9CABA1C3836F1BD2A10AEDE67B5874D28A6FF0591B0C` |
| `libavcodec.so` | `9E481707D5FADD0E18F3245215170B68C5047334999FD489D383BC87A037DC92` |
| `libavdevice.so` | `D5756FFBFBA473D46E62D491DFB411F6A5506B891C22E1AF75B216A638113A1F` |
| `libavfilter.so` | `A704B347D3B441CD5CFBB355C8E3E9B2F55976759808DAB80FBAEE2A96A8DE1A` |
| `libavformat.so` | `CFBB8ACB373C44EADB2C846CC84308FBA9AEF5D9B07A17B6ED98444B394C4BB6` |
| `libavutil.so` | `267AC0C8318AF8884EEBF0E73AF8AF93AC19C6AF0F72DB0022CB172E2EFE0F70` |
| `libswresample.so` | `3C3AFCD1147280376DE4F7D8EEADB6AEFFBD9555E428C6F26C97FFA6F2B658B5` |
| `libswscale.so` | `10BEDC80F849F4C582F9F606CA7F20FDA043359F3D3EDD2411F060E57E150986` |
| `libffmpegkit.so` | `4E730F5FAFF7A7A3E1A39D5F973CF3AEBDB3A228E1E5E92909A4667D89FCEEF8` |
| `libffmpegkit_abidetect.so` | `030C021A9BF4320403B63D53D3706DFB86BF299A63EA6F5A779909CF9670294C` |
| `libc++_shared.so` | `C4C2FE5CBCB1FBA0003A31FC7AB29A9BB12DF6CC187EC45A806462540E83D93B` |

The complete canonical GNU GPL version 3 body is stored in root `LICENSE` (SHA-256
`71EBD932FFA82EF1ED27738E7236CFC290925B20A0A4970CBC61B11C939D0569`); the Kiyori `GPL-3.0-or-later` grant and
third-party source/notice obligations are in `NOTICE` (SHA-256
`94CD23B244BC2780C5BD62E70726685441EA8B388AA86E61BD7E6F78C773B874`). The libc++ license body is stored in
`docs/legal/third_party/LLVM-APACHE-2.0-WITH-LLVM-EXCEPTION.txt` (SHA-256
`C20FDCFB7F85FD154F13C62587EEA03A514A106A9367EFE5D099149CF0FDBB1D`).
