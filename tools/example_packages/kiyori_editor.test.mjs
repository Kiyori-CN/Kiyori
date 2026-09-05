import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";

async function loadEditor({ bindingError, updateError, configId = "custom-config" } = {}) {
    const calls = [];
    const completions = [];
    const logs = [];
    const config = {
        contextLength: 96, maxContextLength: 256, enableMaxContextMode: false,
        summaryTokenThreshold: 0.85, enableSummary: false,
        enableSummaryByMessageCount: false, summaryMessageCountThreshold: 32,
    };
    const context = vm.createContext({
        exports: {}, Error,
        console: { error: (...args) => logs.push(args) },
        complete: value => completions.push(value),
        KIYORI_DOWNLOAD_DIR: "/sdcard/Download/Kiyori",
        KIYORI_CLEAN_ON_EXIT_DIR: "/sdcard/Download/Kiyori/clean_on_exit",
        Tools: { SoftwareSettings: {
            getFunctionModelConfig: async functionType => {
                calls.push({ method: "get", functionType });
                if (bindingError) throw bindingError;
                return { configId, configName: "Custom", modelIndex: 2, actualModelIndex: 2,
                    selectedModel: "model-c", config };
            },
            updateModelConfig: async (id, updates) => {
                calls.push({ method: "update", id, updates: JSON.parse(JSON.stringify(updates)) });
                if (updateError) throw updateError;
                return { updated: true, config: { ...config, enableSummary: updates.enable_summary } };
            },
        } },
    });
    vm.runInContext(await readFile(new URL("../../app/src/main/assets/packages/kiyori_editor.js", import.meta.url), "utf8"), context);
    return { tools: context.exports, calls, completions, logs };
}

test("single context-summary change preserves every omitted setting", async () => {
    const { tools, calls, completions } = await loadEditor();
    await tools.set_context_summary_config({ function_type: " chat ", enable_summary: true });
    assert.deepEqual(calls, [
        { method: "get", functionType: "CHAT" },
        { method: "update", id: "custom-config", updates: { enable_summary: true } },
    ]);
    assert.equal(completions.length, 1);
    assert.equal(completions[0].success, true);
    assert.equal(completions[0].data.before.context_length, 96);
    assert.equal(completions[0].data.after.context_length, 96);
});

test("explicit false and numeric values survive the update boundary", async () => {
    const { tools, calls, completions } = await loadEditor();
    const updates = { context_length: 64, max_context_length: 512, enable_max_context_mode: false,
        summary_token_threshold: 0.5, enable_summary: false,
        enable_summary_by_message_count: false, summary_message_count_threshold: 20 };
    await tools.set_context_summary_config(updates);
    assert.deepEqual(calls[1].updates, updates);
    assert.equal(completions[0].success, true);
});

for (const params of [undefined, {}, { function_type: "CHAT" }, { enable_summary: undefined }, { function_type: " ", enable_summary: true }]) {
    test(`empty context-summary request has no host side effects: ${JSON.stringify(params)}`, async () => {
        const { tools, calls, completions } = await loadEditor();
        await tools.set_context_summary_config(params);
        assert.deepEqual(calls, []);
        assert.equal(completions.length, 1);
        assert.equal(completions[0].success, false);
    });
}

for (const errorKey of ["bindingError", "updateError"]) {
    test(`${errorKey} is reported once without leaking config values into logs`, async () => {
        const { tools, calls, completions, logs } = await loadEditor({ [errorKey]: new Error("host rejected request") });
        await tools.set_context_summary_config({ enable_summary: true });
        assert.equal(completions.length, 1);
        assert.equal(completions[0].success, false);
        assert.equal(completions[0].message, "host rejected request");
        assert.equal(logs.length, 1);
        assert.equal(JSON.stringify(logs).includes("host rejected request"), false);
        assert.equal(calls.filter(call => call.method === "update").length, errorKey === "bindingError" ? 0 : 1);
    });
}

test("missing binding cannot update another config", async () => {
    const { tools, calls, completions } = await loadEditor({ configId: "" });
    await tools.set_context_summary_config({ enable_summary: true });
    assert.equal(calls.length, 1);
    assert.equal(completions[0].success, false);
});

test("structured host tool errors keep their message", async () => {
    const { tools, completions, logs } = await loadEditor({ bindingError: { name: "Error", message: "Configuration does not exist", data: {} } });
    await tools.set_context_summary_config({ enable_summary: true });
    assert.equal(completions[0].message, "Configuration does not exist");
    assert.equal(logs.length, 1);
});

test("read preserves authoritative model binding fields", async () => {
    const { tools, completions } = await loadEditor();
    await tools.get_context_summary_config({ function_type: "chat" });
    assert.equal(completions[0].data.config_id, "custom-config");
    assert.equal(completions[0].data.actual_model_index, 2);
    assert.equal(completions[0].data.context_summary.enable_summary, false);
});
