# Player native stack

Kiyori has one player owner and two process-isolated arm64 FFmpeg namespaces. The main process keeps FFmpegKit's
normal `libav*.so` names for FFmpeg tools and download remuxing. The non-exported `:player` process loads the single
mpv core against the fixed mpv input's `libmp*.so` FFmpeg closure. Gradle packaging selection, same-name overwrite,
an application-layer media proxy, and a second player runtime are not used.

## Fixed inputs

| Input | Source identity | SHA-256 |
| --- | --- | --- |
| `ffmpeg-kit-full-8.1.7.aar` | `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`; source tag `v8.1.7` at `62b07bf097baf26b416c815aea514e05c9ad6d63`; FFmpeg `n8.1.2` | `C3CBC81D498175FD2AA69EE2DFE7DAFBF519052A96283C2568FE5B3B16618456` |
| `mpv-android-lib-2026-06-25.aar` | `Riteshp2001/mpvlibAndroid` tag `2026-06-25` at `168e0a5e43b37c85509050cddcb5eaddc2e313c0` | `CA0D1C60DDFE5BAD46C369D0D0BF6C2D1A8BB5EC5A3AB916C33F892D0263A75E` |

`ci/script/prepare_mpv_player_dependency.py` verifies both complete inputs before writing two non-overlapping arm64
AARs. `app/libs/mpv-player-arm64.aar` keeps `classes.jar`, the AAR manifest/metadata, `assets/cacert.pem`,
`assets/subfont.ttf`, `libmpv.so`, `libplayer.so`, the matching `libc++_shared.so`, and the seven FFmpeg ELF files
built by the fixed mpv input with Mbed TLS enabled.

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
references. The deterministic mpv output is `50,543,589` bytes with SHA-256
`FC983B7ED0C8B8BE1938283FE94108DFDC593AA31608D55DD1CE119AE201C32C`. Its C++ runtime is
`1,374,336` bytes with SHA-256 `C4C2FE5CBCB1FBA0003A31FC7AB29A9BB12DF6CC187EC45A806462540E83D93B`.
Both `libmpv.so` and this runtime identify Android Clang `21.0.0`, build `13989888`, and use `0x4000` `PT_LOAD`
alignment.

The fixed mpv `libmpformat.so` reports FFmpeg `n8.1.2`, `--enable-mbedtls`, and Mbed TLS `3.6.6`. The FFmpegKit
`libavformat.so` also reports FFmpeg `n8.1.2`, but its build configuration disables OpenSSL and has no enabled TLS
backend. The `2026-07-29` vivo Android 16 report's `No protocol handler found to open URL https://...` therefore
matched the binary capability difference, not the browser request, Surface, renderer, or decoder.

A second vivo report at `2026-07-29 13:53 +08:00` confirmed that the namespaced stack now opens HTTPS, then exposed a
separate request-contract error: `Unexpected offset: expected 0, got 19890176`. The reported public resource is
`25260223` bytes, and requesting `Range: bytes=19890176-` returns the same `Content-Range` start. FFmpeg `n8.1.2`
suppresses its internally generated Range whenever custom headers already contain `Range`, but still validates the
response offset against its current `off`. Kiyori therefore retains browser Range evidence for candidate/download
identity while excluding it from mpv `http-header-fields`; mpv/FFmpeg alone owns playback open and seek offsets.

`app/libs/ffmpeg-kit-player-arm64.aar` retains all non-native Java, proguard, resource, license, and source members
from the fixed FFmpegKit input. Its native set is exactly the nine arm64 FFmpegKit/FFmpeg libraries; the x86_64
libraries and FFmpegKit input's `libc++_shared.so` are omitted. The deterministic output is `29,989,550` bytes with
SHA-256 `1A30A94226BF2157927EC6EDBB20154F9A1C1C53580F59CF55EFE46DB87A5AB3`.

Gradle resolves the fixed FFmpegKit Maven coordinate directly. Existing private dependency archives can still contain
the retired `app/libs/ffmpeg-kit-local.aar` and manual `jniLibs` runtimes; `prepare_android_dependencies.py` removes
those retired owners and then materializes the player mpv AAR. A fresh clone therefore reaches the same dependency graph
without an unreviewed binary overwrite.

## Link ownership

- The main process FFmpegKit tools own the seven normal-name FFmpeg libraries, `libffmpegkit.so`, and
  `libffmpegkit_abidetect.so`.
- The `:player` process owns `libmpv.so`, `libplayer.so`, the seven namespaced `libmp*.so` FFmpeg libraries, and the
  only `libc++_shared.so`.
- `libmpv.so` and `libplayer.so` dynamically resolve all FFmpeg symbols from the namespaced mpv `n8.1.2` set.
- `libmpv.so` dynamically resolves its C++ symbols from the Clang 21 runtime shipped by the same fixed mpv input.
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

The script downloads only the fixed HTTPS release asset when no verified input path is supplied, verifies the input
hash before reading it, writes through a temporary file, validates the exact member allowlist and native ABI, and prints
the deterministic output SHA-256. It fails before replacing an existing output when any check differs.

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

## Current Debug evidence

The final local artifact for this development round is
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
`16E43ACE61DE0D3F90940215244F1B4ED81FF3F3962AD27894C431E579F51FF4`). The libc++ license body is stored in
`docs/legal/third_party/LLVM-APACHE-2.0-WITH-LLVM-EXCEPTION.txt` (SHA-256
`C20FDCFB7F85FD154F13C62587EEA03A514A106A9367EFE5D099149CF0FDBB1D`).
