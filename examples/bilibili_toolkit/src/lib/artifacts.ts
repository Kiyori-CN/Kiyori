import { requireSafeAbsoluteAndroidPath, safePathComponent } from "./json";
import type { JsonValue, VideoContext } from "./types";
import { BilibiliError } from "./errors";

export function defaultOutputRoot(): string {
  return requireSafeAbsoluteAndroidPath(getArtifactPaths().android, "artifact_root") + "/bilibili";
}

export function contextRoot(
  context: VideoContext,
  outputRoot = defaultOutputRoot()
): string {
  const root = requireSafeAbsoluteAndroidPath(outputRoot, "output_root");
  const identity =
    context.episodeId === null ? context.bvid : "ep" + String(context.episodeId);
  return (
    root +
    "/library/" +
    safePathComponent(identity) +
    "/p" +
    String(context.part).padStart(2, "0")
  );
}

export async function ensureDirectory(path: string): Promise<void> {
  const exists = await Tools.Files.exists(path, "android");
  if (exists.exists) {
    if (exists.isDirectory !== true) {
      throw new Error("Expected an output directory at " + path + ".");
    }
    return;
  }
  const result = await Tools.Files.mkdir(path, true, "android");
  if (!result.successful) {
    throw new Error("Could not create output directory " + path + ": " + result.details);
  }
}

export async function writeTextArtifact(
  path: string,
  content: string,
  overwrite: boolean
): Promise<string> {
  const exists = await Tools.Files.exists(path, "android");
  if (exists.exists && !overwrite) {
    throw new BilibiliError("OUTPUT_EXISTS", "产物已存在；设置 overwrite=true 可覆盖重跑。", { path, action: "set_overwrite_true" });
  }
  const result = await Tools.Files.write(path, content, false, "android");
  if (!result.successful) {
    throw new Error("Could not write " + path + ": " + result.details);
  }
  return path;
}

export async function writeJsonArtifact(
  path: string,
  value: JsonValue,
  overwrite: boolean
): Promise<string> {
  return writeTextArtifact(path, JSON.stringify(value, null, 2) + "\n", overwrite);
}

export async function removeTemporaryPath(path: string): Promise<void> {
  const exists = await Tools.Files.exists(path, "android");
  if (!exists.exists) {
    return;
  }
  const removed = await Tools.Files.deleteFile(path, true, "android");
  if (!removed.successful) {
    throw new Error("Could not remove temporary path " + path + ": " + removed.details);
  }
}
