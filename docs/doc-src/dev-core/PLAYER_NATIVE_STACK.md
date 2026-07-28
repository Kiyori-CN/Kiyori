# Player native stack

Kiyori's player uses one arm64 dynamic-link stack. It does not combine the old FFmpeg `n6.0` local AAR with a
different mpv FFmpeg build, and it does not use Gradle packaging selection to hide duplicate libraries.

## Fixed inputs

| Input | Source identity | SHA-256 |
| --- | --- | --- |
| `ffmpeg-kit-full-8.1.7.aar` | `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`; source tag `v8.1.7` at `62b07bf097baf26b416c815aea514e05c9ad6d63`; FFmpeg `n8.1.2` | `C3CBC81D498175FD2AA69EE2DFE7DAFBF519052A96283C2568FE5B3B16618456` |
| `mpv-android-lib-2026-06-25.aar` | `Riteshp2001/mpvlibAndroid` tag `2026-06-25` at `168e0a5e43b37c85509050cddcb5eaddc2e313c0` | `CA0D1C60DDFE5BAD46C369D0D0BF6C2D1A8BB5EC5A3AB916C33F892D0263A75E` |

`ci/script/prepare_mpv_player_dependency.py` verifies both complete inputs before writing two non-overlapping arm64
AARs. `app/libs/mpv-player-arm64.aar` keeps `classes.jar`, the AAR manifest/metadata, `assets/cacert.pem`,
`assets/subfont.ttf`, `jni/arm64-v8a/libmpv.so`, `jni/arm64-v8a/libplayer.so`, and the matching
`jni/arm64-v8a/libc++_shared.so`. No `libav*.so` is permitted in that output.

The deterministic mpv output is `29,075,301` bytes with SHA-256
`ECDC87102E7B4A9BB9C9D46AF863F7C32B25B9AAB7A161614AF6646FFD603F70`. Its C++ runtime is
`1,374,336` bytes with SHA-256 `C4C2FE5CBCB1FBA0003A31FC7AB29A9BB12DF6CC187EC45A806462540E83D93B`.
Both `libmpv.so` and this runtime identify Android Clang `21.0.0`, build `13989888`, and use `0x4000` `PT_LOAD`
alignment.

`app/libs/ffmpeg-kit-player-arm64.aar` retains all non-native Java, proguard, resource, license, and source members
from the fixed FFmpegKit input. Its native set is exactly the nine arm64 FFmpegKit/FFmpeg libraries; the x86_64
libraries and FFmpegKit input's `libc++_shared.so` are omitted. The deterministic output is `29,989,550` bytes with
SHA-256 `1A30A94226BF2157927EC6EDBB20154F9A1C1C53580F59CF55EFE46DB87A5AB3`.

Gradle resolves the fixed FFmpegKit Maven coordinate directly. Existing private dependency archives can still contain
the retired `app/libs/ffmpeg-kit-local.aar` and manual `jniLibs` runtimes; `prepare_android_dependencies.py` removes
those retired owners and then materializes the thin mpv AAR. A fresh clone therefore reaches the same dependency graph
without an unreviewed binary overwrite.

## Link ownership

- FFmpegKit owns the seven FFmpeg libraries, `libffmpegkit.so`, and `libffmpegkit_abidetect.so`.
- The thin mpv AAR owns `libmpv.so`, `libplayer.so`, and the only `libc++_shared.so`.
- `libmpv.so` and `libplayer.so` dynamically resolve FFmpeg symbols from the single FFmpegKit `n8.1.2` set.
- `libmpv.so` dynamically resolves its C++ symbols from the Clang 21 runtime shipped by the same fixed mpv input.
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

1. thin AAR SHA-256 and exact member list;
2. final APK ABI and native entry list, with duplicate names rejected;
3. each arm64 ELF class/machine and every `PT_LOAD` alignment, with a minimum of `0x4000`;
4. `DT_NEEDED` ownership and unresolved FFmpeg symbols for `libmpv.so`/`libplayer.so`;
5. all C++ symbols required by arm64 libraries that depend on `libc++_shared.so`, including both
   `__from_chars_floating_point` instantiations required by `libmpv.so`;
6. APK time, size, SHA-256, package/version, Debug v2 signature, and `zipalign -c -P 16 -v 4` result.

The final APK hash is filled in only after the artifact has actually been built. Device decoding, GPU performance,
Anime4K output, gestures, and site behavior remain `verification_pending` until a user tests the final Debug APK on
target hardware.

## Current Debug evidence

The final local artifact for this development round is
`app/build/outputs/apk/debug/app-debug.apk`, written `2026-07-28 10:59:29 +08:00`, size `468740360` bytes, SHA-256
`44642BF9A4EE713D2566B6FEF2E77865067E7CB2D030F705D139075003B1ED03`.

- APK ABI: only `arm64-v8a`; 46 native entries with no duplicate basename
- ELF: 45 AArch64 ELF64 files; every `PT_LOAD` minimum alignment is at least `0x4000`
- Non-ELF: inherited two-byte `libsudo.so` shell placeholder only
- DEX definitions: `is.xyz.mpv.MPVLib`, `is.xyz.mpv.MPVNode`, and
  `com.ai.assistance.operit.core.player.MpvPlayerEngine` are present; APK duplicate-class validation also passed
- `libmpv.so`: 251 versioned FFmpeg imports; missing from the packaged FFmpeg set: 0
- `libplayer.so`: 34 versioned FFmpeg imports; unique union with `libmpv.so`: 256; missing: 0
- Nine arm64 libraries depend on `libc++_shared.so`; they require 146 C++ references and 121 unique C++ symbols,
  missing from the packaged runtime: 0
- `libmpv.so` and the packaged C++ runtime both identify Android Clang `21.0.0`, build `13989888`; float/double
  `__from_chars_floating_point` exports found: 2
- `libmpv.so` version string: `v0.41.0-dev-g2339eb727`; FFmpeg version string: `n8.1.2`
- `zipalign -c -P 16 -v 4`: `Verification successful`

| APK native entry | SHA-256 |
| --- | --- |
| `libmpv.so` | `200EB3C70DABE131554228864B3B432591B940EA233DBEFC19BDF2A83A1B8613` |
| `libplayer.so` | `9EE2D018E11AA2783C2B979E1EF3C2F74BB0CB642FB87293FB964D690B1C5AE5` |
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
`B362B26D6D23C11B1E42345382047F50F8691B8518AA97E57E9AC1C0296AB423`). The libc++ license body is stored in
`docs/legal/third_party/LLVM-APACHE-2.0-WITH-LLVM-EXCEPTION.txt` (SHA-256
`C20FDCFB7F85FD154F13C62587EEA03A514A106A9367EFE5D099149CF0FDBB1D`).
