import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { readFile } from "node:fs/promises";
import { createInterface } from "node:readline";
import test from "node:test";
import vm from "node:vm";

const windows = process.platform === "win32";
const distro = process.env.KIYORI_PTY_WSL_DISTRO;

async function fixture(t) {
    const source = await readFile(new URL("code_runner_pty_fixture.py", import.meta.url));
    const loader = "import base64,sys;exec(base64.b64decode(sys.argv[1]))";
    const args = ["-c", loader, source.toString("base64")];
    const child = windows
        ? spawn("wsl", ["-d", distro, "--exec", "python3", ...args])
        : spawn("python3", args);
    let stderr = "";
    child.stderr.on("data", data => { stderr += data; });
    const replies = createInterface({ input: child.stdout })[Symbol.asyncIterator]();
    const done = new Promise(resolve => child.on("close", code => resolve(code)));
    t.after(async () => {
        child.stdin.end();
        const timer = setTimeout(() => child.kill(), 5000);
        const code = await done;
        clearTimeout(timer);
        assert.equal(code, 0, stderr);
    });
    async function request(value) {
        child.stdin.write(JSON.stringify(value) + "\n");
        const reply = await replies.next();
        assert.equal(reply.done, false, stderr);
        return JSON.parse(reply.value);
    }
    const exec = command => request({ command });
    const completions = [], calls = [], writes = [];
    const artifactRoot = JSON.parse((await exec("python3 -c 'import os,json; print(json.dumps(os.path.join(os.environ[\"HOME\"], \"AI artifacts\")))'")).output.trim());
    const context = vm.createContext({
        exports: {}, console: { error() {} }, complete: value => completions.push(value),
        getArtifactPath: env => { assert.equal(env, "linux"); return artifactRoot; },
        getArtifactPaths: () => ({ android: '/unused', linux: artifactRoot, linuxIsLocal: true }),
        Tools: { Files: { write: async (path, content, append, env) => {
            assert.equal(append, false); assert.equal(env, 'linux');
            writes.push({ path, content });
            return request({ path, content });
        } }, System: { terminal: {
            create: async () => ({ sessionId: "code_runner_session" }),
            exec: async (sessionId, command) => {
                calls.push(command);
                // Exercise actual Python, but avoid installing packages in this local regression.
                if (command.includes("-m pip --version")) return { exitCode: 0, output: "pip fixture", sessionId };
                return { ...await exec(command), sessionId };
            },
        } } },
    });
    vm.runInContext(await readFile(new URL("../../app/src/main/assets/packages/code_runner.js", import.meta.url), "utf8"), context);
    return {
        exec, calls, writes, artifactRoot,
        async run(params, tool = "run_python") {
            await context.exports[tool](params);
            return completions.at(-1);
        },
    };
}

const pty = (name, fn) => test(name, { skip: windows && !distro, timeout: 30000 }, fn);

pty('relative Python output stays in the configured artifact directory while HOME retains dependencies', async t => {
    const f = await fixture(t);
    const result = await f.run({ script: "import os,json,pathlib; pathlib.Path('报告.txt').write_text('ok'); print(json.dumps({'cwd': os.getcwd(), 'home': os.environ['HOME']}))" });
    assert.equal(result.success, true, JSON.stringify(result));
    const state = JSON.parse(result.data);
    assert.equal(state.cwd, f.artifactRoot + '/code-runner');
    assert.notEqual(state.cwd, state.home);
    assert.equal((await f.run({ script: "from pathlib import Path; print(Path('报告.txt').read_text())" })).data, 'ok');
});

pty("complex quotes, tabs, Unicode, CRLF and a long line execute unchanged", async t => {
    const f = await fixture(t);
    const value = "中文😀 ' \" \\ $HOME `echo bad` ! " + "x".repeat(18000);
    const result = await f.run({ script: `import json\r\nif True:\r\n\tprint(json.dumps(${JSON.stringify(value)}, ensure_ascii=False))\r\n` });
    assert.equal(result.success, true, JSON.stringify(result));
    assert.equal(JSON.parse(result.data), value);
});

pty("Python input receives EOF promptly and the same session stays usable", async t => {
    const f = await fixture(t);
    const result = await f.run({ script: "input('need input: ')" });
    assert.equal(result.success, false);
    assert.match(result.message, /EOFError/);
    assert.equal((await f.run({ script: "print('alive')" })).data, "alive");
});

pty("stdout resembling a shell error is successful Python output", async t => {
    const f = await fixture(t);
    const result = await f.run({ script: "print('bash: sample: command not found')" });
    assert.equal(result.success, true, JSON.stringify(result));
    assert.equal(result.data, "bash: sample: command not found");
});

pty("flags cannot replace script execution or inject shell statements", async t => {
    const f = await fixture(t);
    for (const python_flags of ["-i", "-Oi", "-c 'input()'", "-m timeit", "-u; printf injected #", '-W "unterminated', "--help", "--version", "-W", "-X", "-u\\", "--check-hash-based-pycs", "--check-hash-based-pycs invalid", "-u\0"]) {
        const count = f.calls.length;
        const result = await f.run({ script: "print('expected')", python_flags });
        assert.equal(result.success, false, python_flags);
        assert.match(result.message, /python_flags/, python_flags);
        assert.equal(f.calls.length, count, `invalid flags must fail before terminal work: ${python_flags}`);
    }
});

pty("quoted option values and pipe-separated script arguments remain literal", async t => {
    const f = await fixture(t);
    const result = await f.run({ script: "import sys,json; print(json.dumps(sys.argv[1:]))", python_flags: '-O -u -W "ignore:foo bar:UserWarning" -X utf8', script_args: "a'b|double\"quote|$(echo BAD)|`echo BAD`|arg with space" });
    assert.equal(result.success, true, JSON.stringify(result));
    assert.deepEqual(JSON.parse(result.data), ["a'b", 'double"quote', "$(echo BAD)", "`echo BAD`", "arg with space"]);
});

pty("syntax and runtime errors preserve diagnostics and remove temporary source", async t => {
    const f = await fixture(t);
    for (const script of ["print('unterminated)", "raise ValueError('visible failure')"]) {
        const result = await f.run({ script });
        assert.equal(result.success, false);
        assert.match(result.message, /SyntaxError|ValueError/);
        const path = f.writes.at(-1).path;
        assert.equal((await f.exec(`test ! -e '${path}'`)).exitCode, 0);
    }
});

pty("large Unicode source is written outside PTY and executes without truncation", async t => {
    const f = await fixture(t);
    const source = "# 论文表格公式中文注释\n".repeat(30000) + "print('large source complete')";
    const result = await f.run({ script: source });
    assert.equal(result.success, true, JSON.stringify(result));
    assert.equal(result.data, 'large source complete');
    assert.equal(f.writes.at(-1).content, source);
    assert.ok(f.calls.every(command => !command.includes('论文表格公式中文注释')));
});

pty("timeouts are explicit and cleanup permits the next Python invocation", async t => {
    const f = await fixture(t);
    const result = await f.run({ script: "import time; print('before wait'); time.sleep(30)" });
    assert.equal(result.success, false);
    assert.match(result.message, /超时/);
    assert.match(result.message, /before wait/);
    assert.equal((await f.run({ script: "print('after timeout')" })).data, "after timeout");
});

pty("file runner supports relative paths beginning with a dash", async t => {
    const f = await fixture(t);
    assert.equal((await f.exec("mkdir project && cd project")).exitCode, 0);
    for (const file_path of ["-sample.py", "-", "space ' quoted.py"]) {
        const quoted = "'" + file_path.replaceAll("'", "'\\''") + "'";
        assert.equal((await f.exec(`printf "print('file ok')\\n" > ${quoted}`)).exitCode, 0);
        const result = await f.run({ file_path }, "run_python_file");
        assert.equal(result.success, true, JSON.stringify(result));
        assert.equal(result.data, "file ok");
    }
});

pty("file runner shares EOF and validation rules without deleting user files", async t => {
    const f = await fixture(t);
    assert.equal((await f.exec("printf \"input('file input: ')\\n\" > input.py")).exitCode, 0);
    const invalid = await f.run({ file_path: "input.py", python_flags: "-i" }, "run_python_file");
    assert.match(invalid.message, /python_flags/);
    assert.equal(f.calls.length, 0);
    const result = await f.run({ file_path: "input.py" }, "run_python_file");
    assert.equal(result.success, false);
    assert.match(result.message, /EOFError/);
    assert.equal((await f.exec("test -f input.py")).exitCode, 0);
});

pty("option values cannot run substitutions and standard Python switches still work", async t => {
    const f = await fixture(t);
    const result = await f.run({
        script: "import sys,json; print(json.dumps([sys.warnoptions, sys._xoptions, sys.flags.optimize]))",
        python_flags: '-OOu -W "ignore:$(touch unexpected):UserWarning" -X "probe=`touch unexpected`" --check-hash-based-pycs always',
    });
    assert.equal(result.success, true, JSON.stringify(result));
    assert.deepEqual(JSON.parse(result.data), [["ignore:$(touch unexpected):UserWarning"], { probe: "`touch unexpected`" }, 2]);
    assert.equal((await f.exec("test ! -e unexpected")).exitCode, 0);
    assert.equal((await f.run({ script: "pass" })).data, "");
    const exited = await f.run({ script: "import sys; sys.exit(7)" });
    assert.equal(exited.success, false);
    assert.match(exited.message, /exitCode=7/);
});

pty("invalid NUL inputs fail before any terminal work", async t => {
    const f = await fixture(t);
    for (const params of [{ script: "print('a\0b')" }, { script: "pass", script_args: "a\0b" }]) {
        assert.match((await f.run(params)).message, /NUL/);
    }
    assert.match((await f.run({ file_path: "a\0b" }, "run_python_file")).message, /NUL/);
    assert.equal(f.calls.length, 0);
});
