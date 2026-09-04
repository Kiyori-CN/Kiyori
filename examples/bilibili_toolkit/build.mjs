import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import esbuild from "esbuild";

const projectDir = path.dirname(fileURLToPath(import.meta.url));
const tsconfig = path.join(projectDir, "tsconfig.json");
const distDir = path.join(projectDir, "dist");

function metadataBanner(sourcePath) {
  const source = fs.readFileSync(sourcePath, "utf8");
  const match = source.match(/\/\*\s*METADATA[\s\S]*?\*\//);
  if (!match) {
    throw new Error("Bilibili package entry is missing its METADATA banner.");
  }
  return match[0];
}

async function bundle(entryPoint, outfile, banner) {
  await esbuild.build({
    absWorkingDir: projectDir,
    entryPoints: [entryPoint],
    outfile,
    bundle: true,
    format: "cjs",
    platform: "neutral",
    target: ["es2020"],
    tsconfig,
    charset: "utf8",
    legalComments: "none",
    minify: true,
    sourcemap: false,
    banner: banner ? { js: banner } : undefined,
    logLevel: "info"
  });
}

async function main() {
  fs.rmSync(distDir, { recursive: true, force: true });
  fs.mkdirSync(path.join(distDir, "packages"), { recursive: true });
  const packageEntry = path.join(projectDir, "src", "packages", "bilibili.ts");
  await bundle(
    path.join(projectDir, "src", "main.ts"),
    path.join(distDir, "main.js")
  );
  await bundle(
    packageEntry,
    path.join(distDir, "packages", "bilibili.js"),
    metadataBanner(packageEntry)
  );
}

main().catch((error) => {
  console.error(error instanceof Error ? error.stack : String(error));
  process.exitCode = 1;
});
