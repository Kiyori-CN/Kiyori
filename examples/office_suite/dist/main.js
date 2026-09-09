"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
const index_ui_js_1 = __importDefault(require("./office_console/index.ui.js"));
function registerToolPkg() {
    ToolPkg.registerToolboxUiModule({ id: "office_console", runtime: "compose_dsl", screen: index_ui_js_1.default,
        params: {}, title: { zh: "办公文档", en: "Office Documents" } });
    return true;
}
if (typeof exports !== "undefined") {
    exports.registerToolPkg = registerToolPkg;
}
