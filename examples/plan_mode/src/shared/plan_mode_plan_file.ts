import { PLAN_FILE_DIRECTORY_NAME } from "./plan_mode_constants.js";
import { normalizePlanContent } from "./plan_mode_content.js";
export { normalizePlanContent } from "./plan_mode_content.js";
import type { FileEnvironment } from "../../../types/files";
import { buildPlanFilePath, resolveChatWorkspace, type ChatWorkspaceBinding } from "./plan_mode_workspace.js";

export type PlanFileRecord = ChatWorkspaceBinding & {
  path: string;
  content: string;
};

export type PlanFileBinding = ChatWorkspaceBinding & {
  path: string;
};

function fileEnvironment(binding: ChatWorkspaceBinding): FileEnvironment {
  const environment = binding.workspaceEnv ?? "android";
  if (environment === "android" || environment === "linux") return environment;
  throw new Error("Unsupported workspace file environment");
}

export function resolvePlanFileBinding(chatId: string): PlanFileBinding | null {
  const binding = resolveChatWorkspace(chatId);
  if (!binding) {
    return null;
  }
  return {
    ...binding,
    path: buildPlanFilePath(binding.workspacePath),
  };
}

export async function hasPlanFile(chatId: string): Promise<boolean> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    return false;
  }
  const result = await Tools.Files.exists(binding.path, fileEnvironment(binding));
  return result.exists;
}

export async function readPlanFile(chatId: string): Promise<PlanFileRecord | null> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    return null;
  }
  return readBoundPlanFile(binding);
}

export async function readBoundPlanFile(binding: PlanFileBinding): Promise<PlanFileRecord | null> {
  const environment = fileEnvironment(binding);
  const exists = await Tools.Files.exists(binding.path, environment);
  if (!exists.exists) {
    return null;
  }
  const result = await Tools.Files.read({ path: binding.path, environment });
  return {
    ...binding,
    content: result.content.replace(/\r\n/g, "\n"),
  };
}

export async function writePlanFile(chatId: string, content: string): Promise<PlanFileRecord> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    throw new Error("workspace is not bound");
  }
  return writeBoundPlanFile(binding, content);
}

export async function writeBoundPlanFile(binding: PlanFileBinding, content: string): Promise<PlanFileRecord> {
  const normalized = normalizePlanContent(content);
  const environment = fileEnvironment(binding);
  await Tools.Files.mkdir(`${binding.workspacePath}/${PLAN_FILE_DIRECTORY_NAME}`, true, environment);
  await Tools.Files.write(binding.path, normalized, false, environment);
  return {
    ...binding,
    path: binding.path,
    content: normalized,
  };
}

export async function deletePlanFile(chatId: string): Promise<{
  chatId: string;
  workspacePath: string;
  path: string;
  deleted: boolean;
}> {
  const binding = resolvePlanFileBinding(chatId);
  if (!binding) {
    throw new Error("workspace is not bound");
  }
  const environment = fileEnvironment(binding);
  const exists = await Tools.Files.exists(binding.path, environment);
  if (exists.exists) {
    await Tools.Files.deleteFile(binding.path, false, environment);
  }
  return {
    chatId: binding.chatId,
    workspacePath: binding.workspacePath,
    path: binding.path,
    deleted: exists.exists,
  };
}
