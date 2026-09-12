package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.WebSettings
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools

internal const val BROWSER_FORCE_PAGE_ZOOM_MIN_SCALE = "0.1"

/**
 * 手动桌面模式的最小布局宽度（CSS px）。UA 只影响服务器/脚本识别；响应式页面仍会按
 * width=device-width 使用手机断点，因此需同时覆盖 viewport。宽屏设备不缩小到该最小值。
 */
internal const val BROWSER_DESKTOP_LAYOUT_VIEWPORT_WIDTH_CSS_PX = 980

internal fun shouldUseBrowserOverviewMode(
    usesDesktopLayout: Boolean,
    forcePageZoomEnabled: Boolean,
    viewportWidthCssPx: Int?,
): Boolean =
    usesDesktopLayout &&
        viewportWidthCssPx == null &&
        !forcePageZoomEnabled

/**
 * AI 显式设置的会话视口宽度是更高优先级的布局契约；只有它未生效时，PC UA 才接管宽视口/
 * 桌面排版覆盖。与 [useWideViewPort] 共用同一判定，避免两处条件表达式各自维护后逐渐分叉。
 */
internal fun shouldOverrideDesktopViewportForPage(
    usesDesktopLayout: Boolean,
    viewportWidthCssPx: Int?,
): Boolean = usesDesktopLayout && viewportWidthCssPx == null

internal fun StandardBrowserSessionTools.isForcePageZoomEnabledForPage(
    domainOrUrl: String,
): Boolean {
    val settings = browserSettingsStore.current
    return resolveWebSessionSiteFeatureEnabled(
        settings = settings,
        domainOrUrl = domainOrUrl,
        feature = WebSessionSiteFeature.FORCE_PAGE_ZOOM,
        globalEnabled = settings.forcePageZoomEnabled,
    )
}

internal fun StandardBrowserSessionTools.applyBrowserViewportSettings(
    session: BrowserToolSession,
    domainOrUrl: String = session.currentUrl,
) {
    val forcePageZoomEnabled = isForcePageZoomEnabledForPage(domainOrUrl)
    val overrideDesktopViewport =
        shouldOverrideDesktopViewportForPage(
            usesDesktopLayout = session.usesDesktopUserAgentLayout,
            viewportWidthCssPx = session.viewportWidthCssPx,
        )
    with(session.webView.settings) {
        useWideViewPort = overrideDesktopViewport
        loadWithOverviewMode =
            shouldUseBrowserOverviewMode(
                usesDesktopLayout = session.usesDesktopUserAgentLayout,
                forcePageZoomEnabled = forcePageZoomEnabled,
                viewportWidthCssPx = session.viewportWidthCssPx,
            )
    }
    // 必须在 loadUrl 前注册，避免站点在初始化时锁定手机布局；句柄属于同一 WebSession。
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        val script = browserViewportOverrideScript(forcePageZoomEnabled, overrideDesktopViewport)
        if (session.viewportDocumentStartScript != script) {
            session.viewportDocumentStartHandler?.remove()
            session.viewportDocumentStartHandler =
                WebViewCompat.addDocumentStartJavaScript(session.webView, script, setOf("*"))
            session.viewportDocumentStartScript = script
        }
    }
}

private fun StandardBrowserSessionTools.evaluateViewportOverrideScript(session: BrowserToolSession) {
    val forcePageZoomEnabled = isForcePageZoomEnabledForPage(session.currentUrl)
    val overrideDesktopViewport =
        shouldOverrideDesktopViewportForPage(
            usesDesktopLayout = session.usesDesktopUserAgentLayout,
            viewportWidthCssPx = session.viewportWidthCssPx,
        )
    session.webView.evaluateJavascript(
        browserViewportOverrideScript(
            forceZoomEnabled = forcePageZoomEnabled,
            desktopLayoutEnabled = overrideDesktopViewport,
        ),
        null,
    )
}

internal fun StandardBrowserSessionTools.applyBrowserDisplaySettingsOnMain() {
    val settings = browserSettingsStore.current
    StandardBrowserSessionTools.sessions.values.forEach { session ->
        session.webView.settings.textZoom = settings.webTextZoomPercent
        applyBrowserViewportSettings(session)
        if (session.pageLoaded) {
            evaluateViewportOverrideScript(session)
        }
    }
    refreshSessionUiOnMain()
}

internal fun StandardBrowserSessionTools.applyBrowserDisplaySettingsOnPage(
    session: BrowserToolSession,
) {
    val settings = browserSettingsStore.current
    session.webView.settings.textZoom = settings.webTextZoomPercent
    applyBrowserViewportSettings(session)
    evaluateViewportOverrideScript(session)
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

/**
 * 统一管理页面 `<meta name="viewport">` 的单一脚本：强制缩放只改写 scale 相关指令，
 * 桌面排版只改写 width/initial-scale 相关指令，两者写入同一个 content 字符串、共用同一个
 * MutationObserver，避免各自独立的观察者互相触发对方的回调、在同一 meta 标签上来回改写。
 */
internal fun browserViewportOverrideScript(
    forceZoomEnabled: Boolean,
    desktopLayoutEnabled: Boolean,
): String =
    BROWSER_FORCE_PAGE_ZOOM_SCRIPT_TEMPLATE
        .replace("__KIYORI_FORCE_ZOOM_ENABLED__", forceZoomEnabled.toString())
        .replace("__KIYORI_DESKTOP_LAYOUT_ENABLED__", desktopLayoutEnabled.toString())

private val BROWSER_FORCE_PAGE_ZOOM_SCRIPT_TEMPLATE =
    """
    (() => {
      // document-start 也会在子框架执行；桌面布局只属于顶层页面。
      if (window.top !== window) return false;
      const forceZoom = __KIYORI_FORCE_ZOOM_ENABLED__;
      const desktop = __KIYORI_DESKTOP_LAYOUT_ENABLED__;
      const stateKey = "__kiyoriViewportOverrideV1";
      const previous = window[stateKey];
      if (previous && previous.forceZoom === forceZoom && previous.desktop === desktop) {
        previous.refresh();
        return true;
      }
      if (previous) previous.dispose();
      delete window[stateKey];
      if (!forceZoom && !desktop) return true;

      const originals = new Map();
      const applied = new Map();
      const created = new Set();
      const isMeta = (node) => node instanceof HTMLMetaElement;
      const isViewport = (node) => isMeta(node) &&
        (node.getAttribute("name") || "").trim().toLowerCase() === "viewport";
      const viewportMetas = () => Array.from(document.querySelectorAll("meta[name]")).filter(isViewport);
      const read = (meta) => meta.getAttribute("content");
      const restore = (meta, content) => {
        if (content === null) meta.removeAttribute("content");
        else meta.setAttribute("content", content);
      };
      const desktopWidth = Math.max(${BROWSER_DESKTOP_LAYOUT_VIEWPORT_WIDTH_CSS_PX}, Number(window.screen.width) || 0);
      const rewriteContent = (content) => {
        const directives = content.split(/[,;]/).map((part) => part.trim()).filter(Boolean).filter((part) => {
          const key = part.split("=")[0].trim().toLowerCase();
          // 桌面初始适屏也不能保留作者 minimum-scale=1 等手机缩放限制。
          if ((forceZoom || desktop) && ["user-scalable", "minimum-scale", "maximum-scale"].includes(key)) return false;
          if (desktop && ["width", "height", "initial-scale"].includes(key)) return false;
          return true;
        });
        if (desktop) directives.push("width=" + desktopWidth);
        if (forceZoom || desktop) {
          directives.push("user-scalable=yes", "minimum-scale=${BROWSER_FORCE_PAGE_ZOOM_MIN_SCALE}", "maximum-scale=10.0");
        }
        return directives.join(", ");
      };
      // 只处理 viewport 相关变化；大型页面每次新增正文节点不能触发整页 querySelectorAll。
      const containsViewport = (node) => isViewport(node) ||
        (node.nodeType === 1 && Array.from(node.querySelectorAll("meta[name]")).some(isViewport));
      const relevant = (records) => records.some((record) => {
        if (record.type === "attributes") return isViewport(record.target) || applied.has(record.target);
        return [...record.addedNodes, ...record.removedNodes].some((node) =>
          containsViewport(node) || (node.nodeType === 1 && (node.localName === "head" || node.localName === "html")));
      });
      const captureAuthorChanges = () => {
        applied.forEach((content, meta) => {
          if (read(meta) !== content) {
            originals.set(meta, read(meta));
            // 页面接管了我们创建的标签时，关闭功能必须保留页面的新值。
            created.delete(meta);
          }
        });
      };
      const release = (meta) => {
        if (read(meta) === applied.get(meta)) {
          if (created.has(meta)) meta.remove();
          else restore(meta, originals.get(meta));
        }
        originals.delete(meta);
        applied.delete(meta);
        created.delete(meta);
      };
      const observe = () => observer.observe(document, {
        attributes: true, attributeFilter: ["content", "name"], childList: true, subtree: true
      });
      const refresh = () => {
        observer.disconnect();
        captureAuthorChanges();
        // 已移除/改名的 meta 不能永久驻留 Map，亦不能把覆盖值带进下一用途。
        Array.from(applied.keys()).forEach((meta) => {
          if (!meta.isConnected || !isViewport(meta)) release(meta);
        });
        let metas = viewportMetas();
        if (!metas.length && document.head) {
          const meta = document.createElement("meta");
          meta.setAttribute("name", "viewport");
          document.head.appendChild(meta);
          created.add(meta);
          metas = [meta];
        }
        // 作者稍后添加真实标签时，移除我们的占位标签，避免两个 viewport 相互竞争。
        if (metas.some((meta) => !created.has(meta))) {
          metas.filter((meta) => created.has(meta)).forEach((meta) => {
            meta.remove(); originals.delete(meta); applied.delete(meta); created.delete(meta);
          });
          metas = metas.filter((meta) => !created.has(meta) && meta.isConnected);
        }
        metas.forEach((meta) => {
          if (!originals.has(meta)) originals.set(meta, read(meta));
          const rewritten = rewriteContent(originals.get(meta) || "");
          if (read(meta) !== rewritten) meta.setAttribute("content", rewritten);
          applied.set(meta, rewritten);
        });
        observe();
      };
      const observer = new MutationObserver((records) => { if (relevant(records)) refresh(); });
      window[stateKey] = {
        forceZoom, desktop, refresh,
        dispose: () => {
          observer.disconnect();
          // 同一 JS 任务内先写 meta 再切换开关时，观察器尚未回调，也要保留作者最新值。
          captureAuthorChanges();
          Array.from(applied.keys()).forEach(release);
          created.clear(); originals.clear(); applied.clear();
        }
      };
      refresh();
      return true;
    })();
    """.trimIndent()
