"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.registerToolPkg = registerToolPkg;
function registerToolPkg() {
    // 第一期不注册 UI 模块：Compose 控制台属于第二期（见专项 index 的分期计划）。
    return true;
}
if (typeof exports !== "undefined") {
    exports.registerToolPkg = registerToolPkg;
}
