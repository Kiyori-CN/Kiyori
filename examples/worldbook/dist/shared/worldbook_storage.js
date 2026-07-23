"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getWorldBookDir = getWorldBookDir;
exports.getWorldBookFile = getWorldBookFile;
exports.ensureWorldBookStorage = ensureWorldBookStorage;
exports.readWorldBookEntries = readWorldBookEntries;
exports.writeWorldBookEntries = writeWorldBookEntries;
function getWorldBookDir() {
    return ToolPkg.getConfigDir();
}
function getWorldBookFile() {
    return `${getWorldBookDir()}/entries.json`;
}
async function ensureWorldBookStorage() {
    const worldBookDir = getWorldBookDir();
    const worldBookFile = getWorldBookFile();
    await Tools.Files.mkdir(worldBookDir, true);
    const currentFileExists = await Tools.Files.exists(worldBookFile);
    if (!currentFileExists?.exists) {
        await Tools.Files.write(worldBookFile, "[]", false);
    }
}
async function readWorldBookEntries() {
    await ensureWorldBookStorage();
    try {
        const fileResult = await Tools.Files.read(getWorldBookFile());
        if (!fileResult?.content) {
            return [];
        }
        const parsed = JSON.parse(fileResult.content);
        return Array.isArray(parsed) ? parsed : [];
    }
    catch (_error) {
        return [];
    }
}
async function writeWorldBookEntries(entries) {
    await ensureWorldBookStorage();
    await Tools.Files.write(getWorldBookFile(), JSON.stringify(entries, null, 2));
}
