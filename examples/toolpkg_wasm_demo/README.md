# ToolPkg WASM 示例

本示例通过计算质数演示 TypeScript 与 AssemblyScript WASM 的分层：

- `src/main.ts`：ToolPkg 作者入口与公共导出。
- `src/wasm/core.ts`：供主入口使用的强类型 TypeScript 接口。
- `src/wasm/core.as.ts`：由 AssemblyScript 编译的计算逻辑。
- `manifest.json`：声明 `wasm_modules`。
- `main.js` 与 `modules/core.wasm`：本地生成后一起打包。
- `src/wasm/assemblyscript-env.d.ts`：仅服务编辑器 TypeScript 诊断，不定义运行时 ABI。

## 作者入口

使用普通 TypeScript import：

```ts
import { nthPrime } from "./wasm/core";

export async function nth_prime(params: { index: number }) {
  return { prime: await nthPrime(params.index) };
}
```

## 构建与打包

使用 Node.js 22 或以上版本，在本目录执行：

```bash
npm ci
npm run pack:toolpkg
```

命令生成 `dist/toolpkg_wasm_demo.toolpkg`。`main.js`、`modules/core.wasm` 和最终包均为本地产物，不提交到 Git。
