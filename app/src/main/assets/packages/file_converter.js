/* METADATA
{
    "name": "file_converter",

    "display_name": {
        "zh": "文件转换器",
        "en": "File Converter"
    },
    "description": {
        "zh": "在音视频、图像和文档格式之间转换本地文件；根据输入和输出扩展名选择 FFmpeg、ImageMagick 或 Pandoc，并返回输出路径。",
        "en": "Convert local audio/video, image, and document files by selecting FFmpeg, ImageMagick, or Pandoc from the input and output extensions, then return the output path."
    },
    "enabledByDefault": true,
    "category": "File",
    "tools": [
        {
            "name": "convert_file",
            "description": {
                "zh": "调用 FFmpeg、ImageMagick 和 Pandoc 等外部命令行工具，转换文件的格式。支持音视频、图像和文档等多种类型。如果检测到工具未安装，会尝试自动安装。",
                "en": "Convert files using external CLI tools such as FFmpeg, ImageMagick, and Pandoc. Supports audio/video, images, and documents. If a required tool is missing, it will attempt to install it automatically."
            },
            "parameters": [
                { "name": "input_path", "description": { "zh": "输入文件的路径。", "en": "Input file path." }, "type": "string", "required": true },
                { "name": "output_path", "description": { "zh": "输出文件的路径。扩展名决定了目标格式。", "en": "Output file path. The file extension determines the target format." }, "type": "string", "required": true },
                { "name": "options", "description": { "zh": "可选命令行参数数组，每个参数单独一项，例如 ['-quality', '80']。", "en": "Optional CLI argument array with one argument per item, for example ['-quality', '80']." }, "type": "array", "required": false }
            ]
        }
    ]
}*/
const fileConverter = (function () {
    let terminalSessionId = null;
    const TOOL_CHECK_TIMEOUT_MS = 15000;
    const PACKAGE_UPDATE_TIMEOUT_MS = 120000;
    const PACKAGE_INSTALL_TIMEOUT_MS = 300000;
    const CONVERSION_TIMEOUT_MS = 600000;
    async function getTerminalSessionId() {
        if (terminalSessionId) {
            return terminalSessionId;
        }
        const session = await Tools.System.terminal.create("file_converter_session");
        terminalSessionId = session.sessionId;
        return terminalSessionId;
    }
    async function executeTerminalCommand(command, timeoutMs) {
        const sessionId = await getTerminalSessionId();
        return await Tools.System.terminal.exec(sessionId, command, timeoutMs);
    }
    function shellQuote(value) {
        return `'${value.replace(/'/g, `'"'"'`)}'`;
    }
    function errorMessage(error) {
        return error instanceof Error ? error.message : String(error);
    }
    function errorStack(error) {
        return error instanceof Error ? error.stack : undefined;
    }
    /**
     * Checks if a command-line tool is installed, and attempts to install it if not found.
     * @param toolName The name of the command (e.g., "ffmpeg").
     * @param packageName The name of the package to install (e.g., "ffmpeg").
     * @returns A promise that resolves to true if the tool is available.
     * @throws An error if the tool is not found and installation fails.
     */
    async function checkAndInstall(toolName, packageName) {
        console.log(`Checking for ${toolName}...`);
        const checkCmd = `command -v ${toolName}`;
        const checkResult = await executeTerminalCommand(checkCmd, TOOL_CHECK_TIMEOUT_MS);
        if (checkResult.exitCode === 0 && checkResult.output.trim() !== '') {
            console.log(`${toolName} is already installed at: ${checkResult.output.trim()}`);
            return true;
        }
        console.log(`${toolName} not found. Attempting to install package: ${packageName}...`);
        // Assuming an apt-based system (like Debian/Ubuntu).
        const updateCmd = 'apt-get update';
        console.log(`Running: ${updateCmd}`);
        const updateResult = await executeTerminalCommand(updateCmd, PACKAGE_UPDATE_TIMEOUT_MS);
        if (updateResult.exitCode !== 0) {
            console.warn(`'apt-get update' failed. This might be okay if caches are fresh, but installation may fail.\nOutput: ${updateResult.output}`);
        }
        const installCmd = `apt-get install -y ${packageName}`;
        console.log(`Running: ${installCmd}`);
        const installResult = await executeTerminalCommand(installCmd, PACKAGE_INSTALL_TIMEOUT_MS);
        if (installResult.exitCode !== 0) {
            console.error(`Failed to install ${packageName}: ${installResult.output}`);
            throw new Error(`Failed to install required tool: ${toolName} (package: ${packageName}). Please try installing it manually.`);
        }
        console.log(`${packageName} installed successfully.`);
        return true;
    }
    /**
     * Determines the appropriate conversion tool and command based on file extensions.
     * @param inputPath Path to the input file.
     * @param outputPath Path to the output file.
     * @param options Additional options for the command.
     * @returns An object with tool information and the command to execute.
     */
    function getConverterInfo(inputPath, outputPath, options) {
        const getExt = (path) => path.split('.').pop()?.toLowerCase() || '';
        const inputExt = getExt(inputPath);
        const outputExt = getExt(outputPath);
        const inputArgument = shellQuote(inputPath);
        const outputArgument = shellQuote(outputPath);
        const optionArguments = options?.map(shellQuote).join(' ') ?? '';
        const isAudioVideo = (ext) => ['mp4', 'mkv', 'avi', 'mov', 'flv', 'webm', 'mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'wma', 'wmv'].includes(ext);
        const isImage = (ext) => ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'tiff', 'ico', 'svg'].includes(ext);
        const isDocument = (ext) => ['md', 'html', 'docx', 'pdf', 'txt', 'epub', 'odt', 'rtf', 'tex', 'rst', 'json', 'csv'].includes(ext);
        if (isAudioVideo(inputExt) || isAudioVideo(outputExt)) {
            return {
                tool: 'ffmpeg',
                pkg: 'ffmpeg',
                command: `ffmpeg -y -i ${inputArgument} ${optionArguments} ${outputArgument}`
            };
        }
        if (isImage(inputExt) || isImage(outputExt)) {
            return {
                tool: 'convert',
                pkg: 'imagemagick',
                command: `convert ${inputArgument} ${optionArguments} ${outputArgument}`
            };
        }
        if (isDocument(inputExt) || isDocument(outputExt)) {
            return {
                tool: 'pandoc',
                pkg: 'pandoc',
                command: `pandoc ${inputArgument} -o ${outputArgument} ${optionArguments}`
            };
        }
        throw new Error(`Unsupported or ambiguous file conversion from .${inputExt} to .${outputExt}.`);
    }
    /**
     * The core logic for the convert_file tool.
     * @param params Parameters for the conversion.
     * @returns An object with the result of the conversion.
     */
    async function convert_file(params) {
        const { input_path, output_path, options } = params;
        const fileExists = await Tools.Files.exists(input_path);
        if (!fileExists.exists) {
            throw new Error(`Input file not found: ${input_path}`);
        }
        const converter = getConverterInfo(input_path, output_path, options);
        await checkAndInstall(converter.tool, converter.pkg);
        console.log(`Executing conversion command: ${converter.command}`);
        const result = await executeTerminalCommand(converter.command, CONVERSION_TIMEOUT_MS);
        if (result.exitCode !== 0) {
            throw new Error(`Conversion failed. Exit code: ${result.exitCode}\nOutput:\n${result.output}`);
        }
        const outputExists = await Tools.Files.exists(output_path);
        if (!outputExists.exists) {
            // Sometimes a tool exits with 0 but fails, writing to stderr.
            throw new Error(`Conversion process finished, but output file was not created at: ${output_path}\nTerminal Output:\n${result.output}`);
        }
        return {
            output_path: output_path,
            details: `File converted successfully and saved to ${output_path}.`,
            terminal_output: result.output
        };
    }
    /**
     * A wrapper function for executing tools to provide standardized success/error handling.
     */
    async function wrap(func, params, successMessage, failMessage) {
        try {
            const result = await func(params);
            complete({ success: true, message: successMessage, data: result });
        }
        catch (error) {
            const message = errorMessage(error);
            console.error(`Function ${func.name} failed: ${message}`, error);
            complete({ success: false, message: `${failMessage}: ${message}`, error_stack: errorStack(error) });
        }
    }
    /**
     * A main function for self-testing the capabilities of this tool package.
     */
    async function main() {
        console.log("--- Starting File Converter Tool Test ---");
        const testDir = "/sdcard/Download/converter_test";
        await Tools.Files.mkdir(testDir, true);
        // Test 1: Image conversion (PNG to JPG)
        try {
            console.log("\n[1/3] Testing Image Conversion (PNG -> JPG)");
            // A simple 1x1 red pixel PNG in base64
            const pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/wcAAwAB/epv2AAAAABJRU5ErkJggg==";
            const inputPng = `${testDir}/test.png`;
            const outputJpg = `${testDir}/test.jpg`;
            await Tools.Files.writeBinary(inputPng, pngBase64);
            const imageResult = await convert_file({ input_path: inputPng, output_path: outputJpg });
            console.log("Image conversion success:", imageResult);
            if (!(await Tools.Files.exists(outputJpg)).exists) {
                throw new Error("JPG file not created.");
            }
            // Verify that readBinary can read the converted image as Base64
            const jpgBinary = await Tools.Files.readBinary(outputJpg);
            console.log("ReadBinary success: size=", jpgBinary.size, "bytes, base64 length=", jpgBinary.contentBase64.length);
        }
        catch (error) {
            console.error("Image conversion test failed:", errorMessage(error), error);
        }
        // Test 2: Document conversion (MD to HTML)
        try {
            console.log("\n[2/3] Testing Document Conversion (MD -> HTML)");
            const inputMd = `${testDir}/test.md`;
            const outputHtml = `${testDir}/test.html`;
            await Tools.Files.write(inputMd, "# Hello World");
            const docResult = await convert_file({ input_path: inputMd, output_path: outputHtml });
            console.log("Document conversion success:", docResult);
            const htmlContent = (await Tools.Files.read(outputHtml)).content;
            if (!htmlContent.includes("<h1")) {
                throw new Error("HTML content is incorrect.");
            }
        }
        catch (error) {
            console.error("Document conversion test failed:", errorMessage(error), error);
        }
        // Test 3: Unsupported conversion
        try {
            console.log("\n[3/3] Testing Unsupported Conversion (zip -> tar)");
            const inputZip = `${testDir}/test.zip`;
            await Tools.Files.write(inputZip, "dummy content");
            await convert_file({ input_path: inputZip, output_path: `${testDir}/test.tar` });
            console.error("Unsupported conversion test FAILED: It should have thrown an error but didn't.");
        }
        catch (error) {
            console.log("Unsupported conversion test PASSED as expected:", errorMessage(error));
        }
        console.log("\n--- File Converter Tool Test Finished ---");
        await Tools.Files.deleteFile(testDir, true);
        console.log("Cleaned up test directory.");
        complete({ success: true, message: "All tests finished." });
    }
    return {
        convert_file: (p) => wrap(convert_file, p, 'File conversion successful.', 'File conversion failed.'),
        main: main,
    };
})();
exports.convert_file = fileConverter.convert_file;
exports.main = fileConverter.main;
