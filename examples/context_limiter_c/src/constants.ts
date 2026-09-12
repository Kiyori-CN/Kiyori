type SupportedInputMenuToggleAction = "create" | "toggle";
type InputMenuToggleActionLiteral<T extends ToolPkg.InputMenuToggleEventPayload["action"]> = T;

type SupportedPromptTurnKind = Extract<
  ToolPkg.PromptTurnKind,
  "SYSTEM" | "USER" | "ASSISTANT"
>;

export const DEFAULT_FLOOR_LIMIT = 5;
// UI、Hook 和工具共用整数解释，不能把 1.5 / 5abc 静默截成合法层数。
export function parseFloorLimit(value: string | number | undefined): number | undefined {
  if ((typeof value !== 'string' && typeof value !== 'number') || (typeof value === 'string' && !/^\d+$/.test(value.trim()))) return undefined;
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined;
}
export const DEFAULT_LIMITER_ENABLED = true;
export const FLOOR_OPTIONS = [3, 5, 8, 10, 15, 20, 30, 50, 100] as const;

export const ENV_KEYS = {
  floorLimit: "CTX_LIMITER_C_FLOOR_LIMIT",
  enabled: "CTX_LIMITER_C_ENABLED",
} as const;

export const HOOK_IDS = {
  finalize: "ctx_limiter_c_finalize",
  menu: "ctx_limiter_c_menu",
} as const;

export const TOGGLE_IDS = {
  limiter: "ctx_limiter_toggle",
  adjust: "ctx_limiter_adjust",
} as const;

export const INPUT_MENU_TOGGLE_ACTION: Record<
  SupportedInputMenuToggleAction,
  InputMenuToggleActionLiteral<SupportedInputMenuToggleAction>
> = {
  create: "create",
  toggle: "toggle",
};

export const PROMPT_TURN_KIND: Record<
  SupportedPromptTurnKind,
  SupportedPromptTurnKind
> = {
  SYSTEM: "SYSTEM",
  USER: "USER",
  ASSISTANT: "ASSISTANT",
};
