export function getWorldBookDir(): string {
  return ToolPkg.getConfigDir();
}

export function getWorldBookFile(): string {
  return `${getWorldBookDir()}/entries.json`;
}

export async function ensureWorldBookStorage(): Promise<void> {
  const worldBookDir = getWorldBookDir();
  const worldBookFile = getWorldBookFile();

  const directory = await Tools.Files.mkdir(worldBookDir, true);
  if (!directory.successful) throw new Error('无法创建世界书配置目录');

  const currentFileExists = await Tools.Files.exists(worldBookFile);
  if (!currentFileExists?.exists) {
    const created = await Tools.Files.write(worldBookFile, "[]", false);
    if (!created.successful) throw new Error('无法初始化世界书配置文件');
  }
}

export async function readWorldBookEntries<T>(): Promise<T[]> {
  await ensureWorldBookStorage();

  const fileResult = await Tools.Files.read(getWorldBookFile());
  let parsed: unknown;
  try {
    parsed = JSON.parse(fileResult.content);
  } catch (error) {
    console.error('worldbook: stored entries are not valid JSON; original file preserved');
    throw new Error('世界书配置无法解析，请修复或恢复 entries.json 后重试；原文件已保留。');
  }
  // 将损坏文件当作空列表会让下一次新增条目覆盖全部既有数据。
  if (!Array.isArray(parsed)) throw new Error('世界书配置必须为数组；原文件已保留。');
  return parsed as T[];
}

export async function writeWorldBookEntries(entries: unknown[]): Promise<void> {
  await ensureWorldBookStorage();
  const result = await Tools.Files.write(getWorldBookFile(), JSON.stringify(entries, null, 2));
  if (!result.successful) throw new Error('世界书保存失败，请核对配置文件后重试。');
}
