# API 文档：`files.d.ts`

`files.d.ts` 描述的是 `Tools.Files` 命名空间。它是包内最常用的文件系统工具之一，负责在 `android` 与 `linux` 两种执行环境里读写、搜索、移动和下载文件。

## 作用

当前定义覆盖：

- 目录遍历与文件读取。
- 文本 / 二进制写入。
- 删除、移动、复制、建目录。
- 文件搜索、正则检索、上下文检索。
- 获取文件信息、应用 AI diff、压缩解压、打开分享、下载。

## 基本类型

### `FileEnvironment`

```ts
type FileEnvironment = 'android' | 'linux' | `repo:${string}`
```

多数 API 都支持显式指定执行环境；默认环境以类型定义里的注释为准，通常是 `android`。

### `ApplyFileType`

```ts
type ApplyFileType = 'replace' | 'delete' | 'create'
```

用于 `Tools.Files.apply()`。

## 运行时入口

```ts
Tools.Files
```

## 主要 API

### 目录与读取

#### `list(path, environment?)`

列出目录内容，返回 `DirectoryListingData`。

#### `read(path)` / `read(options)`

```ts
read(path: string): Promise<FileContentData>
read({ path, environment?, intent?, direct_image?, direct_audio?, direct_video?, text_only? }): Promise<FileContentData>
```

第二个重载额外支持：

- `intent?`：读取意图说明。
- `direct_image?` / `direct_audio?` / `direct_video?`：显式注册对应媒体，返回媒体池链接。
  一次只传一个匹配参数；当前模型必须开启对应原生处理能力，API 协议也必须支持该输入。
- `text_only?`：拒绝非文本，不能与直接媒体读取混用。

`Tools.Files.read(options)` 委托现有 `read_file_full`；普通 Agent 使用 `read_file`，两者共用
直接媒体处理。必须将返回的 `content` 保留在工具结果中，宿主才能把媒体送入后续请求；
只返回路径、日志或“已读取”不会获得视觉/听觉输入。不要手工构造没有池内容的链接 ID。

Android 读取真实本地文件；`linux` 使用当前 Linux/SSH 文件提供者；`repo:<名称>` 使用已授权
SAF 仓库。Linux/SSH 媒体读取前后校验大小，最大 20 MiB；SAF 流读取严格限制 20 MiB。
Android 音视频沿用附件池既有容量处理，图片沿用归一化、方向与缩放规则。媒体解码和供应商
支持范围不同，HEIC/HEIF/AVIF 等仍取决于 Android 解码能力；GIF 按图片池生成静态标准图。

PDF、Office 和其他文档不是图片；使用既有文档工具提取文本，或渲染页图后逐页直接读取。
显式直接读取失败不自动改用 OCR、后端模型或元数据。模型/API 不支持或历史媒体池失效时，
请求提交前明确失败；用户可重新读取/附加文件，或明确选择其他处理方式。

```ts
const image = await Tools.Files.read({
  path: '/sdcard/Download/Kiyori/chart.png',
  environment: 'android',
  direct_image: true
});
// 将 image.content 原样纳入当前工具返回值，不能只写入 console 日志。
complete({ content: image.content });
```

#### `readPart(path, startLine?, endLine?, environment?)`

按行读取部分内容，返回 `FilePartContentData`。

#### `readBinary(path, environment?)`

读取二进制文件，返回 `BinaryFileContentData`，内容字段为 Base64。

### 写入与修改

#### `write(path, content, append?, environment?)`

写入文本，可选追加。

#### `writeBinary(path, base64Content, environment?)`

写入 Base64 编码的二进制内容。

#### `apply(path, type, old?, newContent?, environment?)`

```ts
apply(path, 'replace' | 'delete' | 'create', old?, newContent?, environment?)
```

说明：

- `replace` / `delete` 通常需要 `old` 精确匹配。
- `create` / `replace` 通常需要 `newContent`。
- 返回 `FileApplyResultData`，里面包含 `operation` 与 `aiDiffInstructions`。

#### `create(path, newContent, environment?)`

创建新文件。

- 内部等价于 `apply(path, 'create', undefined, newContent, environment)`。
- 返回 `FileApplyResultData`。

#### `edit(path, oldContent, newContent, environment?)`

编辑已存在文件。

- 内部等价于 `apply(path, 'replace', oldContent, newContent, environment)`。
- 返回 `FileApplyResultData`。

### 删除、移动、复制

#### `deleteFile(path, recursive?, environment?)`

删除文件或目录。

#### `move(source, destination, environment?)`

移动文件。

#### `copy(source, destination, recursive?, sourceEnvironment?, destEnvironment?)`

支持跨环境复制，是 `files.d.ts` 里很重要的一点。

#### `mkdir(path, create_parents?, environment?)`

创建目录。

### 搜索与信息

#### `exists(path, environment?)`

检查路径是否存在，返回 `FileExistsData`。

#### `info(path, environment?)`

获取详细文件信息，返回 `FileInfoData`。

#### `find(path, pattern, options?, environment?)`

按文件名 / 模式搜索，返回 `FindFilesResultData`。

#### `grep(path, pattern, options?)`

```ts
grep(path, pattern, {
  file_pattern?,
  case_insensitive?,
  context_lines?,
  max_results?,
  environment?
})
```

做正则级内容检索，返回 `GrepResultData`。

#### `grepContext(path, intent, options?)`

按意图做语义相关内容检索，返回 `GrepResultData`。

### 压缩、打开、分享、下载

#### `zip(source, destination, environment?, include_root_directory?)`

压缩文件或目录。

- `include_root_directory` 仅在 `source` 为目录时生效。
- 默认 `true`：压缩包内会保留源目录名作为顶层目录。
- 传 `false`：只压缩目录内容本身，不额外套一层顶层目录。

#### `unzip(source, destination, environment?)`

解压归档文件。

#### `open(path, environment?)`

调用系统处理器打开文件。

#### `share(path, title?, environment?)`

分享文件给其他应用。

#### `download(url, destination, environment?, headers?)`

从 URL 下载文件。

#### `download(options)`

```ts
download({
  url?,
  visit_key?,
  link_number?,
  image_number?,
  destination,
  environment?,
  headers?
})
```

这个重载说明下载不仅可以直接给 URL，也可以配合 `visit_web` 结果里的 `visit_key` 与链接序号继续下载。

## 示例

### 读取文本文件

```ts
const file = await Tools.Files.read({
  path: '/sdcard/notes/todo.txt',
  environment: 'android'
});
console.log(file.content);
```

### 读取部分行号

```ts
const part = await Tools.Files.readPart('/sdcard/app.log', 1, 80);
console.log(part.content);
```

### 跨环境复制

```ts
await Tools.Files.copy(
  '/sdcard/input.txt',
  '/tmp/input.txt',
  false,
  'android',
  'linux'
);
```

### 搜索代码

```ts
const matches = await Tools.Files.grep('/workspace', 'toolCall\\(', {
  file_pattern: '*.ts',
  context_lines: 2,
  max_results: 20,
  environment: 'linux'
});
```

### 应用替换补丁

```ts
await Tools.Files.apply(
  '/sdcard/demo.txt',
  'replace',
  'old text',
  'new text'
);
```

## 返回值

本文件主要使用以下结果类型：

- `DirectoryListingData`
- `FileContentData`
- `BinaryFileContentData`
- `FilePartContentData`
- `FileOperationData`
- `FileExistsData`
- `FindFilesResultData`
- `FileInfoData`
- `FileApplyResultData`
- `GrepResultData`

## 相关文件

- `examples/types/files.d.ts`
- `examples/types/results.d.ts`
- `docs/doc-src/package-dev/results.md`
