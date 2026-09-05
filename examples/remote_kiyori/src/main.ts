import toolboxUI from "./ui/remote_kiyori_setup/index.ui.js";

export function registerToolPkg() {
  ToolPkg.registerToolboxUiModule({
    id: "remote_kiyori_setup",
    runtime: "compose_dsl",
    screen: toolboxUI,
    params: {},
    title: {
      zh: "远程 Kiyori 配置",
      en: "Remote Kiyori Setup",
    },
  });

  ToolPkg.registerAppLifecycleHook({
    id: "remote_kiyori_app_create",
    event: "application_on_create",
    function: onApplicationCreate,
  });

  return true;
}

export function onApplicationCreate() {
  return { ok: true };
}
