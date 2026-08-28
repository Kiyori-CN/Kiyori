"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
const index_ui_js_1 = __importDefault(require("./ui/index.ui.js"));
function registerToolPkg() {
    ToolPkg.registerToolboxUiModule({
        id: "openai_web_search_settings",
        runtime: "compose_dsl",
        screen: index_ui_js_1.default,
        params: {},
        title: {
            zh: "OpenAI 搜索",
            en: "OpenAI Search",
        },
    });
    console.log("[openai_web_search] plugin registered");
    return true;
}
