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
            zh: "\u529e\u516c\u4efb\u52a1\u5148\u8c03\u7528 office_read_guide(format=core)\uff0c\u518d\u8bfb\u53d6\u5bf9\u5e94\u683c\u5f0f\u7684\u5185\u7f6e Skill\u3002\u5148\u68c0\u67e5\u73af\u5883\u4e0e\u8f93\u5165\u7ed3\u6784\uff0c\u7f16\u8f91\u540e\u6821\u9a8c\u3001\u9884\u89c8\u5e76\u770b\u56fe\uff1b\u6309\u771f\u5b9e\u9a8c\u6536\u72b6\u6001\u4ea4\u4ed8\u3002",
            en: "Start office tasks with office_read_guide(format=core), then the format-specific built-in Skill. Check environment and inputs, validate and preview edits, inspect images, and report verified delivery status.",
            advice: true,
            params: []
        },
        spec: {
            command: "",
            defaultOutputName: "",
            timeoutMs: 300000,
        }
    },
    "office_read_guide": {
        meta: {
            name: "office_read_guide",
            zh: "\u8bfb\u53d6\u968f\u63d2\u4ef6\u5185\u7f6e\u7684\u529e\u516c Skill\uff0c\u65e0\u9700\u5b89\u88c5 Python\uff1bformat=core/docx/xlsx/pptx/pdf\u3002\u5148\u8bfb core\uff0c\u518d\u6309\u5b9e\u9645\u683c\u5f0f\u8bfb\u53d6\u3002",
            en: "Read the bundled office Skill without Python: format=core/docx/xlsx/pptx/pdf. Read core first, then the relevant format.",
            params: [
                { name: "format", zh: "core/docx/xlsx/pptx/pdf\uff0c\u9ed8\u8ba4 core", en: "core/docx/xlsx/pptx/pdf; default core", type: "string", required: false },
            ]
        },
        spec: {
            command: "",
            params: ["format"],
            defaultOutputName: "office_read_guide",
            requiresEnv: false,
            timeoutMs: 20000,
        }
    },
    "office_env_check": {
        meta: {
            name: "office_env_check",
            zh: "\u63a2\u6d4b Python\u3001T1-T4 \u7ec4\u4ef6\u4e0e\u78c1\u76d8\u3002T4 \u6309\u5b9e\u9645\u4e8c\u8fdb\u5236\u63a2\u6d4b\uff1bfonts.system_font_families \u4e3a fontconfig \u786e\u8ba4\u7684\u4e2d\u6587\u7cfb\u7edf\u5b57\u4f53\u65cf\uff0cReportLab CID \u5b57\u4f53\u5355\u72ec\u5217\u51fa\u3002",
            en: "Inspect Python, Tier1-Tier4 components and disk space. Tier4 probes executable names; fonts.system_font_families contains fontconfig-confirmed Chinese families, separate from ReportLab CID fonts.",
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "options", zh: "\u989d\u5916\u5f15\u64ce\u53c2\u6570\u6570\u7ec4", en: "Extra engine options", type: "array", required: false },
                { name: "cjk_font", zh: "\u4e2d\u6587\u5b57\u4f53\u65cf\uff1bPandoc \u8def\u7ebf\u5fc5\u987b\u662f fonts.system_font_families \u4e2d\u7684\u771f\u5b9e\u65cf\u540d\uff0c\u4e0d\u80fd\u4f20\u5b57\u4f53\u6587\u4ef6\u540d\u6216 STSong-Light", en: "CJK font family; Pandoc requires an actual fonts.system_font_families entry, not a filename or STSong-Light", type: "string", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "timeout_ms", zh: "\u8f6c\u6362\u8d85\u65f6\u6beb\u79d2\uff0c\u9ed8\u8ba4 600000", en: "Conversion timeout in ms; default 600000", type: "number", required: false },
            ]
        },
        spec: {
            command: "office_convert",
            params: ["from_path", "to_format", "engine", "env", "output_path", "output_env", "overwrite", "options", "cjk_font", "task_id", "in_place", "timeout_ms"],
            inputPaths: ["from_path"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "pages", zh: "\u9875\u7801\u8303\u56f4\uff0c\u5982 1-3", en: "Page range such as 1-3", type: "string", required: false },
                { name: "dpi", zh: "\u6e32\u67d3 DPI\uff0c\u9ed8\u8ba4 150", en: "Render DPI; default 150", type: "number", required: false },
                { name: "max_pages", zh: "\u6700\u591a\u6e32\u67d3\u9875\u6570\uff0c\u9ed8\u8ba4 8", en: "Max pages; default 8", type: "number", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u76ee\u5f55\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output directory; defaults to the delivery directory", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u76ee\u5f55\u975e\u7a7a\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite a non-empty target directory; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_render_preview",
            params: ["path", "env", "output_env", "pages", "dpi", "max_pages", "output_path", "overwrite", "task_id"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "original_path", zh: "\u6a21\u677f\u6d3e\u751f\u573a\u666f\u7684\u57fa\u7ebf\u6587\u4ef6", en: "Baseline file for template-derived output", type: "string", required: false },
                { name: "strict", zh: "\u6709\u95ee\u9898\u65f6\u8fd4\u56de E_VALIDATION_FAILED", en: "Fail with E_VALIDATION_FAILED on issues", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_validate",
            params: ["path", "env", "original_path", "strict", "task_id"],
            inputPaths: ["path", "original_path"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "max_chars", zh: "\u8f93\u51fa\u9884\u7b97", en: "Character budget", type: "number", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_diff",
            params: ["left", "right", "env", "max_chars", "task_id"],
            inputPaths: ["left", "right"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
    "office_workspace_status": {
        meta: {
            name: "office_workspace_status",
            zh: "\u5217\u51fa Ubuntu \u529e\u516c\u4efb\u52a1\u7684\u771f\u5b9e\u8def\u5f84\u3001\u4e34\u65f6/\u8f93\u51fa\u5360\u7528\u548c\u4f7f\u7528\u72b6\u6001\uff1b\u4e0d\u4fee\u6539\u6587\u4ef6\u3002\u533a\u5206\u5f15\u64ceruntime\u3001work\u4efb\u52a1\u548cAndroid\u4ea4\u4ed8\u76ee\u5f55\u3002",
            en: "List Ubuntu office task paths, temporary/output usage and active state without modifying tasks. Distinguishes runtime, work and Android delivery.",
            params: [
                { name: "offset", zh: "\u5206\u9875\u8d77\u70b9\uff0c\u9ed8\u8ba40", en: "Page offset, default 0", type: "number", required: false },
                { name: "limit", zh: "\u6bcf\u9875\u4efb\u52a1\u65701-50\uff0c\u9ed8\u8ba420", en: "Page size 1-50, default 20", type: "number", required: false },
            ]
        },
        spec: {
            command: "office_workspace_status",
            params: ["offset", "limit"],
            defaultOutputName: "workspace-status.json",
            requiresEnv: false,
            timeoutMs: 120000,
        }
    },
    "office_workspace_clean": {
        meta: {
            name: "office_workspace_clean",
            zh: "\u6e05\u7406\u6307\u5b9a Ubuntu \u529e\u516c\u4efb\u52a1\uff1b\u9ed8\u8ba4\u4ec5\u9884\u89c8\u3002scope=temporary \u4fdd\u7559\u8f93\u51fa\uff0ctask \u5220\u9664\u6574\u4e2a\u4efb\u52a1\u542b\u8f93\u51fa\uff1b\u5148\u9884\u89c8\uff0c\u518d\u4f20 confirm=true \u4e0e\u539f plan_token\u3002\u4e0d\u80fd\u6e05\u7406\u8fd0\u884c\u65f6\u3001Android\u4ea4\u4ed8\u6216\u4efb\u610f\u76ee\u5f55\u3002",
            en: "Preview cleanup of an Ubuntu office task. temporary preserves outputs; task deletes the whole task including outputs. Confirm using confirm=true and the returned plan_token. Runtime and external/Android outputs are excluded.",
            params: [
                { name: "task_id", zh: "\u5f85\u6e05\u7406\u7684\u771f\u5b9e\u4efb\u52a1ID\uff1b\u5148\u7528 office_workspace_status \u67e5\u770b", en: "Existing task ID from office_workspace_status", type: "string", required: true },
                { name: "scope", zh: "temporary\uff08\u9ed8\u8ba4\uff09\uff1a\u6e05\u7406in/tmp/\u53c2\u6570\uff1btask\uff1a\u5220\u9664\u6574\u4e2a\u4efb\u52a1\u542bout\u8f93\u51fa", en: "temporary (default): inputs/tmp/args; task: whole task including out", type: "string", required: false },
                { name: "confirm", zh: "\u9ed8\u8ba4 false\uff0c\u53ea\u8fd4\u56de\u6e05\u7406\u8ba1\u5212\uff1b\u7528\u6237\u786e\u8ba4\u7cbe\u786e\u6e05\u5355\u540e\u4f20 true", en: "Defaults false: preview. True executes the reviewed plan", type: "boolean", required: false },
                { name: "plan_token", zh: "\u9884\u89c8\u8fd4\u56de\u7684 plan_token\uff0c\u786e\u8ba4\u65f6\u5fc5\u9700\uff1b\u76ee\u5f55\u53d8\u5316\u9700\u91cd\u65b0\u9884\u89c8", en: "Required when confirming; token from unchanged preview", type: "string", required: false },
            ]
        },
        spec: {
            command: "office_workspace_clean",
            params: ["task_id", "scope", "confirm", "plan_token"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "spec", zh: "\u542b blocks \u4e0e\u53ef\u9009 layout \u7684\u7ed3\u6784\u5316\u6587\u6863\uff1bbullet/number \u4f7f\u7528 text \u6216 runs \u521b\u5efa\u5355\u9879\uff0citems \u5b57\u7b26\u4e32\u6570\u7ec4\u521b\u5efa\u591a\u9879\uff0c\u4e09\u8005\u4e0d\u53ef\u6df7\u7528", en: "Structured document with blocks and optional layout; bullet/number accepts text or runs for one item, or a string items array for multiple items; do not mix these inputs", type: "object", required: false },
                { name: "markdown", zh: "Markdown \u6587\u672c", en: "Markdown text", type: "string", required: false },
                { name: "file_name", zh: "\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
            zh: "\u5355\u6b21\u586b\u5145 {{\u53d8\u91cf}}\uff0c\u4fdd\u7559\u8de8 run \u683c\u5f0f\uff0c\u8986\u76d6\u5d4c\u5957\u8868\u683c\u53ca\u5df2\u6709\u9875\u7709\u9875\u811a\u3002\u503c\u4e0d\u9012\u5f52\u5c55\u5f00\uff1bstrict \u62d2\u7edd\u7f3a\u5931\u53d8\u91cf\u548c\u53d7\u4fdd\u62a4\u7ed3\u6784\u5185\u7684\u6807\u8bb0\u3002",
            en: "Fill {{variables}} once across runs, nested tables and existing headers/footers. Values are literal; strict mode rejects missing values and protected markers.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
            zh: "\u6309\u6bb5\u843d\u951a\u70b9\u66ff\u6362/\u63d2\u5165/\u5220\u9664\u3002\u6574\u6bb5\u64cd\u4f5c\u4fdd\u62a4\u57df\u3001\u516c\u5f0f\u3001\u4e66\u7b7e\u7b49\u7ed3\u6784\uff1b\u63d2\u5165\u7ee7\u627f\u683c\u5f0f\u4f46\u4e0d\u590d\u5236\u5206\u8282\u3002\u666e\u901a\u5c40\u90e8\u6587\u5b57\u4f18\u5148 docx_find_replace\u3002",
            en: "Replace/insert/delete at a paragraph anchor. Whole-paragraph edits protect fields, equations and bookmarks; insertion inherits formatting without copying sections. Prefer docx_find_replace for local text.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "image_path", zh: "\u56fe\u7247\u8def\u5f84", en: "Image path", type: "string", required: true },
                { name: "width_cm", zh: "\u56fe\u7247\u5bbd\u5ea6\u5398\u7c73\uff1b\u7701\u7565\u65f6\u6309\u6700\u540e\u4e00\u8282\u6b63\u6587\u5bbd\u9ad8\u7b49\u6bd4\u7ea6\u675f\uff1b\u663e\u5f0f\u5bbd\u5ea6\u8d85\u51fa\u6b63\u6587\u65f6\u62a5\u9519\u3002", en: "Width in cm. Omitted size fits final section content area; explicit overflowing sizes are rejected.", type: "number", required: false },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "margins_cm", zh: "{top,bottom,left,right}", en: "{top,bottom,left,right}", type: "object", required: false },
                { name: "default_font", zh: "\u9ed8\u8ba4\u5b57\u4f53 {name,east_asia,size_pt,bold,italic,color_rgb}\uff1bname \u4e3a\u897f\u6587\uff0ceast_asia \u4e3a\u4e2d\u6587\u5b57\u4f53\u3002", en: "Default font {name,east_asia,size_pt,bold,italic,color_rgb}; east_asia sets the CJK typeface.", type: "object", required: false },
                { name: "paragraph_styles", zh: "\u6837\u5f0f\u540d\u5230\u914d\u7f6e\uff1a\u5b57\u4f53\u5b57\u6bb5\u53ca alignment\u3001line_spacing \u500d\u6570\u6216 line_spacing_pt \u56fa\u5b9a\u78c5\u3001space_before_pt/space_after_pt\u3001first_line_indent_cm/left_indent_cm/right_indent_cm\u3001keep_with_next/keep_together/page_break_before/widow_control\u3002", en: "Style-name map: font, alignment, line_spacing OR line_spacing_pt, space_before_pt/space_after_pt, first_line_indent_cm/left_indent_cm/right_indent_cm, keep_with_next/keep_together/page_break_before/widow_control.", type: "object", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
                { name: "page_setup", zh: "\u9875\u9762\u5398\u7c73\u53c2\u6570\uff1awidth_cm/height_cm/header_distance_cm/footer_distance_cm\uff1bA4 \u4e3a 21\u00d729.7\u3002", en: "Page dimensions/distances in cm: width_cm, height_cm, header_distance_cm, footer_distance_cm; A4 21\u00d729.7.", type: "object", required: false },
                { name: "header_footer", zh: "\u66ff\u6362\u9ed8\u8ba4\u9875\u7709/\u9875\u811a\u533a\u57df\uff08\u4e0d\u6539\u9996\u9875\u4e0e\u5076\u6570\u9875\u533a\u57df\uff09\uff1a{header:{text,alignment},footer:{text,alignment,page_number,total_pages}}\uff1b\u9875\u7801\u4e3a Word \u57df\uff0c\u987b\u5728\u6392\u7248\u5f15\u64ce\u66f4\u65b0\u5e76\u9884\u89c8\u3002", en: "Replace default header/footer regions only: {header:{text,alignment},footer:{text,alignment,page_number,total_pages}}. Page fields require layout-engine refresh and preview.", type: "object", required: false },
            ]
        },
        spec: {
            command: "docx_style",
            params: ["path", "env", "margins_cm", "default_font", "paragraph_styles", "output_path", "output_env", "overwrite", "in_place", "task_id", "page_setup", "header_footer"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "target_dir_name", zh: "\u8f93\u51fa\u76ee\u5f55\u540d", en: "Output directory name", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u76ee\u5f55\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output directory; defaults to the delivery directory", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u76ee\u5f55\u975e\u7a7a\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite a non-empty target directory; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "docx_extract_media",
            params: ["path", "env", "output_env", "target_dir_name", "output_path", "overwrite", "task_id"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "sheet_name", zh: "\u5de5\u4f5c\u8868\u540d", en: "Sheet name", type: "string", required: false },
                { name: "cells", zh: "\u7cbe\u786e\u5355\u5143\u683c\u6570\u7ec4 [{cell,value \u6216 formula}]\uff1b\u5148 rows \u540e cells\uff0ccells \u8986\u76d6\u540c\u6279 rows\uff1bnull \u663e\u5f0f\u6e05\u7a7a\u3002", en: "Explicit cells [{cell,value OR formula}]. Rows first, cells override rows; null explicitly clears.", type: "array", required: false },
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
            zh: "\u6309 range \u8bbe\u7f6e\u6570\u5b57\u683c\u5f0f\u3001\u5b57\u4f53\u3001\u586b\u5145\uff1b\u53ea\u66f4\u65b0\u6307\u5b9a\u5b57\u4f53\u5c5e\u6027\uff0c\u4fdd\u7559\u5176\u4ed6\u5c5e\u6027\u3002\u652f\u6301\u5217\u5bbd\u3001\u884c\u9ad8\u3001\u51bb\u7ed3\u7a97\u683c\u4e0e\u7b5b\u9009\uff1b\u4fdd\u5b58\u542b\u516c\u5f0f\u7684\u6587\u4ef6\u540e\u9700\u91cd\u65b0 xlsx_recalc\u3002",
            en: "Format a range while preserving unspecified font properties; set column widths, row heights, freeze panes and filters. Recalculate formula workbooks after saving.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "range", zh: "\u4ec5\u683c\u5f0f\u5316\u6b64\u77e9\u5f62\u533a\u57df\uff0c\u5982 A1:D20\uff1b\u7701\u7565\u4e3a\u5df2\u7528\u533a\u57df\uff0c\u6700\u591a 100000 \u5355\u5143\u683c", en: "Format only this rectangle, e.g. A1:D20; defaults to used range, capped at 100000 cells", type: "string", required: false },
            ]
        },
        spec: {
            command: "xlsx_format",
            params: ["path", "env", "sheet_name", "number_format", "font", "fill", "column_widths", "row_heights", "freeze_panes", "auto_filter", "output_path", "output_env", "overwrite", "in_place", "task_id", "range"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
    "xlsx_chart": {
        meta: {
            name: "xlsx_chart",
            zh: "\u5728\u5df2\u6709\u5de5\u4f5c\u7c3f\u4e2d\u6dfb\u52a0\u539f\u751f\u53ef\u7f16\u8f91\u67f1\u72b6/\u6761\u5f62/\u6298\u7ebf/\u997c/\u6563\u70b9\u56fe\u3002\u5148 xlsx_read \u786e\u8ba4\u8868\u5934\u4e0e\u6570\u503c\u8303\u56f4\uff1b\u4fdd\u5b58\u540e\u91cd\u7b97\u516c\u5f0f\uff0c\u518d\u6821\u9a8c\u548c\u9884\u89c8\u3002",
            en: "Add an editable column/bar/line/pie/scatter chart to a workbook. Inspect headers and data with xlsx_read first; recalculate formulas, validate and preview after saving.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "sheet_name", zh: "\u5de5\u4f5c\u8868\u540d", en: "Sheet name", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "in_place", zh: "\u539f\u5730\u7f16\u8f91\uff08\u4ec5 Linux \u5de5\u4f5c\u533a\uff0c\u4ecd\u9700\u4e34\u65f6\u6587\u4ef6\u539f\u5b50\u66ff\u6362\uff09", en: "Edit in place (Linux workspace only; still atomic replacement)", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
                { name: "data_range", zh: "\u542b\u8868\u5934\u7684\u77e9\u5f62\u8303\u56f4\uff0c\u9996\u5217\u4e3a\u5206\u7c7b/X \u503c\uff0c\u5176\u4f59\u5217\u4e3a\u6570\u503c\u7cfb\u5217", en: "Rectangle with headers; first column contains categories/X, remaining columns contain values", type: "string", required: true },
                { name: "chart_type", zh: "column/bar/line/pie/scatter\uff0c\u9ed8\u8ba4 column\uff1b\u997c\u56fe\u4ec5\u4e00\u4e2a\u6570\u503c\u7cfb\u5217", en: "column/bar/line/pie/scatter; defaults to column; pie requires one value series", type: "string", required: false },
                { name: "anchor", zh: "\u56fe\u8868\u5de6\u4e0a\u89d2\u5355\u5143\u683c\uff0c\u9ed8\u8ba4 E2", en: "Top-left cell, defaults to E2", type: "string", required: false },
                { name: "title", zh: "\u56fe\u8868\u6807\u9898", en: "Chart title", type: "string", required: false },
                { name: "width_cm", zh: "\u5bbd\u5ea6\uff08\u5398\u7c73\uff09\uff0c\u9ed8\u8ba4 18", en: "Width in cm, defaults to 18", type: "number", required: false },
                { name: "height_cm", zh: "\u9ad8\u5ea6\uff08\u5398\u7c73\uff09\uff0c\u9ed8\u8ba4 10", en: "Height in cm, defaults to 10", type: "number", required: false },
                { name: "style", zh: "Excel \u56fe\u8868\u6837\u5f0f 1-48\uff0c\u9ed8\u8ba4 10", en: "Excel chart style 1-48, defaults to 10", type: "number", required: false },
            ]
        },
        spec: {
            command: "xlsx_chart",
            params: ["path", "env", "sheet_name", "output_path", "output_env", "overwrite", "in_place", "task_id", "data_range", "chart_type", "anchor", "title", "width_cm", "height_cm", "style"],
            inputPaths: ["path"],
            defaultOutputName: "formatted.xlsx",
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "slides", zh: "\u9875\u9762\u6570\u7ec4\uff1atitle/bullets/layout_index/title_size_pt/body_size_pt\uff1b\u53ef\u9009 background_rgb\u3001notes\u3001elements\u3002elements \u652f\u6301 text/shape/table\uff0c\u5398\u7c73\u4f4d\u7f6e\u5c3a\u5bf8 left_cm/top_cm/width_cm/height_cm\uff1b\u6587\u672c\u652f\u6301 paragraphs \u4e0e\u5b57\u4f53\u6392\u7248\u3002\u5148\u8bfb pptx \u6307\u5f15\u3002 chart \u5143\u7d20\u652f\u6301 column/bar/line/pie\uff0ccategories \u4e3a\u5206\u7c7b\u6570\u7ec4\uff0cseries:[{name,values}]\uff0c\u751f\u6210\u539f\u751f\u53ef\u7f16\u8f91\u56fe\u8868\u3002", en: "Slide specs with title/bullets/layout_index and optional background_rgb, notes, elements. Elements: editable text/shape/table with left_cm/top_cm/width_cm/height_cm and paragraph styling. Read pptx guide. chart elements accept column/bar/line/pie, categories, series:[{name,values}] for native editable charts.", type: "array", required: true },
                { name: "template_path", zh: "\u6a21\u677f\u8def\u5f84", en: "Template path", type: "string", required: false },
                { name: "layout_index", zh: "\u9ed8\u8ba4\u7248\u5f0f\u7d22\u5f15", en: "Default layout index", type: "number", required: false },
                { name: "file_name", zh: "\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
                { name: "slide_size_cm", zh: "\u5e7b\u706f\u7247\u5c3a\u5bf8\u5398\u7c73 {width,height}\uff1b16:9 \u793a\u4f8b {width:33.867,height:19.05}\u3002", en: "Slide dimensions in cm {width,height}; 16:9 example {width:33.867,height:19.05}.", type: "object", required: false },
            ]
        },
        spec: {
            command: "pptx_create",
            params: ["slides", "template_path", "layout_index", "file_name", "output_path", "env", "output_env", "overwrite", "task_id", "slide_size_cm"],
            inputPaths: ["template_path"],
            defaultOutputName: "presentation.pptx",
            timeoutMs: 300000,
        }
    },
    "pptx_template_fill": {
        meta: {
            name: "pptx_template_fill",
            zh: "\u586b\u5145\u6a21\u677f\u53d8\u91cf\uff0c\u9012\u5f52\u8986\u76d6\u7ec4\u5408\u5bf9\u8c61\u548c\u8868\u683c\uff0c\u4fdd\u7559 run \u683c\u5f0f\u4e0e\u8f6f\u6362\u884c\u8fb9\u754c\uff1b\u66ff\u6362\u503c\u4e0d\u9012\u5f52\u5c55\u5f00\u3002",
            en: "Fill template variables recursively in groups and tables, preserving run formatting and soft-break boundaries without recursively expanding values.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
            zh: "\u589e/\u5220/\u590d\u5236/\u91cd\u6392\u5e7b\u706f\u7247\uff0c\u7ef4\u62a4\u5173\u7cfb\u4e0e <p:sldIdLst>\u3002\u5220\u9664\u4ecd\u88ab\u9875\u9762\u6216\u81ea\u5b9a\u4e49\u653e\u6620\u5f15\u7528\u7684\u9875\u4f1a\u62d2\u7edd\u5e76\u5b9a\u4f4d\uff1b\u5148\u5b8c\u6210\u7ed3\u6784\u64cd\u4f5c\u518d\u7f16\u8f91\u5185\u5bb9\u3002",
            en: "Add/delete/duplicate/reorder slides while maintaining relationships. Deletion rejects and locates incoming slide or custom-show references. Finish structure before content edits.",
            params: [
                { name: "path", zh: "Linux \u6216 Android \u6587\u4ef6\u8def\u5f84", en: "Linux or Android file path", type: "string", required: true },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "slide_index", zh: "0-based \u9875\u7d22\u5f15", en: "0-based slide index", type: "number", required: true },
                { name: "shape_index", zh: "\u5f62\u72b6\u7d22\u5f15", en: "Shape index", type: "number", required: false },
                { name: "shape_name", zh: "\u9876\u5c42\u552f\u4e00\u5f62\u72b6\u540d\uff1b\u591a\u5904\u547d\u4e2d\u65f6\u62a5\u9519\uff0c\u4f7f\u7528 shape_index \u6d88\u9664\u6b67\u4e49", en: "Unique top-level shape name; use shape_index if the name is ambiguous", type: "string", required: false },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "slide_index", zh: "0-based \u9875\u7d22\u5f15", en: "0-based slide index", type: "number", required: true },
                { name: "image_path", zh: "\u56fe\u7247\u8def\u5f84", en: "Image path", type: "string", required: true },
                { name: "left_emu", zh: "\u5de6\u8fb9\u8ddd EMU", en: "Left EMU", type: "number", required: false },
                { name: "top_emu", zh: "\u4e0a\u8fb9\u8ddd EMU", en: "Top EMU", type: "number", required: false },
                { name: "width_emu", zh: "\u56fe\u7247\u5bbd\u5ea6 EMU\uff1b\u53ea\u7ed9\u5bbd\u5ea6\u65f6\u7b49\u6bd4\u63a8\u7b97\u9ad8\u5ea6\uff0c\u663e\u5f0f\u5c3a\u5bf8\u8d8a\u754c\u62a5\u9519\uff1b1 cm=360000 EMU\u3002", en: "Width in EMU; height inferred proportionally if omitted. Explicit out-of-slide bounds rejected. 1 cm=360000 EMU.", type: "number", required: false },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "range", zh: "\u9875\u8303\u56f4", en: "Page range", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u76ee\u5f55\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output directory; defaults to the delivery directory", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u76ee\u5f55\u975e\u7a7a\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite a non-empty target directory; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_split",
            params: ["path", "env", "range", "output_env", "output_path", "overwrite", "task_id"],
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
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
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "range", zh: "\u9875\u8303\u56f4", en: "Page range", type: "string", required: false },
                { name: "dpi", zh: "DPI\uff0c\u9ed8\u8ba4 150", en: "DPI; default 150", type: "number", required: false },
                { name: "max_pages", zh: "\u6700\u591a\u9875\u6570", en: "Max pages", type: "number", required: false },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u76ee\u5f55\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output directory; defaults to the delivery directory", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u76ee\u5f55\u975e\u7a7a\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite a non-empty target directory; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_to_images",
            params: ["path", "env", "range", "dpi", "max_pages", "output_env", "output_path", "overwrite", "task_id"],
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
                { name: "blocks", zh: "\u62a5\u544a\u5757\u6570\u7ec4\uff1aheading/title/paragraph/bullet(items)/spacer", en: "Blocks: heading/title/paragraph/bullet(items)/spacer", type: "array", required: false },
                { name: "source_path", zh: "\u5916\u90e8\u5f15\u64ce\u7684\u6e90\u6587\u4ef6", en: "Source file for external engines", type: "string", required: false },
                { name: "file_name", zh: "\u9ed8\u8ba4\u6587\u4ef6\u540d", en: "Default file name", type: "string", required: false },
                { name: "cjk_font", zh: "\u4e2d\u6587\u5b57\u4f53\u65cf\uff1bPandoc \u8def\u7ebf\u5fc5\u987b\u662f fonts.system_font_families \u4e2d\u7684\u771f\u5b9e\u65cf\u540d\uff0c\u4e0d\u80fd\u4f20\u5b57\u4f53\u6587\u4ef6\u540d\u6216 STSong-Light", en: "CJK font family; Pandoc requires an actual fonts.system_font_families entry, not a filename or STSong-Light", type: "string", required: false },
                { name: "margin_cm", zh: "\u9875\u8fb9\u8ddd\uff08cm\uff09", en: "Margin in cm", type: "number", required: false },
                { name: "output_path", zh: "\u4ea7\u7269\u8def\u5f84\uff1b\u7701\u7565\u65f6\u5199\u5165\u4ea4\u4ed8\u76ee\u5f55", en: "Output path; defaults to the delivery directory", type: "string", required: false },
                { name: "env", zh: "\u8f93\u5165\u6587\u4ef6\u6240\u5728\u73af\u5883\uff1aandroid=\u624b\u673a\u6587\u4ef6\uff0clinux=Ubuntu\u8def\u5f84\uff1b\u4ec5\u51b3\u5b9a\u5982\u4f55\u8bfb\u53d6\u6587\u4ef6\uff0c\u529e\u516c\u5f15\u64ce\u59cb\u7ec8\u5728\u672c\u673aUbuntu\u6267\u884c\u3002", en: "Input file location: android=phone files, linux=Ubuntu paths. Controls input reading; office engines always execute in local Ubuntu.", type: "string", required: true },
                { name: "output_env", zh: "\u4ea7\u7269\u76ee\u6807\u73af\u5883\uff0c\u9ed8\u8ba4 android\uff08\u4ea4\u4ed8\u76ee\u5f55\uff09\uff1b\u5199\u5165 Linux \u65f6\u5fc5\u987b\u663e\u5f0f\u4f20 linux", en: "Artifact environment; defaults to android delivery directory, and must be explicitly linux for Linux output", type: "string", required: false },
                { name: "overwrite", zh: "\u76ee\u6807\u5df2\u5b58\u5728\u65f6\u662f\u5426\u8986\u76d6\uff0c\u9ed8\u8ba4 false", en: "Overwrite an existing target; default false", type: "boolean", required: false },
                { name: "task_id", zh: "\u590d\u7528\u540c\u4e00\u4e2a Linux \u6682\u5b58\u533a", en: "Reuse the same Linux staging directory", type: "string", required: false },
            ]
        },
        spec: {
            command: "pdf_create",
            params: ["engine", "blocks", "source_path", "file_name", "cjk_font", "margin_cm", "output_path", "env", "output_env", "overwrite", "task_id"],
            inputPaths: ["source_path"],
            defaultOutputName: "document.pdf",
            timeoutMs: 600000,
        }
    },
};
function toolNames() {
    return Object.keys(exports.OFFICE_TOOLS);
}
