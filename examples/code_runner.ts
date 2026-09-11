/* METADATA
{
  name: code_runner
  display_name: {
    zh: "代码运行器"
    en: "Code Runner"
  }
  description: { zh: "执行 JavaScript、Python、Ruby、Go、Rust、C 或 C++ 代码字符串和本地文件，并返回标准输出、错误输出与运行结果。", en: "Execute JavaScript, Python, Ruby, Go, Rust, C, or C++ source strings and local files, returning stdout, stderr, and execution results." }
  enabledByDefault: true
  
  category: "Development"
  // Multiple tools in this package
  tools: [
    {
      name: run_javascript_es5
      description: { zh: "运行自定义 JavaScript (ES5) 脚本。会捕获 console.log 的输出以及最后表达式的结果；也支持显式 return。", en: "Run custom JavaScript (ES5). Captures console.log output and the final expression result; explicit return is also supported." }
      // This tool takes parameters
      parameters: [
        {
          name: script
          description: { zh: "要执行的 JavaScript 脚本内容", en: "JavaScript script content to execute." }
          type: string
          required: true
        }
      ]
    },
    {
      name: run_javascript_file
      description: { zh: "运行 Ubuntu 环境中的 JavaScript (ES5) 文件。会捕获 console.log 的输出以及最后表达式的结果；也支持显式 return。", en: "Run a JavaScript (ES5) file in the Ubuntu environment. Captures console.log output and the final expression result; explicit return is also supported." }
      parameters: [
        {
          name: file_path
          description: { zh: "JavaScript 文件路径", en: "Path to the JavaScript file." }
          type: string
          required: true
        }
      ]
    },
    {
      name: run_javascript_node
      description: { zh: "使用 Node.js 运行 JavaScript 脚本。返回 stdout/stderr 输出。", en: "Run JavaScript using Node.js. Returns stdout/stderr output." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 JavaScript 脚本内容", en: "JavaScript script content to execute." }
          type: string
          required: true
        },
        {
          name: node_flags
          description: { zh: "Node.js 解释器选项，默认为空。可自定义如 --trace-warnings、--no-warnings 等", en: "Node.js interpreter flags (default: empty). Examples: --trace-warnings, --no-warnings." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_javascript_node_file
      description: { zh: "使用 Node.js 运行 JavaScript 文件。返回 stdout/stderr 输出。", en: "Run a JavaScript file using Node.js. Returns stdout/stderr output." }
      parameters: [
        {
          name: file_path
          description: { zh: "JavaScript 文件路径", en: "Path to the JavaScript file." }
          type: string
          required: true
        },
        {
          name: node_flags
          description: { zh: "Node.js 解释器选项，默认为空。可自定义如 --trace-warnings、--no-warnings 等", en: "Node.js interpreter flags (default: empty). Examples: --trace-warnings, --no-warnings." }
          type: string
          required: false
        }
      ]
    },
    {
      name: install_node_packages
      description: { zh: "在持久 Node 工作目录($HOME/.code_runner/node)中安装 pnpm 包", en: "Install packages with pnpm in the persistent Node workspace ($HOME/.code_runner/node)." }
      parameters: [
        {
          name: packages
          description: { zh: "要安装的包名（用 | 分隔），例如 axios|lodash|@types/node", en: "Package names to install, separated by | (e.g. axios|lodash|@types/node)." }
          type: string
          required: true
        },
        {
          name: save_dev
          description: { zh: "是否作为开发依赖安装（pnpm add -D）", en: "Whether to install as dev dependency (pnpm add -D)." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: install_python_packages
      description: { zh: "在持久虚拟环境(~/.code_runner/py)中安装 Python 包（使用 pip）", en: "Install Python packages in the persistent virtual environment (~/.code_runner/py) using pip." }
      parameters: [
        {
          name: packages
          description: { zh: "要安装的包名（用 | 分隔），例如 numpy|pydantic==2.*", en: "Package names to install, separated by | (e.g. numpy|pydantic==2.*)." }
          type: string
          required: true
        },
        {
          name: upgrade
          description: { zh: "是否升级已安装的包，等价于 pip -U", en: "Whether to upgrade already installed packages (equivalent to pip -U)." }
          type: boolean
          required: false
        }
      ]
    },
    {
      name: run_python
      description: { zh: "以临时文件非交互运行 Python 源码，无需 Shell 转义；捕获输出，标准输入为 EOF。需要交互时使用 super_admin:terminal。", en: "Run Python source non-interactively from a temporary file, without shell escaping. Captures output; stdin is EOF. Use super_admin:terminal for interactive programs." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Python 脚本内容", en: "Python script content to execute." }
          type: string
          required: true
        },
        {
          name: python_flags
          description: { zh: "以空格分隔的解释器选项，支持引号包裹值，如 -O -W 'ignore::UserWarning' -X utf8；不执行 Shell 展开，不接受 -i/-c/-m 或帮助/版本选项。输出默认无缓冲。", en: "Space-separated interpreter options with quoted values, e.g. -O -W 'ignore::UserWarning' -X utf8. No shell expansion, -i/-c/-m, help or version modes. Output is unbuffered by default." }
          type: string
          required: false
        },
        {
          name: script_args
          description: { zh: "传递给 Python 脚本的参数，使用 | 分隔，例如 arg1|arg with space|--name=alice", en: "Arguments passed to the Python script, separated by | (e.g. arg1|arg with space|--name=alice)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_python_file
      description: { zh: "非交互运行 Ubuntu 会话中的 Python 文件，保留相对路径语义；捕获输出，标准输入为 EOF。需要交互时使用 super_admin:terminal。", en: "Run a Python file in the Ubuntu session non-interactively, retaining relative paths. Captures output; stdin is EOF. Use super_admin:terminal for interactive programs." }
      parameters: [
        {
          name: file_path
          description: { zh: "Python 文件路径", en: "Path to the Python file." }
          type: string
          required: true
        },
        {
          name: python_flags
          description: { zh: "以空格分隔的解释器选项，支持引号包裹值，如 -O -W 'ignore::UserWarning' -X utf8；不执行 Shell 展开，不接受 -i/-c/-m 或帮助/版本选项。输出默认无缓冲。", en: "Space-separated interpreter options with quoted values, e.g. -O -W 'ignore::UserWarning' -X utf8. No shell expansion, -i/-c/-m, help or version modes. Output is unbuffered by default." }
          type: string
          required: false
        },
        {
          name: script_args
          description: { zh: "传递给 Python 文件的参数，使用 | 分隔，例如 arg1|arg with space|--name=alice", en: "Arguments passed to the Python file, separated by | (e.g. arg1|arg with space|--name=alice)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_ruby
      description: { zh: "运行自定义 Ruby 脚本", en: "Run custom Ruby scripts." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Ruby 脚本内容", en: "Ruby script content to execute." }
          type: string
          required: true
        },
        {
          name: ruby_flags
          description: { zh: "Ruby 解释器选项，默认为空。可自定义如 --jit（JIT 编译）等", en: "Ruby interpreter flags (default: empty). Example: --jit." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_ruby_file
      description: { zh: "运行 Ruby 文件", en: "Run a Ruby file." }
      parameters: [
        {
          name: file_path
          description: { zh: "Ruby 文件路径", en: "Path to the Ruby file." }
          type: string
          required: true
        },
        {
          name: ruby_flags
          description: { zh: "Ruby 解释器选项，默认为空。可自定义如 --jit（JIT 编译）等", en: "Ruby interpreter flags (default: empty). Example: --jit." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_go
      description: { zh: "运行自定义 Go 代码", en: "Run custom Go code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Go 代码内容", en: "Go source code to execute." }
          type: string
          required: true
        },
        {
          name: build_flags
          description: { zh: "Go 编译选项，默认为空。可自定义如 -ldflags='-s -w'（减小二进制体积）等", en: "Go build flags (default: empty). Example: -ldflags='-s -w' (reduce binary size)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_go_file
      description: { zh: "运行 Go 文件", en: "Run a Go file." }
      parameters: [
        {
          name: file_path
          description: { zh: "Go 文件路径", en: "Path to the Go file." }
          type: string
          required: true
        },
        {
          name: build_flags
          description: { zh: "Go 编译选项，默认为空。可自定义如 -ldflags='-s -w'（减小二进制体积）等", en: "Go build flags (default: empty). Example: -ldflags='-s -w' (reduce binary size)." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_rust
      description: { zh: "运行自定义 Rust 代码", en: "Run custom Rust code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 Rust 代码内容", en: "Rust source code to execute." }
          type: string
          required: true
        },
        {
          name: cargo_flags
          description: { zh: "Cargo 构建选项，默认为 --release。可自定义如 空字符串（调试模式）、--release --features xxx 等", en: "Cargo build flags (default: --release). Examples: empty string (debug), --release --features xxx." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_rust_file
      description: { zh: "运行 Rust 文件", en: "Run a Rust file." }
      parameters: [
        {
          name: file_path
          description: { zh: "Rust 文件路径", en: "Path to the Rust file." }
          type: string
          required: true
        },
        {
          name: cargo_flags
          description: { zh: "Cargo 构建选项，默认为 --release。可自定义如 空字符串（调试模式）、--release --features xxx 等", en: "Cargo build flags (default: --release). Examples: empty string (debug), --release --features xxx." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_c
      description: { zh: "运行自定义 C 代码", en: "Run custom C code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 C 代码内容", en: "C source code to execute." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_c_file
      description: { zh: "运行 C 文件", en: "Run a C file." }
      parameters: [
        {
          name: file_path
          description: { zh: "C 文件路径", en: "Path to the C file." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_cpp
      description: { zh: "运行自定义 C++ 代码", en: "Run custom C++ code." }
      parameters: [
        {
          name: script
          description: { zh: "要执行的 C++ 代码内容", en: "C++ source code to execute." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    },
    {
      name: run_cpp_file
      description: { zh: "运行 C++ 文件", en: "Run a C++ file." }
      parameters: [
        {
          name: file_path
          description: { zh: "C++ 文件路径", en: "Path to the C++ file." }
          type: string
          required: true
        },
        {
          name: compile_flags
          description: { zh: "编译选项，默认为 -O3 -march=native -fopenmp。可自定义如 -O2、-O0 -g 等", en: "Compile flags (default: -O3 -march=native -fopenmp). Examples: -O2, -O0 -g." }
          type: string
          required: false
        }
      ]
    },
    {
      name: get_environment_info
      description: { zh: "报告 code_runner 当前终端、Ubuntu/proot、Python 虚拟环境、Node 工作区和 PATH。", en: "Report the code_runner terminal, Ubuntu/proot, Python virtual environment, Node workspace, and PATH." }
      parameters: []
    }
  ]
}*/

const codeRunner = (function () {

  const CARGO_MIRROR_ENV = 'export CARGO_REGISTRIES_CRATES_IO_REPLACE_WITH="ustc" && export CARGO_REGISTRIES_USTC_INDEX="https://mirrors.ustc.edu.cn/crates.io-index"';
  const RUST_TOOLCHAIN_ENV = 'export PATH="$HOME/.cargo/bin:$PATH"';
  const CODE_RUNNER_SESSION_NAME = "code_runner_session";
  const DEFAULT_COMMAND_TIMEOUT_MS = 120000;
  const NODE_WORKSPACE_DIR = "$HOME/.code_runner/node";
  let writeFileSequence = 0;
  let tempPathSequence = 0;

  function createTempToken(prefix: string): string {
    tempPathSequence += 1;
    return `${prefix}_${Date.now()}_${tempPathSequence}`;
  }

  // Keep one real PTY session so the software terminal can show code_runner commands and output.
  async function executeTerminalCommand(command: string, timeoutMs: number = DEFAULT_COMMAND_TIMEOUT_MS): Promise<import("./types/results").TerminalCommandResultData> {
    const session = await Tools.System.terminal.create(CODE_RUNNER_SESSION_NAME, "~");
    return await Tools.System.terminal.exec(session.sessionId, command, timeoutMs);
  }

  function buildSubshellCommand(directory: string, command: string): string {
    const directoryArgument = directory === "$HOME" || directory.startsWith("$HOME/")
      ? `"${directory.replace(/["\\`]/g, "\\$&")}"`
      : `'${escapeForShell(directory)}'`;
    // Temporary compilers must not leave the long-lived PTY inside a directory that cleanup removes.
    return `(cd ${directoryArgument} && ${command})`;
  }

  async function executeFromHome(command: string, timeoutMs: number = DEFAULT_COMMAND_TIMEOUT_MS): Promise<import("./types/results").TerminalCommandResultData> {
    return executeTerminalCommand(buildSubshellCommand("$HOME", command), timeoutMs);
  }

  // 运行用户源码与安装依赖是不同职责：依赖仍留在 HOME，源码的相对输出进入产物区。
  function resolveArtifactRoot(): string {
    const root = getArtifactPath("linux");
    if (!root.startsWith("/") || /[\0\r\n\\]/.test(root) || root.split("/").includes("..")) {
      throw new Error("Invalid Linux artifact directory");
    }
    return root;
  }

  async function executeInArtifactDirectory(command: string, root: string, timeoutMs: number = DEFAULT_COMMAND_TIMEOUT_MS): Promise<import("./types/results").TerminalCommandResultData> {
    const directory = `${root.replace(/\/+$/, "")}/code-runner`;
    // 校验通过后再建会话，避免路径无效时留下一个没有用途的终端会话。
    const session = await Tools.System.terminal.create(CODE_RUNNER_SESSION_NAME, root);
    return Tools.System.terminal.exec(session.sessionId, `mkdir -p -- '${escapeForShell(directory)}' && ${buildSubshellCommand(directory, command)}`, timeoutMs);
  }

  // Ensure a persistent Python venv under ~/.code_runner/py and return python/pip paths
  async function ensurePersistentVenv(): Promise<{ pythonBin: string; pipBin: string }> {
    const venvDir = "~/.code_runner/py";
    const pythonBin = `${venvDir}/bin/python`;
    const pipBin = `${venvDir}/bin/pip`;

    const exists = await executeFromHome(`[ -x ${pythonBin} ] && ${pythonBin} -m pip --version`);
    if (exists.exitCode === 0) {
      return { pythonBin, pipBin };
    }

    const directoryState = await executeFromHome(`[ -e ${venvDir} ] && printf PRESENT || printf ABSENT`);
    if (directoryState.exitCode !== 0) {
      throw new Error(`检查持久 venv 目录失败：\n${directoryState.output}`);
    }
    if (directoryState.output.trim() === "PRESENT") {
      throw new Error(`持久 venv 不完整：${venvDir}。请修复或移除该目录后重试。`);
    }

    const setup = await executeFromHome(`python3 -m venv ${venvDir}`);
    if (setup.exitCode !== 0) {
      throw new Error(`创建持久 venv 失败：\n${setup.output}`);
    }
    const verify = await executeFromHome(`[ -x ${pythonBin} ] && ${pythonBin} -m pip --version`);
    if (verify.exitCode !== 0) {
      throw new Error(`持久 venv 创建后校验失败：\n${verify.output}`);
    }
    return { pythonBin, pipBin };
  }

  async function get_environment_info() {
    const { pythonBin } = await ensurePersistentVenv();
    const probeCommand = [
      `printf 'session_name=${CODE_RUNNER_SESSION_NAME}\\n'`,
      "printf 'cwd='; pwd",
      `printf 'python_bin=${pythonBin}\\n'`,
      `${pythonBin} -c 'import json,os,sys; print("python_runtime=" + json.dumps({"executable":sys.executable,"prefix":sys.prefix,"base_prefix":sys.base_prefix,"virtualenv":sys.prefix != sys.base_prefix}, sort_keys=True))'`,
      "printf 'python3_bin='; command -v python3",
      `printf 'pip='; ${pythonBin} -m pip --version`,
      "printf 'node_bin='; command -v node",
      "printf 'node_version='; node --version",
      "printf 'npm_prefix='; npm prefix -g",
      "printf 'path=%s\\n' \"$PATH\"",
      "printf 'ubuntu='; . /etc/os-release; printf '%s %s\\n' \"$ID\" \"$VERSION_ID\""
    ].join("; ");
    const result = await executeFromHome(probeCommand);
    if (result.exitCode !== 0) {
      throw new Error(`读取 code_runner 环境失败：\\n${result.output}`);
    }
    return {
      sessionId: result.sessionId,
      output: result.output.trim()
    };
  }

  // Install Python packages into the persistent venv using pip
  async function install_python_packages(params: { packages: string; upgrade?: boolean }) {
    const raw = (params.packages || "").trim();
    const pkgs = raw
      .split("|")
      .map(s => s.trim())
      .filter(s => s.length > 0);
    if (!pkgs.length) throw new Error("请提供要安装的包列表（用 | 分隔）packages");

    const upgradeFlag = params.upgrade ? "-U" : "";
    const { pythonBin } = await ensurePersistentVenv();

    const packageArgs = pkgs.map(p => `'${escapeForShell(p)}'`).join(" ");
    const r2 = await executeFromHome(`${pythonBin} -m pip install ${upgradeFlag} ${packageArgs}`.trim());
    if (r2.exitCode !== 0) {
      throw new Error(`安装依赖失败：\n${r2.output}`);
    }
    return `Python environment: ${pythonBin}\nInstalled with pip:\n${r2.output}`.trim();
  }

  async function ensureNodeAvailable() {
    const nodeCheckResult = await executeFromHome("node --version");
    if (nodeCheckResult.exitCode !== 0) {
      throw new Error("Node.js 不可用，请确保已安装 Node.js");
    }
  }

  async function ensurePersistentNodeWorkspace(): Promise<{ workspaceDir: string }> {
    await ensureNodeAvailable();

    const createDirResult = await executeFromHome(`mkdir -p ${NODE_WORKSPACE_DIR}`);
    if (createDirResult.exitCode !== 0) {
      throw new Error(`创建 Node 工作目录失败:\n${createDirResult.output}`);
    }

    const hasPackageJson = await executeFromHome(`[ -f ${NODE_WORKSPACE_DIR}/package.json ] && echo OK || echo NO`);
    if (hasPackageJson.exitCode !== 0) {
      throw new Error(`检查 Node 工作目录失败:\n${hasPackageJson.output}`);
    }
    if (!hasPackageJson.output.includes("OK")) {
      await writeTextFile(
        `${NODE_WORKSPACE_DIR}/package.json`,
        `{
  "name": "code-runner-node-workspace",
  "private": true
}`
      );
    }

    return { workspaceDir: NODE_WORKSPACE_DIR };
  }

  async function install_node_packages(params: { packages: string; save_dev?: boolean }) {
    const raw = (params.packages || "").trim();
    const pkgs = raw
      .split("|")
      .map(s => s.trim())
      .filter(s => s.length > 0);
    if (!pkgs.length) throw new Error("请提供要安装的包列表（用 | 分隔）packages");

    const { workspaceDir } = await ensurePersistentNodeWorkspace();
    const saveFlag = params.save_dev ? "-D" : "--save";
    const packageArgs = pkgs.map(p => `'${escapeForShell(p)}'`).join(" ");
    const result = await executeTerminalCommand(buildSubshellCommand(workspaceDir, `pnpm add ${saveFlag} ${packageArgs}`));
    if (result.exitCode !== 0) {
      throw new Error(`安装 pnpm 依赖失败:\n${result.output}`);
    }
    return `Installed with pnpm in ${workspaceDir}:\n${result.output}`.trim();
  }

  // Helper function to safely escape strings for shell commands
  function escapeForShell(str: string): string {
    return str.replace(/'/g, "'\\''");
  }

  function createHereDocMarker(content: string): string {
    let marker = `__CODE_RUNNER_FILE_${Date.now()}_${writeFileSequence++}__`;
    while (content.includes(marker)) {
      marker += "_";
    }
    return marker;
  }

  function buildWriteFileCommand(filePath: string, content: string): string {
    const marker = createHereDocMarker(content);
    const normalizedContent = content.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    const body = normalizedContent.endsWith("\n") ? normalizedContent : `${normalizedContent}\n`;
    // Internal workspace paths may intentionally use $HOME; double quotes expand it while keeping the path literal.
    const pathArgument = filePath.startsWith("$HOME/")
      ? `"${filePath.replace(/["\\`]/g, "\\$&")}"`
      : `'${escapeForShell(filePath)}'`;
    // The delimiter must end with its own LF; executeFromHome wraps this command in a subshell,
    // so omitting it would concatenate the closing ')' and make Bash treat the heredoc as unterminated.
    return `cat > ${pathArgument} <<'${marker}'\n${body}${marker}\n`;
  }

  async function writeTextFile(filePath: string, content: string): Promise<void> {
    const result = await executeFromHome(buildWriteFileCommand(filePath, content));
    if (result.exitCode !== 0) {
      throw new Error(`写入临时文件失败：${filePath}\n${result.output}`);
    }
  }

  function buildPipeSeparatedShellArgs(raw?: string): string {
    if (!raw || raw.trim() === "") {
      return "";
    }

    return raw
      .split("|")
      .map(part => part.trim())
      .filter(part => part.length > 0)
      .map(part => `'${escapeForShell(part)}'`)
      .join(" ");
  }

  function buildPythonFlags(raw: string = ""): string {
    // 参数是 argv 数据，不是 Shell 程序。先解析引号，再逐项引用；否则 -c 或分号能
    // 绕过文件执行，-i 则在脚本完成后占用共享 PTY，Ctrl+C 也不会退出解释器。
    if (typeof raw !== "string" || raw.includes("\0")) {
      throw new Error("python_flags 必须是不含 NUL 的字符串");
    }
    const args: string[] = [];
    let word = "";
    let quote = "";
    let started = false;
    for (let i = 0; i < raw.length; i++) {
      const char = raw[i];
      if (char === "\\" && quote !== "'") {
        const next = raw[i + 1];
        if (next === undefined) throw new Error("python_flags 末尾的反斜杠缺少字符");
        if (quote === '"' && !['"', "\\", "$", "`", "\n"].includes(next)) {
          word += char;
        } else {
          i += 1;
          if (next !== "\n") word += next;
        }
        started = true;
      } else if (quote) {
        if (char === quote) quote = "";
        else word += char;
      } else if (char === "'" || char === '"') {
        quote = char;
        started = true;
      } else if (/\s/.test(char)) {
        if (started) args.push(word);
        word = "";
        started = false;
      } else {
        word += char;
        started = true;
      }
    }
    if (quote) throw new Error("python_flags 存在未闭合的引号");
    if (started) args.push(word);
    for (let i = 0; i < args.length; i++) {
      const arg = args[i];
      if (arg === "--check-hash-based-pycs") {
        if (!["default", "always", "never"].includes(args[++i] ?? "")) {
          throw new Error("python_flags 的 --check-hash-based-pycs 需要 default、always 或 never");
        }
      } else if (/^-[bBdEIOPqRsSuvx]*[WX]/.test(arg)) {
        // -W/-X 消费同 token 的余下字符或下一个完整参数；值中的 -i 等文字不是选项。
        if (/^-[bBdEIOPqRsSuvx]*[WX]$/.test(arg) && ++i >= args.length) {
          throw new Error("python_flags 的 -W/-X 缺少参数值");
        }
      } else if (!/^-[bBdEIOPqRsSuvx]+$/.test(arg)) {
        throw new Error("python_flags 只接受脚本解释器选项；不支持交互、-c/-m、帮助/版本、位置参数或 Shell 语法");
      }
    }
    return args.map(arg => `'${escapeForShell(arg)}'`).join(" ");
  }

  function pythonOutput(result: import("./types/results").TerminalCommandResultData): string {
    // stdout/stderr 可以合法打印 Shell 错误示例，不能凭输出正文推翻真实退出码。
    if (result.timedOut) {
      throw new Error(`Python 执行超时（命令限时 ${DEFAULT_COMMAND_TIMEOUT_MS} ms），已请求终止；请检查循环、阻塞调用或交互输入。\n${result.output}`);
    }
    if (result.exitCode !== 0) {
      throw new Error(`Python 执行失败（exitCode=${result.exitCode}；标准输入为 EOF）：\n${result.output}`);
    }
    return result.output.trim();
  }

  // Helper function to execute JavaScript code and capture logs/completion value.
  async function executeJavaScript(script: string): Promise<string> {
    const logs: string[] = [];

    // Create a proxy for the console object
    const consoleProxy = {
      log: (...args: any[]) => {
        const formattedArgs = args.map(arg => {
          if (arg === null) return 'null';
          if (arg === undefined) return 'undefined';
          if (typeof arg === 'object') {
            try { return JSON.stringify(arg, null, 2); } catch (e) { return arg.toString(); }
          }
          return arg.toString();
        });
        logs.push(formattedArgs.join(' '));
      },
      warn: (...args: any[]) => {
        const formattedArgs = args.map(arg => {
          if (arg === null) return 'null';
          if (arg === undefined) return 'undefined';
          if (typeof arg === 'object') {
            try { return JSON.stringify(arg, null, 2); } catch (e) { return arg.toString(); }
          }
          return arg.toString();
        });
        logs.push(`WARN: ${formattedArgs.join(' ')}`);
      },
      error: (...args: any[]) => {
        const formattedArgs = args.map(arg => {
          if (arg === null) return 'null';
          if (arg === undefined) return 'undefined';
          if (typeof arg === 'object') {
            try { return JSON.stringify(arg, null, 2); } catch (e) { return arg.toString(); }
          }
          return arg.toString();
        });
        logs.push(`ERROR: ${formattedArgs.join(' ')}`);
      },
    };

    try {
      let returnValue;
      try {
        // Direct eval follows the JavaScript completion-value rule, so a final expression is
        // observable instead of being discarded by an IIFE. The script is still isolated in a
        // Function scope and receives only the console proxy as an explicit host binding.
        const evaluateCompletion = new Function('console', `
          "use strict";
          return eval(${JSON.stringify(script)});
        `);
        returnValue = evaluateCompletion(consoleProxy);
      } catch (error) {
        // Top-level return is invalid in eval, but is part of the existing tool contract. Parsing
        // fails before execution, so retrying through an isolated Function body cannot duplicate
        // user side effects or console output.
        if (!(error instanceof SyntaxError) || !/\breturn\b/i.test(error.message)) {
          throw error;
        }
        const executeFunctionBody = new Function('console', `
          "use strict";
          ${script}
        `);
        returnValue = executeFunctionBody(consoleProxy);
      }

      let output = logs.join('\n');

      if (returnValue !== undefined) {
        if (output) {
          output += '\n';
        }
        let formattedReturnValue;
        if (returnValue === null) {
          formattedReturnValue = 'null';
        } else if (typeof returnValue === 'object') {
          try {
            formattedReturnValue = JSON.stringify(returnValue, null, 2);
          } catch (e) {
            formattedReturnValue = returnValue.toString();
          }
        } else {
          formattedReturnValue = returnValue.toString();
        }
        output += `Return value: ${formattedReturnValue}`;
      }

      return output || "(No output from console.log or return value)";

    } catch (e: any) {
      console.error("JavaScript execution failed", e);
      const errorOutput = logs.join('\n');
      if (errorOutput) {
        throw new Error(`Script execution failed: ${e.message}\n\nLogs before error:\n${errorOutput}`);
      } else {
        throw new Error(`Script execution failed: ${e.message}`);
      }
    }
  }

  // Exit codes are authoritative; only detect launcher diagnostics that can be emitted with rc=0.
  function hasError(output: string): boolean {
    return /(?:^|\n)(?:bash|sh|busybox): .*?(?:command not found|No such file or directory)(?:\n|$)/i.test(output);
  }

  async function main() {
    // Ensure /tmp exists
    const tmpResult = await executeFromHome("mkdir -p /tmp");
    if (tmpResult.exitCode !== 0) {
      throw new Error(`创建临时目录失败：\n${tmpResult.output}`);
    }

    const results = {
      javascript: await testJavaScript(),
      python: await testPython(),
      ruby: await testRuby(),
      go: await testGo(),
      rust: await testRust(),
      c: await testC(),
      cpp: await testCpp()
    };

    // Format results for display
    let summary = "代码执行器功能测试结果：\n";
    for (const [lang, result] of Object.entries(results)) {
      summary += `${lang}: ${result.success ? '✅ 成功' : '❌ 失败'} - ${result.message}\n`;
    }

    return summary;
  }

  // 测试JavaScript执行功能
  async function testJavaScript() {
    try {
      // 测试简单的JS代码
      const script = "console.log('JavaScript 运行正常'); const testVar = 42; return '测试值: ' + testVar;";
      const result = await executeJavaScript(script);
      const expectedOutput = `JavaScript 运行正常\nReturn value: "测试值: 42"`;

      if (result !== expectedOutput) {
        return { success: false, message: `JavaScript执行器测试失败: 期望 "${expectedOutput}", 实际 "${result}"` };
      }
      return { success: true, message: "JavaScript执行器测试成功" };
    } catch (error) {
      console.error("JavaScript executor self-test failed", error);
      return { success: false, message: `JavaScript执行器测试失败: ${error.message}` };
    }
  }

  // 测试Python执行功能  
  async function testPython() {
    try {
      // Validate the same persistent venv used by run_python and package installs.
      const { pythonBin } = await ensurePersistentVenv();
      const pythonCheckResult = await executeFromHome(`${pythonBin} --version`);
      if (pythonCheckResult.exitCode !== 0 || hasError(pythonCheckResult.output)) {
        return { success: false, message: "Python venv 不可用，请检查 ~/.code_runner/py" };
      }

      // 测试简单的Python代码
      const script = "print('Python运行正常')";
      const tempPyFile = `/tmp/code_runner_${createTempToken("python")}.py`;
      await writeTextFile(tempPyFile, script);
      const runResult = await executeFromHome(`${pythonBin} ${tempPyFile}`);
      await executeFromHome(`rm -f ${tempPyFile}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Python运行正常")) {
        return { success: false, message: `Python执行器测试失败: ${runResult.output}` };
      }
      return { success: true, message: "Python执行器测试成功" };
    } catch (error) {
      console.error("Python executor self-test failed", error);
      return { success: false, message: `Python执行器测试失败: ${error.message}` };
    }
  }

  // 测试Ruby执行功能
  async function testRuby() {
    try {
      // 检查Ruby是否可用
      const rubyCheckResult = await executeFromHome("ruby --version");
      if (rubyCheckResult.exitCode !== 0 || hasError(rubyCheckResult.output)) {
        return { success: false, message: "Ruby不可用，请确保已安装Ruby" };
      }

      // 测试简单的Ruby代码
      const script = "puts 'Ruby运行正常'";
      const tempRbFile = `/tmp/code_runner_${createTempToken("ruby")}.rb`;
      await writeTextFile(tempRbFile, script);
      const runResult = await executeFromHome(`ruby ${tempRbFile}`);
      await executeFromHome(`rm -f ${tempRbFile}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Ruby运行正常")) {
        return { success: false, message: `Ruby执行器测试失败: ${runResult.output}` };
      }
      return { success: true, message: "Ruby执行器测试成功" };
    } catch (error) {
      console.error("Ruby executor self-test failed", error);
      return { success: false, message: `Ruby执行器测试失败: ${error.message}` };
    }
  }

  // 测试Go执行功能
  async function testGo() {
    try {
      // 检查Go是否可用
      const goCheckResult = await executeFromHome("go version");
      if (goCheckResult.exitCode !== 0 || hasError(goCheckResult.output)) {
        return { success: false, message: "Go不可用，请确保已安装Go" };
      }

      // 测试简单的Go代码
      const script = `
package main
import "fmt"
func main() {
  fmt.Println("Go运行正常")
}`;
      const tempGoDir = `/tmp/code_runner_${createTempToken("go")}`;
      const tempGoFile = `${tempGoDir}/main.go`;
      const tempGoExec = `${tempGoDir}/main`;
      await executeFromHome(`mkdir -p ${tempGoDir}`);
      await writeTextFile(tempGoFile, script);

      const compileResult = await executeTerminalCommand(buildSubshellCommand(tempGoDir, "go build -o main main.go"));
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeFromHome(`rm -rf ${tempGoDir}`);
        return { success: false, message: `Go 编译失败: ${compileResult.output}` };
      }

      const runResult = await executeFromHome(tempGoExec);
      await executeFromHome(`rm -rf ${tempGoDir}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Go运行正常")) {
        return { success: false, message: `Go 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "Go执行器测试成功" };
    } catch (error) {
      console.error("Go executor self-test failed", error);
      return { success: false, message: `Go执行器测试失败: ${error.message}` };
    }
  }

  // 检查并配置Rust环境
  async function ensureRustConfigured(): Promise<{ success: boolean; message: string }> {
    // 在有效目录中运行，避免 "Could not locate working directory" 错误
    let rustCheckResult = await executeTerminalCommand(
      buildSubshellCommand("/tmp", `${RUST_TOOLCHAIN_ENV} && rustc --version && cargo --version`)
    );

    if (rustCheckResult.exitCode === 0 && !hasError(rustCheckResult.output)) {
      return { success: true, message: "Rust环境已配置" };
    }

    // 如果未配置默认工具链，则尝试设置
    if (rustCheckResult.output.includes("no default is configured")) {
      const setupResult = await executeFromHome(`${RUST_TOOLCHAIN_ENV} && export RUSTUP_DIST_SERVER="https://mirrors.ustc.edu.cn/rust-static" && export RUSTUP_UPDATE_ROOT="https://mirrors.ustc.edu.cn/rust-static/rustup" && rustup default stable`);
      if (setupResult.exitCode !== 0 || hasError(setupResult.output)) {
        return { success: false, message: `运行 'rustup default stable' 失败: ${setupResult.output}` };
      }

      // 再次检查
      rustCheckResult = await executeTerminalCommand(
        buildSubshellCommand("/tmp", `${RUST_TOOLCHAIN_ENV} && rustc --version && cargo --version`)
      );
      if (rustCheckResult.exitCode === 0 && !hasError(rustCheckResult.output)) {
        return { success: true, message: "Rust环境已自动配置" };
      }
    }

    return { success: false, message: `Rust环境检查失败: ${rustCheckResult.output}` };
  }

  // 测试Rust执行功能
  async function testRust() {
    try {
      const rustConfig = await ensureRustConfigured();
      if (!rustConfig.success) {
        return { success: false, message: rustConfig.message };
      }

      // 测试简单的Rust代码
      const script = `
fn main() {
  println!("Rust运行正常");
}`;
      const tempRustDir = `/tmp/code_runner_${createTempToken("rust")}`;
      const tempRustSrcDir = `${tempRustDir}/src`;
      const tempRustFile = `${tempRustSrcDir}/main.rs`;
      const cargoToml = `
[package]
name = "test_rust"
version = "0.1.0"
edition = "2021"
[dependencies]
`;
      await executeFromHome(`mkdir -p ${tempRustSrcDir}`);
      await writeTextFile(`${tempRustDir}/Cargo.toml`, cargoToml);
      await writeTextFile(tempRustFile, script);

      // 在有效目录中运行 cargo
      const compileResult = await executeTerminalCommand(buildSubshellCommand(tempRustDir, `${RUST_TOOLCHAIN_ENV} && ${CARGO_MIRROR_ENV} && cargo build --release`));
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeFromHome(`rm -rf ${tempRustDir}`);
        return { success: false, message: `Rust 编译失败: ${compileResult.output}` };
      }

      const execPath = `${tempRustDir}/target/release/test_rust`;
      const runResult = await executeFromHome(execPath);
      await executeFromHome(`rm -rf ${tempRustDir}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("Rust运行正常")) {
        return { success: false, message: `Rust 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "Rust执行器测试成功" };
    } catch (error) {
      console.error("Rust executor self-test failed", error);
      return { success: false, message: `Rust执行器测试失败: ${error.message}` };
    }
  }

  // 测试C执行功能
  async function testC() {
    try {
      // 检查gcc是否可用
      const gccCheckResult = await executeFromHome("gcc --version");
      if (gccCheckResult.exitCode !== 0 || hasError(gccCheckResult.output)) {
        return { success: false, message: "GCC不可用，请确保已安装gcc" };
      }

      // 测试简单的C代码
      const script = `
#include <stdio.h>
int main() {
  printf("C运行正常\\n");
  return 0;
}`;
      const tempCFile = `/tmp/code_runner_${createTempToken("c")}.c`;
      const tempCExec = `/tmp/code_runner_${createTempToken("c_exec")}`;
      await writeTextFile(tempCFile, script);

      const compileResult = await executeFromHome(`gcc -O3 -march=native -fopenmp ${tempCFile} -o ${tempCExec}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeFromHome(`rm -f ${tempCFile} ${tempCExec}`);
        return { success: false, message: `C 编译失败: ${compileResult.output}` };
      }

      const runResult = await executeFromHome(tempCExec);
      await executeFromHome(`rm -f ${tempCFile} ${tempCExec}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("C运行正常")) {
        return { success: false, message: `C 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "C执行器测试成功" };
    } catch (error) {
      console.error("C executor self-test failed", error);
      return { success: false, message: `C执行器测试失败: ${error.message}` };
    }
  }

  // 测试C++执行功能
  async function testCpp() {
    try {
      // 检查g++是否可用
      const gppCheckResult = await executeFromHome("g++ --version");
      if (gppCheckResult.exitCode !== 0 || hasError(gppCheckResult.output)) {
        return { success: false, message: "G++不可用，请确保已安装g++" };
      }

      // 测试简单的C++代码
      const script = `
#include <iostream>
int main() {
  std::cout << "C++运行正常" << std::endl;
  return 0;
}`;
      const tempCppFile = `/tmp/code_runner_${createTempToken("cpp")}.cpp`;
      const tempCppExec = `/tmp/code_runner_${createTempToken("cpp_exec")}`;
      await writeTextFile(tempCppFile, script);

      const compileResult = await executeFromHome(`g++ -O3 -march=native -fopenmp ${tempCppFile} -o ${tempCppExec}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        await executeFromHome(`rm -f ${tempCppFile} ${tempCppExec}`);
        return { success: false, message: `C++ 编译失败: ${compileResult.output}` };
      }

      const runResult = await executeFromHome(tempCppExec);
      await executeFromHome(`rm -f ${tempCppFile} ${tempCppExec}`);

      if (runResult.exitCode !== 0 || hasError(runResult.output) || !runResult.output.includes("C++运行正常")) {
        return { success: false, message: `C++ 执行失败: ${runResult.output}` };
      }

      return { success: true, message: "C++执行器测试成功" };
    } catch (error) {
      console.error("C++ executor self-test failed", error);
      return { success: false, message: `C++执行器测试失败: ${error.message}` };
    }
  }

  async function run_javascript_es5(params: { script: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的脚本内容");
    }
    return executeJavaScript(script);
  }

  async function run_javascript_file(params: { file_path: string }) {
    const filePath = params.file_path;

    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 JavaScript 文件路径");
    }

    // Keep JavaScript file execution in the same Ubuntu/proot filesystem as every other
    // *_file tool. Android Tools.Files.read() would make /sdcard visible only to this tool and
    // make ordinary files created in the Ubuntu session appear to be missing.
    const escapedPath = escapeForShell(filePath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0) {
      throw new Error(`JavaScript 文件不存在或路径错误: ${filePath}`);
    }

    const fileResult = await executeTerminalCommand(`cat '${escapedPath}'`);
    if (fileResult.exitCode !== 0) {
      throw new Error(`无法读取文件: ${filePath}\n${fileResult.output}`);
    }

    return executeJavaScript(fileResult.output);
  }

  async function run_javascript_node(params: { script: string; node_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 JavaScript 脚本内容");
    }

    // 先锁定本次产物目标，再安装依赖、写暂存源码或编译。
    const artifactRoot = resolveArtifactRoot();

    const { workspaceDir } = await ensurePersistentNodeWorkspace();
    const nodeFlags = params.node_flags || "";
    const tempFileName = `temp_script_node_${createTempToken("node")}.js`;
    const tempFilePath = `${workspaceDir}/${tempFileName}`;
    try {
      await writeTextFile(tempFilePath, script);
      const result = await executeInArtifactDirectory(`NODE_PATH=${workspaceDir}/node_modules node ${nodeFlags} ${tempFilePath}`.trim(), artifactRoot);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`JavaScript (Node.js) 脚本执行失败:\n${result.output}`);
      }
    } finally {
      await executeFromHome(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }


  async function run_javascript_node_file(params: { file_path: string; node_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 JavaScript 文件路径");
    }

    const { workspaceDir } = await ensurePersistentNodeWorkspace();
    const escapedPath = escapeForShell(filePath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`JavaScript 文件不存在或路径错误: ${filePath}`);
    }

    const nodeFlags = params.node_flags || "";
    const result = await executeTerminalCommand(`NODE_PATH=${workspaceDir}/node_modules node ${nodeFlags} '${escapedPath}'`.trim());
    if (result.exitCode === 0 && !hasError(result.output)) {
      return result.output.trim();
    } else {
      throw new Error(`JavaScript (Node.js) 文件执行失败:\n${result.output}`);
    }
  }

  async function run_python(params: { script: string; python_flags?: string; script_args?: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Python 脚本内容");
    }

    // 先锁定本次产物目标，再安装依赖、写暂存源码或编译。
    const artifactRoot = resolveArtifactRoot();

    const pythonFlags = buildPythonFlags(params.python_flags);
    if (script.includes("\0") || params.script_args?.includes("\0")) {
      throw new Error("Python 源码和 script_args 不能包含 NUL");
    }
    const scriptArgs = buildPipeSeparatedShellArgs(params.script_args);
    // Validate caller-controlled arguments before initializing the persistent interpreter.
    const { pythonBin } = await ensurePersistentVenv();
    const tempFilePath = `/tmp/code_runner_${createTempToken("python")}.py`;
    const escapedTempFilePath = escapeForShell(tempFilePath);
    try {
      await writeTextFile(tempFilePath, script);
      // 批处理不能继承共享 PTY 的输入，否则 input()/子进程会等待 AI 无法提供的按键。
      const result = await executeInArtifactDirectory(`${pythonBin} -u ${pythonFlags} -- '${escapedTempFilePath}' ${scriptArgs} </dev/null`, artifactRoot);
      return pythonOutput(result);
    } finally {
      await executeFromHome(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }


  async function run_python_file(params: { file_path: string; python_flags?: string; script_args?: string }) {
    const filePath = params.file_path;

    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Python 文件路径");
    }

    const pythonFlags = buildPythonFlags(params.python_flags);
    if (filePath.includes("\0") || params.script_args?.includes("\0")) {
      throw new Error("Python file_path 和 script_args 不能包含 NUL");
    }
    const scriptArgs = buildPipeSeparatedShellArgs(params.script_args);
    // 即使加 --，Python 仍将单独的 '-' 当作 stdin；显式相对路径保留文件含义。
    const escapedPath = escapeForShell(filePath.startsWith("-") ? `./${filePath}` : filePath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Python 文件不存在或路径错误: ${filePath}`);
    }

    // Use persistent venv interpreter
    const { pythonBin } = await ensurePersistentVenv();
    const result = await executeTerminalCommand(`${pythonBin} -u ${pythonFlags} -- '${escapedPath}' ${scriptArgs} </dev/null`);
    return pythonOutput(result);
  }

  async function run_ruby(params: { script: string; ruby_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Ruby 脚本内容");
    }

    // 先锁定本次产物目标，再安装依赖、写暂存源码或编译。
    const artifactRoot = resolveArtifactRoot();

    const rubyFlags = params.ruby_flags || "";
    const tempFilePath = `/tmp/code_runner_${createTempToken("ruby")}.rb`;
    try {
      await writeTextFile(tempFilePath, script);
      const result = await executeInArtifactDirectory(`ruby ${rubyFlags} ${tempFilePath}`, artifactRoot);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Ruby 脚本执行失败:\n${result.output}`);
      }
    } finally {
      await executeFromHome(`rm -f ${tempFilePath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }


  async function run_ruby_file(params: { file_path: string; ruby_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Ruby 文件路径");
    }

    const escapedPath = escapeForShell(filePath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Ruby 文件不存在或路径错误: ${filePath}`);
    }

    const rubyFlags = params.ruby_flags || "";
    const result = await executeTerminalCommand(`ruby ${rubyFlags} '${escapedPath}'`);
    if (result.exitCode === 0 && !hasError(result.output)) {
      return result.output.trim();
    } else {
      throw new Error(`Ruby 文件执行失败:\n${result.output}`);
    }
  }

  async function run_go(params: { script: string; build_flags?: string }) {
    const script = params.script;

    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Go 代码内容");
    }

    // 先锁定本次产物目标，再安装依赖、写暂存源码或编译。
    const artifactRoot = resolveArtifactRoot();

    const buildFlags = params.build_flags || "";
    const tempDirPath = `/tmp/code_runner_${createTempToken("go")}`;
    const tempFilePath = `${tempDirPath}/main.go`;

    try {
      await executeFromHome(`mkdir -p ${tempDirPath}`);
      await writeTextFile(tempFilePath, script);

      const compileResult = await executeTerminalCommand(buildSubshellCommand(tempDirPath, `go build ${buildFlags} -o main main.go`));
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Go 代码编译失败:\n${compileResult.output}`);
      }

      const result = await executeInArtifactDirectory(`${tempDirPath}/main`, artifactRoot);

      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Go 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeFromHome(`rm -rf ${tempDirPath}`).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }


  async function run_go_file(params: { file_path: string; build_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Go 文件路径");
    }

    const escapedPath = escapeForShell(filePath);
    const tempExecPath = `/tmp/code_runner_${createTempToken("go_exec")}`;
    const escapedTempExecPath = escapeForShell(tempExecPath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Go 文件不存在或路径错误: ${filePath}`);
    }

    const buildFlags = params.build_flags || "";
    try {
      const compileResult = await executeTerminalCommand(`go build ${buildFlags} -o '${escapedTempExecPath}' '${escapedPath}'`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Go 文件编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(`'${escapedTempExecPath}'`);

      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Go 文件执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f '${escapedTempExecPath}'`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_rust(params: { script: string; cargo_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 Rust 代码内容");
    }

    // 先锁定本次产物目标，再安装依赖、写暂存源码或编译。
    const artifactRoot = resolveArtifactRoot();

    const rustConfig = await ensureRustConfigured();
    if (!rustConfig.success) {
      throw new Error(rustConfig.message);
    }

    const cargoFlags = params.cargo_flags || "--release";
    const buildMode = cargoFlags.includes("--release") ? "release" : "debug";
    const tempDirPath = `/tmp/code_runner_${createTempToken("rust")}`;
    try {
      const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
      await executeFromHome(`mkdir -p ${tempDirPath}/src`, 10000);
      await writeTextFile(`${tempDirPath}/Cargo.toml`, cargoToml);
      await writeTextFile(`${tempDirPath}/src/main.rs`, script);

      const compileResult = await executeTerminalCommand(buildSubshellCommand(tempDirPath, `${RUST_TOOLCHAIN_ENV} && ${CARGO_MIRROR_ENV} && cargo build ${cargoFlags}`));
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Rust 代码编译失败:\n${compileResult.output}`);
      }

      const execPath = `${tempDirPath}/target/${buildMode}/temp_rust_script`;
      const result = await executeInArtifactDirectory(execPath, artifactRoot, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Rust 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeFromHome(`rm -rf ${tempDirPath}`).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }


  async function run_rust_file(params: { file_path: string; cargo_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 Rust 文件路径");
    }
    const escapedPath = escapeForShell(filePath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`Rust 文件不存在或路径错误: ${filePath}`);
    }

    const rustConfig = await ensureRustConfigured();
    if (!rustConfig.success) {
      throw new Error(rustConfig.message);
    }

    const cargoFlags = params.cargo_flags || "--release";
    const buildMode = cargoFlags.includes("--release") ? "release" : "debug";
    const tempDirPath = `/tmp/code_runner_${createTempToken("rust")}`;
    try {
      const cargoToml = `
[package]
name = "temp_rust_script"
version = "0.1.0"
edition = "2021"

[dependencies]
      `;
      await executeFromHome(`mkdir -p ${tempDirPath}/src`, 10000);
      await writeTextFile(`${tempDirPath}/Cargo.toml`, cargoToml);

      const readResult = await executeTerminalCommand(`cat '${escapedPath}'`);
      if (readResult.exitCode !== 0 || hasError(readResult.output)) {
        throw new Error(`无法读取文件: ${filePath}`);
      }
      const fileContent = readResult.output;
      await writeTextFile(`${tempDirPath}/src/main.rs`, fileContent);

      const compileResult = await executeTerminalCommand(buildSubshellCommand(tempDirPath, `${RUST_TOOLCHAIN_ENV} && ${CARGO_MIRROR_ENV} && cargo build ${cargoFlags}`));
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`Rust 文件编译失败:\n${compileResult.output}`);
      }

      const execPath = `${tempDirPath}/target/${buildMode}/temp_rust_script`;
      const result = await executeTerminalCommand(execPath, 30000);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`Rust 项目执行失败:\n${result.output}`);
      }
    } finally {
      await executeFromHome(`rm -rf ${tempDirPath}`).catch(err => console.error(`删除临时目录失败: ${err.message}`));
    }
  }

  async function run_c(params: { script: string; compile_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 C 代码内容");
    }

    // 先锁定本次产物目标，再安装依赖、写暂存源码或编译。
    const artifactRoot = resolveArtifactRoot();

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    const tempFilePath = `/tmp/code_runner_${createTempToken("c")}.c`;
    const tempExecPath = `/tmp/code_runner_${createTempToken("c_exec")}`;
    try {
      await writeTextFile(tempFilePath, script);

      const compileResult = await executeFromHome(`gcc ${compileFlags} ${tempFilePath} -o ${tempExecPath}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C 代码编译失败:\n${compileResult.output}`);
      }

      const result = await executeInArtifactDirectory(tempExecPath, artifactRoot);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeFromHome(`rm -f ${tempFilePath} ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }


  async function run_c_file(params: { file_path: string; compile_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 C 文件路径");
    }

    const escapedPath = escapeForShell(filePath);
    const tempExecPath = `/tmp/code_runner_${createTempToken("c_exec")}`;
    const escapedTempExecPath = escapeForShell(tempExecPath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`C 文件不存在或路径错误: ${filePath}`);
    }

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    try {
      const compileResult = await executeTerminalCommand(`gcc ${compileFlags} '${escapedPath}' -o '${escapedTempExecPath}'`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C 文件编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(`'${escapedTempExecPath}'`);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C 文件执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f '${escapedTempExecPath}'`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  async function run_cpp(params: { script: string; compile_flags?: string }) {
    const script = params.script;
    if (!script || script.trim() === "") {
      throw new Error("请提供要执行的 C++ 代码内容");
    }

    // 先锁定本次产物目标，再安装依赖、写暂存源码或编译。
    const artifactRoot = resolveArtifactRoot();

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    const tempFilePath = `/tmp/code_runner_${createTempToken("cpp")}.cpp`;
    const tempExecPath = `/tmp/code_runner_${createTempToken("cpp_exec")}`;
    try {
      await writeTextFile(tempFilePath, script);

      const compileResult = await executeFromHome(`g++ ${compileFlags} ${tempFilePath} -o ${tempExecPath}`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C++ 代码编译失败:\n${compileResult.output}`);
      }

      const result = await executeInArtifactDirectory(tempExecPath, artifactRoot);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C++ 代码执行失败:\n${result.output}`);
      }
    } finally {
      await executeFromHome(`rm -f ${tempFilePath} ${tempExecPath}`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }


  async function run_cpp_file(params: { file_path: string; compile_flags?: string }) {
    const filePath = params.file_path;
    if (!filePath || filePath.trim() === "") {
      throw new Error("请提供要执行的 C++ 文件路径");
    }

    const escapedPath = escapeForShell(filePath);
    const tempExecPath = `/tmp/code_runner_${createTempToken("cpp_exec")}`;
    const escapedTempExecPath = escapeForShell(tempExecPath);
    const fileExistsResult = await executeTerminalCommand(`test -f '${escapedPath}'`);
    if (fileExistsResult.exitCode !== 0 || hasError(fileExistsResult.output)) {
      throw new Error(`C++ 文件不存在或路径错误: ${filePath}`);
    }

    const compileFlags = params.compile_flags || "-O3 -march=native -fopenmp";
    try {
      const compileResult = await executeTerminalCommand(`g++ ${compileFlags} '${escapedPath}' -o '${escapedTempExecPath}'`);
      if (compileResult.exitCode !== 0 || hasError(compileResult.output)) {
        throw new Error(`C++ 文件编译失败:\n${compileResult.output}`);
      }

      const result = await executeTerminalCommand(`'${escapedTempExecPath}'`);
      if (result.exitCode === 0 && !hasError(result.output)) {
        return result.output.trim();
      } else {
        throw new Error(`C++ 文件执行失败:\n${result.output}`);
      }
    } finally {
      await executeTerminalCommand(`rm -f '${escapedTempExecPath}'`).catch(err => console.error(`删除临时文件失败: ${err.message}`));
    }
  }

  function wrap(func: (params: any) => Promise<any>) {
    return async (params: any) => {
      try {
        const result = await func(params);
        complete({
          success: true,
          data: result,
        });
      } catch (error: any) {
        console.error("code_runner tool failed", error);
        complete({
          success: false,
          message: error.message,
          error_stack: error.stack,
        });
      }
    };
  }

  return {
    main,
    run_javascript_es5,
    run_javascript_file,
    run_javascript_node,
    run_javascript_node_file,
    get_environment_info,
    install_node_packages,
    install_python_packages,
    run_python,
    run_python_file,
    run_ruby,
    run_ruby_file,
    run_go,
    run_go_file,
    run_rust,
    run_rust_file,
    run_c,
    run_c_file,
    run_cpp,
    run_cpp_file,
    wrap
  };
})();

// 逐个导出
exports.main = codeRunner.wrap(codeRunner.main);
exports.run_javascript_es5 = codeRunner.wrap(codeRunner.run_javascript_es5);
exports.run_javascript_file = codeRunner.wrap(codeRunner.run_javascript_file);
exports.run_javascript_node = codeRunner.wrap(codeRunner.run_javascript_node);
exports.run_javascript_node_file = codeRunner.wrap(codeRunner.run_javascript_node_file);
exports.get_environment_info = codeRunner.wrap(codeRunner.get_environment_info);
exports.install_node_packages = codeRunner.wrap(codeRunner.install_node_packages);
exports.install_python_packages = codeRunner.wrap(codeRunner.install_python_packages);
exports.run_python = codeRunner.wrap(codeRunner.run_python);
exports.run_python_file = codeRunner.wrap(codeRunner.run_python_file);
exports.run_ruby = codeRunner.wrap(codeRunner.run_ruby);
exports.run_ruby_file = codeRunner.wrap(codeRunner.run_ruby_file);
exports.run_go = codeRunner.wrap(codeRunner.run_go);
exports.run_go_file = codeRunner.wrap(codeRunner.run_go_file);
exports.run_rust = codeRunner.wrap(codeRunner.run_rust);
exports.run_rust_file = codeRunner.wrap(codeRunner.run_rust_file);
exports.run_c = codeRunner.wrap(codeRunner.run_c);
exports.run_c_file = codeRunner.wrap(codeRunner.run_c_file);
exports.run_cpp = codeRunner.wrap(codeRunner.run_cpp);
exports.run_cpp_file = codeRunner.wrap(codeRunner.run_cpp_file);
