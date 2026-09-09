import officeConsole from "./office_console/index.ui.js";

export function registerToolPkg(): boolean {
  ToolPkg.registerToolboxUiModule({ id: "office_console", runtime: "compose_dsl", screen: officeConsole,
    params: {}, title: { zh: "办公文档", en: "Office Documents" } });
  return true;
}

if (typeof exports !== "undefined") {
  exports.registerToolPkg = registerToolPkg;
}
