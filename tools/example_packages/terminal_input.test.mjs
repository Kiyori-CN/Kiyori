import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";

async function loadPackage(name, options = {}) {
    const calls = [];
    const completions = [];
    const context = vm.createContext({
        exports: {},
        complete: value => completions.push(value),
        console,
        getChatId: () => "input-regression",
        getArtifactPath: env => { assert.equal(env, "linux"); if (options.artifactError) throw new Error(options.artifactError); return '/workspace'; },
        getArtifactPaths: () => ({ android: '/sdcard/Download/Kiyori/workspace', linux: '/workspace', linuxIsLocal: true }),
        Tools: { System: { terminal: {
            create: async name => ({ sessionId: name }),
            exec: async (...args) => {
                calls.push(args);
                return { sessionId: args[0], exitCode: 0, output: "OK", timedOut: false };
            },
            input: async (...args) => { calls.push(args); return true; },
        } } },
    });
    vm.runInContext(await readFile(new URL(`../../app/src/main/assets/packages/${name}.js`, import.meta.url), "utf8"), context);
    return { tools: context.exports, calls, completions };
}

for (const tool of ["run_go", "run_python", "run_javascript_node", "run_ruby", "run_rust", "run_c", "run_cpp"]) {
    test(`${tool}: invalid artifact root fails before dependency setup or temporary writes`, async () => {
        const { tools, calls, completions } = await loadPackage("code_runner", { artifactError: "invalid root" });
        await tools[tool]({ script: "anything" });
        assert.equal(completions[0].success, false);
        assert.match(completions[0].message, /invalid root/);
        assert.equal(calls.length, 0);
    });
    test(`${tool}: production package preserves TAB/Unicode/quotes in generated source`, async () => {
        const { tools, calls, completions } = await loadPackage("code_runner");
        // Assert the transport boundary here; this mock does not execute guest language runtimes.
        const script = "first\n\t中文😀 '\"\\$`!\n  spaces\n\t\tlast\n";
        await tools[tool]({ script });
        assert.equal(completions.length, 1);
        assert.equal(completions[0].success, true, JSON.stringify(completions[0]));
        const writes = calls.filter(([, command]) => command.includes(script));
        assert.equal(writes.length, 1);
        assert.ok(writes[0][1].includes(`\n${script}__CODE_RUNNER_FILE_`));
        assert.ok(calls.every(([sessionId]) => sessionId === "code_runner_session"));
    });
}

test("explicit Rust file execution does not consult or reset the default artifact directory", async () => {
    const { tools, completions } = await loadPackage("code_runner", { artifactError: "default must not be consulted" });
    await tools.run_rust_file({ file_path: "/project/main.rs" });
    assert.equal(completions[0].success, true, JSON.stringify(completions[0]));
});

test("terminal foreground/background preserve command and timeout policies", async () => {
    const { tools, calls } = await loadPackage("super_admin");
    const command = "cat <<'EOF'\n\t中文 '\"\\$`!\nEOF\n";
    await tools.terminal({ command, timeoutMs: 3000 });
    await tools.terminal({ command, background: true, timeoutMs: 4000 });
    assert.equal(calls.length, 2);
    assert.equal(calls[0][1], command);
    assert.equal(calls[0][2], 3000);
    assert.equal(calls[1][1], command);
    assert.equal(calls[1][2], undefined);
    assert.equal(calls[1][3].timeoutPolicy, "none");
    assert.notEqual(calls[0][0], calls[1][0]);
});

test("terminal_input retains raw TAB and Ctrl+C control contract", async () => {
    const { tools, calls } = await loadPackage("super_admin");
    await tools.terminal_input({ sessionId: "interactive", input: "\t" });
    await tools.terminal_input({ sessionId: "interactive", control: "ctrl+c" });
    assert.equal(calls[0][1].input, "\t");
    assert.equal(calls[1][1].control, "ctrl+c");
});
