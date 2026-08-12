import settingsScreen from "./ui/index.ui.js";

export function registerToolPkg(): boolean {
  ToolPkg.registerToolboxUiModule({
    id: "openai_web_search_settings",
    runtime: "compose_dsl",
    screen: settingsScreen,
    params: {},
    title: {
      zh: "OpenAI Web Search",
      en: "OpenAI Web Search",
    },
  });

  console.log("[openai_web_search] plugin registered");
  return true;
}
