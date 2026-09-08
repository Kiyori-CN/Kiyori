const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { spawnSync } = require("child_process");

const DEFAULT_SEGMENT_LENGTH = 64 * 1024;
const MAX_SEGMENT_LENGTH = 2 * 1024 * 1024;
const MAX_TEXT_READ_BYTES = 4 * 1024 * 1024;
const MAX_TEXT_WRITE_BYTES = 4 * 1024 * 1024;
const MAX_BASE64_BYTES = 16 * 1024 * 1024;
const MAX_LIST_DEPTH = 5;

function normalizeEncoding(value) {
  const raw = String(value || "utf8").trim().toLowerCase();

  if (raw === "utf8" || raw === "utf-8") {
    return "utf8";
  }

  if (raw === "utf16le" || raw === "utf-16le") {
    return "utf16le";
  }

  if (raw === "latin1") {
    return "latin1";
  }

  if (raw === "ascii") {
    return "ascii";
  }

  throw new Error("Unsupported encoding");
}

function parseNonNegativeInt(value, fallback, fieldName) {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }

  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 0) {
    throw new Error(`${fieldName} must be a non-negative integer`);
  }

  return Math.floor(parsed);
}

function parsePositiveInt(value, fallback, maxValue, fieldName) {
  if (value === undefined || value === null || value === "") {
    return fallback;
  }

  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${fieldName} must be a positive integer`);
  }

  const safe = Math.floor(parsed);
  if (safe > maxValue) {
    throw new Error(`${fieldName} too large (max ${maxValue})`);
  }

  return safe;
}

function parseExpectedReplacements(value) {
  if (value === undefined || value === null || value === "") {
    return 1;
  }

  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < 1) {
    throw new Error("expected_replacements must be an integer >= 1");
  }

  return Math.floor(parsed);
}

function countOccurrences(haystack, needle) {
  if (!needle) {
    return 0;
  }

  return haystack.split(needle).length - 1;
}

function resolveTargetPath(projectRoot, inputPath) {
  if (typeof inputPath !== "string" || !inputPath.trim()) {
    throw new Error("Missing path");
  }

  const raw = inputPath;
  if (raw.includes("\0")) {
    throw new Error("Invalid path");
  }

  if (process.platform === "win32" && (/^[a-z]:(?![\\/])/i.test(raw) || /^\\\\\.\\/.test(raw))) {
    throw new Error("Use an absolute Windows file path; drive-relative and device paths are not supported");
  }
  if (process.platform === "win32" && /(?:^|[\\/])(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\.|[\\/]|$)/i.test(raw)) throw new Error("Windows device names are not file paths");

  if (path.isAbsolute(raw)) {
    return path.normalize(raw);
  }

  return path.resolve(projectRoot, raw);
}

function readDirectoryEntries(targetPath, currentDepth, maxDepth, budget) {
  const dirents = fs.readdirSync(targetPath, { withFileTypes: true });
  if (dirents.length > budget.remaining) throw new Error("Directory listing exceeds 5000 entries; use a narrower path or depth");
  budget.remaining -= dirents.length;

  const sorted = dirents.sort((a, b) => {
    if (a.isDirectory() && !b.isDirectory()) {
      return -1;
    }
    if (!a.isDirectory() && b.isDirectory()) {
      return 1;
    }
    return a.name.localeCompare(b.name, "en", { sensitivity: "base" });
  });

  return sorted.map((dirent) => {
    const itemPath = path.join(targetPath, dirent.name);
    let stat = null;

    try {
      stat = fs.statSync(itemPath);
    } catch {
      stat = null;
    }

    const type = dirent.isDirectory()
      ? "directory"
      : dirent.isFile()
      ? "file"
      : dirent.isSymbolicLink()
      ? "symlink"
      : "other";

    const item = {
      name: dirent.name,
      path: itemPath,
      type,
      sizeBytes: stat ? stat.size : 0,
      modifiedAt: stat ? stat.mtime.toISOString() : ""
    };

    if (type === "directory" && currentDepth < maxDepth) {
      try {
        item.children = readDirectoryEntries(itemPath, currentDepth + 1, maxDepth, budget);
      } catch (error) {
        if (error.code !== "EACCES" && error.code !== "EPERM") throw error;
        item.error = error.code;
      }
    }

    return item;
  });
}

function ensureReadableFile(targetPath) {
  if (!fs.existsSync(targetPath)) {
    throw new Error("Path not found");
  }

  const stat = fs.statSync(targetPath);
  if (!stat.isFile()) {
    throw new Error("Target path is not a file");
  }

  return stat;
}

function readFileChunk(targetPath, startOffset, length) {
  const buffer = Buffer.alloc(length);
  const fd = fs.openSync(targetPath, "r");

  try {
    const bytesRead = fs.readSync(fd, buffer, 0, length, startOffset);
    return buffer.subarray(0, bytesRead);
  } finally {
    fs.closeSync(fd);
  }
}

function normalizeBase64Input(rawBase64) {
  if (typeof rawBase64 !== "string") {
    throw new Error("Missing base64");
  }

  let text = rawBase64.trim().replace(/\s+/g, "");
  text = text.replace(/-/g, "+").replace(/_/g, "/");

  const remainder = text.length % 4;
  if (remainder === 1) {
    throw new Error("Invalid base64");
  }

  if (remainder > 0) {
    text += "=".repeat(4 - remainder);
  }

  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(text)) {
    throw new Error("Invalid base64");
  }

  return text;
}

function createFileService({ projectRoot }) {
  function assertBoundedText(targetPath) {
    const stat = ensureReadableFile(targetPath);
    if (stat.size > MAX_TEXT_READ_BYTES) throw new Error("File too large; use byte segments (max text read 4 MiB)");
    return stat;
  }
  function atomicWrite(targetPath, data) {
    // 不截断原文件：同目录临时文件完整落盘后替换。符号链接保留原有指向，不替换链接本身。
    const actualPath = fs.existsSync(targetPath) ? fs.realpathSync(targetPath) : targetPath;
    if (fs.existsSync(actualPath) && fs.statSync(actualPath).nlink > 1) throw new Error("Atomic replacement of a hard-linked file is not supported; inspect its link ownership first");
    fs.mkdirSync(path.dirname(actualPath), { recursive: true });
    const temporary = path.join(path.dirname(actualPath), `.kiyori-${crypto.randomUUID()}.tmp`);
    let fd;
    try {
      fd = fs.openSync(temporary, "wx", fs.existsSync(actualPath) ? fs.statSync(actualPath).mode : 0o600);
      fs.writeFileSync(fd, data);
      fs.fsyncSync(fd);
      fs.closeSync(fd);
      fd = undefined;
      if (process.platform === "win32" && fs.existsSync(actualPath)) {
        // File.Replace 保留目标 ACL 等元数据；直接 rename 临时文件会改用父目录继承的 ACL。
        const literal = value => `'${value.replace(/'/g, "''")}'`;
        const script = `$ErrorActionPreference = 'Stop'; [System.IO.File]::Replace(${literal(temporary)}, ${literal(actualPath)}, [NullString]::Value)`;
        const result = spawnSync("powershell.exe", ["-NoProfile", "-NonInteractive", "-EncodedCommand", Buffer.from(script, "utf16le").toString("base64")], { windowsHide: true, encoding: "utf8", timeout: 10000 });
        if (result.error) throw new Error("REPLACE_RESULT_UNKNOWN: inspect the target before retrying");
        if (result.status !== 0) throw new Error("File replacement failed; check Windows file locks and permissions");
      } else fs.renameSync(temporary, actualPath);
    } finally {
      if (fd !== undefined) fs.closeSync(fd);
      if (fs.existsSync(temporary)) fs.unlinkSync(temporary);
    }
  }

  function statPath(pathInput) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    const stat = fs.lstatSync(targetPath);
    return { path: targetPath, type: stat.isSymbolicLink() ? "symlink" : stat.isDirectory() ? "directory" : "file",
      sizeBytes: stat.size, modifiedAt: stat.mtime.toISOString() };
  }

  function makeDirectory(pathInput) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    const created = fs.mkdirSync(targetPath, { recursive: true }) !== undefined;
    return { path: targetPath, created };
  }

  function resolveDestination(sourceInput, destinationInput) {
    const source = resolveTargetPath(projectRoot, sourceInput);
    const destination = resolveTargetPath(projectRoot, destinationInput);
    // lstat 同时检测悬空链接，避免把已存在的目标误认为可覆盖。
    try { fs.lstatSync(destination); } catch (error) {
      if (error.code === "ENOENT") return { source, destination };
      throw error;
    }
    throw Object.assign(new Error("Destination already exists; choose a new path"), { code: "EEXIST" });
  }

  function movePath(sourceInput, destinationInput) {
    const { source, destination } = resolveDestination(sourceInput, destinationInput);
    const stat = fs.lstatSync(source);
    if (source === path.parse(source).root) throw new Error("Cannot move a filesystem root");
    if (stat.isDirectory() && path.relative(source, destination).split(path.sep)[0] !== ".." && !path.isAbsolute(path.relative(source, destination))) {
      throw new Error("Cannot move a directory into itself");
    }
    // Windows rename 拒绝已存在目标；跨卷 EXDEV 直接返回，不以复制后删除冒充原子移动。
    fs.renameSync(source, destination);
    return { path: source, destination, moved: true };
  }

  function copyFile(sourceInput, destinationInput) {
    const { source, destination } = resolveDestination(sourceInput, destinationInput);
    ensureReadableFile(source);
    fs.copyFileSync(source, destination, fs.constants.COPYFILE_EXCL);
    return { path: source, destination, sizeBytes: fs.statSync(destination).size };
  }

  function listDirectory(pathInput, depthInput) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    if (!fs.existsSync(targetPath)) {
      throw new Error("Path not found");
    }

    const stat = fs.statSync(targetPath);
    if (!stat.isDirectory()) {
      throw new Error("Target path is not a directory");
    }

    const depth = parsePositiveInt(depthInput, 1, MAX_LIST_DEPTH, "depth");

    return {
      path: targetPath,
      depth,
      items: readDirectoryEntries(targetPath, 1, depth, { remaining: 5000 })
    };
  }

  function readTextFile(pathInput, encodingInput) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    const stat = ensureReadableFile(targetPath);
    const encoding = normalizeEncoding(encodingInput);

    if (stat.size > MAX_TEXT_READ_BYTES) {
      throw new Error(
        `File too large (${stat.size} bytes). Use read_segment endpoint for large files.`
      );
    }

    const content = fs.readFileSync(targetPath, { encoding });

    return {
      path: targetPath,
      sizeBytes: stat.size,
      encoding,
      content
    };
  }

  function readTextSegment(pathInput, options = {}) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    const stat = ensureReadableFile(targetPath);
    const encoding = normalizeEncoding(options.encoding);

    const offset = parseNonNegativeInt(options.offset, 0, "offset");
    const length = parsePositiveInt(
      options.length,
      DEFAULT_SEGMENT_LENGTH,
      MAX_SEGMENT_LENGTH,
      "length"
    );

    const start = Math.min(offset, stat.size);
    const readableLength = Math.min(length, stat.size - start);

    if (readableLength <= 0) {
      return {
        path: targetPath,
        encoding,
        offset: start,
        length: 0,
        totalBytes: stat.size,
        eof: true,
        content: ""
      };
    }

    let chunk = readFileChunk(targetPath, start, readableLength);
    // 字节分页不能把 UTF-8 字符或 UTF-16 代理对切成替换字符；返回实际消费字节数供续读。
    if (encoding === "utf8") {
      if ((chunk[0] & 0xc0) === 0x80) throw new Error("offset is inside a UTF-8 character; use the previous returned offset + length");
      if (start + chunk.length < stat.size) {
        let lead = chunk.length - 1;
        while (lead > 0 && (chunk[lead] & 0xc0) === 0x80) lead--;
        const byte = chunk[lead];
        const width = byte < 0x80 ? 1 : byte < 0xe0 ? 2 : byte < 0xf0 ? 3 : 4;
        if (chunk.length - lead < width) chunk = chunk.subarray(0, lead);
      }
    } else if (encoding === "utf16le") {
      if (start % 2) throw new Error("UTF-16 offset must be even");
      chunk = chunk.subarray(0, chunk.length - chunk.length % 2);
      if (chunk.length >= 2 && start + chunk.length < stat.size) {
        const tail = chunk.readUInt16LE(chunk.length - 2);
        if (tail >= 0xd800 && tail <= 0xdbff) chunk = chunk.subarray(0, chunk.length - 2);
      }
    }
    if (!chunk.length) throw new Error("length is too small for a complete character; request at least 4 bytes");

    return {
      path: targetPath,
      encoding,
      offset: start,
      length: chunk.length,
      totalBytes: stat.size,
      eof: start + chunk.length >= stat.size,
      content: chunk.toString(encoding)
    };
  }

  function readTextLines(pathInput, options = {}) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    assertBoundedText(targetPath);
    const encoding = normalizeEncoding(options.encoding);

    const rawContent = fs.readFileSync(targetPath, { encoding });
    const normalizedContent = rawContent.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    const lines = normalizedContent.length ? normalizedContent.split("\n") : [];
    const totalLines = lines.length;

    const lineStart = parsePositiveInt(options.line_start, 1, Number.MAX_SAFE_INTEGER, "line_start");
    const defaultLineEnd = totalLines === 0 ? lineStart : totalLines;
    const lineEnd = parsePositiveInt(options.line_end, defaultLineEnd, Number.MAX_SAFE_INTEGER, "line_end");

    if (lineEnd < lineStart) {
      throw new Error("line_end must be greater than or equal to line_start");
    }

    if (totalLines === 0 || lineStart > totalLines) {
      return {
        path: targetPath,
        encoding,
        lineStart,
        lineEnd: Math.min(lineEnd, totalLines),
        totalLines,
        eof: true,
        content: ""
      };
    }

    const boundedLineEnd = Math.min(lineEnd, totalLines);
    const content = lines.slice(lineStart - 1, boundedLineEnd).join("\n");

    return {
      path: targetPath,
      encoding,
      lineStart,
      lineEnd: boundedLineEnd,
      totalLines,
      eof: boundedLineEnd >= totalLines,
      content
    };
  }

  function writeTextFile(pathInput, contentInput, encodingInput) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    if (typeof contentInput !== "string") {
      throw new Error("Missing content");
    }

    const encoding = normalizeEncoding(encodingInput);
    const sizeBytes = Buffer.byteLength(contentInput, encoding);

    if (sizeBytes > MAX_TEXT_WRITE_BYTES) {
      throw new Error(`Text content too large (max ${MAX_TEXT_WRITE_BYTES} bytes)`);
    }

    atomicWrite(targetPath, Buffer.from(contentInput, encoding));

    return {
      path: targetPath,
      encoding,
      sizeBytes
    };
  }

  function editTextFile(pathInput, oldTextInput, newTextInput, expectedReplacementsInput, encodingInput) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    assertBoundedText(targetPath);

    if (typeof oldTextInput !== "string" || !oldTextInput.length) {
      throw new Error("Missing old_text");
    }

    if (typeof newTextInput !== "string") {
      throw new Error("Missing new_text");
    }

    const encoding = normalizeEncoding(encodingInput);
    const expectedReplacements = parseExpectedReplacements(expectedReplacementsInput);

    const content = fs.readFileSync(targetPath, { encoding });
    const replacements = countOccurrences(content, oldTextInput);

    if (replacements === 0) {
      throw new Error("old_text not found in file");
    }

    if (replacements !== expectedReplacements) {
      throw new Error(
        `Replacement count mismatch: expected ${expectedReplacements}, actual ${replacements}`
      );
    }

    const nextContent = content.split(oldTextInput).join(newTextInput);
    const sizeBytes = Buffer.byteLength(nextContent, encoding);

    if (sizeBytes > MAX_TEXT_WRITE_BYTES) {
      throw new Error(`Text content too large (max ${MAX_TEXT_WRITE_BYTES} bytes)`);
    }

    atomicWrite(targetPath, Buffer.from(nextContent, encoding));

    return {
      path: targetPath,
      encoding,
      sizeBytes,
      replacements,
      expectedReplacements
    };
  }

  function readBase64File(pathInput, options = {}) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    const stat = ensureReadableFile(targetPath);

    const offset = parseNonNegativeInt(options.offset, 0, "offset");
    const start = Math.min(offset, stat.size);

    const remaining = Math.max(0, stat.size - start);
    const length =
      options.length === undefined || options.length === null || options.length === ""
        ? remaining
        : parsePositiveInt(options.length, remaining, MAX_BASE64_BYTES, "length");

    const readableLength = Math.min(length, remaining);

    if (readableLength > MAX_BASE64_BYTES) {
      throw new Error(`Read too large (max ${MAX_BASE64_BYTES} bytes)`);
    }

    if (readableLength <= 0) {
      return {
        path: targetPath,
        offset: start,
        length: 0,
        totalBytes: stat.size,
        eof: true,
        base64: ""
      };
    }

    const chunk = readFileChunk(targetPath, start, readableLength);

    return {
      path: targetPath,
      offset: start,
      length: chunk.length,
      totalBytes: stat.size,
      eof: start + chunk.length >= stat.size,
      base64: chunk.toString("base64")
    };
  }

  function writeBase64File(pathInput, rawBase64) {
    const targetPath = resolveTargetPath(projectRoot, pathInput);
    const normalizedBase64 = normalizeBase64Input(rawBase64);
    const binary = Buffer.from(normalizedBase64, "base64");

    if (binary.length > MAX_BASE64_BYTES) {
      throw new Error(`Binary content too large (max ${MAX_BASE64_BYTES} bytes)`);
    }

    atomicWrite(targetPath, binary);

    return {
      path: targetPath,
      sizeBytes: binary.length
    };
  }

  return {
    statPath,
    makeDirectory,
    movePath,
    copyFile,
    listDirectory,
    readTextFile,
    readTextSegment,
    readTextLines,
    writeTextFile,
    editTextFile,
    readBase64File,
    writeBase64File
  };
}

module.exports = {
  createFileService
};
