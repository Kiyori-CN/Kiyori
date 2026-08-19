package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.WebSettings
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools

internal fun StandardBrowserSessionTools.applyBrowserDisplaySettingsOnMain() {
    val settings = browserSettingsStore.current
    StandardBrowserSessionTools.sessions.values.forEach { session ->
        session.webView.settings.textZoom = settings.webTextZoomPercent
        if (session.pageLoaded) {
            val forcePageZoomEnabled =
                resolveWebSessionSiteFeatureEnabled(
                    settings = settings,
                    domainOrUrl = session.currentUrl,
                    feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
                    globalEnabled = settings.forcePageZoomEnabled,
                )
            session.webView.evaluateJavascript(
                browserForcePageZoomScript(forcePageZoomEnabled),
                null,
            )
        }
    }
    refreshSessionUiOnMain()
}

internal fun StandardBrowserSessionTools.applyBrowserDisplaySettingsOnPage(
    session: BrowserToolSession,
) {
    val settings = browserSettingsStore.current
    session.webView.settings.textZoom = settings.webTextZoomPercent
    val forcePageZoomEnabled =
        resolveWebSessionSiteFeatureEnabled(
            settings = settings,
            domainOrUrl = session.currentUrl,
            feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
            globalEnabled = settings.forcePageZoomEnabled,
        )
    session.webView.evaluateJavascript(
        browserForcePageZoomScript(forcePageZoomEnabled),
        null,
    )
}

internal fun StandardBrowserSessionTools.prepareReturnWithoutReloadOnMain(
    session: BrowserToolSession,
) {
    if (session.returnWithoutReloadOriginalCacheMode != null) {
        return
    }
    val history = session.webView.copyBackForwardList()
    val backTarget =
        requireNotNull(history.getItemAtIndex(history.currentIndex - 1)) {
            "WebView Back target is unavailable for ${session.id}"
        }
    val backTargetUrl =
        requireNotNull(backTarget.url) {
            "WebView Back target URL is unavailable for ${session.id}"
        }
    val settings = browserSettingsStore.current
    val returnWithoutReloadEnabled =
        resolveWebSessionSiteFeatureEnabled(
            settings = settings,
            domainOrUrl = backTargetUrl,
            feature = WebSessionSiteFeature.RETURN_WITHOUT_RELOAD,
            globalEnabled = settings.returnWithoutReloadEnabled,
        )
    if (!returnWithoutReloadEnabled) {
        return
    }
    val originalCacheMode = session.webView.settings.cacheMode
    session.returnWithoutReloadOriginalCacheMode = originalCacheMode
    session.webView.settings.cacheMode =
        resolveBrowserBackCacheMode(
            returnWithoutReloadEnabled = true,
            currentCacheMode = originalCacheMode,
        )
}

internal fun StandardBrowserSessionTools.restoreReturnWithoutReloadOnMain(
    session: BrowserToolSession,
) {
    val originalCacheMode = session.returnWithoutReloadOriginalCacheMode ?: return
    session.webView.settings.cacheMode = originalCacheMode
    session.returnWithoutReloadOriginalCacheMode = null
}

internal fun resolveBrowserBackCacheMode(
    returnWithoutReloadEnabled: Boolean,
    currentCacheMode: Int,
): Int =
    if (returnWithoutReloadEnabled) {
        WebSettings.LOAD_CACHE_ELSE_NETWORK
    } else {
        currentCacheMode
    }

internal fun browserForcePageZoomScript(enabled: Boolean): String =
    BROWSER_FORCE_PAGE_ZOOM_SCRIPT_TEMPLATE.replace(
        "__KIYORI_FORCE_ZOOM_ENABLED__",
        enabled.toString(),
    )

private val BROWSER_FORCE_PAGE_ZOOM_SCRIPT_TEMPLATE =
    """
    (() => {
      const stateKey = "__kiyoriForcePageZoomV1";
      const previous = window[stateKey];
      if (previous && typeof previous.dispose === "function") {
        previous.dispose();
      }
      if (!__KIYORI_FORCE_ZOOM_ENABLED__) {
        delete window[stateKey];
        return true;
      }

      const originals = new Map();
      const created = new Set();
      let applying = false;
      let observer = null;

      const observe = () => {
        observer.observe(document.documentElement, {
          attributes: true,
          attributeFilter: ["content", "name"],
          childList: true,
          subtree: true
        });
      };

      const rewriteContent = (content) => {
        const directives = content
          .split(",")
          .map((part) => part.trim())
          .filter((part) => part.length > 0)
          .filter((part) => {
            const key = part.split("=")[0].trim().toLowerCase();
            return key !== "user-scalable" && key !== "maximum-scale";
          });
        directives.push("user-scalable=yes");
        directives.push("maximum-scale=10.0");
        return directives.join(", ");
      };

      const apply = () => {
        if (applying) {
          return;
        }
        applying = true;
        if (observer) {
          observer.disconnect();
        }
        let metas = Array.from(document.querySelectorAll("meta[name='viewport']"));
        if (metas.length === 0 && document.head) {
          const meta = document.createElement("meta");
          meta.setAttribute("name", "viewport");
          document.head.appendChild(meta);
          created.add(meta);
          metas = [meta];
        }
        metas.forEach((meta) => {
          if (!originals.has(meta) && !created.has(meta)) {
            originals.set(meta, meta.getAttribute("content"));
          }
          const rewritten = rewriteContent(meta.getAttribute("content") || "");
          if (meta.getAttribute("content") !== rewritten) {
            meta.setAttribute("content", rewritten);
          }
        });
        applying = false;
        if (observer) {
          observe();
        }
      };

      apply();
      observer = new MutationObserver((records) => {
        records.forEach((record) => {
          if (
            record.type === "attributes" &&
            record.target instanceof HTMLMetaElement &&
            originals.has(record.target) &&
            !created.has(record.target)
          ) {
            originals.set(record.target, record.target.getAttribute("content"));
          }
          if (record.type === "childList") {
            record.addedNodes.forEach((node) => {
              if (
                node instanceof HTMLMetaElement &&
                originals.has(node) &&
                !created.has(node)
              ) {
                originals.set(node, node.getAttribute("content"));
              }
            });
          }
        });
        apply();
      });
      observe();
      window[stateKey] = {
        dispose: () => {
          observer.disconnect();
          originals.forEach((content, meta) => {
            if (!meta.isConnected) {
              return;
            }
            if (content === null) {
              meta.removeAttribute("content");
            } else {
              meta.setAttribute("content", content);
            }
          });
          created.forEach((meta) => {
            if (meta.isConnected) {
              meta.remove();
            }
          });
        }
      };
      return true;
    })();
    """.trimIndent()
