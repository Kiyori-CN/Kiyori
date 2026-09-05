import { REMOTE_KIYORI_SETUP_EN_US } from "./en-US";
import { REMOTE_KIYORI_SETUP_ZH_CN } from "./zh-CN";
import type { RemoteKiyoriSetupI18n } from "./types";

const DEFAULT_LOCALE = "zh-CN";

const REMOTE_KIYORI_SETUP_I18N_MAP: Record<string, RemoteKiyoriSetupI18n> = {
  "zh-CN": REMOTE_KIYORI_SETUP_ZH_CN,
  "en-US": REMOTE_KIYORI_SETUP_EN_US
};

function normalizeLocale(locale?: string): string {
  const value = (locale || "").trim();
  if (!value) {
    return DEFAULT_LOCALE;
  }

  const lower = value.toLowerCase();
  if (lower.startsWith("zh")) {
    return "zh-CN";
  }
  if (lower.startsWith("en")) {
    return "en-US";
  }

  return value;
}

export function resolveRemoteKiyoriSetupI18n(locale?: string): RemoteKiyoriSetupI18n {
  const normalized = normalizeLocale(locale);
  return REMOTE_KIYORI_SETUP_I18N_MAP[normalized] || REMOTE_KIYORI_SETUP_I18N_MAP[DEFAULT_LOCALE];
}

export type { RemoteKiyoriSetupI18n } from "./types";
