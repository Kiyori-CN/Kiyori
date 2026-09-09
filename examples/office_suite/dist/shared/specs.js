"use strict";
/**
 * 由 spec/tools.json 生成，请勿手改；修改后执行
 *   python -B examples/office_suite/scripts/generate_tool_sources.py
 */
Object.defineProperty(exports, "__esModule", { value: true });
exports.OFFICE_TOOLS = void 0;
exports.toolNames = toolNames;
exports.OFFICE_TOOLS = {
    "office_workflow_guide": {
        meta: {
            name: "office_workflow_guide",
            zh: "\u529e\u516c\u6587\u6863\u5f3a\u5236\u5de5\u4f5c\u6d41\uff08\u4ec5\u63d0\u793a\uff0c\u65e0\u9700\u5b9e\u73b0\uff09\uff1a\u5148 office_env_check\uff0c\u518d\u8bfb\u5bf9\u5e94 Skill\uff1b\u5df2\u6709\u6587\u4ef6\u5148 outline/read \u53d6\u951a\u70b9\uff1b\u5199\u516c\u5f0f\u7684 xlsx \u5fc5\u987b xlsx_recalc\uff1b\u4ea4\u4ed8\u524d office_validate + office_render_preview \u5e76\u7528 direct_image \u770b\u56fe\uff1b\u4e0d\u539f\u5730\u8986\u76d6\u7528\u6237\u6e90\u6587\u4ef6\uff1bE_ENV_MISSING \u65f6\u4e0d\u5f97\u6539\u7528\u300c\u5dee\u4e0d\u591a\u300d\u7684\u66ff\u4ee3\u65b9\u6848\u3002",
            en: "Mandatory office workflow (advice only): office_env_check first, then read the matching Skill; outline/read existing files before editing; formulas require xlsx_recalc; before delivery run office_validate + office_render_preview and inspect pages with direct_image; never overwrite user sources; never substitute an approximate engine after E_ENV_MISSING.",
            advice: true,
            params: []
        },
        spec: {
            command: "",
            defaultOutputName: "",
            timeoutMs: 300000,
        }
    },
    "office_env_check": {
        meta: {
            name: "office_env_check",
            zh: "\u63a2\u6d4b T1-T4 \u7ec4\u4ef6\u3001\u7248\u672c\u3001CJK \u5b57\u4f53\u4e0e\u78c1\u76d8\u4f59\u91cf\uff1b\u7f3a\u5931\u7ec4\u4ef6\u6309 remedy \u5904\u7406\u3002",
            en: "Probe T1-T4 components, versions, CJK fonts, and free disk; follow remedy for missing parts.",
            params: [
                { name: "verbose", zh: "\u662f\u5426\u8fd4\u56de PATH \u4e0e\u7ec4\u4ef6\u6e05\u5355", en: "Return PATH and component list", type: "boolean", required: false },
            ]
        },
        spec: {
            command: "office_env_check",
            params: ["verbose"],
            defaultOutputName: "office_env.json",
            requiresEnv: false,
            timeoutMs: 300000,
        }
    },
    "office_env_setup": {
        meta: {
            name: "office_env_setup",
            zh: "\u8fd4\u56de\u5b89\u88c5\u8ba1\u5212\uff1bconfirm=true \u65f6\u7528\u53ef\u89c1\u7ec8\u7aef\u6d41\u5f0f\u6267\u884c\uff0c\u5931\u8d25\u4fdd\u7559\u73b0\u573a\u4e0d\u56de\u6eda\u3002",
            en: "Return an install plan; with confirm=true execute it in a visible terminal, keeping failures in place.",
            params: [
                { name: "tier", zh: "\u73af\u5883\u5206\u5c42 1-4\uff0c\u9ed8\u8ba4 1", en: "Tier 1-4; default 1", type: "number", required: false },
                { name: "components", zh: "\u53ea\u5b89\u88c5\u6307\u5b9a\u7ec4\u4ef6", en: "Install only these components", type: "array", required: false },
                { name: "confirm", zh: "\u662f\u5426\u786e\u8ba4\u6267\u884c", en: "Confirm execution", type: "boolean", required: false },
                { name: "visible", zh: "\u662f\u5426\u4f7f\u7528\u53ef\u89c1\u7ec8\u7aef", en: "Use a visible terminal", type: "boolean", required: false },
                { name: "timeout_ms", zh: "\u5b89\u88c5\u8d85\u65f6\u6beb\u79d2\uff0c\u9ed8\u8ba4 600000", en: "Install timeout in ms; default 600000", type: "number", required: false },
            ]
        },
        spec: {
            command: "office_env_setup",
            params: ["tier", "components", "confirm", "visible", "timeout_ms"],
            defaultOutputName: "office_env_plan.json",
            requiresEnv: false,
            timeoutMs: 600000,
        }
    },
    "office_read": {
        meta: {
            name: "office_read",
            zh: "\u7edf\u4e00\u8bfb\u53d6 docx/xlsx/pptx/pdf/csv/md/html/txt\uff1bmode=outline \u8fd4\u56de\u5bfc\u822a\uff0c\u7981\u6b62\u4e00\u6b21\u8bfb\u7206\u4e0a\u4e0b\u6587\u3002",
            en: "Read docx/xlsx/pptx/pdf/csv/md/html/txt uniformly; mode=outline returns navigation to protect context.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "mode", zh: "outline/full/range", en: "outline/full/range", type: "string", required: false },
                { name: "range", zh: "\u9875\u7801\u8303\u56f4\uff0c\u5982 1-3,7", en: "Page range such as 1-3,7", type: "string", required: false },
                { name: "max_chars", zh: "\u8f93\u51fa\u5b57\u7b26\u9884\u7b97\uff0c\u9ed8\u8ba4 20000", en: "Character budget; default 20000", type: "number", required: false },
                { name: "max_rows", zh: "\u8868\u683c\u6700\u5927\u884c\u6570", en: "Max table rows", type: "number", required: false },
                { name: "sheet_name", zh: "\u5de5\u4f5c\u8868\u540d", en: "Sheet name", type: "string", required: false },
                { name: "layout", zh: "PDF \u662f\u5426\u4fdd\u7559\u7248\u9762\uff08\u9700\u8981 pdfplumber\uff09", en: "Keep PDF layout (requires pdfplumber)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
                { name: "with_anchors", zh: "\u662f\u5426\u8fd4\u56de\u5bfc\u822a\u951a\u70b9\uff0c\u9ed8\u8ba4 true", en: "Return navigation anchors; default true", type: "boolean", required: false },
            ]
        },
        spec: {
            command: "office_read",
            params: ["path", "env", "mode", "range", "max_chars", "max_rows", "sheet_name", "layout", "task_id", "with_anchors"],
            inputPaths: ["path"],
            defaultOutputName: "read.txt",
            timeoutMs: 300000,
        }
    },
    "office_convert": {
        meta: {
            name: "office_convert",
            zh: "\u683c\u5f0f\u8f6c\u6362\uff1bengine \u5fc5\u987b\u663e\u5f0f\u6307\u5b9a libreoffice \u6216 pandoc\uff0c\u7f3a\u5931\u65f6\u660e\u786e\u62a5\u9519\u4e0d\u5207\u6362\u5f15\u64ce\u3002",
            en: "Convert formats; engine must be explicitly libreoffice or pandoc; missing engines fail instead of switching.",
            params: [
                { name: "from_path", zh: "\u6e90\u6587\u4ef6\u8def\u5f84", en: "Source file path", type: "string", required: true },
                { name: "to_format", zh: "\u76ee\u6807\u683c\u5f0f\uff0c\u5982 pdf/docx", en: "Target format such as pdf/docx", type: "string", required: true },
                { name: "engine", zh: "libreoffice \u6216 pandoc", en: "libreoffice or pandoc", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "options", zh: "\u989d\u5916\u5f15\u64ce\u53c2\u6570\u6570\u7ec4", en: "Extra engine options", type: "array", required: false },
                { name: "cjk_font", zh: "CJK \u5b57\u4f53\u65cf", en: "CJK font family", type: "string", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "timeout_ms", zh: "\u8f6c\u6362\u8d85\u65f6\u6beb\u79d2\uff0c\u9ed8\u8ba4 600000", en: "Conversion timeout in ms; default 600000", type: "number", required: false },
            ]
        },
        spec: {
            command: "office_convert",
            params: ["from_path", "to_format", "engine", "env", "output_path", "output_env", "overwrite", "options", "cjk_font", "task_id", "in_place", "timeout_ms"],
            defaultOutputName: "converted.bin",
            timeoutMs: 600000,
        }
    },
    "office_render_preview": {
        meta: {
            name: "office_render_preview",
            zh: "\u4ea7\u7269 \u2192 PDF \u2192 \u5206\u9875 JPEG\uff0c\u8fd4\u56de Android \u8def\u5f84\uff1b\u5fc5\u987b\u518d\u7528 direct_image \u9010\u9875\u770b\u56fe\u3002",
            en: "Render output to PDF then per-page JPEG and return Android paths; inspect every page with direct_image.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "pages", zh: "\u9875\u7801\u8303\u56f4\uff0c\u5982 1-3", en: "Page range such as 1-3", type: "string", required: false },
                { name: "dpi", zh: "\u6e32\u67d3 DPI\uff0c\u9ed8\u8ba4 150", en: "Render DPI; default 150", type: "number", required: false },
                { name: "max_pages", zh: "\u6700\u591a\u6e32\u67d3\u9875\u6570\uff0c\u9ed8\u8ba4 8", en: "Max pages; default 8", type: "number", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_render_preview",
            params: ["path", "env", "output_env", "pages", "dpi", "max_pages", "task_id"],
            inputPaths: ["path"],
            outputKind: "multi",
            defaultOutputName: "preview",
            timeoutMs: 600000,
        }
    },
    "office_validate": {
        meta: {
            name: "office_validate",
            zh: "\u6821\u9a8c OOXML \u5173\u7cfb/\u5185\u5bb9\u7c7b\u578b/\u5a92\u4f53\u5f15\u7528/\u516c\u5f0f\u7f13\u5b58/pptx sldIdLst/PDF \u7ed3\u6784\uff1bstrict=true \u6709\u95ee\u9898\u76f4\u63a5\u5931\u8d25\u3002",
            en: "Validate OOXML relationships, content types, media, formula caches, pptx sldIdLst, and PDF structure; strict=true fails on issues.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "original_path", zh: "\u6a21\u677f\u6d3e\u751f\u573a\u666f\u7684\u57fa\u7ebf\u6587\u4ef6", en: "Baseline file for template-derived output", type: "string", required: false },
                { name: "strict", zh: "\u6709\u95ee\u9898\u65f6\u8fd4\u56de E_VALIDATION_FAILED", en: "Fail with E_VALIDATION_FAILED on issues", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_validate",
            params: ["path", "env", "original_path", "strict", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "validate.json",
            timeoutMs: 300000,
        }
    },
    "office_diff": {
        meta: {
            name: "office_diff",
            zh: "\u4e24\u4e2a\u6587\u6863\u8f6c\u6587\u672c\u540e\u7684\u5dee\u5f02\u5bf9\u6bd4\u3002",
            en: "Text-level diff between two documents.",
            params: [
                { name: "left", zh: "\u5de6\u4fa7\u6587\u4ef6", en: "Left file", type: "string", required: true },
                { name: "right", zh: "\u53f3\u4fa7\u6587\u4ef6", en: "Right file", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "max_chars", zh: "\u8f93\u51fa\u9884\u7b97", en: "Character budget", type: "number", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_diff",
            params: ["left", "right", "env", "max_chars", "task_id"],
            defaultOutputName: "diff.txt",
            timeoutMs: 300000,
        }
    },
    "office_workspace_init": {
        meta: {
            name: "office_workspace_init",
            zh: "\u521b\u5efa source/output/templates/assets \u5de5\u4f5c\u533a\u9aa8\u67b6\u4e0e AGENTS.md \u89c4\u5219\u7247\u6bb5\u3002",
            en: "Create the source/output/templates/assets workspace skeleton plus an AGENTS.md rule snippet.",
            params: [
                { name: "dir", zh: "\u5de5\u4f5c\u533a\u76ee\u5f55", en: "Workspace directory", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
            ]
        },
        spec: {
            command: "office_workspace_init",
            params: ["dir", "env", "output_env", "overwrite"],
            defaultOutputName: "workspace.json",
            timeoutMs: 300000,
        }
    },
    "office_workspace_clean": {
        meta: {
            name: "office_workspace_clean",
            zh: "\u6e05\u7406\u6307\u5b9a task_id \u7684 Linux \u6682\u5b58\u533a\u3002",
            en: "Clean the Linux staging directory for a task_id.",
            params: [
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_workspace_clean",
            params: ["task_id"],
            defaultOutputName: "clean.json",
            requiresEnv: false,
            timeoutMs: 300000,
        }
    },
    "docx_outline": {
        meta: {
            name: "docx_outline",
            zh: "\u8fd4\u56de\u6bb5\u843d\u7d22\u5f15/\u6837\u5f0f/\u5c42\u7ea7/\u8868\u683c\u5750\u6807/\u7ae0\u8282/\u56fe\u7247\uff1b\u6240\u6709 DOCX \u7f16\u8f91\u7684\u524d\u7f6e\u6b65\u9aa4\u3002",
            en: "Return paragraph indices, styles, levels, table coordinates, sections, and images; prerequisite for any DOCX edit.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "max_items", zh: "\u6700\u591a\u8fd4\u56de\u6bb5\u843d\u6570", en: "Max paragraphs", type: "number", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_outline",
            params: ["path", "env", "max_items", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "outline.json",
            timeoutMs: 300000,
        }
    },
    "docx_create": {
        meta: {
            name: "docx_create",
            zh: "\u4ece\u7ed3\u6784\u5316 spec \u6216 Markdown \u751f\u6210 DOCX\uff08python-docx\uff09\u3002",
            en: "Create a DOCX from a structured spec or Markdown (python-docx).",
            params: [
                { name: "spec", zh: "\u7ed3\u6784\u5316 blocks \u6570\u7ec4", en: "Structured blocks array", type: "object", required: false },
                { name: "markdown", zh: "Markdown \u6587\u672c", en: "Markdown text", type: "string", required: false },
                { name: "file_name", zh: "\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_create",
            params: ["spec", "markdown", "file_name", "output_path", "env", "output_env", "overwrite", "task_id"],
            defaultOutputName: "document.docx",
            timeoutMs: 300000,
        }
    },
    "docx_from_template": {
        meta: {
            name: "docx_from_template",
            zh: "{{\u53d8\u91cf}} \u6a21\u677f\u586b\u5145\uff08\u4e1a\u52a1\u6587\u6863\u9996\u9009\u8def\u5f84\uff09\uff1bstrict=true \u65f6\u7f3a\u5931\u53d8\u91cf\u76f4\u63a5\u5931\u8d25\u3002",
            en: "Fill {{variable}} placeholders (preferred for business documents); strict=true fails on missing variables.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "variables", zh: "\u53d8\u91cf\u5bf9\u8c61", en: "Variable object", type: "object", required: true },
                { name: "strict", zh: "\u7f3a\u5931\u53d8\u91cf\u65f6\u5931\u8d25", en: "Fail on missing variables", type: "boolean", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_from_template",
            params: ["path", "env", "variables", "strict", "output_path", "output_env", "overwrite", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "filled.docx",
            timeoutMs: 300000,
        }
    },
    "docx_edit": {
        meta: {
            name: "docx_edit",
            zh: "\u57fa\u4e8e\u951a\u70b9\u7684 replace/insert_before/insert_after/delete\uff1b\u5fc5\u987b\u5148 docx_outline \u53d6\u951a\u70b9\u3002",
            en: "Anchor-based replace/insert_before/insert_after/delete; call docx_outline first.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "anchor", zh: "{index} \u6216 {text}", en: "{index} or {text}", type: "object", required: true },
                { name: "operation", zh: "replace/insert_before/insert_after/delete", en: "replace/insert_before/insert_after/delete", type: "string", required: true },
                { name: "text", zh: "\u5199\u5165\u6587\u672c", en: "Text to write", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_edit",
            params: ["path", "env", "anchor", "operation", "text", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "edited.docx",
            timeoutMs: 300000,
        }
    },
    "docx_find_replace": {
        meta: {
            name: "docx_find_replace",
            zh: "\u8de8 run \u5408\u5e76\u540e\u7684\u67e5\u627e\u66ff\u6362\uff0c\u4fdd\u7559\u683c\u5f0f\uff1b\u652f\u6301\u6b63\u5219\u4e0e\u8868\u683c\u8303\u56f4\u3002",
            en: "Find/replace after merging runs, preserving formatting; supports regex and table scope.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "find", zh: "\u67e5\u627e\u5185\u5bb9", en: "Find text", type: "string", required: true },
                { name: "replace", zh: "\u66ff\u6362\u5185\u5bb9", en: "Replacement", type: "string", required: false },
                { name: "use_regex", zh: "\u6309\u6b63\u5219\u89e3\u91ca find", en: "Treat find as regex", type: "boolean", required: false },
                { name: "ignore_case", zh: "\u5ffd\u7565\u5927\u5c0f\u5199", en: "Ignore case", type: "boolean", required: false },
                { name: "scope", zh: "all/paragraphs/tables", en: "all/paragraphs/tables", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_find_replace",
            params: ["path", "env", "find", "replace", "use_regex", "ignore_case", "scope", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "replaced.docx",
            timeoutMs: 300000,
        }
    },
    "docx_table": {
        meta: {
            name: "docx_table",
            zh: "\u8ffd\u52a0\u8868\u683c\uff1b\u5217\u5bbd\u4e0e\u5355\u5143\u683c\u5bbd\u5ea6\u540c\u5355\u4f4d\u540c\u65f6\u8bbe\u7f6e\u3002",
            en: "Append a table; column and cell widths are set together in the same unit.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "rows", zh: "\u4e8c\u7ef4\u6570\u7ec4", en: "2-D array", type: "array", required: true },
                { name: "header", zh: "\u9996\u884c\u662f\u5426\u4e3a\u8868\u5934", en: "Whether the first row is a header", type: "boolean", required: false },
                { name: "style", zh: "\u8868\u683c\u6837\u5f0f\u540d", en: "Table style name", type: "string", required: false },
                { name: "column_widths_cm", zh: "\u5217\u5bbd\u6570\u7ec4\uff08cm\uff09", en: "Column widths in cm", type: "array", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_table",
            params: ["path", "env", "rows", "header", "style", "column_widths_cm", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "table.docx",
            timeoutMs: 300000,
        }
    },
    "docx_insert_image": {
        meta: {
            name: "docx_insert_image",
            zh: "\u63d2\u5165\u56fe\u7247\uff0c\u81ea\u52a8\u6309\u9875\u5bbd\u7ea6\u675f\u3002",
            en: "Insert an image, constrained to the page width.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "image_path", zh: "\u56fe\u7247\u8def\u5f84", en: "Image path", type: "string", required: true },
                { name: "width_cm", zh: "\u5bbd\u5ea6\uff08cm\uff09", en: "Width in cm", type: "number", required: false },
                { name: "alignment", zh: "left/center/right", en: "left/center/right", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_insert_image",
            params: ["path", "env", "image_path", "width_cm", "alignment", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path", "image_path"],
            defaultOutputName: "image.docx",
            timeoutMs: 300000,
        }
    },
    "docx_style": {
        meta: {
            name: "docx_style",
            zh: "\u9875\u9762\u5c3a\u5bf8/\u9875\u8fb9\u8ddd/\u9ed8\u8ba4\u5b57\u4f53/\u6bb5\u843d\u6837\u5f0f\u3002",
            en: "Page size, margins, default font, and paragraph styles.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "margins_cm", zh: "{top,bottom,left,right}", en: "{top,bottom,left,right}", type: "object", required: false },
                { name: "default_font", zh: "{name,size_pt}", en: "{name,size_pt}", type: "object", required: false },
                { name: "paragraph_styles", zh: "\u6837\u5f0f\u540d\u5230\u914d\u7f6e\u7684\u6620\u5c04", en: "Style name to config map", type: "object", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_style",
            params: ["path", "env", "margins_cm", "default_font", "paragraph_styles", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "styled.docx",
            timeoutMs: 300000,
        }
    },
    "docx_merge": {
        meta: {
            name: "docx_merge",
            zh: "\u5408\u5e76\u591a\u4e2a\u6587\u6863\uff1bstyle_mode \u663e\u5f0f\u6307\u5b9a preserve \u6216 unified\u3002",
            en: "Merge documents; style_mode must be explicitly preserve or unified.",
            params: [
                { name: "paths", zh: "\u81f3\u5c11\u4e24\u4e2a\u6587\u4ef6\u8def\u5f84", en: "At least two file paths", type: "array", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "style_mode", zh: "preserve/unified", en: "preserve/unified", type: "string", required: false },
                { name: "file_name", zh: "\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_merge",
            params: ["paths", "env", "style_mode", "file_name", "output_path", "output_env", "overwrite", "task_id"],
            inputPaths: ["paths"],
            defaultOutputName: "merged.docx",
            timeoutMs: 300000,
        }
    },
    "docx_extract_media": {
        meta: {
            name: "docx_extract_media",
            zh: "\u5bfc\u51fa\u5185\u5d4c\u56fe\u7247\u5230\u4ea4\u4ed8\u76ee\u5f55\u3002",
            en: "Export embedded images to the delivery directory.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "target_dir_name", zh: "\u8f93\u51fa\u76ee\u5f55\u540d", en: "Output directory name", type: "string", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_extract_media",
            params: ["path", "env", "output_env", "target_dir_name", "task_id"],
            inputPaths: ["path"],
            outputKind: "multi",
            defaultOutputName: "media",
            timeoutMs: 300000,
        }
    },
    "xlsx_info": {
        meta: {
            name: "xlsx_info",
            zh: "\u5de5\u4f5c\u8868\u5217\u8868\u3001\u4f7f\u7528\u8303\u56f4\u3001\u547d\u540d\u533a\u57df\u3001\u5916\u90e8\u94fe\u63a5\u3001\u662f\u5426\u542b\u5b8f\u3002",
            en: "Sheet list, used ranges, named ranges, external links, and macro presence.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_info",
            params: ["path", "env", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "info.json",
            timeoutMs: 300000,
        }
    },
    "xlsx_read": {
        meta: {
            name: "xlsx_read",
            zh: "\u6309\u8303\u56f4\u8bfb\uff0c\u540c\u65f6\u8fd4\u56de\u516c\u5f0f\u4e0e\u7f13\u5b58\u503c\uff08\u4e24\u6b21 load\uff09\u3002",
            en: "Read a range and return both formulas and cached values (two loads).",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "sheet_name", zh: "\u5de5\u4f5c\u8868\u540d", en: "Sheet name", type: "string", required: false },
                { name: "range", zh: "\u5355\u5143\u683c\u8303\u56f4\uff0c\u5982 A1:C10", en: "Cell range such as A1:C10", type: "string", required: false },
                { name: "max_rows", zh: "\u6700\u5927\u884c\u6570", en: "Max rows", type: "number", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_read",
            params: ["path", "env", "sheet_name", "range", "max_rows", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "read.json",
            timeoutMs: 300000,
        }
    },
    "xlsx_write": {
        meta: {
            name: "xlsx_write",
            zh: "\u6279\u91cf\u5199\u5355\u5143\u683c/\u533a\u57df\uff1b\u5199\u5165\u516c\u5f0f\u540e\u5fc5\u987b\u8c03\u7528 xlsx_recalc\uff08\u6ea2\u51fa\u6570\u7ec4\u51fd\u6570\u4f1a\u88ab\u62d2\u7edd\uff09\u3002",
            en: "Write cells/ranges in bulk; formulas require xlsx_recalc afterwards (spill functions are rejected).",
            params: [
                { name: "path", zh: "\u5df2\u6709\u5de5\u4f5c\u7c3f\u8def\u5f84\uff1b\u7701\u7565\u65f6\u65b0\u5efa\u5de5\u4f5c\u7c3f", en: "Existing workbook path; omitted creates a new workbook", type: "string", required: false },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "sheet_name", zh: "\u5de5\u4f5c\u8868\u540d", en: "Sheet name", type: "string", required: false },
                { name: "cells", zh: "[{cell,value|formula}]", en: "[{cell,value|formula}]", type: "array", required: false },
                { name: "rows", zh: "\u4e8c\u7ef4\u6570\u7ec4", en: "2-D array", type: "array", required: false },
                { name: "start_cell", zh: "rows \u8d77\u70b9\uff0c\u9ed8\u8ba4 A1", en: "Start cell for rows; default A1", type: "string", required: false },
                { name: "file_name", zh: "\u65b0\u5efa\u5de5\u4f5c\u7c3f\u7684\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name for a new workbook", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_write",
            params: ["path", "env", "sheet_name", "cells", "rows", "start_cell", "file_name", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "workbook.xlsx",
            timeoutMs: 300000,
        }
    },
    "xlsx_format": {
        meta: {
            name: "xlsx_format",
            zh: "\u6570\u5b57\u683c\u5f0f\u3001\u5b57\u4f53\u3001\u586b\u5145\u3001\u5217\u5bbd\u884c\u9ad8\u3001\u51bb\u7ed3\u7a97\u683c\u3001\u81ea\u52a8\u7b5b\u9009\u3002",
            en: "Number formats, fonts, fills, widths/heights, freeze panes, and auto-filter.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "sheet_name", zh: "\u5de5\u4f5c\u8868\u540d", en: "Sheet name", type: "string", required: false },
                { name: "number_format", zh: "\u6570\u5b57\u683c\u5f0f\u4e32", en: "Number format string", type: "string", required: false },
                { name: "font", zh: "{name,size,bold,italic,color}", en: "{name,size,bold,italic,color}", type: "object", required: false },
                { name: "fill", zh: "{type,color}", en: "{type,color}", type: "object", required: false },
                { name: "column_widths", zh: "{\u5217:\u5bbd\u5ea6}", en: "{column: width}", type: "object", required: false },
                { name: "row_heights", zh: "{\u884c:\u9ad8\u5ea6}", en: "{row: height}", type: "object", required: false },
                { name: "freeze_panes", zh: "\u51bb\u7ed3\u7a97\u683c\u5355\u5143\u683c", en: "Freeze panes cell", type: "string", required: false },
                { name: "auto_filter", zh: "\u81ea\u52a8\u7b5b\u9009\u8303\u56f4", en: "Auto-filter range", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_format",
            params: ["path", "env", "sheet_name", "number_format", "font", "fill", "column_widths", "row_heights", "freeze_panes", "auto_filter", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "formatted.xlsx",
            timeoutMs: 300000,
        }
    },
    "xlsx_sheet": {
        meta: {
            name: "xlsx_sheet",
            zh: "\u589e/\u5220/\u6539\u540d/\u6392\u5e8f/\u590d\u5236\u5de5\u4f5c\u8868\u3002",
            en: "Create, delete, rename, move, or copy sheets.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "operation", zh: "create/delete/rename/move/copy", en: "create/delete/rename/move/copy", type: "string", required: true },
                { name: "name", zh: "\u76ee\u6807\u5de5\u4f5c\u8868\u540d", en: "Target sheet name", type: "string", required: false },
                { name: "new_name", zh: "\u65b0\u540d\u79f0", en: "New name", type: "string", required: false },
                { name: "index", zh: "\u4f4d\u7f6e\u7d22\u5f15", en: "Position index", type: "number", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_sheet",
            params: ["path", "env", "operation", "name", "new_name", "index", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "sheets.xlsx",
            timeoutMs: 300000,
        }
    },
    "xlsx_recalc": {
        meta: {
            name: "xlsx_recalc",
            zh: "LibreOffice \u91cd\u7b97\u5e76\u56de\u5199\uff1btotal_errors \u5fc5\u987b\u4e3a 0 \u624d\u53ef\u4ea4\u4ed8\u3002",
            en: "Recalculate with LibreOffice and write back; total_errors must be 0 before delivery.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_recalc",
            params: ["path", "env", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "recalculated.xlsx",
            timeoutMs: 600000,
        }
    },
    "xlsx_table": {
        meta: {
            name: "xlsx_table",
            zh: "\u628a\u533a\u57df\u8f6c\u6210 Excel Table \u5e76\u8bbe\u7f6e\u6837\u5f0f\u3002",
            en: "Convert a range into an Excel Table with a style.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "sheet_name", zh: "\u5de5\u4f5c\u8868\u540d", en: "Sheet name", type: "string", required: false },
                { name: "range", zh: "\u8868\u683c\u8303\u56f4", en: "Table range", type: "string", required: false },
                { name: "table_name", zh: "\u8868\u540d", en: "Table name", type: "string", required: false },
                { name: "style", zh: "\u8868\u683c\u6837\u5f0f", en: "Table style", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_table",
            params: ["path", "env", "sheet_name", "range", "table_name", "style", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "table.xlsx",
            timeoutMs: 300000,
        }
    },
    "pptx_outline": {
        meta: {
            name: "pptx_outline",
            zh: "\u5e7b\u706f\u7247/\u5f62\u72b6/\u5360\u4f4d\u7b26/\u5750\u6807/\u6587\u672c/\u6bcd\u7248\u4e0e\u7248\u5f0f\u3002",
            en: "Slides, shapes, placeholders, coordinates, text, masters, and layouts.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "max_slides", zh: "\u6700\u591a\u8fd4\u56de\u9875\u6570", en: "Max slides", type: "number", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_outline",
            params: ["path", "env", "max_slides", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "outline.json",
            timeoutMs: 300000,
        }
    },
    "pptx_create": {
        meta: {
            name: "pptx_create",
            zh: "\u4ece\u5927\u7eb2\u751f\u6210 PPTX\uff1b\u53ef\u57fa\u4e8e\u6a21\u677f\u7248\u5f0f\u3002",
            en: "Create a PPTX from an outline, optionally based on a template.",
            params: [
                { name: "slides", zh: "[{title,bullets,layout_index}]", en: "[{title,bullets,layout_index}]", type: "array", required: true },
                { name: "template_path", zh: "\u6a21\u677f\u8def\u5f84", en: "Template path", type: "string", required: false },
                { name: "layout_index", zh: "\u9ed8\u8ba4\u7248\u5f0f\u7d22\u5f15", en: "Default layout index", type: "number", required: false },
                { name: "file_name", zh: "\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_create",
            params: ["slides", "template_path", "layout_index", "file_name", "output_path", "env", "output_env", "overwrite", "task_id"],
            defaultOutputName: "presentation.pptx",
            timeoutMs: 300000,
        }
    },
    "pptx_template_fill": {
        meta: {
            name: "pptx_template_fill",
            zh: "\u57fa\u4e8e\u6a21\u677f\u586b\u5145 {{\u53d8\u91cf}}\uff0c\u4fdd\u7559\u6a21\u677f\u8bbe\u8ba1\u3002",
            en: "Fill {{variables}} in a template while preserving its design.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "variables", zh: "\u53d8\u91cf\u5bf9\u8c61", en: "Variable object", type: "object", required: true },
                { name: "strict", zh: "\u7f3a\u5931\u53d8\u91cf\u65f6\u5931\u8d25", en: "Fail on missing variables", type: "boolean", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_template_fill",
            params: ["path", "env", "variables", "strict", "output_path", "output_env", "overwrite", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "filled.pptx",
            timeoutMs: 300000,
        }
    },
    "pptx_slide": {
        meta: {
            name: "pptx_slide",
            zh: "\u589e/\u5220/\u590d\u5236/\u91cd\u6392\u5e7b\u706f\u7247\uff0c\u7ef4\u62a4\u5173\u7cfb\u4e0e <p:sldIdLst>\u3002\u7ed3\u6784\u6027\u64cd\u4f5c\u5fc5\u987b\u5728\u5185\u5bb9\u7f16\u8f91\u4e4b\u524d\u5b8c\u6210\u3002",
            en: "Add/delete/duplicate/reorder slides while maintaining rels and <p:sldIdLst>. Finish structural changes before editing content.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "operation", zh: "add/delete/duplicate/move", en: "add/delete/duplicate/move", type: "string", required: true },
                { name: "index", zh: "0-based \u9875\u7d22\u5f15", en: "0-based slide index", type: "number", required: false },
                { name: "target_index", zh: "move \u76ee\u6807\u4f4d\u7f6e", en: "Target index for move", type: "number", required: false },
                { name: "layout_index", zh: "add \u4f7f\u7528\u7684\u7248\u5f0f", en: "Layout for add", type: "number", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_slide",
            params: ["path", "env", "operation", "index", "target_index", "layout_index", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "slides.pptx",
            timeoutMs: 300000,
        }
    },
    "pptx_edit": {
        meta: {
            name: "pptx_edit",
            zh: "\u7f16\u8f91\u6307\u5b9a\u5f62\u72b6\u7684\u6587\u672c/\u4f4d\u7f6e/\u5c3a\u5bf8/\u5b57\u4f53\uff1b\u5148 pptx_outline \u53d6\u951a\u70b9\u3002",
            en: "Edit a shape's text/position/size/font; call pptx_outline first.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "slide_index", zh: "0-based \u9875\u7d22\u5f15", en: "0-based slide index", type: "number", required: true },
                { name: "shape_index", zh: "\u5f62\u72b6\u7d22\u5f15", en: "Shape index", type: "number", required: false },
                { name: "shape_name", zh: "\u5f62\u72b6\u540d", en: "Shape name", type: "string", required: false },
                { name: "operation", zh: "set_text/append_text/set_position/set_size/set_font", en: "set_text/append_text/set_position/set_size/set_font", type: "string", required: false },
                { name: "text", zh: "\u6587\u672c", en: "Text", type: "string", required: false },
                { name: "size_pt", zh: "\u5b57\u53f7", en: "Font size", type: "number", required: false },
                { name: "bold", zh: "\u662f\u5426\u52a0\u7c97", en: "Bold", type: "boolean", required: false },
                { name: "color_rgb", zh: "RGB \u989c\u8272", en: "RGB color", type: "string", required: false },
                { name: "left_emu", zh: "\u5de6\u8fb9\u8ddd EMU", en: "Left EMU", type: "number", required: false },
                { name: "top_emu", zh: "\u4e0a\u8fb9\u8ddd EMU", en: "Top EMU", type: "number", required: false },
                { name: "width_emu", zh: "\u5bbd\u5ea6 EMU", en: "Width EMU", type: "number", required: false },
                { name: "height_emu", zh: "\u9ad8\u5ea6 EMU", en: "Height EMU", type: "number", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_edit",
            params: ["path", "env", "slide_index", "shape_index", "shape_name", "operation", "text", "size_pt", "bold", "color_rgb", "left_emu", "top_emu", "width_emu", "height_emu", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "edited.pptx",
            timeoutMs: 300000,
        }
    },
    "pptx_notes": {
        meta: {
            name: "pptx_notes",
            zh: "\u6f14\u8bb2\u8005\u5907\u6ce8\u8bfb\u5199\u3002",
            en: "Read or write speaker notes.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "operation", zh: "read/write", en: "read/write", type: "string", required: false },
                { name: "slide_index", zh: "0-based \u9875\u7d22\u5f15", en: "0-based slide index", type: "number", required: false },
                { name: "text", zh: "\u5907\u6ce8\u6587\u672c", en: "Notes text", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_notes",
            params: ["path", "env", "operation", "slide_index", "text", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "notes.pptx",
            timeoutMs: 300000,
        }
    },
    "pptx_media": {
        meta: {
            name: "pptx_media",
            zh: "\u63d2\u5165\u56fe\u7247\uff0c\u672a\u7ed9\u5c3a\u5bf8\u65f6\u6309\u9875\u5bbd 80% \u7b49\u6bd4\u7f29\u653e\u3002",
            en: "Insert an image; without explicit size it scales to 80% of slide width.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "slide_index", zh: "0-based \u9875\u7d22\u5f15", en: "0-based slide index", type: "number", required: true },
                { name: "image_path", zh: "\u56fe\u7247\u8def\u5f84", en: "Image path", type: "string", required: true },
                { name: "left_emu", zh: "\u5de6\u8fb9\u8ddd EMU", en: "Left EMU", type: "number", required: false },
                { name: "top_emu", zh: "\u4e0a\u8fb9\u8ddd EMU", en: "Top EMU", type: "number", required: false },
                { name: "width_emu", zh: "\u5bbd\u5ea6 EMU", en: "Width EMU", type: "number", required: false },
                { name: "height_emu", zh: "\u9ad8\u5ea6 EMU", en: "Height EMU", type: "number", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_media",
            params: ["path", "env", "slide_index", "image_path", "left_emu", "top_emu", "width_emu", "height_emu", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path", "image_path"],
            defaultOutputName: "media.pptx",
            timeoutMs: 300000,
        }
    },
    "pptx_clean": {
        meta: {
            name: "pptx_clean",
            zh: "\u6e05\u7406\u65e0\u5f15\u7528\u5185\u5bb9\uff1b\u4f1a\u5220\u9664\u4e0d\u5728 <p:sldIdLst> \u4e2d\u7684 slide\uff0c\u5fc5\u987b\u5728\u7ed3\u6784\u6027\u64cd\u4f5c\u4e4b\u540e\u8c03\u7528\u3002",
            en: "Clean unreferenced content; deletes slides absent from <p:sldIdLst>, so call it after structural operations.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pptx_clean",
            params: ["path", "env", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "cleaned.pptx",
            timeoutMs: 300000,
        }
    },
    "pdf_info": {
        meta: {
            name: "pdf_info",
            zh: "\u9875\u6570\u3001\u5c3a\u5bf8\u3001\u5143\u6570\u636e\u3001\u662f\u5426\u52a0\u5bc6\u3001\u662f\u5426\u542b\u6587\u672c\u5c42\u3001\u8868\u5355\u5b57\u6bb5\u6570\u3002",
            en: "Page count, size, metadata, encryption, text layer, and form field count.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_info",
            params: ["path", "env", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "info.json",
            timeoutMs: 300000,
        }
    },
    "pdf_extract": {
        meta: {
            name: "pdf_extract",
            zh: "\u6587\u672c/\u7248\u9762/\u8868\u683c\u63d0\u53d6\uff0c\u652f\u6301\u9875\u8303\u56f4\uff1b\u7248\u9762\u4e0e\u8868\u683c\u9700\u8981 pdfplumber\u3002",
            en: "Extract text/layout/tables with page ranges; layout and tables need pdfplumber.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "range", zh: "\u9875\u8303\u56f4\uff0c\u5982 1-3,7", en: "Page range such as 1-3,7", type: "string", required: false },
                { name: "mode", zh: "text/layout/tables", en: "text/layout/tables", type: "string", required: false },
                { name: "max_chars", zh: "\u8f93\u51fa\u9884\u7b97", en: "Character budget", type: "number", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_extract",
            params: ["path", "env", "range", "mode", "max_chars", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "extract.txt",
            timeoutMs: 300000,
        }
    },
    "pdf_merge": {
        meta: {
            name: "pdf_merge",
            zh: "\u6309\u987a\u5e8f\u5408\u5e76\u591a\u4e2a PDF\u3002",
            en: "Merge PDFs in order.",
            params: [
                { name: "paths", zh: "\u81f3\u5c11\u4e24\u4e2a PDF \u8def\u5f84", en: "At least two PDF paths", type: "array", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_merge",
            params: ["paths", "env", "output_path", "output_env", "overwrite", "task_id"],
            inputPaths: ["paths"],
            defaultOutputName: "merged.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_split": {
        meta: {
            name: "pdf_split",
            zh: "\u6309\u9875\u8303\u56f4\u62c6\u5206\u6210\u5355\u9875\u6587\u4ef6\u3002",
            en: "Split into single-page files by range.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "range", zh: "\u9875\u8303\u56f4", en: "Page range", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_split",
            params: ["path", "env", "range", "output_env", "task_id"],
            inputPaths: ["path"],
            outputKind: "multi",
            defaultOutputName: "split",
            timeoutMs: 300000,
        }
    },
    "pdf_rotate": {
        meta: {
            name: "pdf_rotate",
            zh: "\u6309 90 \u5ea6\u500d\u6570\u65cb\u8f6c\u6307\u5b9a\u9875\u3002",
            en: "Rotate selected pages by a multiple of 90 degrees.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "angle", zh: "\u65cb\u8f6c\u89d2\u5ea6", en: "Rotation angle", type: "number", required: true },
                { name: "range", zh: "\u9875\u8303\u56f4", en: "Page range", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_rotate",
            params: ["path", "env", "angle", "range", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "rotated.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_reorder": {
        meta: {
            name: "pdf_reorder",
            zh: "\u6309\u5b8c\u6574\u6392\u5217\u91cd\u6392\u9875\u9762\u3002",
            en: "Reorder pages using a complete permutation.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "order", zh: "1..N \u7684\u6392\u5217", en: "Permutation of 1..N", type: "array", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_reorder",
            params: ["path", "env", "order", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "reordered.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_delete_pages": {
        meta: {
            name: "pdf_delete_pages",
            zh: "\u5220\u9664\u6307\u5b9a\u9875\uff0c\u62d2\u7edd\u5220\u9664\u5168\u90e8\u9875\u9762\u3002",
            en: "Delete pages; refuses to delete every page.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "range", zh: "\u9875\u8303\u56f4", en: "Page range", type: "string", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_delete_pages",
            params: ["path", "env", "range", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "trimmed.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_form_list": {
        meta: {
            name: "pdf_form_list",
            zh: "\u679a\u4e3e\u8868\u5355\u5b57\u6bb5\u4e0e\u53d6\u503c\u3002",
            en: "Enumerate form fields and values.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_form_list",
            params: ["path", "env", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "form.json",
            timeoutMs: 300000,
        }
    },
    "pdf_form_fill": {
        meta: {
            name: "pdf_form_fill",
            zh: "\u586b\u5199\u8868\u5355\u5b57\u6bb5\uff1bstrict=true \u65f6\u672a\u77e5\u5b57\u6bb5\u76f4\u63a5\u5931\u8d25\u3002",
            en: "Fill form fields; strict=true fails on unknown fields.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "values", zh: "\u5b57\u6bb5\u540d\u5230\u53d6\u503c", en: "Field name to value", type: "object", required: true },
                { name: "strict", zh: "\u672a\u77e5\u5b57\u6bb5\u65f6\u5931\u8d25", en: "Fail on unknown fields", type: "boolean", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_form_fill",
            params: ["path", "env", "values", "strict", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "filled.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_watermark": {
        meta: {
            name: "pdf_watermark",
            zh: "\u6dfb\u52a0\u6c34\u5370\uff1b\u4e2d\u6587\u5fc5\u987b\u63d0\u4f9b\u53ef\u7528 CJK \u5b57\u4f53\uff0c\u5426\u5219\u62a5 E_ENV_MISSING\u3002",
            en: "Add a watermark; Chinese text requires a usable CJK font or fails with E_ENV_MISSING.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "text", zh: "\u6c34\u5370\u6587\u5b57", en: "Watermark text", type: "string", required: true },
                { name: "size", zh: "\u5b57\u53f7", en: "Font size", type: "number", required: false },
                { name: "opacity", zh: "\u900f\u660e\u5ea6 0-1", en: "Opacity 0-1", type: "number", required: false },
                { name: "angle", zh: "\u65cb\u8f6c\u89d2\u5ea6", en: "Rotation angle", type: "number", required: false },
                { name: "cjk_font", zh: "CJK \u5b57\u4f53\u65cf", en: "CJK font family", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_watermark",
            params: ["path", "env", "text", "size", "opacity", "angle", "cjk_font", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "watermarked.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_encrypt": {
        meta: {
            name: "pdf_encrypt",
            zh: "\u52a0\u5bc6 PDF\u3002",
            en: "Encrypt a PDF.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "password", zh: "\u5bc6\u7801", en: "Password", type: "string", required: true },
                { name: "algorithm", zh: "\u52a0\u5bc6\u7b97\u6cd5", en: "Encryption algorithm", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_encrypt",
            params: ["path", "env", "password", "algorithm", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "encrypted.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_decrypt": {
        meta: {
            name: "pdf_decrypt",
            zh: "\u89e3\u5bc6 PDF\uff1b\u5bc6\u7801\u9519\u8bef\u660e\u786e\u62a5\u9519\uff0c\u4e0d\u53cd\u590d\u5c1d\u8bd5\u3002",
            en: "Decrypt a PDF; a wrong password fails explicitly without retries.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "password", zh: "\u5bc6\u7801", en: "Password", type: "string", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_decrypt",
            params: ["path", "env", "password", "output_path", "output_env", "overwrite", "in_place", "task_id"],
            inputPaths: ["path"],
            defaultOutputName: "decrypted.pdf",
            timeoutMs: 300000,
        }
    },
    "pdf_to_images": {
        meta: {
            name: "pdf_to_images",
            zh: "PDF \u8f6c JPEG\uff08pdftoppm\uff09\uff1b\u626b\u63cf\u4ef6\u4f18\u5148\u51fa\u56fe\u540e\u81ea\u5df1\u770b\u3002",
            en: "Convert PDF to JPEG with pdftoppm; for scans prefer looking at rendered pages.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "range", zh: "\u9875\u8303\u56f4", en: "Page range", type: "string", required: false },
                { name: "dpi", zh: "DPI\uff0c\u9ed8\u8ba4 150", en: "DPI; default 150", type: "number", required: false },
                { name: "max_pages", zh: "\u6700\u591a\u9875\u6570", en: "Max pages", type: "number", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_to_images",
            params: ["path", "env", "range", "dpi", "max_pages", "output_env", "task_id"],
            inputPaths: ["path"],
            outputKind: "multi",
            defaultOutputName: "images",
            timeoutMs: 300000,
        }
    },
    "pdf_create": {
        meta: {
            name: "pdf_create",
            zh: "\u751f\u6210 PDF\uff1bengine \u5fc5\u987b\u663e\u5f0f\u6307\u5b9a reportlab/pandoc/weasyprint\uff0c\u4e2d\u6587\u4f1a\u5148\u63a2\u6d4b CJK \u5b57\u4f53\u3002",
            en: "Create a PDF; engine must be explicitly reportlab/pandoc/weasyprint and CJK fonts are probed first.",
            params: [
                { name: "engine", zh: "reportlab/pandoc/weasyprint", en: "reportlab/pandoc/weasyprint", type: "string", required: true },
                { name: "blocks", zh: "[{type,text}]", en: "[{type,text}]", type: "array", required: false },
                { name: "source_path", zh: "\u5916\u90e8\u5f15\u64ce\u7684\u6e90\u6587\u4ef6", en: "Source file for external engines", type: "string", required: false },
                { name: "file_name", zh: "\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name", type: "string", required: false },
                { name: "cjk_font", zh: "CJK \u5b57\u4f53\u65cf", en: "CJK font family", type: "string", required: false },
                { name: "margin_cm", zh: "\u9875\u8fb9\u8ddd\uff08cm\uff09", en: "Margin in cm", type: "number", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "env", zh: "\u8def\u5f84\u6240\u5c5e\u73af\u5883\uff0c\u5fc5\u987b\u663e\u5f0f\u4f20\u5165 android \u6216 linux\uff0c\u7981\u6b62\u63a8\u65ad", en: "Path environment; must be explicitly android or linux, never inferred", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_create",
            params: ["engine", "blocks", "source_path", "file_name", "cjk_font", "margin_cm", "output_path", "env", "output_env", "overwrite", "task_id"],
            defaultOutputName: "document.pdf",
            timeoutMs: 600000,
        }
    },
};
function toolNames() {
    return Object.keys(exports.OFFICE_TOOLS);
}
