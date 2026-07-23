export function getWorldBookDir(): string {
  return ToolPkg.getConfigDir();
}

export function getWorldBookFile(): string {
  return `${getWorldBookDir()}/entries.json`;
}

export async function ensureWorldBookStorage(): Promise<void> {
  const worldBookDir = getWorldBookDir();
  const worldBookFile = getWorldBookFile();

  await Tools.Files.mkdir(worldBookDir, true);

  const currentFileExists = await Tools.Files.exists(worldBookFile);
  if (!currentFileExists?.exists) {
    await Tools.Files.write(worldBookFile, "[]", false);
  }
}

export async function readWorldBookEntries<T>(): Promise<T[]> {
  await ensureWorldBookStorage();

  try {
    const fileResult = await Tools.Files.read(getWorldBookFile());
    if (!fileResult?.content) {
      return [];
    }

    const parsed = JSON.parse(fileResult.content);
    return Array.isArray(parsed) ? (parsed as T[]) : [];
  } catch (_error) {
    return [];
  }
}

export async function writeWorldBookEntries(entries: unknown[]): Promise<void> {
  await ensureWorldBookStorage();
  await Tools.Files.write(getWorldBookFile(), JSON.stringify(entries, null, 2));
}
