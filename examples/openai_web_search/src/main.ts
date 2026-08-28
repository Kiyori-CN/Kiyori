import settingsScreen from "./ui/index.ui.js";

export function registerToolPkg(): boolean {
  ToolPkg.registerToolboxUiModule({
    id: "openai_web_search_settings",
    runtime: "compose_dsl",
    screen: settingsScreen,
    params: {},
    title: {
      zh: "OpenAI 搜索",
      en: "OpenAI Search",
    },
  });

  console.log("[openai_web_search] plugin registered");
  return true;
}
