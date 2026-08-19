package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private const val EXECUTION_SUPPORT_TAG = "BrowserSessionTools"

internal class BrowserWebDownloadBridge(
    private val tools: StandardBrowserSessionTools,
    private val session: BrowserToolSession
) {
    @JavascriptInterface
    fun downloadBase64(base64Data: String?, fileName: String?, mimeType: String?) {
        tools.handleInlineDownload(
            session = session,
            base64Data = base64Data.orEmpty(),
            fileName = fileName.orEmpty(),
            mimeType = mimeType.orEmpty(),
            type = "inline"
        )
    }

    @JavascriptInterface
    fun log(message: String?) {
        AppLogger.d(EXECUTION_SUPPORT_TAG, "web download bridge: ${message.orEmpty()}")
    }
}

internal class BrowserAsyncBridge {
    @JavascriptInterface
    fun resolve(callId: String?, payload: String?) {
        if (callId.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)?.let { pending ->
            pending.result = payload
            pending.latch.countDown()
        }
    }

    @JavascriptInterface
    fun reject(callId: String?, error: String?) {
        if (callId.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)?.let { pending ->
            pending.error = error ?: "Unknown JavaScript error"
            pending.latch.countDown()
        }
    }
}

internal class BrowserTextSelectionBridge(
    private val browserTools: StandardBrowserSessionTools,
) {
    private val editableLongPressTarget = AtomicBoolean(false)

    @JavascriptInterface
    fun setEditableLongPressTarget(editable: Boolean) {
        editableLongPressTarget.set(editable)
    }

    fun isEditableLongPressTarget(): Boolean = editableLongPressTarget.get()

    @JavascriptInterface
    fun showActions(
        anchorX: Double,
        anchorY: Double,
        viewportWidth: Double,
        viewportHeight: Double
    ) {
        StandardBrowserSessionTools.mainHandler.post {
            browserTools.browserHost?.showTextSelectionActionsOverlay(
                anchorX = anchorX,
                anchorY = anchorY,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight
            )
        }
    }

    @JavascriptInterface
    fun performHapticFeedback() {
        StandardBrowserSessionTools.mainHandler.post {
            browserTools.browserHost?.performTextSelectionHaptic()
        }
    }

    @JavascriptInterface
    fun hideActions() {
        StandardBrowserSessionTools.mainHandler.post {
            browserTools.browserHost?.hideTextSelectionActionsOverlay()
        }
    }
}

internal class BrowserWebElementBridge(
    private val browserTools: StandardBrowserSessionTools,
    private val session: BrowserToolSession,
) {
    @JavascriptInterface
    fun showActions(payload: String?) {
        if (payload.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.mainHandler.post {
            browserTools.browserHost?.showWebElementActions(
                sessionId = session.id,
                payload = payload,
            )
        }
    }

    @JavascriptInterface
    fun showImageViewer(payload: String?) {
        if (payload.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.mainHandler.post {
            browserTools.browserHost?.showWebElementImageViewer(
                sessionId = session.id,
                payload = payload,
            )
        }
    }

    @JavascriptInterface
    fun updateAdMarking(payload: String?) {
        if (payload.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.mainHandler.post {
            browserTools.browserHost?.updateAdMarkingSelection(
                sessionId = session.id,
                payload = payload,
            )
        }
    }

    @JavascriptInterface
    fun requestNavigation(payload: String?) {
        if (payload.isNullOrBlank()) {
            return
        }
        StandardBrowserSessionTools.mainHandler.post {
            browserTools.browserHost?.showAdMarkingNavigationRequest(
                sessionId = session.id,
                payload = payload,
            )
        }
    }

    @JavascriptInterface
    fun reportRuntimeFailure(operation: String?) {
        val normalizedOperation =
            when (operation) {
                "resolve-url" -> "resolve-url"
                else -> "unknown"
            }
        AppLogger.w(
            EXECUTION_SUPPORT_TAG,
            "Browser element helper failed operation=$normalizedOperation session=${session.id}",
        )
    }
}

internal fun StandardBrowserSessionTools.createDownloadListener(
    session: BrowserToolSession
): DownloadListener {
    return DownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
        try {
            when {
                url.startsWith("blob:") -> {
                    injectBlobDownloaderScript(session, url)
                }

                url.startsWith("data:") -> {
                    handleInlineDownload(
                        session = session,
                        base64Data = url,
                        fileName = "",
                        mimeType = guessMimeTypeFromDataUrl(url).ifBlank { mimetype.orEmpty() },
                        type = "data",
                        sourceUrl = url
                    )
                }

                else -> {
                    handleRegularDownload(
                        session = session,
                        url = url,
                        userAgent = userAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimetype,
                        contentLength = contentLength,
                    )
                }
            }
        } catch (e: Exception) {
            recordDownloadEvent(
                session,
                WebDownloadEvent(
                    status = "failed",
                    type =
                        when {
                            url.startsWith("blob:") -> "blob"
                            url.startsWith("data:") -> "data"
                            else -> "http"
                        },
                    fileName =
                        if (url.startsWith("data:") || url.startsWith("blob:")) {
                            resolveInlineDownloadFileName("", mimetype.orEmpty().ifBlank { guessMimeTypeFromDataUrl(url) })
                        } else {
                            sanitizeFileName(URLUtil.guessFileName(url, contentDisposition, mimetype))
                        },
                    url = url,
                    mimeType = mimetype,
                    error = e.message ?: "download_failed"
                )
            )
            AppLogger.e(EXECUTION_SUPPORT_TAG, "Download failed for session=${session.id}: ${e.message}", e)
            StandardBrowserSessionTools.mainHandler.post {
                Toast.makeText(
                    context,
                    context.getString(R.string.download_failed, e.message ?: url),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

internal fun StandardBrowserSessionTools.injectBrowserElementInteractionHelper(
    webView: WebView,
    navigationPolicy: BrowserAdMarkingNavigationPolicy = BrowserAdMarkingNavigationPolicy.DEFAULT,
    elementActionsEnabled: Boolean,
) {
    val navigationPolicyValue = navigationPolicy.toJavascriptValue()
    val elementActionsEnabledValue = elementActionsEnabled.toString()
    webView.evaluateJavascript(
        """
        (function() {
            if (window.__kiyoriElementActions) {
                if (typeof window.__kiyoriElementActions.destroy === "function") {
                    window.__kiyoriElementActions.destroy();
                } else {
                    window.__kiyoriElementActions.finish();
                }
            }

            const runtimeAttribute = "data-kiyori-runtime-ui";
            const state = {
                contextElement: null,
                selectedElement: null,
                highlight: null,
                marking: false,
                previewing: false,
                previewDisplayValue: "",
                previewDisplayPriority: "",
                previewedElements: [],
                navigationPolicy: "$navigationPolicyValue",
                elementActionsEnabled: $elementActionsEnabledValue,
                eventHandlers: [],
                lastSelectionAt: 0,
                lastNavigationUrl: "",
                lastNavigationAt: 0
            };

            function stringValue(value, limit) {
                const normalized = String(value || "").replace(/\s+/g, " ").trim();
                return normalized.length > limit
                    ? normalized.slice(0, limit) + "…"
                    : normalized;
            }

            function prettyHtml(value) {
                const source = String(value || "").trim();
                if (!source) {
                    return "";
                }
                let depth = 0;
                return source
                    .replace(/>\s*</g, ">\n<")
                    .split("\n")
                    .map(function(rawLine) {
                        const line = rawLine.trim();
                        if (/^<\//.test(line)) {
                            depth = Math.max(0, depth - 1);
                        }
                        const rendered = "    ".repeat(depth) + line;
                        const opensContainer =
                            /^<[^!/][^>]*>$/.test(line) &&
                            !/^<[^>]+\/>$/.test(line) &&
                            !/<\/[^>]+>$/.test(line) &&
                            !/^<(area|base|br|col|embed|hr|img|input|link|meta|param|source|track|wbr)\b/i.test(line);
                        if (opensContainer) {
                            depth += 1;
                        }
                        return rendered;
                    })
                    .join("\n");
            }

            function isRuntimeElement(element) {
                return !!element &&
                    !!element.closest &&
                    !!element.closest("[" + runtimeAttribute + "]");
            }

            function stableClasses(element) {
                return Array.from(element.classList || [])
                    .filter(function(name) {
                        return name.length > 0 &&
                            name.length <= 48 &&
                            name.replace(/[^0-9]/g, "").length <= 3 &&
                            name.indexOf("&&") < 0 &&
                            name.indexOf(",") < 0;
                    })
                    .slice(0, 3);
            }

            function previousMatchIndex(element, kind, name) {
                let count = 0;
                let sibling = element.previousElementSibling;
                while (sibling) {
                    const tag = String(sibling.localName || "").toLowerCase();
                    if (tag !== "script" && tag !== "style") {
                        if (
                            kind === "class" &&
                            sibling.classList &&
                            sibling.classList.contains(name)
                        ) {
                            count += 1;
                        } else if (kind === "tag" && tag === name) {
                            count += 1;
                        }
                    }
                    sibling = sibling.previousElementSibling;
                }
                return count;
            }

            function hikerSelectorPart(element) {
                const tag = String(element.localName || "").toLowerCase();
                if (tag === "body") {
                    return { value: "body", stop: true };
                }
                const id = String(element.getAttribute("id") || "").trim();
                if (
                    id &&
                    id.length <= 128 &&
                    id.indexOf("&&") < 0 &&
                    id.replace(/[^0-9]/g, "").length <= 3 &&
                    document.getElementById(id) === element
                ) {
                    return { value: "#" + id, stop: true };
                }
                const classes = stableClasses(element);
                if (classes.length > 0) {
                    const className = classes[0];
                    return {
                        value:
                            "." +
                            className +
                            "," +
                            previousMatchIndex(element, "class", className),
                        stop: false
                    };
                }
                if (!tag) {
                    return null;
                }
                return {
                    value: tag + "," + previousMatchIndex(element, "tag", tag),
                    stop: false
                };
            }

            function selectorFor(element) {
                if (!element || element.nodeType !== Node.ELEMENT_NODE) {
                    return "";
                }
                const parts = [];
                let current = element;
                while (
                    current &&
                    current.nodeType === Node.ELEMENT_NODE &&
                    current !== document.documentElement &&
                    parts.length < 12
                ) {
                    const part = hikerSelectorPart(current);
                    if (!part) {
                        break;
                    }
                    parts.unshift(part.value);
                    if (part.stop) {
                        break;
                    }
                    current = current.parentElement;
                }
                return parts.join("&&");
            }

            function parseIndexedSelectorPart(part) {
                const separator = part.lastIndexOf(",");
                if (separator <= 0) {
                    return null;
                }
                const index = Number(part.slice(separator + 1));
                if (!Number.isInteger(index) || index < 0) {
                    return null;
                }
                return {
                    token: part.slice(0, separator),
                    index: index
                };
            }

            function matchingSelectorChildren(parent, token) {
                const children = Array.from(parent.children || []).filter(function(child) {
                    const tag = String(child.localName || "").toLowerCase();
                    return tag !== "script" && tag !== "style";
                });
                if (token.startsWith(".")) {
                    const className = token.slice(1);
                    return children.filter(function(child) {
                        return child.classList && child.classList.contains(className);
                    });
                }
                return children.filter(function(child) {
                    return String(child.localName || "").toLowerCase() === token.toLowerCase();
                });
            }

            function resolveHikerSelector(selector) {
                const parts = String(selector || "")
                    .split("&&")
                    .map(function(part) {
                        return part.trim();
                    })
                    .filter(Boolean);
                if (parts.length === 0) {
                    return null;
                }
                let current = null;
                for (let index = 0; index < parts.length; index += 1) {
                    const part = parts[index];
                    if (part.startsWith("#")) {
                        const identified = document.getElementById(part.slice(1));
                        if (!identified || (current && identified.parentElement !== current)) {
                            return null;
                        }
                        current = identified;
                        continue;
                    }
                    if (part === "body") {
                        if (index !== 0 || !document.body) {
                            return null;
                        }
                        current = document.body;
                        continue;
                    }
                    const parsed = parseIndexedSelectorPart(part);
                    if (!parsed) {
                        return null;
                    }
                    if (!current) {
                        const rootMatches =
                            parsed.token.startsWith(".")
                                ? Array.from(document.getElementsByClassName(parsed.token.slice(1)))
                                : Array.from(document.getElementsByTagName(parsed.token));
                        current = rootMatches[parsed.index] || null;
                    } else {
                        current =
                            matchingSelectorChildren(current, parsed.token)[parsed.index] || null;
                    }
                    if (!current) {
                        return null;
                    }
                }
                return current;
            }

            function elementsForRule(selector) {
                const normalized = String(selector || "").trim();
                if (!normalized) {
                    return [];
                }
                if (normalized.indexOf("&&") >= 0) {
                    const resolved = resolveHikerSelector(normalized);
                    return resolved ? [resolved] : [];
                }
                try {
                    return Array.from(document.querySelectorAll(normalized));
                } catch (error) {
                    return [];
                }
            }

            function resolvedUrl(value) {
                const raw = String(value || "").trim();
                if (!raw) {
                    return "";
                }
                try {
                    return new URL(raw, document.baseURI).href;
                } catch (error) {
                    window.OperitWebElementBridge.reportRuntimeFailure("resolve-url");
                    return "";
                }
            }

            function nestedImageFor(element) {
                if (!element) {
                    return null;
                }
                const selector =
                    "img[src], img[data-src], img[data-original], " +
                    "img[data-lazy-src], picture img";
                const directNested =
                    element.querySelector ? element.querySelector(selector) : null;
                if (directNested) {
                    return directNested;
                }
                const linkContainer =
                    element.closest ? element.closest("a[href]") : null;
                return linkContainer && linkContainer !== element && linkContainer.querySelector
                    ? linkContainer.querySelector(selector)
                    : null;
            }

            function resourceUrlFor(element) {
                if (!element) {
                    return "";
                }
                const directValues = [
                    element.currentSrc,
                    element.src,
                    element.getAttribute("src"),
                    element.getAttribute("data-src"),
                    element.getAttribute("data-original"),
                    element.getAttribute("data-lazy-src"),
                    element.getAttribute("data-url"),
                    element.getAttribute("poster")
                ];
                for (let index = 0; index < directValues.length; index += 1) {
                    const direct = resolvedUrl(directValues[index]);
                    if (direct) {
                        return direct;
                    }
                }
                const nestedImage = nestedImageFor(element);
                if (nestedImage) {
                    const nestedImageValues = [
                        nestedImage.currentSrc,
                        nestedImage.src,
                        nestedImage.getAttribute("src"),
                        nestedImage.getAttribute("data-src"),
                        nestedImage.getAttribute("data-original"),
                        nestedImage.getAttribute("data-lazy-src")
                    ];
                    for (let index = 0; index < nestedImageValues.length; index += 1) {
                        const nestedImageUrl = resolvedUrl(nestedImageValues[index]);
                        if (nestedImageUrl) {
                            return nestedImageUrl;
                        }
                    }
                }
                const source = element.querySelector
                    ? element.querySelector("source[src], source[data-src]")
                    : null;
                if (source) {
                    const sourceUrl = resolvedUrl(
                        source.getAttribute("src") || source.getAttribute("data-src")
                    );
                    if (sourceUrl) {
                        return sourceUrl;
                    }
                }
                const backgroundImage =
                    element.ownerDocument.defaultView.getComputedStyle(element).backgroundImage || "";
                const backgroundMatch =
                    backgroundImage.match(/url\((?:"([^"]+)"|'([^']+)'|([^)]*))\)/i);
                return backgroundMatch
                    ? resolvedUrl(backgroundMatch[1] || backgroundMatch[2] || backgroundMatch[3])
                    : "";
            }

            function resourceKindFor(element, resourceUrl) {
                if (!element || !resourceUrl) {
                    return "none";
                }
                const tag = String(element.localName || "").toLowerCase();
                if (tag === "img" || tag === "picture") {
                    return "image";
                }
                if (tag === "source") {
                    const parentTag =
                        String(element.parentElement && element.parentElement.localName || "")
                            .toLowerCase();
                    if (parentTag === "picture") {
                        return "image";
                    }
                    if (parentTag === "video") {
                        return "video";
                    }
                    if (parentTag === "audio") {
                        return "audio";
                    }
                }
                if (tag === "video") {
                    const posterUrl = resolvedUrl(element.getAttribute("poster"));
                    return posterUrl && posterUrl === resourceUrl ? "image" : "video";
                }
                if (tag === "audio") {
                    return "audio";
                }
                if (tag === "iframe") {
                    return "frame";
                }
                if (tag === "object" || tag === "embed") {
                    return "other";
                }
                const imageAncestor =
                    element.closest ? element.closest("img, picture") : null;
                if (imageAncestor) {
                    return "image";
                }
                const nestedImage = nestedImageFor(element);
                if (nestedImage) {
                    return "image";
                }
                const backgroundImage =
                    element.ownerDocument.defaultView.getComputedStyle(element).backgroundImage || "";
                const backgroundMatch =
                    backgroundImage.match(/url\((?:"([^"]+)"|'([^']+)'|([^)]*))\)/i);
                if (backgroundMatch) {
                    const backgroundUrl =
                        resolvedUrl(
                            backgroundMatch[1] ||
                                backgroundMatch[2] ||
                                backgroundMatch[3]
                        );
                    if (backgroundUrl === resourceUrl) {
                        return "image";
                    }
                }
                return "other";
            }

            function textSelectionTargetFor(element) {
                if (!element || isRuntimeElement(element)) {
                    return null;
                }
                const target =
                    element.closest
                        ? element.closest("input, textarea, [contenteditable]")
                        : element;
                if (
                    target &&
                    (
                        target.tagName === "TEXTAREA" ||
                        (
                            target.tagName === "INPUT" &&
                            !/^(button|checkbox|color|file|hidden|image|radio|range|reset|submit)$/i.test(target.type || "")
                        ) ||
                        target.isContentEditable
                    )
                ) {
                    return target;
                }
                return null;
            }

            function payloadFor(element, x, y) {
                if (!element || isRuntimeElement(element)) {
                    return null;
                }
                const anchor = element.closest ? element.closest("a[href], area[href]") : null;
                const selector = selectorFor(element);
                if (!selector) {
                    return null;
                }
                const resourceUrl = resourceUrlFor(element);
                return {
                    pageUrl: String(location.href || ""),
                    tagName: String(element.tagName || "").toLowerCase(),
                    text: stringValue(element.innerText || element.textContent || "", 1000),
                    linkUrl: anchor ? resolvedUrl(anchor.getAttribute("href")) : "",
                    resourceUrl: resourceUrl,
                    resourceKind: resourceKindFor(element, resourceUrl),
                    selector: selector,
                    html: prettyHtml(String(element.outerHTML || "").slice(0, 20000)),
                    clientX: Number(x || 0),
                    clientY: Number(y || 0)
                };
            }

            function collectPageImageUrls(selectedUrl) {
                const urls = [];
                const identities = new Set();
                function addImageUrl(value) {
                    const url = resolvedUrl(value);
                    if (!url || !/^https?:\/\//i.test(url)) {
                        return;
                    }
                    const identity = url.split("#", 1)[0];
                    if (identities.has(identity) || urls.length >= 200) {
                        return;
                    }
                    identities.add(identity);
                    urls.push(url);
                }
                addImageUrl(selectedUrl);
                const elements = Array.from(document.querySelectorAll("*")).slice(0, 2000);
                for (let index = 0; index < elements.length && urls.length < 200; index += 1) {
                    const element = elements[index];
                    const url = resourceUrlFor(element);
                    if (resourceKindFor(element, url) === "image") {
                        addImageUrl(url);
                    }
                }
                return urls;
            }

            function navigationUrlFor(element) {
                if (!element || !element.closest) {
                    return "";
                }
                const anchor = element.closest("a[href], area[href]");
                return anchor ? resolvedUrl(anchor.getAttribute("href")) : "";
            }

            function elementFromEvent(event) {
                const path =
                    event && typeof event.composedPath === "function"
                        ? event.composedPath()
                        : [];
                for (let index = 0; index < path.length; index += 1) {
                    const candidate = path[index];
                    if (
                        candidate &&
                        candidate.nodeType === Node.ELEMENT_NODE &&
                        !isRuntimeElement(candidate)
                    ) {
                        return candidate;
                    }
                }
                const target = event ? event.target : null;
                return target && target.nodeType === Node.ELEMENT_NODE &&
                    !isRuntimeElement(target)
                    ? target
                    : null;
            }

            function suppressMarkingEvent(event) {
                event.preventDefault();
                event.stopPropagation();
                if (typeof event.stopImmediatePropagation === "function") {
                    event.stopImmediatePropagation();
                }
            }

            function isActivationEvent(event) {
                return event.type === "pointerup" ||
                    event.type === "touchend" ||
                    event.type === "mouseup" ||
                    event.type === "click";
            }

            function addManagedEventListener(target, type, handler, capture) {
                target.addEventListener(type, handler, capture);
                state.eventHandlers.push({
                    target: target,
                    type: type,
                    handler: handler,
                    capture: capture
                });
            }

            function removeHighlight() {
                if (state.highlight && state.highlight.parentNode) {
                    state.highlight.parentNode.removeChild(state.highlight);
                }
                state.highlight = null;
            }

            function ensureHighlight() {
                if (state.highlight && state.highlight.parentNode) {
                    return state.highlight;
                }
                const highlight = document.createElement("div");
                highlight.setAttribute(runtimeAttribute, "ad-marking-highlight");
                highlight.style.position = "fixed";
                highlight.style.pointerEvents = "none";
                highlight.style.zIndex = "2147483646";
                highlight.style.border = "2px solid #ef4444";
                highlight.style.background = "rgba(239, 68, 68, 0.14)";
                highlight.style.borderRadius = "4px";
                highlight.style.boxSizing = "border-box";
                document.documentElement.appendChild(highlight);
                state.highlight = highlight;
                return highlight;
            }

            function updateHighlight() {
                const element = state.selectedElement;
                if (!state.marking || !element || !element.isConnected || state.previewing) {
                    removeHighlight();
                    return;
                }
                const rect = element.getBoundingClientRect();
                const highlight = ensureHighlight();
                highlight.style.left = rect.left + "px";
                highlight.style.top = rect.top + "px";
                highlight.style.width = Math.max(0, rect.width) + "px";
                highlight.style.height = Math.max(0, rect.height) + "px";
            }

            function restorePreview() {
                if (!state.previewing) {
                    state.previewing = false;
                    return;
                }
                state.previewedElements.forEach(function(item) {
                    if (!item.element || !item.element.style) {
                        return;
                    }
                    if (item.displayValue) {
                        item.element.style.setProperty(
                            "display",
                            item.displayValue,
                            item.displayPriority
                        );
                    } else {
                        item.element.style.removeProperty("display");
                    }
                });
                state.previewedElements = [];
                state.previewing = false;
            }

            function notifySelection(x, y) {
                const payload = payloadFor(state.selectedElement, x, y);
                if (!payload) {
                    return;
                }
                payload.previewing = state.previewing;
                window.OperitWebElementBridge.updateAdMarking(JSON.stringify(payload));
            }

            function selectElement(element, x, y) {
                if (!element || isRuntimeElement(element)) {
                    return false;
                }
                restorePreview();
                state.selectedElement = element;
                state.previewDisplayValue = element.style.getPropertyValue("display");
                state.previewDisplayPriority = element.style.getPropertyPriority("display");
                updateHighlight();
                notifySelection(x, y);
                return true;
            }

            function moveSelection(direction) {
                const current = state.selectedElement;
                if (!current) {
                    return false;
                }
                let target = null;
                if (direction === "parent") {
                    target = current.parentElement;
                } else if (direction === "previous") {
                    target = current.previousElementSibling;
                } else if (direction === "next") {
                    target = current.nextElementSibling;
                } else if (direction === "child") {
                    target = current.firstElementChild;
                }
                return selectElement(target, 0, 0);
            }

            function previewRule(selector, notifyNative) {
                const elements = elementsForRule(selector);
                if (elements.length === 0) {
                    return false;
                }
                restorePreview();
                state.previewedElements = elements.map(function(element) {
                    const original = {
                        element: element,
                        displayValue: element.style.getPropertyValue("display"),
                        displayPriority: element.style.getPropertyPriority("display")
                    };
                    element.style.setProperty("display", "none", "important");
                    return original;
                });
                state.previewing = true;
                updateHighlight();
                if (notifyNative !== false) {
                    notifySelection(0, 0);
                }
                return true;
            }

            function setPreview(enabled) {
                const element = state.selectedElement;
                if (!element) {
                    return false;
                }
                if (enabled) {
                    return previewRule(selectorFor(element), true);
                }
                if (state.previewing) {
                    restorePreview();
                }
                updateHighlight();
                notifySelection(0, 0);
                return true;
            }

            function startMarking(useContextElement) {
                state.marking = true;
                if (useContextElement && state.contextElement) {
                    selectElement(state.contextElement, 0, 0);
                } else {
                    restorePreview();
                    state.selectedElement = null;
                    removeHighlight();
                }
            }

            function finish(removeListeners) {
                restorePreview();
                state.marking = false;
                state.contextElement = null;
                state.selectedElement = null;
                if (removeListeners) {
                    state.eventHandlers.forEach(function(binding) {
                        binding.target.removeEventListener(
                            binding.type,
                            binding.handler,
                            binding.capture
                        );
                    });
                    state.eventHandlers = [];
                }
                removeHighlight();
            }

            function handleMarkingEvent(event) {
                if (!state.marking) {
                    return;
                }
                const element = elementFromEvent(event);
                if (!element) {
                    return;
                }
                suppressMarkingEvent(event);
                const now = Date.now();
                if (
                    state.selectedElement !== element ||
                    now - state.lastSelectionAt >= 160
                ) {
                    state.lastSelectionAt = now;
                    selectElement(element, event.clientX, event.clientY);
                }
            }

            function sameHostFamily(left, right) {
                if (!left || !right) {
                    return true;
                }
                return left === right ||
                    left.endsWith("." + right) ||
                    right.endsWith("." + left);
            }

            function isExternalNavigation(url) {
                try {
                    const current = new URL(String(location.href || ""));
                    const target = new URL(String(url || ""), document.baseURI);
                    if (
                        (target.protocol !== "http:" && target.protocol !== "https:") ||
                        (current.protocol !== "http:" && current.protocol !== "https:")
                    ) {
                        return false;
                    }
                    return !sameHostFamily(
                        String(current.hostname || "").toLowerCase(),
                        String(target.hostname || "").toLowerCase()
                    );
                } catch (error) {
                    return false;
                }
            }

            function handleNormalNavigationEvent(event) {
                if (state.marking || state.navigationPolicy === "allow") {
                    return;
                }
                const element = elementFromEvent(event);
                const linkUrl = navigationUrlFor(element);
                if (!linkUrl || !isExternalNavigation(linkUrl)) {
                    return;
                }
                suppressMarkingEvent(event);
                if (
                    state.navigationPolicy === "ask" &&
                    isActivationEvent(event)
                ) {
                    const now = Date.now();
                    if (
                        state.lastNavigationUrl === linkUrl &&
                        now - state.lastNavigationAt < 700
                    ) {
                        return;
                    }
                    state.lastNavigationUrl = linkUrl;
                    state.lastNavigationAt = now;
                    window.OperitWebElementBridge.requestNavigation(
                        JSON.stringify({
                            url: linkUrl,
                            text: stringValue(
                                element.innerText || element.textContent || "",
                                160
                            )
                        })
                    );
                }
            }

            function handleInteractionEvent(event) {
                if (state.marking) {
                    handleMarkingEvent(event);
                } else {
                    handleNormalNavigationEvent(event);
                }
            }

            function selectAtViewPoint(x, y) {
                if (!state.marking) {
                    return false;
                }
                const viewX = Number(x);
                const viewY = Number(y);
                const pixelRatio = Number(window.devicePixelRatio);
                if (
                    !Number.isFinite(viewX) ||
                    !Number.isFinite(viewY) ||
                    !Number.isFinite(pixelRatio) ||
                    pixelRatio <= 0
                ) {
                    return false;
                }
                const clientX = viewX / pixelRatio;
                const clientY = viewY / pixelRatio;
                return selectElement(document.elementFromPoint(clientX, clientY), clientX, clientY);
            }

            [
                "pointerdown",
                "pointerup",
                "touchstart",
                "touchend",
                "mousedown",
                "mouseup",
                "click"
            ].forEach(function(type) {
                addManagedEventListener(document, type, handleInteractionEvent, true);
            });
            addManagedEventListener(window, "scroll", updateHighlight, true);
            addManagedEventListener(window, "resize", updateHighlight, true);

            window.__kiyoriElementActions = {
                handleLongPress: function(x, y) {
                    if (!state.elementActionsEnabled) {
                        return false;
                    }
                    const element = document.elementFromPoint(x, y);
                    if (textSelectionTargetFor(element)) {
                        return false;
                    }
                    const payload = payloadFor(element, x, y);
                    if (!payload) {
                        return false;
                    }
                    state.contextElement = element;
                    window.OperitWebElementBridge.showActions(JSON.stringify(payload));
                    return true;
                },
                openImageMode: function(selectedUrl) {
                    const normalizedSelectedUrl = resolvedUrl(selectedUrl);
                    if (!normalizedSelectedUrl) {
                        return false;
                    }
                    const images = collectPageImageUrls(normalizedSelectedUrl);
                    if (images.length === 0) {
                        return false;
                    }
                    window.OperitWebElementBridge.showImageViewer(
                        JSON.stringify({
                            pageUrl: String(location.href || ""),
                            selectedUrl: normalizedSelectedUrl,
                            images: images
                        })
                    );
                    return true;
                },
                startMarking: startMarking,
                moveSelection: moveSelection,
                selectAtViewPoint: selectAtViewPoint,
                setPreview: setPreview,
                previewSelector: function(selector) {
                    return previewRule(selector, false);
                },
                clearPreview: function() {
                    if (state.previewing) {
                        restorePreview();
                    }
                    updateHighlight();
                    return true;
                },
                setNavigationPolicy: function(policy) {
                    state.navigationPolicy =
                        policy === "allow"
                            ? "allow"
                            : policy === "ask"
                                ? "ask"
                                : "block";
                },
                setElementActionsEnabled: function(enabled) {
                    state.elementActionsEnabled = enabled === true;
                    if (!state.elementActionsEnabled) {
                        finish(false);
                    }
                },
                selectText: function(x, y) {
                    if (
                        window.__operitTextSelection &&
                        typeof window.__operitTextSelection.selectAtPoint === "function"
                    ) {
                        window.__operitTextSelection.selectAtPoint(x, y);
                    }
                },
                finish: function() {
                    finish(false);
                },
                destroy: function() {
                    finish(true);
                }
            };
        })();
        """.trimIndent(),
        null,
    )
}

internal fun StandardBrowserSessionTools.applyBrowserWebElementLongPressMenuSettingOnMain() {
    val activeSession = getActiveSessionOnMain()
    val activeEnabled = isBrowserWebElementLongPressMenuEnabledForPage(activeSession?.currentUrl.orEmpty())
    browserHost?.applyWebElementLongPressMenuSetting(activeEnabled)
    StandardBrowserSessionTools.sessions.values.forEach { session ->
        val enabled = isBrowserWebElementLongPressMenuEnabledForPage(session.currentUrl)
        session.webView.evaluateJavascript(
            """
            (function() {
                if (
                    window.__kiyoriElementActions &&
                    typeof window.__kiyoriElementActions.setElementActionsEnabled === "function"
                ) {
                    window.__kiyoriElementActions.setElementActionsEnabled($enabled);
                }
            })();
            """.trimIndent(),
            null,
        )
    }
}

internal fun StandardBrowserSessionTools.isBrowserWebElementLongPressMenuEnabledForPage(
    pageUrl: String,
): Boolean {
    val settings = browserSettingsStore.current
    return resolveWebSessionSiteFeatureEnabled(
        settings = settings,
        domainOrUrl = pageUrl,
        feature = WebSessionSiteFeature.WEB_ELEMENT_LONG_PRESS_MENU,
        globalEnabled = settings.webElementLongPressMenuEnabled,
    )
}

internal fun StandardBrowserSessionTools.injectTextSelectionHelper(webView: WebView) {
    val script =
        """
        (function() {
            if (window.__operitTextSelectionHelperInjected) {
                return;
            }
            window.__operitTextSelectionHelperInjected = true;

            const uiAttribute = "data-operit-selection-ui";
            const state = {
                longPressTimer: null,
                longPressArmed: false,
                startX: 0,
                startY: 0,
                range: null,
                selectedText: "",
                overlay: null,
                highlights: [],
                startHandle: null,
                endHandle: null,
                draggingHandle: "",
                dragFrame: 0,
                dragX: 0,
                dragY: 0,
                dragAdjustX: 0,
                dragAdjustY: 0
            };

            function clearLongPressTimer() {
                if (state.longPressTimer !== null) {
                    clearTimeout(state.longPressTimer);
                    state.longPressTimer = null;
                }
            }

            function isSelectionUiTarget(target) {
                return !!target && !!target.closest && !!target.closest("[" + uiAttribute + "='true']");
            }

            function editableTextControlFor(element) {
                if (!element) {
                    return null;
                }
                const target =
                    element.closest
                        ? element.closest("input, textarea, [contenteditable]")
                        : element;
                if (!target) {
                    return null;
                }
                if (target.tagName === "TEXTAREA") {
                    return target;
                }
                if (
                    target.tagName === "INPUT" &&
                    !/^(button|checkbox|color|file|hidden|image|radio|range|reset|submit)$/i.test(
                        target.type || ""
                    )
                ) {
                    return target;
                }
                return target.isContentEditable ? target : null;
            }

            function isEditableTextControl(element) {
                return !!editableTextControlFor(element);
            }

            function clearNativeSelection() {
                const selection = window.getSelection();
                if (selection) {
                    selection.removeAllRanges();
                }
            }

            function performHapticFeedback() {
                window.OperitTextSelectionBridge.performHapticFeedback();
            }

            function ensureOverlay() {
                if (state.overlay && state.overlay.parentNode) {
                    return state.overlay;
                }
                const overlay = document.createElement("div");
                overlay.setAttribute(uiAttribute, "true");
                overlay.style.position = "fixed";
                overlay.style.left = "0";
                overlay.style.top = "0";
                overlay.style.right = "0";
                overlay.style.bottom = "0";
                overlay.style.pointerEvents = "none";
                overlay.style.zIndex = "2147483647";
                overlay.style.contain = "layout style paint";
                document.documentElement.appendChild(overlay);
                state.overlay = overlay;
                return overlay;
            }

            function rangeFromPoint(x, y) {
                const previousStartPointerEvents = state.startHandle ? state.startHandle.style.pointerEvents : "";
                const previousEndPointerEvents = state.endHandle ? state.endHandle.style.pointerEvents : "";
                if (state.startHandle) {
                    state.startHandle.style.pointerEvents = "none";
                }
                if (state.endHandle) {
                    state.endHandle.style.pointerEvents = "none";
                }
                let pointRange = null;
                if (document.caretRangeFromPoint) {
                    pointRange = document.caretRangeFromPoint(x, y);
                } else if (document.caretPositionFromPoint) {
                    const position = document.caretPositionFromPoint(x, y);
                    if (position) {
                        pointRange = document.createRange();
                        pointRange.setStart(position.offsetNode, position.offset);
                        pointRange.collapse(true);
                    }
                }
                if (state.startHandle) {
                    state.startHandle.style.pointerEvents = previousStartPointerEvents;
                }
                if (state.endHandle) {
                    state.endHandle.style.pointerEvents = previousEndPointerEvents;
                }
                return pointRange;
            }

            function isSelectableCharacter(character) {
                return !!character && !/[\s.,!?;:()[\]{}"']/.test(character);
            }

            function wordRangeFromPointRange(pointRange) {
                const node = pointRange ? pointRange.startContainer : null;
                if (!node || node.nodeType !== Node.TEXT_NODE) {
                    return null;
                }
                const text = String(node.textContent || "");
                if (!text.trim()) {
                    return null;
                }

                let cursor = Math.max(0, Math.min(pointRange.startOffset, text.length));
                if (cursor === text.length && cursor > 0) {
                    cursor -= 1;
                }
                if (!isSelectableCharacter(text.charAt(cursor)) && cursor > 0 && isSelectableCharacter(text.charAt(cursor - 1))) {
                    cursor -= 1;
                }

                let start = cursor;
                let end = cursor + 1;
                while (start > 0 && isSelectableCharacter(text.charAt(start - 1))) {
                    start -= 1;
                }
                while (end < text.length && isSelectableCharacter(text.charAt(end))) {
                    end += 1;
                }

                if (start >= end || !text.slice(start, end).trim()) {
                    return null;
                }

                const range = document.createRange();
                range.setStart(node, start);
                range.setEnd(node, end);
                return range;
            }

            function textNodeFromElement(element, x, y) {
                if (!element) {
                    return null;
                }
                const walker = document.createTreeWalker(
                    element,
                    NodeFilter.SHOW_TEXT,
                    {
                        acceptNode: function(node) {
                            const text = String(node.textContent || "");
                            if (!text.trim()) {
                                return NodeFilter.FILTER_REJECT;
                            }

                            const range = document.createRange();
                            range.selectNodeContents(node);
                            const rects = range.getClientRects();
                            for (let index = 0; index < rects.length; index += 1) {
                                const rect = rects[index];
                                if (
                                    x >= rect.left &&
                                    x <= rect.right &&
                                    y >= rect.top &&
                                    y <= rect.bottom
                                ) {
                                    return NodeFilter.FILTER_ACCEPT;
                                }
                            }
                            return NodeFilter.FILTER_SKIP;
                        }
                    }
                );
                return walker.nextNode();
            }

            function clearVisuals() {
                state.highlights.forEach(function(highlight) {
                    if (highlight.parentNode) {
                        highlight.parentNode.removeChild(highlight);
                    }
                });
                state.highlights = [];
                [state.startHandle, state.endHandle].forEach(function(handle) {
                    if (handle && handle.parentNode) {
                        handle.parentNode.removeChild(handle);
                    }
                });
                state.startHandle = null;
                state.endHandle = null;
            }

            function selectionRects(range) {
                const rects = Array.from(range.getClientRects())
                    .filter(function(rect) {
                        return rect.width > 0 && rect.height > 0;
                    });
                if (rects.length > 0) {
                    return rects;
                }
                const rect = range.getBoundingClientRect();
                if (rect.width > 0 || rect.height > 0) {
                    return [rect];
                }
                return [];
            }

            function unionRects(rects) {
                let left = rects[0].left;
                let top = rects[0].top;
                let right = rects[0].right;
                let bottom = rects[0].bottom;
                rects.forEach(function(rect) {
                    left = Math.min(left, rect.left);
                    top = Math.min(top, rect.top);
                    right = Math.max(right, rect.right);
                    bottom = Math.max(bottom, rect.bottom);
                });
                return { left: left, top: top, right: right, bottom: bottom };
            }

            function setHighlightRect(highlight, rect) {
                highlight.setAttribute(uiAttribute, "true");
                highlight.style.position = "fixed";
                highlight.style.left = rect.left + "px";
                highlight.style.top = rect.top + "px";
                highlight.style.width = rect.width + "px";
                highlight.style.height = rect.height + "px";
                highlight.style.backgroundColor = "rgba(25, 118, 210, 0.32)";
                highlight.style.borderRadius = "2px";
                highlight.style.pointerEvents = "none";
            }

            function renderHighlights(rects) {
                const overlay = ensureOverlay();
                rects.forEach(function(rect, index) {
                    let highlight = state.highlights[index];
                    if (!highlight) {
                        highlight = document.createElement("div");
                        highlight.setAttribute(uiAttribute, "true");
                        overlay.appendChild(highlight);
                        state.highlights[index] = highlight;
                    }
                    setHighlightRect(highlight, rect);
                });
                while (state.highlights.length > rects.length) {
                    const highlight = state.highlights.pop();
                    if (highlight && highlight.parentNode) {
                        highlight.parentNode.removeChild(highlight);
                    }
                }
            }

            function createHandleElement(kind) {
                const overlay = ensureOverlay();
                const handle = document.createElement("div");
                const knob = document.createElement("div");
                const stem = document.createElement("div");
                handle.setAttribute(uiAttribute, "true");
                knob.setAttribute(uiAttribute, "true");
                stem.setAttribute(uiAttribute, "true");
                handle.style.position = "fixed";
                handle.style.width = "32px";
                handle.style.pointerEvents = "auto";
                handle.style.touchAction = "none";
                stem.style.position = "absolute";
                stem.style.left = "15px";
                stem.style.top = "0";
                stem.style.width = "2px";
                stem.style.backgroundColor = "#1976d2";
                stem.style.borderRadius = "2px";
                knob.style.position = "absolute";
                knob.style.left = "8px";
                knob.style.width = "16px";
                knob.style.height = "16px";
                knob.style.borderRadius = "50%";
                knob.style.backgroundColor = "#1976d2";
                knob.style.boxShadow = "0 1px 4px rgba(0,0,0,0.35)";
                handle.appendChild(stem);
                handle.appendChild(knob);
                handle.addEventListener("touchstart", function(event) {
                    const point = eventPoint(event);
                    const rect = handle.__operitBoundaryRect;
                    const boundaryX = handle.__operitBoundaryX;
                    const boundaryY = rect.top + rect.height / 2;
                    state.draggingHandle = kind;
                    state.dragAdjustX = boundaryX - point.x;
                    state.dragAdjustY = boundaryY - point.y;
                    window.OperitTextSelectionBridge.hideActions();
                    performHapticFeedback();
                    event.preventDefault();
                    event.stopPropagation();
                }, { capture: true, passive: false });
                overlay.appendChild(handle);
                return handle;
            }

            function updateHandle(kind, rect) {
                let handle = kind === "start" ? state.startHandle : state.endHandle;
                if (!handle || !handle.parentNode) {
                    handle = createHandleElement(kind);
                    if (kind === "start") {
                        state.startHandle = handle;
                    } else {
                        state.endHandle = handle;
                    }
                }
                const handleHeight = Math.max(30, rect.height + 18);
                handle.style.height = handleHeight + "px";
                handle.style.left = (kind === "start" ? rect.left - 16 : rect.right - 16) + "px";
                handle.style.top = rect.top + "px";
                handle.__operitBoundaryRect = rect;
                handle.__operitBoundaryX = kind === "start" ? rect.left : rect.right;
                const stem = handle.firstChild;
                const knob = handle.lastChild;
                stem.style.height = Math.max(1, rect.height + 7) + "px";
                knob.style.top = Math.max(0, rect.height - 1) + "px";
            }

            function showActionsForRect(rect) {
                const anchorX = (rect.left + rect.right) / 2;
                const anchorY = rect.top;
                window.OperitTextSelectionBridge.showActions(
                    anchorX,
                    anchorY,
                    window.innerWidth,
                    window.innerHeight
                );
            }

            function renderRangeSelection(showActions) {
                if (!state.range) {
                    clearVisuals();
                    clearNativeSelection();
                    return;
                }
                const rects = selectionRects(state.range);
                if (rects.length === 0) {
                    clearVisuals();
                    clearNativeSelection();
                    return;
                }
                renderHighlights(rects);
                updateHandle("start", rects[0]);
                updateHandle("end", rects[rects.length - 1]);
                state.selectedText = String(state.range.toString() || "");
                clearNativeSelection();
                if (showActions) {
                    showActionsForRect(unionRects(rects));
                }
            }

            function selectRange(range) {
                const text = String(range.toString() || "");
                if (!text.trim()) {
                    return false;
                }
                clearOperitSelection(false);
                state.range = range.cloneRange();
                state.selectedText = text;
                renderRangeSelection(true);
                performHapticFeedback();
                return true;
            }

            function selectAtPoint(x, y) {
                const element = document.elementFromPoint(x, y);
                if (editableTextControlFor(element)) {
                    // Editable controls are selected by Android WebView. Keeping this
                    // branch empty prevents a programmatic caller from recreating the
                    // old painted control selection.
                    return;
                }

                const pointRange = rangeFromPoint(x, y);
                const wordRange = wordRangeFromPointRange(pointRange);
                if (wordRange && selectRange(wordRange)) {
                    return;
                }

                const textNode = textNodeFromElement(element, x, y);
                if (textNode) {
                    const range = document.createRange();
                    range.selectNodeContents(textNode);
                    selectRange(range);
                }
            }

            function compareBoundaryPoint(nodeA, offsetA, nodeB, offsetB) {
                const pointA = document.createRange();
                const pointB = document.createRange();
                pointA.setStart(nodeA, offsetA);
                pointA.collapse(true);
                pointB.setStart(nodeB, offsetB);
                pointB.collapse(true);
                return pointA.compareBoundaryPoints(Range.START_TO_START, pointB);
            }

            function updateDraggedSelection(x, y) {
                if (!state.range || !state.draggingHandle) {
                    return;
                }
                const pointRange = rangeFromPoint(x, y);
                if (!pointRange) {
                    return;
                }
                const current = state.range;
                const nextRange = document.createRange();
                if (state.draggingHandle === "start") {
                    const order = compareBoundaryPoint(
                        pointRange.startContainer,
                        pointRange.startOffset,
                        current.endContainer,
                        current.endOffset
                    );
                    if (order <= 0) {
                        nextRange.setStart(pointRange.startContainer, pointRange.startOffset);
                        nextRange.setEnd(current.endContainer, current.endOffset);
                    } else {
                        nextRange.setStart(current.endContainer, current.endOffset);
                        nextRange.setEnd(pointRange.startContainer, pointRange.startOffset);
                        state.draggingHandle = "end";
                    }
                } else {
                    const order = compareBoundaryPoint(
                        current.startContainer,
                        current.startOffset,
                        pointRange.startContainer,
                        pointRange.startOffset
                    );
                    if (order <= 0) {
                        nextRange.setStart(current.startContainer, current.startOffset);
                        nextRange.setEnd(pointRange.startContainer, pointRange.startOffset);
                    } else {
                        nextRange.setStart(pointRange.startContainer, pointRange.startOffset);
                        nextRange.setEnd(current.startContainer, current.startOffset);
                        state.draggingHandle = "start";
                    }
                }
                if (!String(nextRange.toString() || "").trim()) {
                    return;
                }
                state.range = nextRange;
                renderRangeSelection(false);
            }

            function scheduleDraggedSelection(x, y) {
                state.dragX = x + state.dragAdjustX;
                state.dragY = y + state.dragAdjustY;
                if (state.dragFrame) {
                    return;
                }
                state.dragFrame = requestAnimationFrame(function() {
                    state.dragFrame = 0;
                    updateDraggedSelection(state.dragX, state.dragY);
                });
            }

            function eventPoint(event) {
                const touch =
                    event.touches && event.touches.length > 0
                        ? event.touches[0]
                        : event.changedTouches && event.changedTouches.length > 0
                            ? event.changedTouches[0]
                            : null;
                if (touch) {
                    return { x: touch.clientX, y: touch.clientY };
                }
                return { x: event.clientX, y: event.clientY };
            }

            function selectAllText() {
                const active = document.activeElement;
                if (isEditableTextControl(active)) {
                    // Native WebView ActionMode owns editable select-all. The custom
                    // helper must never paint or report a second control selection.
                    return;
                }
                const body = document.body;
                if (!body) {
                    return;
                }
                const range = document.createRange();
                range.selectNodeContents(body);
                selectRange(range);
            }

            function clearOperitSelection(hideActions) {
                if (state.dragFrame) {
                    cancelAnimationFrame(state.dragFrame);
                    state.dragFrame = 0;
                }
                clearVisuals();
                state.range = null;
                state.selectedText = "";
                clearNativeSelection();
                if (hideActions) {
                    window.OperitTextSelectionBridge.hideActions();
                }
            }

            window.__operitTextSelection = {
                getText: function() {
                    return state.selectedText;
                },
                clear: function() {
                    clearOperitSelection(true);
                },
                selectAll: function() {
                    selectAllText();
                },
                selectAtPoint: function(x, y) {
                    selectAtPoint(x, y);
                }
            };

            document.addEventListener("touchstart", function(event) {
                if (isSelectionUiTarget(event.target)) {
                    return;
                }
                clearLongPressTimer();
                state.longPressArmed = false;
                window.OperitTextSelectionBridge.setEditableLongPressTarget(
                    isEditableTextControl(event.target)
                );
                if (state.selectedText) {
                    clearOperitSelection(true);
                }
                if (!event.touches || event.touches.length !== 1) {
                    return;
                }
                const touch = event.touches[0];
                state.startX = touch.clientX;
                state.startY = touch.clientY;
                state.longPressTimer = setTimeout(function() {
                    state.longPressTimer = null;
                    const pressedElement = document.elementFromPoint(
                        state.startX,
                        state.startY
                    );
                    if (isEditableTextControl(pressedElement)) {
                        // Android WebView owns editable long-press. Calling the custom
                        // selector here would replace the real selection with a painted
                        // rectangle that cannot participate in the system clipboard or
                        // ActionMode.
                        return;
                    }
                    state.longPressArmed = true;
                    const handled =
                        window.__kiyoriElementActions &&
                        window.__kiyoriElementActions.handleLongPress(
                            state.startX,
                            state.startY
                        );
                    if (!handled) {
                        selectAtPoint(state.startX, state.startY);
                    } else {
                        performHapticFeedback();
                    }
                }, 560);
            }, { capture: true, passive: false });

            document.addEventListener("touchmove", function(event) {
                if (state.draggingHandle) {
                    const point = eventPoint(event);
                    scheduleDraggedSelection(point.x, point.y);
                    event.preventDefault();
                    event.stopPropagation();
                    return;
                }
                if (!event.touches || event.touches.length !== 1) {
                    clearLongPressTimer();
                    return;
                }
                if (state.longPressArmed) {
                    event.preventDefault();
                    event.stopPropagation();
                    return;
                }
                const touch = event.touches[0];
                const dx = touch.clientX - state.startX;
                const dy = touch.clientY - state.startY;
                if (dx * dx + dy * dy > 144) {
                    clearLongPressTimer();
                }
            }, { capture: true, passive: false });

            document.addEventListener("touchend", function(event) {
                window.OperitTextSelectionBridge.setEditableLongPressTarget(false);
                if (state.draggingHandle) {
                    if (state.dragFrame) {
                        cancelAnimationFrame(state.dragFrame);
                        state.dragFrame = 0;
                        updateDraggedSelection(state.dragX, state.dragY);
                    }
                    state.draggingHandle = "";
                    event.preventDefault();
                    event.stopPropagation();
                    if (state.range) {
                        renderRangeSelection(true);
                    }
                    return;
                }
                clearLongPressTimer();
                if (state.longPressArmed || state.selectedText) {
                    event.preventDefault();
                    event.stopPropagation();
                    setTimeout(clearNativeSelection, 0);
                }
                state.longPressArmed = false;
            }, { capture: true, passive: false });

            document.addEventListener("touchcancel", function() {
                window.OperitTextSelectionBridge.setEditableLongPressTarget(false);
                clearLongPressTimer();
                state.longPressArmed = false;
                if (state.dragFrame) {
                    cancelAnimationFrame(state.dragFrame);
                    state.dragFrame = 0;
                }
                state.draggingHandle = "";
            }, { capture: true, passive: false });

            document.addEventListener("contextmenu", function(event) {
                if (state.selectedText) {
                    event.preventDefault();
                    event.stopPropagation();
                }
            }, true);

            document.addEventListener("selectionchange", function() {
                if (state.selectedText) {
                    setTimeout(clearNativeSelection, 0);
                }
            }, true);

            document.addEventListener("scroll", function() {
                if (state.range) {
                    renderRangeSelection(true);
                }
            }, true);

            window.addEventListener("resize", function() {
                if (state.range) {
                    renderRangeSelection(true);
                }
            }, true);
        })();
        """.trimIndent()

    runCatching { webView.evaluateJavascript(script, null) }
        .onFailure { AppLogger.w(EXECUTION_SUPPORT_TAG, "Failed to inject text selection helper: ${it.message}") }
}

internal fun StandardBrowserSessionTools.handleRegularDownload(
    session: BrowserToolSession,
    url: String,
    userAgent: String,
    contentDisposition: String?,
    mimeType: String?,
    contentLength: Long,
) {
    startBrowserManagedDownload(
        session = session,
        url = url,
        userAgent = userAgent,
        contentDisposition = contentDisposition,
        mimeType = mimeType,
        contentLength = contentLength,
    )
}

internal fun StandardBrowserSessionTools.injectDownloadHelper(webView: WebView) {
    val script =
        """
        (function() {
            if (window.__operitDownloadHelperInjected) {
                return;
            }
            window.__operitDownloadHelperInjected = true;

            function guessName(anchor) {
                if (!anchor) return "";
                const raw = String(anchor.getAttribute("download") || "").trim();
                if (raw) return raw;
                try {
                    const url = String(anchor.href || "");
                    if (!url) return "";
                    const pathname = new URL(url, location.href).pathname || "";
                    const last = pathname.split("/").filter(Boolean).pop() || "";
                    return decodeURIComponent(last || "");
                } catch (_) {
                    return "";
                }
            }

            function downloadBlob(blobUrl, fileName) {
                fetch(blobUrl)
                    .then((response) => response.blob())
                    .then((blob) => new Promise((resolve, reject) => {
                        const reader = new FileReader();
                        reader.onload = function() {
                            resolve({ data: String(reader.result || ""), type: String(blob.type || "") });
                        };
                        reader.onerror = function() {
                            reject(reader.error || new Error("blob_reader_failed"));
                        };
                        reader.readAsDataURL(blob);
                    }))
                    .then((payload) => {
                        window.OperitWebDownloadBridge.downloadBase64(payload.data, fileName || "", payload.type || "application/octet-stream");
                    })
                    .catch((error) => {
                        window.OperitWebDownloadBridge.log("blob download failed: " + String(error || "unknown"));
                    });
            }

            document.addEventListener("click", function(event) {
                const anchor = event.target && event.target.closest ? event.target.closest("a[href]") : null;
                if (!anchor) {
                    return;
                }
                const href = String(anchor.href || "");
                if (!href) {
                    return;
                }
                const fileName = guessName(anchor);
                if (href.startsWith("blob:")) {
                    event.preventDefault();
                    downloadBlob(href, fileName);
                } else if (href.startsWith("data:")) {
                    event.preventDefault();
                    const mimeType = href.slice(5).split(";")[0] || "application/octet-stream";
                    window.OperitWebDownloadBridge.downloadBase64(href, fileName || "", mimeType);
                }
            }, true);
        })();
        """.trimIndent()

    runCatching { webView.evaluateJavascript(script, null) }
        .onFailure { AppLogger.w(EXECUTION_SUPPORT_TAG, "Failed to inject download helper: ${it.message}") }
}

internal fun StandardBrowserSessionTools.injectBlobDownloaderScript(
    session: BrowserToolSession,
    blobUrl: String
) {
    val script =
        """
        (function() {
            fetch(${quoteJs(blobUrl)})
                .then((response) => response.blob())
                .then((blob) => new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = function() {
                        resolve({ data: String(reader.result || ""), type: String(blob.type || "") });
                    };
                    reader.onerror = function() {
                        reject(reader.error || new Error("blob_reader_failed"));
                    };
                    reader.readAsDataURL(blob);
                }))
                .then((payload) => {
                    window.OperitWebDownloadBridge.downloadBase64(
                        payload.data,
                        "download_${System.currentTimeMillis()}",
                        payload.type || "application/octet-stream"
                    );
                })
                .catch((error) => {
                    window.OperitWebDownloadBridge.log("blob downloader script failed: " + String(error || "unknown"));
                });
        })();
        """.trimIndent()

    runCatching {
        StandardBrowserSessionTools.mainHandler.post {
            runCatching {
                if (session.webView.isAttachedToWindow) {
                    session.webView.evaluateJavascript(script, null)
                } else {
                    recordDownloadEvent(
                        session,
                        WebDownloadEvent(
                            status = "failed",
                            type = "blob",
                            fileName = "download_${System.currentTimeMillis()}",
                            url = blobUrl,
                            error = "webview_not_attached"
                        )
                    )
                }
            }.onFailure { postError ->
                recordDownloadEvent(
                    session,
                    WebDownloadEvent(
                        status = "failed",
                        type = "blob",
                        fileName = "download_${System.currentTimeMillis()}",
                        url = blobUrl,
                        error = postError.message ?: "blob_download_script_failed"
                    )
                )
            }
        }
    }.onFailure {
        recordDownloadEvent(
            session,
            WebDownloadEvent(
                status = "failed",
                type = "blob",
                fileName = "download_${System.currentTimeMillis()}",
                url = blobUrl,
                error = it.message ?: "blob_download_script_failed"
            )
        )
    }
}

internal fun StandardBrowserSessionTools.handleInlineDownload(
    session: BrowserToolSession,
    base64Data: String,
    fileName: String,
    mimeType: String,
    type: String,
    sourceUrl: String? = null
) {
    runCatching {
        startInlineManagedDownload(
            session = session,
            type = type,
            base64Data = base64Data,
            fileName = fileName,
            mimeType = mimeType,
            sourceUrl = sourceUrl
        )
    }.onFailure { error ->
        val resolvedFileName = resolveInlineDownloadFileName(fileName, mimeType)
        recordDownloadEvent(
            session,
            WebDownloadEvent(
                status = "failed",
                type = type,
                fileName = resolvedFileName,
                mimeType = mimeType,
                error = error.message ?: "inline_download_failed"
            )
        )
        StandardBrowserSessionTools.mainHandler.post {
            Toast.makeText(
                context,
                context.getString(R.string.download_failed, error.message ?: resolvedFileName),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

internal fun StandardBrowserSessionTools.recordDownloadEvent(
    session: BrowserToolSession,
    event: WebDownloadEvent
) {
    session.lastDownloadEvent = event
    session.lastDownloadEventAt = System.currentTimeMillis()
    notifySessionStateChanged(session)
    AppLogger.d(
        EXECUTION_SUPPORT_TAG,
        "web download event session=${session.id}, status=${event.status}, type=${event.type}, file=${event.fileName}, url=${event.url.orEmpty()}"
    )
}

internal fun StandardBrowserSessionTools.guessMimeTypeFromDataUrl(dataUrl: String): String {
    if (!dataUrl.startsWith("data:")) {
        return "application/octet-stream"
    }
    return dataUrl.substringAfter("data:", "")
        .substringBefore(';', "application/octet-stream")
        .ifBlank { "application/octet-stream" }
}

internal fun StandardBrowserSessionTools.resolveInlineDownloadFileName(
    fileName: String,
    mimeType: String
): String {
    val trimmed = fileName.trim()
    if (trimmed.isNotBlank()) {
        return sanitizeFileName(trimmed)
    }
    return sanitizeFileName(
        "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}${determineExtensionFromMimeType(mimeType)}"
    )
}

private fun determineExtensionFromMimeType(mimeType: String): String {
    val lowerMimeType = mimeType.lowercase(Locale.ROOT)
    return when {
        lowerMimeType.startsWith("image/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.startsWith("audio/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.startsWith("video/") -> ".${lowerMimeType.substringAfter('/')}"
        lowerMimeType.contains("pdf") -> ".pdf"
        lowerMimeType.contains("json") -> ".json"
        lowerMimeType.contains("xml") -> ".xml"
        lowerMimeType.contains("csv") -> ".csv"
        lowerMimeType.contains("zip") -> ".zip"
        lowerMimeType.contains("html") -> ".html"
        lowerMimeType.contains("javascript") -> ".js"
        lowerMimeType.contains("plain") -> ".txt"
        else -> ".bin"
    }
}

internal fun StandardBrowserSessionTools.sanitizeFileName(fileName: String): String {
    val sanitized = fileName.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
    return if (sanitized.isBlank()) {
        "download_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
    } else {
        sanitized
    }
}

internal fun StandardBrowserSessionTools.getSession(sessionId: String?): BrowserToolSession? {
    if (!sessionId.isNullOrBlank()) {
        return sessionById(sessionId)
    }

    return buildPageRegistry().activeSessionId?.let(::sessionById)
}

internal fun StandardBrowserSessionTools.sessionById(sessionId: String): BrowserToolSession? =
    StandardBrowserSessionTools.sessions[sessionId]

internal fun StandardBrowserSessionTools.resolvePreferredSessionId(): String? {
    val registry = buildPageRegistry()
    StandardBrowserSessionTools.activeSessionId = registry.activeSessionId
    return registry.activeSessionId
}

internal fun StandardBrowserSessionTools.addSessionOrder(sessionId: String) {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        if (!StandardBrowserSessionTools.sessionOrder.contains(sessionId)) {
            StandardBrowserSessionTools.sessionOrder.add(sessionId)
        }
    }
}

internal fun StandardBrowserSessionTools.removeSessionOrder(sessionId: String) {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        StandardBrowserSessionTools.sessionOrder.remove(sessionId)
    }
}

internal fun StandardBrowserSessionTools.listSessionIdsInOrder(): List<String> {
    synchronized(StandardBrowserSessionTools.sessionOrderLock) {
        return StandardBrowserSessionTools.sessionOrder.toList()
    }
}

private fun quoteJs(value: String): String = JSONObject.quote(value)

private fun playwrightLikeInputRuntimeJs(): String =
    """
    const __operitPw = (() => {
        const setValueInputTypes = new Set(["color", "date", "time", "datetime-local", "month", "range", "week"]);
        const typeIntoInputTypes = new Set(["", "email", "number", "password", "search", "tel", "text", "url"]);

        function createError(message) {
            throw new Error(message);
        }

        function retarget(node, behavior) {
            let element = node && node.nodeType === Node.ELEMENT_NODE ? node : node && node.parentElement;
            if (!element) {
                return null;
            }
            if (behavior === "none" || behavior === "no-follow-label") {
                return element;
            }
            if (!element.matches("input, textarea, select") && !element.isContentEditable) {
                if (behavior === "button-link") {
                    element = element.closest("button, [role=button], a, [role=link]") || element;
                } else {
                    element = element.closest("button, [role=button], [role=checkbox], [role=radio]") || element;
                }
            }
            if (behavior === "follow-label") {
                if (!element.matches("a, input, textarea, button, select, [role=link], [role=button], [role=checkbox], [role=radio]") && !element.isContentEditable) {
                    const enclosingLabel = element.closest("label");
                    if (enclosingLabel && enclosingLabel.control) {
                        element = enclosingLabel.control;
                    }
                }
            }
            return element;
        }

        function isVisible(element) {
            const style = element.ownerDocument.defaultView.getComputedStyle(element);
            if (!style || style.visibility === "hidden" || style.visibility === "collapse" || style.display === "none") {
                return false;
            }
            const rect = element.getBoundingClientRect();
            return !!(rect.width || rect.height || element.getClientRects().length);
        }

        const ariaCheckedRoles = new Set(["checkbox", "menuitemcheckbox", "option", "radio", "switch", "menuitemradio", "treeitem"]);
        const ariaDisabledRoles = new Set(["application", "button", "composite", "gridcell", "group", "input", "link", "menuitem", "scrollbar", "separator", "tab", "checkbox", "columnheader", "combobox", "grid", "listbox", "menu", "menubar", "menuitemcheckbox", "menuitemradio", "option", "radio", "radiogroup", "row", "rowheader", "searchbox", "select", "slider", "spinbutton", "switch", "tablist", "textbox", "toolbar", "tree", "treegrid", "treeitem"]);
        const ariaReadonlyRoles = new Set(["checkbox", "combobox", "grid", "gridcell", "listbox", "radiogroup", "slider", "spinbutton", "textbox", "columnheader", "rowheader", "searchbox", "switch", "treegrid"]);

        function elementTagName(element) {
            const tagName = element && element.tagName;
            if (typeof tagName === "string") {
                return tagName.toUpperCase();
            }
            return String(tagName || "").toUpperCase();
        }

        function ariaRole(element) {
            return String((element.getAttribute && element.getAttribute("role")) || "");
        }

        function parentElementOrShadowHost(element) {
            if (!element) {
                return null;
            }
            if (element.parentElement) {
                return element.parentElement;
            }
            const parentNode = element.parentNode;
            return parentNode && parentNode.host ? parentNode.host : null;
        }

        function belongsToDisabledOptGroup(element) {
            return elementTagName(element) === "OPTION" && !!element.closest("OPTGROUP[DISABLED]");
        }

        function belongsToDisabledFieldSet(element) {
            const fieldSetElement = element && element.closest ? element.closest("FIELDSET[DISABLED]") : null;
            if (!fieldSetElement) {
                return false;
            }
            const legendElement = fieldSetElement.querySelector(":scope > LEGEND");
            return !legendElement || !legendElement.contains(element);
        }

        function isNativelyDisabled(element) {
            const isNativeFormControl = ["BUTTON", "INPUT", "SELECT", "TEXTAREA", "OPTION", "OPTGROUP"].includes(elementTagName(element));
            return isNativeFormControl && (element.hasAttribute("disabled") || belongsToDisabledOptGroup(element) || belongsToDisabledFieldSet(element));
        }

        function hasExplicitAriaDisabled(element, isAncestor) {
            if (!element) {
                return false;
            }
            if (isAncestor || ariaDisabledRoles.has(ariaRole(element))) {
                const attribute = String((element.getAttribute("aria-disabled") || "")).toLowerCase();
                if (attribute === "true") {
                    return true;
                }
                if (attribute === "false") {
                    return false;
                }
                return hasExplicitAriaDisabled(parentElementOrShadowHost(element), true);
            }
            return false;
        }

        function getAriaDisabled(element) {
            return isNativelyDisabled(element) || hasExplicitAriaDisabled(element, false);
        }

        function getChecked(element, allowMixed) {
            const tagName = elementTagName(element);
            if (allowMixed && tagName === "INPUT" && element.indeterminate) {
                return "mixed";
            }
            if (tagName === "INPUT" && ["checkbox", "radio"].includes(String(element.type || "").toLowerCase())) {
                return !!element.checked;
            }
            if (ariaCheckedRoles.has(ariaRole(element))) {
                const checked = element.getAttribute("aria-checked");
                if (checked === "true") {
                    return true;
                }
                if (allowMixed && checked === "mixed") {
                    return "mixed";
                }
                return false;
            }
            return "error";
        }

        function getCheckedAllowMixed(element) {
            return getChecked(element, true);
        }

        function getCheckedWithoutMixed(element) {
            return getChecked(element, false);
        }

        function getReadonly(element) {
            const tagName = elementTagName(element);
            if (["INPUT", "TEXTAREA", "SELECT"].includes(tagName)) {
                return element.hasAttribute("readonly");
            }
            if (ariaReadonlyRoles.has(ariaRole(element))) {
                return element.getAttribute("aria-readonly") === "true";
            }
            if (element.isContentEditable) {
                return false;
            }
            return "error";
        }

        function elementState(node, state) {
            const element = retarget(node, state === "visible" || state === "hidden" ? "none" : "follow-label");
            if (!element || !element.isConnected) {
                if (state === "hidden") {
                    return { matches: true, received: "hidden" };
                }
                return { matches: false, received: "error:notconnected" };
            }
            if (state === "visible" || state === "hidden") {
                const visible = isVisible(element);
                return {
                    matches: state === "visible" ? visible : !visible,
                    received: visible ? "visible" : "hidden"
                };
            }
            if (state === "enabled" || state === "disabled") {
                const disabled = getAriaDisabled(element);
                return {
                    matches: state === "disabled" ? disabled : !disabled,
                    received: disabled ? "disabled" : "enabled"
                };
            }
            if (state === "editable") {
                const disabled = getAriaDisabled(element);
                const readonly = getReadonly(element);
                if (readonly === "error") {
                    createError("Element is not an <input>, <textarea>, <select> or [contenteditable] element");
                }
                return {
                    matches: !disabled && !readonly,
                    received: disabled ? "disabled" : readonly ? "readOnly" : "editable"
                };
            }
            if (state === "checked" || state === "unchecked") {
                const checked = getCheckedWithoutMixed(element);
                if (checked === "error") {
                    createError("Not a checkbox or radio button");
                }
                return {
                    matches: state === "checked" ? checked : !checked,
                    received: checked ? "checked" : "unchecked",
                    isRadio: elementTagName(element) === "INPUT" && String(element.type || "").toLowerCase() === "radio"
                };
            }
            if (state === "indeterminate") {
                const checked = getCheckedAllowMixed(element);
                if (checked === "error") {
                    createError("Not a checkbox or radio button");
                }
                return {
                    matches: checked === "mixed",
                    received: checked === true ? "checked" : checked === false ? "unchecked" : "mixed"
                };
            }
            createError("Unsupported element state: " + state);
        }

        function checkElementStates(node, states) {
            for (const state of states) {
                const result = elementState(node, state);
                if (result.received === "error:notconnected") {
                    return "error:notconnected";
                }
                if (!result.matches) {
                    return { missingState: state };
                }
            }
            return null;
        }

        function ensureStatesOrThrow(node, states) {
            const result = checkElementStates(node, states);
            if (result === "error:notconnected") {
                createError("Element is not connected");
            }
            if (result) {
                createError("Element is not " + result.missingState);
            }
        }

        function selectText(node) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            if (element.nodeName.toLowerCase() === "input") {
                element.select();
                element.focus();
                return "done";
            }
            if (element.nodeName.toLowerCase() === "textarea") {
                element.selectionStart = 0;
                element.selectionEnd = element.value.length;
                element.focus();
                return "done";
            }
            element.focus();
            const range = element.ownerDocument.createRange();
            range.selectNodeContents(element);
            const selection = element.ownerDocument.defaultView.getSelection();
            if (selection) {
                selection.removeAllRanges();
                selection.addRange(range);
            }
            return "done";
        }

        function activelyFocused(node) {
            const activeElement = node.getRootNode().activeElement;
            const isFocused = activeElement === node && !!node.ownerDocument && node.ownerDocument.hasFocus();
            return { activeElement, isFocused };
        }

        function focusNode(node, resetSelectionIfNotFocused) {
            if (!node.isConnected) {
                return "error:notconnected";
            }
            if (node.nodeType !== Node.ELEMENT_NODE) {
                createError("Node is not an element");
            }
            const state = activelyFocused(node);
            if (node.isContentEditable && !state.isFocused && state.activeElement && state.activeElement.blur) {
                state.activeElement.blur();
            }
            node.focus();
            node.focus();
            if (resetSelectionIfNotFocused && !state.isFocused && node.nodeName.toLowerCase() === "input") {
                try {
                    node.setSelectionRange(0, 0);
                } catch (_) {
                }
            }
            return "done";
        }

        function setControlValue(element, value) {
            const prototype = element.nodeName.toLowerCase() === "textarea" ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
            const descriptor = Object.getOwnPropertyDescriptor(prototype, "value");
            if (!descriptor || !descriptor.set) {
                createError("Cannot access value setter");
            }
            descriptor.set.call(element, value);
        }

        function dispatchSimpleInput(element) {
            element.dispatchEvent(new Event("input", { bubbles: true, composed: true }));
        }

        function dispatchChange(element) {
            element.dispatchEvent(new Event("change", { bubbles: true }));
        }

        function dispatchBeforeInput(element, inputType, data) {
            return element.dispatchEvent(new InputEvent("beforeinput", {
                bubbles: true,
                cancelable: true,
                composed: true,
                inputType: inputType,
                data: data
            }));
        }

        function dispatchInput(element, inputType, data) {
            element.dispatchEvent(new InputEvent("input", {
                bubbles: true,
                composed: true,
                inputType: inputType,
                data: data
            }));
        }

        function replaceSelectionWithText(element, text) {
            if (element.matches("input, textarea")) {
                const currentValue = String(element.value || "");
                const start = typeof element.selectionStart === "number" ? element.selectionStart : 0;
                const end = typeof element.selectionEnd === "number" ? element.selectionEnd : currentValue.length;
                const nextValue = currentValue.slice(0, start) + text + currentValue.slice(end);
                setControlValue(element, nextValue);
                if (typeof element.setSelectionRange === "function") {
                    const caret = start + text.length;
                    try {
                        element.setSelectionRange(caret, caret);
                    } catch (_) {
                    }
                }
                return;
            }
            const selection = element.ownerDocument.defaultView.getSelection();
            if (!selection) {
                createError("Selection is not available");
            }
            if (!selection.rangeCount || !element.contains(selection.anchorNode)) {
                const resetRange = element.ownerDocument.createRange();
                resetRange.selectNodeContents(element);
                resetRange.collapse(false);
                selection.removeAllRanges();
                selection.addRange(resetRange);
            }
            const range = selection.getRangeAt(0);
            range.deleteContents();
            if (text) {
                const textNode = element.ownerDocument.createTextNode(text);
                range.insertNode(textNode);
                range.setStartAfter(textNode);
            }
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
        }

        function insertTextWithoutKeyboard(element, text) {
            const stringValue = text == null ? "" : String(text);
            if (!dispatchBeforeInput(element, "insertText", stringValue)) {
                return "done";
            }
            replaceSelectionWithText(element, stringValue);
            dispatchInput(element, "insertText", stringValue);
            return "done";
        }

        function deleteSelectionOrCharacter(element, direction) {
            if (element.matches("input, textarea")) {
                const currentValue = String(element.value || "");
                const start = typeof element.selectionStart === "number" ? element.selectionStart : currentValue.length;
                const end = typeof element.selectionEnd === "number" ? element.selectionEnd : start;
                let from = start;
                let to = end;
                if (from === to) {
                    if (direction === "backward" && from > 0) {
                        from -= 1;
                    }
                    if (direction === "forward" && to < currentValue.length) {
                        to += 1;
                    }
                }
                setControlValue(element, currentValue.slice(0, from) + currentValue.slice(to));
                if (typeof element.setSelectionRange === "function") {
                    try {
                        element.setSelectionRange(from, from);
                    } catch (_) {
                    }
                }
                return;
            }
            const selection = element.ownerDocument.defaultView.getSelection();
            if (!selection) {
                createError("Selection is not available");
            }
            if (!selection.rangeCount || !element.contains(selection.anchorNode)) {
                const range = element.ownerDocument.createRange();
                range.selectNodeContents(element);
                range.collapse(false);
                selection.removeAllRanges();
                selection.addRange(range);
            }
            const range = selection.getRangeAt(0);
            if (range.collapsed) {
                const container = range.startContainer;
                const offset = range.startOffset;
                if (container.nodeType === Node.TEXT_NODE) {
                    const length = String(container.textContent || "").length;
                    if (direction === "backward" && offset > 0) {
                        range.setStart(container, offset - 1);
                    } else if (direction === "forward" && offset < length) {
                        range.setEnd(container, offset + 1);
                    } else {
                        return;
                    }
                } else {
                    const childNodes = container.childNodes || [];
                    const targetNode = direction === "backward" ? childNodes[offset - 1] : childNodes[offset];
                    if (!targetNode) {
                        return;
                    }
                    if (targetNode.nodeType === Node.TEXT_NODE) {
                        const textLength = String(targetNode.textContent || "").length;
                        if (direction === "backward") {
                            range.setStart(targetNode, Math.max(0, textLength - 1));
                            range.setEnd(targetNode, textLength);
                        } else {
                            range.setStart(targetNode, 0);
                            range.setEnd(targetNode, Math.min(1, textLength));
                        }
                    } else {
                        range.selectNode(targetNode);
                    }
                }
            }
            range.deleteContents();
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
        }

        function deleteTextWithoutKeyboard(element, direction) {
            const inputType = direction === "forward" ? "deleteContentForward" : "deleteContentBackward";
            if (!dispatchBeforeInput(element, inputType, null)) {
                return "done";
            }
            deleteSelectionOrCharacter(element, direction);
            dispatchInput(element, inputType, null);
            return "done";
        }

        function dispatchKeyEvent(element, type, key) {
            return element.dispatchEvent(new KeyboardEvent(type, {
                key: key,
                bubbles: true,
                cancelable: true,
                composed: true
            }));
        }

        function fillElement(node, value) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible", "enabled", "editable"]);
            if (element.nodeName.toLowerCase() === "input") {
                const input = element;
                const type = String(input.type || "").toLowerCase();
                if (!typeIntoInputTypes.has(type) && !setValueInputTypes.has(type)) {
                    createError('Input of type "' + type + '" cannot be filled');
                }
                let normalizedValue = String(value);
                if (type === "number") {
                    normalizedValue = normalizedValue.trim();
                    if (isNaN(Number(normalizedValue))) {
                        createError("Cannot type text into input[type=number]");
                    }
                }
                if (type === "color") {
                    normalizedValue = normalizedValue.toLowerCase();
                }
                if (setValueInputTypes.has(type)) {
                    normalizedValue = normalizedValue.trim();
                    input.focus();
                    setControlValue(input, normalizedValue);
                    if (input.value !== normalizedValue) {
                        createError("Malformed value");
                    }
                    dispatchSimpleInput(input);
                    dispatchChange(input);
                    return "done";
                }
                selectText(input);
                return "needsinput";
            }
            if (element.nodeName.toLowerCase() === "textarea") {
                selectText(element);
                return "needsinput";
            }
            if (!element.isContentEditable) {
                createError("Element is not an <input>, <textarea> or [contenteditable] element");
            }
            selectText(element);
            return "needsinput";
        }

        function completeFill(node, value) {
            const stringValue = value == null ? "" : String(value);
            const result = fillElement(node, stringValue);
            if (result === "needsinput") {
                return stringValue ? insertTextWithoutKeyboard(retarget(node, "follow-label"), stringValue) : deleteTextWithoutKeyboard(retarget(node, "follow-label"), "forward");
            }
            return result;
        }

        async function typeCharacter(element, ch) {
            const isPrintableAscii = /^[\u0020-\u007e]$/.test(ch);
            if (isPrintableAscii) {
                return pressKey(element, ch);
            }
            return insertTextWithoutKeyboard(element, ch);
        }

        async function typeElement(node, text, delayMs) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            const focusResult = focusNode(element, true);
            if (focusResult !== "done") {
                return focusResult;
            }
            for (const rawCh of String(text)) {
                if (rawCh === "\r") {
                    continue;
                }
                if (rawCh === "\n") {
                    const enterResult = pressEnter(element);
                    if (enterResult !== "done") {
                        return enterResult;
                    }
                } else {
                    const typeResult = await typeCharacter(element, rawCh);
                    if (typeResult !== "done") {
                        return typeResult;
                    }
                }
                if (delayMs > 0) {
                    await new Promise((resolve) => setTimeout(resolve, delayMs));
                }
            }
            return "done";
        }

        function pressEnter(node) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            const keydownAccepted = dispatchKeyEvent(element, "keydown", "Enter");
            if (keydownAccepted) {
                dispatchKeyEvent(element, "keypress", "Enter");
                if (element.nodeName.toLowerCase() === "textarea" || element.isContentEditable) {
                    if (dispatchBeforeInput(element, "insertLineBreak", "\n")) {
                        replaceSelectionWithText(element, "\n");
                        dispatchInput(element, "insertLineBreak", "\n");
                    }
                } else {
                    const form = element.form || (element.closest && element.closest("form"));
                    if (form && typeof form.requestSubmit === "function") {
                        form.requestSubmit();
                    }
                }
            }
            dispatchKeyEvent(element, "keyup", "Enter");
            return "done";
        }

        function pressKey(node, key) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            const keyValue = String(key);
            const editable = element.matches("input, textarea") || element.isContentEditable;
            if (keyValue === "Enter") {
                return pressEnter(element);
            }
            const keydownAccepted = dispatchKeyEvent(element, "keydown", keyValue);
            if (keydownAccepted) {
                if (keyValue.length === 1) {
                    const keypressAccepted = dispatchKeyEvent(element, "keypress", keyValue);
                    if (keypressAccepted && editable) {
                        insertTextWithoutKeyboard(element, keyValue);
                    }
                } else if (keyValue === "Backspace" && editable) {
                    deleteTextWithoutKeyboard(element, "backward");
                } else if (keyValue === "Delete" && editable) {
                    deleteTextWithoutKeyboard(element, "forward");
                }
            }
            dispatchKeyEvent(element, "keyup", keyValue);
            return "done";
        }

        function clickElement(node) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible", "enabled"]);
            try {
                element.scrollIntoView({ block: "center", inline: "center" });
            } catch (_) {
            }
            try {
                element.focus({ preventScroll: true });
            } catch (_) {
            }
            if (typeof element.click !== "function") {
                createError("Element is not clickable");
            }
            element.click();
            return "done";
        }

        function hoverElement(node) {
            const element = retarget(node, "none");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible"]);
            try {
                element.scrollIntoView({ block: "center", inline: "center" });
            } catch (_) {
            }
            const rect = element.getBoundingClientRect();
            ["pointerover", "mouseover", "mouseenter", "mousemove"].forEach((type) => {
                element.dispatchEvent(new MouseEvent(type, {
                    bubbles: true,
                    cancelable: true,
                    composed: true,
                    clientX: rect.left + rect.width / 2,
                    clientY: rect.top + rect.height / 2
                }));
            });
            return "done";
        }

        function setChecked(node, desiredState) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible", "enabled"]);
            const currentState = elementState(element, "checked");
            if (currentState.received === "error:notconnected") {
                return "error:notconnected";
            }
            if (currentState.matches === desiredState) {
                return "done";
            }
            if (!desiredState && currentState.isRadio) {
                createError("Cannot uncheck radio button. Radio buttons can only be unchecked by selecting another radio button in the same group.");
            }
            if (typeof element.click !== "function") {
                createError("Element is not clickable");
            }
            element.click();
            const finalState = elementState(element, "checked");
            if (finalState.received === "error:notconnected") {
                return "error:notconnected";
            }
            if (finalState.matches !== desiredState) {
                createError("Clicking the checkbox did not change its state");
            }
            return "done";
        }

        function selectOptions(node, optionsToSelect) {
            const element = retarget(node, "follow-label");
            if (!element) {
                return "error:notconnected";
            }
            ensureStatesOrThrow(element, ["visible", "enabled"]);
            if (element.nodeName.toLowerCase() !== "select") {
                createError("Element is not a <select> element");
            }
            const select = element;
            const options = Array.from(select.options || []);
            const selectedOptions = [];
            let remaining = optionsToSelect.slice();
            for (let index = 0; index < options.length; index += 1) {
                const option = options[index];
                const filter = (optionToSelect) => {
                    if (optionToSelect instanceof Node) {
                        return option === optionToSelect;
                    }
                    let matches = true;
                    if (optionToSelect.valueOrLabel !== undefined) {
                        matches = matches && (String(optionToSelect.valueOrLabel) === String(option.value) || String(optionToSelect.valueOrLabel) === String(option.label));
                    }
                    if (optionToSelect.value !== undefined) {
                        matches = matches && String(optionToSelect.value) === String(option.value);
                    }
                    if (optionToSelect.label !== undefined) {
                        matches = matches && String(optionToSelect.label) === String(option.label);
                    }
                    if (optionToSelect.index !== undefined) {
                        matches = matches && Number(optionToSelect.index) === index;
                    }
                    return matches;
                };
                if (!remaining.some(filter)) {
                    continue;
                }
                if (!elementState(option, "enabled").matches) {
                    return "error:optionnotenabled";
                }
                selectedOptions.push(option);
                if (select.multiple) {
                    remaining = remaining.filter((candidate) => !filter(candidate));
                } else {
                    remaining = [];
                    break;
                }
            }
            if (remaining.length) {
                return "error:optionsnotfound";
            }
            select.value = void 0;
            selectedOptions.forEach((option) => {
                option.selected = true;
            });
            dispatchSimpleInput(select);
            dispatchChange(select);
            return selectedOptions.map((option) => String(option.value));
        }

        return {
            fillElement: fillElement,
            completeFill: completeFill,
            typeElement: typeElement,
            pressKey: pressKey,
            pressEnter: pressEnter,
            clickElement: clickElement,
            hoverElement: hoverElement,
            setChecked: setChecked,
            selectOptions: selectOptions
        };
    })();
    """.trimIndent()

internal fun StandardBrowserSessionTools.ensureBrowserExecutionPresentation(
    toolName: String,
): ToolResult? {
    val appContext = context.applicationContext
    val appPresentationActive = browserHost?.hasAppPresentation() == true
    if (
        !appPresentationActive &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(appContext)
    ) {
        return error(toolName, "Overlay permission is required for browser tools.")
    }

    if (!appPresentationActive) {
        runOnMainSync<Unit> {
            ensureBackgroundAnchorOnMain(appContext)
        }
    }
    return null
}

internal fun StandardBrowserSessionTools.buildPageRegistry(): BrowserPageRegistry {
    val orderedIds = orderedSessionIdsFromState()
    val activeId =
        StandardBrowserSessionTools.activeSessionId?.takeIf { sessionById(it) != null }
            ?: orderedIds.firstOrNull()
    return BrowserPageRegistry(
        orderedSessionIds = orderedIds,
        activeSessionId = activeId,
        snapshots = orderedIds.associateWith { id -> sessionById(id)?.lastSnapshot }
    )
}

private fun StandardBrowserSessionTools.orderedSessionIdsFromState(): List<String> {
    val orderedIds = LinkedHashSet<String>()
    listSessionIdsInOrder()
        .filter { sessionById(it) != null }
        .forEach(orderedIds::add)
    StandardBrowserSessionTools.sessions.values
        .sortedWith(compareBy<BrowserToolSession> { it.createdAt }.thenBy { it.id })
        .mapTo(orderedIds) { it.id }
    return orderedIds.toList()
}

internal fun StandardBrowserSessionTools.orderedSessionIds(): List<String> =
    buildPageRegistry().orderedSessionIds

internal fun StandardBrowserSessionTools.currentTabIndex(sessionId: String): Int =
    buildPageRegistry().orderedSessionIds.indexOf(sessionId)

internal fun StandardBrowserSessionTools.sessionIdAtIndex(index: Int): String? =
    buildPageRegistry().orderedSessionIds.getOrNull(index)

internal fun StandardBrowserSessionTools.pageError(
    toolName: String,
    session: BrowserToolSession?,
    message: String
): ToolResult {
    val rendered =
        if (session != null) {
            buildBrowserResponse(
                openTabs = renderOpenTabs(),
                pageState = renderPageState(session),
                snapshot = captureSnapshotText(session),
                modalState = renderModalState(session),
                error = message
            )
        } else {
            buildBrowserResponse(error = message)
        }
    return ToolResult(
        toolName = toolName,
        success = false,
        result = StringResultData(rendered),
        error = rendered
    )
}

internal fun StandardBrowserSessionTools.buildSettledBrowserResponse(
    settlement: StandardBrowserSessionTools.BrowserActionSettlement,
    code: String? = null,
    snapshot: String? = settlement.snapshot?.yaml,
    consoleMessages: String? = renderNewConsoleMessages(settlement.session, settlement.consoleMarker),
    modalState: String? = renderModalState(settlement.session),
    downloads: String? = renderDownloads(settlement.session, settlement.downloadMarker),
    result: String? = null,
    error: String? = null
): String =
    buildBrowserResponse(
        code = code,
        openTabs = renderOpenTabs(settlement.registry),
        pageState = renderPageState(settlement.session),
        snapshot = snapshot,
        consoleMessages = consoleMessages,
        modalState = modalState,
        downloads = downloads,
        result = result,
        error = error
    )

internal fun StandardBrowserSessionTools.renderOpenTabs(
    registry: BrowserPageRegistry = buildPageRegistry()
): String {
    val activeId = registry.activeSessionId
    val ordered = registry.orderedSessionIds.mapNotNull(::sessionById)
    if (ordered.isEmpty()) {
        return "No open tabs."
    }
    return BrowserTabDiscoveryFormatter.render(
        ordered.mapIndexed { index, session ->
            BrowserTabDiscoveryEntry(
                index = index,
                sessionId = session.id,
                title = sessionDisplayTitle(session).ifBlank { "about:blank" },
                url = session.currentUrl.ifBlank { "about:blank" },
                isActive = session.id == activeId,
                profile = session.profile,
            )
        }
    )
}

internal fun StandardBrowserSessionTools.renderPageState(session: BrowserToolSession): String {
    val viewport = readBrowserViewportMetrics(session.webView)
    return buildString {
        appendLine("- Title: ${session.pageTitle.ifBlank { "about:blank" }}")
        appendLine("- URL: ${session.currentUrl.ifBlank { "about:blank" }}")
        appendLine("- Loading: ${if (session.isLoading) "yes" else "no"}")
        appendLine("- Can go back: ${if (session.canGoBack) "yes" else "no"}")
        appendLine("- Can go forward: ${if (session.canGoForward) "yes" else "no"}")
        if (viewport == null) {
            append("- Viewport: unavailable")
        } else {
            appendLine("- Viewport: ${viewport.innerWidth}x${viewport.innerHeight}")
            append("- Document viewport: ${viewport.clientWidth}x${viewport.clientHeight}")
        }
    }
}

internal fun StandardBrowserSessionTools.readBrowserViewportMetrics(
    webView: WebView,
): BrowserViewportMetrics? {
    val json =
        runJsonScript(
            webView,
            """
            (function() {
                const root = document.documentElement;
                return JSON.stringify({
                    ok: true,
                    innerWidth: Number(window.innerWidth || 0),
                    innerHeight: Number(window.innerHeight || 0),
                    clientWidth: Number(root ? root.clientWidth : 0),
                    clientHeight: Number(root ? root.clientHeight : 0)
                });
            })();
            """.trimIndent(),
            "viewport_metrics_error",
        ) ?: return null
    if (!json.optBoolean("ok", false)) {
        return null
    }
    return BrowserViewportMetrics(
        innerWidth = json.optInt("innerWidth"),
        innerHeight = json.optInt("innerHeight"),
        clientWidth = json.optInt("clientWidth"),
        clientHeight = json.optInt("clientHeight"),
    )
}

internal fun StandardBrowserSessionTools.waitForBrowserViewport(
    session: BrowserToolSession,
    requested: BrowserViewportSize,
    timeoutMs: Long = 3_000L,
): BrowserViewportMetrics? {
    val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(250L)
    var latest: BrowserViewportMetrics? = null
    while (System.currentTimeMillis() < deadline) {
        latest = readBrowserViewportMetrics(session.webView)
        if (latest != null && BrowserViewportPolicy.matches(latest, requested)) {
            return latest
        }
        Thread.sleep(40L)
    }
    return latest
}

internal fun StandardBrowserSessionTools.locatorExpressionForElement(
    session: BrowserToolSession,
    ref: String,
    selectorSuffix: String
): String = locatorExpressionForRef(session, ref) + selectorSuffix

internal fun StandardBrowserSessionTools.evaluateJavascriptAsync(
    webView: WebView,
    expression: String,
    timeoutMs: Long
): String {
    val callId = UUID.randomUUID().toString()
    val pending = PendingAsyncJsCall()
    StandardBrowserSessionTools.pendingAsyncJsCalls[callId] = pending
    StandardBrowserSessionTools.mainHandler.post {
        try {
            if (!webView.isAttachedToWindow) {
                StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
                pending.error = "WebView is not attached"
                pending.latch.countDown()
                return@post
            }
            // WebView.evaluateJavascript is the only page execution boundary. Embedding the
            // generated expression directly avoids CSP unsafe-eval and a second script engine.
            val wrapped = buildDirectAsyncJavascript(callId, expression)
            webView.evaluateJavascript(wrapped) { rawResult ->
                if (rawResult == null || rawResult == "null") {
                    StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)?.let { unresolved ->
                        unresolved.error = "JavaScript source could not be evaluated"
                        unresolved.latch.countDown()
                    }
                }
            }
        } catch (e: Exception) {
            StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
            pending.error = e.message ?: "Async JavaScript wrapper error"
            pending.latch.countDown()
        }
    }
    if (!pending.latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
        StandardBrowserSessionTools.pendingAsyncJsCalls.remove(callId)
        throw RuntimeException("JavaScript execution timeout (${timeoutMs}ms)")
    }
    pending.error?.let { throw RuntimeException(it) }
    return pending.result ?: "{\"ok\":true,\"value\":null}"
}

internal fun buildDirectAsyncJavascript(
    callId: String,
    expression: String,
): String = buildDirectAsyncJavascriptSource(quoteJs(callId), expression)

internal fun buildDirectAsyncJavascriptSource(
    quotedCallId: String,
    expression: String,
): String {
    val directExpression = expression.trim().trimEnd(';').trimEnd()
    require(directExpression.isNotBlank()) { "JavaScript expression is empty" }
    return """
        (function() {
            try {
                const operitValue = ($directExpression);
                Promise.resolve(operitValue)
                    .then(function(value) {
                        let payload;
                        try {
                            payload = JSON.stringify({ ok: true, value: value });
                        } catch (_) {
                            payload = JSON.stringify({ ok: true, value: String(value) });
                        }
                        window.OperitAsyncBridge.resolve($quotedCallId, payload);
                    })
                    .catch(function(error) {
                        window.OperitAsyncBridge.reject(
                            $quotedCallId,
                            String(error && (error.stack || error.message || error) || "Async execution failed")
                        );
                    });
            } catch (error) {
                window.OperitAsyncBridge.reject(
                    $quotedCallId,
                    String(error && (error.stack || error.message || error) || "Async execution failed")
                );
            }
            return true;
        })();
    """.trimIndent()
}

internal fun extractAsyncJsValue(payload: String): String {
    val json = JSONObject(payload)
    val value = json.opt("value")
    return if (value == null || value === JSONObject.NULL) {
        ""
    } else if (value is String) {
        value
    } else {
        value.toString()
    }
}

internal fun StandardBrowserSessionTools.evaluatePageFunction(
    webView: WebView,
    functionSource: String,
    ref: String?,
    timeoutMs: Long
): String {
    val expression =
        """
        (async function() {
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref.orEmpty())};
            const target =
                refValue
                    ? (__operitResolveRef(refValue) || {}).element
                    : undefined;
            if (refValue && !target) {
                throw new Error("ref_not_found");
            }
            const fn = (${functionSource});
            if (typeof fn !== "function") {
                throw new Error("function must evaluate to a callable value");
            }
            return refValue ? await fn(target) : await fn();
        })()
        """.trimIndent()
    return extractAsyncJsValue(evaluateJavascriptAsync(webView, expression, timeoutMs))
}

internal fun StandardBrowserSessionTools.parseFormFields(rawFields: String): List<JSONObject>? {
    return try {
        val array = JSONArray(rawFields)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(array.getJSONObject(index))
            }
        }
    } catch (_: Exception) {
        null
    }
}

internal fun StandardBrowserSessionTools.fillFormFields(
    webView: WebView,
    rawFields: String
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            ${browserRefResolverScript()}
            const fields = $rawFields;
            const results = [];
            const findByRef = (refValue) => {
                const resolved = __operitResolveRef(String(refValue || ''));
                return resolved ? resolved.element : null;
            };
            fields.forEach((field, index) => {
                const target =
                    field.ref ? findByRef(field.ref) :
                    field.selector ? document.querySelector(String(field.selector)) :
                    null;
                if (!target) {
                    throw new Error("field_not_found:" + (field.name || index));
                }
                const tagName = String(target.tagName || "").toLowerCase();
                const inputType = tagName === "input" ? String(target.type || "").toLowerCase() : "";
                const controlType = inputType || tagName || "element";
                const value = field.value;
                let result = "done";
                if (inputType === "checkbox" || inputType === "radio") {
                    if (typeof value !== "boolean") {
                        throw new Error("field_value_must_be_boolean:" + (field.name || index));
                    }
                    result = __operitPw.setChecked(target, value);
                } else if (tagName === "select") {
                    result = __operitPw.selectOptions(target, [{ valueOrLabel: String(value) }]);
                } else {
                    result = __operitPw.completeFill(target, String(value));
                }
                if (result !== "done" && !Array.isArray(result)) {
                    throw new Error(String(result));
                }
                results.push(String(field.name || ("field_" + (index + 1))) + " => " + controlType);
            });
            return results.join("\n");
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.pressKeyOnPage(
    webView: WebView,
    key: String
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            const keyValue = ${quoteJs(key)};
            const target = document.activeElement || document.body || document.documentElement;
            if (!target) {
                throw new Error("No focus target available.");
            }
            const result = __operitPw.pressKey(target, keyValue);
            if (result !== "done") {
                throw new Error(String(result));
            }
            return "Pressed " + keyValue;
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.selectOptionsByRef(
    webView: WebView,
    ref: String,
    values: List<String>
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref)};
            const resolved = __operitResolveRef(refValue);
            const target = resolved ? resolved.element : null;
            if (!target) {
                throw new Error("ref_not_found");
            }
            const values = ${JSONArray(values).toString()}.map((value) => ({ valueOrLabel: value }));
            const result = __operitPw.selectOptions(target, values);
            if (!Array.isArray(result)) {
                throw new Error(String(result));
            }
            return "Selected " + result.join(", ");
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.typeIntoElementByRef(
    webView: WebView,
    ref: String,
    text: String,
    submit: Boolean,
    slowly: Boolean
): String {
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref)};
            const resolved = __operitResolveRef(refValue);
            const target = resolved ? resolved.element : null;
            if (!target) {
                throw new Error("ref_not_found");
            }
            const textValue = ${quoteJs(text)};
            const submitValue = ${if (submit) "true" else "false"};
            const slowValue = ${if (slowly) "true" else "false"};
            if (slowValue) {
                const typeResult = await __operitPw.typeElement(target, textValue, 35);
                if (typeResult !== "done") {
                    throw new Error(String(typeResult));
                }
            } else {
                const fillResult = __operitPw.completeFill(target, textValue);
                if (fillResult !== "done") {
                    throw new Error(String(fillResult));
                }
            }
            if (submitValue) {
                const submitResult = __operitPw.pressEnter(target);
                if (submitResult !== "done") {
                    throw new Error(String(submitResult));
                }
            }
            return "Typed " + textValue.length + " character(s).";
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(12_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.writeBrowserTextOutput(
    filename: String,
    content: String,
    defaultPrefix: String,
    extension: String
): String {
    val resolved = resolveBrowserOutputFile(filename, defaultPrefix, extension)
    resolved.parentFile?.mkdirs()
    resolved.writeText(content)
    return resolved.absolutePath
}

internal fun StandardBrowserSessionTools.resolveBrowserOutputFile(
    filename: String?,
    defaultPrefix: String,
    extension: String
): File {
    val baseDir = File(context.cacheDir, "browser-output").apply { mkdirs() }
    if (!filename.isNullOrBlank()) {
        val candidate = File(filename)
        return if (candidate.isAbsolute) {
            candidate
        } else {
            File(baseDir, filename)
        }
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    return File(baseDir, "${defaultPrefix}_$timestamp.$extension")
}

internal fun StandardBrowserSessionTools.takeScreenshot(
    session: BrowserToolSession,
    filename: String?,
    type: String,
    fullPage: Boolean,
    ref: String?
): String {
    return runOnMainSync {
        ensureSessionAttachedOnMain(session.id)
        val resolvedType = if (type == "jpg") "jpeg" else type
        val output = resolveBrowserOutputFile(filename, "page", if (resolvedType == "png") "png" else "jpg")
        output.parentFile?.mkdirs()
        val bitmap =
            when {
                fullPage -> captureFullPageBitmap(session.webView)
                ref != null -> captureElementBitmap(session.webView, ref)
                else -> captureViewportBitmap(session.webView)
            }
        FileOutputStream(output).use { stream ->
            bitmap.compress(
                if (resolvedType == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                92,
                stream
            )
        }
        bitmap.recycle()
        output.absolutePath
    }
}

internal fun StandardBrowserSessionTools.captureViewportBitmap(webView: WebView): Bitmap {
    val width = webView.width.coerceAtLeast(1)
    val height = webView.height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    webView.draw(canvas)
    return bitmap
}

internal fun StandardBrowserSessionTools.captureFullPageBitmap(webView: WebView): Bitmap {
    val (width, height) = resolveFullPageBitmapSize(webView)
    val originalWidth = webView.width.coerceAtLeast(1)
    val originalHeight = webView.height.coerceAtLeast(1)
    val originalScrollX = webView.scrollX
    val originalScrollY = webView.scrollY
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    webView.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    )
    webView.layout(0, 0, width, height)
    webView.scrollTo(0, 0)
    webView.draw(canvas)
    webView.measure(
        View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY)
    )
    webView.layout(0, 0, originalWidth, originalHeight)
    webView.scrollTo(originalScrollX, originalScrollY)
    return bitmap
}

internal fun StandardBrowserSessionTools.resolveFullPageBitmapSize(
    webView: WebView
): Pair<Int, Int> {
    val pageSize =
        runJsonScript(
            webView,
            """
            (function() {
                const doc = document.documentElement;
                const body = document.body;
                const width = Math.max(
                    window.innerWidth || 0,
                    doc ? (doc.scrollWidth || 0) : 0,
                    doc ? (doc.offsetWidth || 0) : 0,
                    body ? (body.scrollWidth || 0) : 0,
                    body ? (body.offsetWidth || 0) : 0
                );
                const height = Math.max(
                    window.innerHeight || 0,
                    doc ? (doc.scrollHeight || 0) : 0,
                    doc ? (doc.offsetHeight || 0) : 0,
                    body ? (body.scrollHeight || 0) : 0,
                    body ? (body.offsetHeight || 0) : 0
                );
                return JSON.stringify({
                    ok: true,
                    width,
                    height,
                    viewportWidth: window.innerWidth
                });
            })();
            """.trimIndent(),
            "page_size_error"
        )

    val viewportWidth =
        pageSize?.optDouble("viewportWidth", 0.0)
            ?.takeIf { it > 0.0 }
            ?: error("Web page reported an invalid viewport width")
    val scale = webView.width.toDouble() / viewportWidth
    val width =
        (((pageSize.optDouble("width", 0.0)) * scale).toInt())
            .coerceAtLeast(webView.width.coerceAtLeast(1))
    val height =
        (((pageSize.optDouble("height", 0.0)) * scale).toInt())
            .coerceAtLeast(webView.height.coerceAtLeast(1))
    return width to height
}

internal fun StandardBrowserSessionTools.captureElementBitmap(
    webView: WebView,
    ref: String
): Bitmap {
    val rect = resolveElementRect(webView, ref) ?: throw RuntimeException("ref_not_found")
    val bitmap = captureViewportBitmap(webView)
    val left = rect.left.coerceIn(0, bitmap.width - 1)
    val top = rect.top.coerceIn(0, bitmap.height - 1)
    val width = rect.width().coerceAtLeast(1).coerceAtMost(bitmap.width - left)
    val height = rect.height().coerceAtLeast(1).coerceAtMost(bitmap.height - top)
    return Bitmap.createBitmap(bitmap, left, top, width, height)
}

internal fun StandardBrowserSessionTools.resolveClickTargetInfoByRef(
    webView: WebView,
    ref: String,
): BrowserClickTargetInfo? {
    val json =
        runJsonScript(
            webView,
            """
            (function() {
                ${browserRefResolverScript()}
                const resolved = __operitResolveRef(${quoteJs(ref)});
                const target = resolved ? resolved.element : null;
                if (!target) {
                    return JSON.stringify({ ok: false, error: "ref_not_found" });
                }
                const anchor = target.closest ? target.closest("a[href]") : null;
                return JSON.stringify({
                    ok: true,
                    href: anchor ? String(anchor.href || "") : "",
                    target: anchor ? String(anchor.target || "") : "",
                    download: !!(anchor && anchor.hasAttribute("download"))
                });
            })();
            """.trimIndent(),
            "click_target_error",
        ) ?: return null
    if (!json.optBoolean("ok", false)) {
        return null
    }
    return BrowserClickTargetInfo(
        href = json.optString("href", ""),
        target = json.optString("target", ""),
        isDownload = json.optBoolean("download", false),
    )
}

internal fun StandardBrowserSessionTools.resolveClickTargetInfoBySelector(
    webView: WebView,
    selector: String,
): BrowserClickTargetInfo? {
    val json =
        runJsonScript(
            webView,
            """
            (function() {
                const target = document.querySelector(${quoteJs(selector)});
                if (!target) {
                    return JSON.stringify({ ok: false, error: "selector_not_found" });
                }
                const anchor = target.closest ? target.closest("a[href]") : null;
                return JSON.stringify({
                    ok: true,
                    href: anchor ? String(anchor.href || "") : "",
                    target: anchor ? String(anchor.target || "") : "",
                    download: !!(anchor && anchor.hasAttribute("download"))
                });
            })();
            """.trimIndent(),
            "click_target_error",
        ) ?: return null
    if (!json.optBoolean("ok", false)) {
        return null
    }
    return BrowserClickTargetInfo(
        href = json.optString("href", ""),
        target = json.optString("target", ""),
        isDownload = json.optBoolean("download", false),
    )
}

internal fun StandardBrowserSessionTools.resolveElementRect(
    webView: WebView,
    ref: String
): Rect? {
    val script =
        """
        (function() {
            ${browserRefResolverScript()}
            const refValue = ${quoteJs(ref)};
            const resolved = __operitResolveRef(refValue);
            const target = resolved ? resolved.element : null;
            const targetWindow = resolved ? (resolved.window || window) : window;
            if (!target) {
                return JSON.stringify({ ok: false, error: "ref_not_found" });
            }
            try { target.scrollIntoView({ block: "center", inline: "center" }); } catch (_) {}
            let rect = target.getBoundingClientRect();
            let currentWindow = targetWindow;
            while (currentWindow && currentWindow !== window) {
                let frameElement = null;
                try {
                    frameElement = currentWindow.frameElement;
                } catch (_) {
                    frameElement = null;
                }
                if (!frameElement) {
                    break;
                }
                const frameRect = frameElement.getBoundingClientRect();
                rect = {
                    left: rect.left + frameRect.left,
                    top: rect.top + frameRect.top,
                    right: rect.right + frameRect.left,
                    bottom: rect.bottom + frameRect.top
                };
                try {
                    currentWindow = frameElement.ownerDocument.defaultView;
                } catch (_) {
                    break;
                }
            }
            return JSON.stringify({
                ok: true,
                left: Math.max(0, Math.floor(rect.left)),
                top: Math.max(0, Math.floor(rect.top)),
                right: Math.max(0, Math.ceil(rect.right)),
                bottom: Math.max(0, Math.ceil(rect.bottom)),
                viewportWidth: Number(window.innerWidth || 0),
                viewportHeight: Number(window.innerHeight || 0)
            });
        })();
        """.trimIndent()
    val json = runJsonScript(webView, script, "element_rect_error") ?: return null
    if (!json.optBoolean("ok", false)) {
        return null
    }
    val viewportWidth = json.optDouble("viewportWidth", 0.0)
    val viewportHeight = json.optDouble("viewportHeight", 0.0)
    if (viewportWidth <= 0.0 || viewportHeight <= 0.0 || webView.width <= 0 || webView.height <= 0) {
        return null
    }
    return Rect(
        BrowserViewportPolicy.mapCssCoordinate(
            cssCoordinate = json.optDouble("left"),
            cssExtent = viewportWidth,
            viewExtent = webView.width,
        ),
        BrowserViewportPolicy.mapCssCoordinate(
            cssCoordinate = json.optDouble("top"),
            cssExtent = viewportHeight,
            viewExtent = webView.height,
        ),
        BrowserViewportPolicy.mapCssCoordinate(
            cssCoordinate = json.optDouble("right"),
            cssExtent = viewportWidth,
            viewExtent = webView.width,
        ),
        BrowserViewportPolicy.mapCssCoordinate(
            cssCoordinate = json.optDouble("bottom"),
            cssExtent = viewportHeight,
            viewExtent = webView.height,
        ),
    )
}

internal object BrowserRunCodeContract {
    const val UNSUPPORTED_API_PREFIX = "Unsupported Playwright API: page."
    const val FUNCTION_SOURCE_ERROR =
        "code must be a JavaScript function source such as async (page) => { ... }"

    val supportedPageMembers =
        setOf(
            "title",
            "url",
            "evaluate",
            "waitForTimeout",
            "setContent",
            "on",
            "once",
            "off",
            "removeListener",
            "locator",
            "getByRole",
            "keyboard",
        )

    val supportedDialogTypes = setOf("alert", "confirm", "prompt")

    val supportedLocatorMembers =
        setOf(
            "click",
            "hover",
            "fill",
            "selectOption",
            "textContent",
        )

    val supportedKeyboardMembers = setOf("press")
    val supportedKeyboardNamedKeys = setOf("Enter", "Backspace", "Delete")

    private val supportedFunctionPrefix =
        Regex(
            """^\s*\(?\s*(?:(?:async\s+)?function(?:\s+[A-Za-z_$][\w$]*)?\s*\(|(?:async\s+)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>)"""
        )

    fun supportsKeyboardPress(key: String): Boolean =
        key.length == 1 || key in supportedKeyboardNamedKeys

    fun supportsFunctionSource(code: String): Boolean =
        supportedFunctionPrefix.containsMatchIn(code)

    fun unsupportedApi(method: String): String = "$UNSUPPORTED_API_PREFIX$method"
}

internal fun StandardBrowserSessionTools.runPlaywrightLikeCode(
    session: BrowserToolSession,
    code: String
): String {
    val functionSource = code.trim().trimEnd(';').trimEnd()
    val expression =
        """
        (async function() {
            ${playwrightLikeInputRuntimeJs()}
            const isVisible = (el) => {
                if (!el || el.nodeType !== 1) return false;
                const style = window.getComputedStyle(el);
                if (!style || style.visibility === "hidden" || style.display === "none") return false;
                const rect = el.getBoundingClientRect();
                return rect.width > 0 || rect.height > 0;
            };
            const roleFor = (el) => {
                const explicit = String(el.getAttribute("role") || "").trim();
                if (explicit) return explicit;
                const tag = String(el.tagName || "").toLowerCase();
                if (tag === "a") return "link";
                if (tag === "button") return "button";
                if (tag === "select") return "combobox";
                if (tag === "textarea") return "textbox";
                if (tag === "input") {
                    const type = String(el.getAttribute("type") || "").toLowerCase();
                    if (type === "checkbox") return "checkbox";
                    if (type === "radio") return "radio";
                    if (type === "submit" || type === "button" || type === "reset") return "button";
                    return "textbox";
                }
                return "generic";
            };
            const nameFor = (el) => String(
                el.getAttribute("aria-label") ||
                el.getAttribute("title") ||
                el.getAttribute("placeholder") ||
                el.getAttribute("alt") ||
                el.getAttribute("value") ||
                el.innerText ||
                el.textContent ||
                ""
            ).replace(/\s+/g, " ").trim();
            const findByRole = (role, name) => Array.from(document.querySelectorAll("*"))
                .filter((el) => isVisible(el) && roleFor(el) === String(role))
                .find((el) => name == null || nameFor(el) === String(name));
            const unsupportedPageApiPrefix = ${quoteJs(BrowserRunCodeContract.UNSUPPORTED_API_PREFIX)};
            const unsupportedPageApi = (method, detail) => {
                const suffix = detail ? ". " + String(detail) : "";
                return unsupportedPageApiPrefix + String(method) + suffix;
            };
            const dialogHandlers = [];
            const pendingDialogHandlerWork = [];
            let dialogHandlerError = null;
            const removeDialogHandler = (handler) => {
                for (let index = dialogHandlers.length - 1; index >= 0; index -= 1) {
                    if (dialogHandlers[index].handler === handler) {
                        dialogHandlers.splice(index, 1);
                    }
                }
            };
            const registerDialogHandler = (event, handler, once) => {
                if (String(event) !== "dialog") {
                    throw new Error(unsupportedPageApi(
                        (once ? "once" : "on") + "(" + JSON.stringify(String(event)) + ")"
                    ));
                }
                if (typeof handler !== "function") {
                    throw new Error("page." + (once ? "once" : "on") + "('dialog') expects a function");
                }
                dialogHandlers.push({ handler, once: !!once });
            };
            const dispatchDialog = (type, message, defaultValue) => {
                const state = {
                    type: String(type),
                    message: String(message == null ? "" : message),
                    defaultValue: String(defaultValue == null ? "" : defaultValue),
                    handled: false,
                    accepted: false,
                    promptText: null
                };
                const dialog = {
                    type() { return state.type; },
                    message() { return state.message; },
                    defaultValue() { return state.defaultValue; },
                    accept(promptText) {
                        if (state.handled) {
                            return Promise.reject(new Error("Dialog is already handled"));
                        }
                        state.handled = true;
                        state.accepted = true;
                        state.promptText =
                            promptText === undefined ? state.defaultValue : String(promptText);
                        return Promise.resolve();
                    },
                    dismiss() {
                        if (state.handled) {
                            return Promise.reject(new Error("Dialog is already handled"));
                        }
                        state.handled = true;
                        state.accepted = false;
                        return Promise.resolve();
                    }
                };
                const handlers = dialogHandlers.slice();
                handlers.forEach((entry) => {
                    if (entry.once) {
                        removeDialogHandler(entry.handler);
                    }
                    try {
                        const work = entry.handler(dialog);
                        pendingDialogHandlerWork.push(
                            Promise.resolve(work).catch((error) => {
                                dialogHandlerError = error;
                            })
                        );
                    } catch (error) {
                        dialogHandlerError = error;
                    }
                });
                return state;
            };
            const flushDialogHandlers = async () => {
                while (pendingDialogHandlerWork.length > 0) {
                    const work = pendingDialogHandlerWork.splice(0, pendingDialogHandlerWork.length);
                    await Promise.all(work);
                }
                if (dialogHandlerError) {
                    const error = dialogHandlerError;
                    dialogHandlerError = null;
                    throw error;
                }
            };
            const replaceAttributes = (target, source) => {
                Array.from(target.attributes || []).forEach((attribute) => {
                    target.removeAttribute(attribute.name);
                });
                Array.from(source.attributes || []).forEach((attribute) => {
                    target.setAttribute(attribute.name, attribute.value);
                });
            };
            const installParsedContent = async (html, options) => {
                const normalizedOptions = options == null ? {} : options;
                if (typeof normalizedOptions !== "object" || Array.isArray(normalizedOptions)) {
                    throw new Error("page.setContent options must be an object");
                }
                const unsupportedOptions =
                    Object.keys(normalizedOptions).filter((key) => key !== "waitUntil");
                if (unsupportedOptions.length > 0) {
                    throw new Error(unsupportedPageApi(
                        "setContent",
                        "Unsupported option(s): " + unsupportedOptions.join(", ")
                    ));
                }
                const waitUntil = String(normalizedOptions.waitUntil || "load");
                if (!["commit", "domcontentloaded", "load"].includes(waitUntil)) {
                    throw new Error(unsupportedPageApi(
                        "setContent",
                        "Unsupported waitUntil value: " + waitUntil
                    ));
                }

                const parsed = new DOMParser().parseFromString(String(html), "text/html");
                const pendingScripts = [];
                const cloneForCurrentDocument = (node) => {
                    if (node.nodeType === Node.TEXT_NODE) {
                        return document.createTextNode(node.nodeValue || "");
                    }
                    if (node.nodeType === Node.COMMENT_NODE) {
                        return document.createComment(node.nodeValue || "");
                    }
                    if (node.nodeType !== Node.ELEMENT_NODE) {
                        return document.createTextNode("");
                    }
                    if (String(node.tagName || "").toLowerCase() === "script") {
                        const marker = document.createComment("browser_run_code_script");
                        pendingScripts.push({ marker, source: node });
                        return marker;
                    }
                    const clone =
                        node.namespaceURI && node.namespaceURI !== "http://www.w3.org/1999/xhtml"
                            ? document.createElementNS(node.namespaceURI, node.nodeName)
                            : document.createElement(String(node.tagName || "").toLowerCase());
                    Array.from(node.attributes || []).forEach((attribute) => {
                        clone.setAttribute(attribute.name, attribute.value);
                    });
                    Array.from(node.childNodes || []).forEach((child) => {
                        clone.appendChild(cloneForCurrentDocument(child));
                    });
                    return clone;
                };
                const replaceChildren = (target, source) => {
                    const fragment = document.createDocumentFragment();
                    Array.from(source.childNodes || []).forEach((child) => {
                        fragment.appendChild(cloneForCurrentDocument(child));
                    });
                    target.replaceChildren(fragment);
                };

                replaceAttributes(document.documentElement, parsed.documentElement);
                replaceAttributes(document.head, parsed.head);
                replaceAttributes(document.body, parsed.body);
                replaceChildren(document.head, parsed.head);
                replaceChildren(document.body, parsed.body);

                for (const pending of pendingScripts) {
                    const script = document.createElement("script");
                    Array.from(pending.source.attributes || []).forEach((attribute) => {
                        script.setAttribute(attribute.name, attribute.value);
                    });
                    script.textContent = pending.source.textContent || "";
                    let scriptLoad = null;
                    if (script.src && waitUntil !== "commit") {
                        scriptLoad = new Promise((resolve) => {
                            script.addEventListener("load", resolve, { once: true });
                            script.addEventListener("error", resolve, { once: true });
                        });
                    }
                    pending.marker.replaceWith(script);
                    if (scriptLoad) {
                        await scriptLoad;
                    }
                    await flushDialogHandlers();
                }
                if (waitUntil !== "commit") {
                    await new Promise((resolve) => setTimeout(resolve, 0));
                }
                return null;
            };
            const makeLocator = (resolver, description) => {
                const locatorTarget = {
                async click() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    const result = __operitPw.clickElement(el);
                    if (result !== "done") throw new Error(String(result));
                    await flushDialogHandlers();
                    await new Promise((resolve) => setTimeout(resolve, 60));
                    return null;
                },
                async hover() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    const result = __operitPw.hoverElement(el);
                    if (result !== "done") throw new Error(String(result));
                    return null;
                },
                async fill(value) {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    const result = __operitPw.completeFill(el, value);
                    if (result !== "done") throw new Error(String(result));
                    return null;
                },
                async selectOption(values) {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    const requestedValues = Array.isArray(values) ? values : [values];
                    const optionsToSelect = requestedValues.map((item) => {
                        if (item && typeof item === "object" && !Array.isArray(item)) {
                            const option = {};
                            if (Object.prototype.hasOwnProperty.call(item, "value")) {
                                option.value = item.value;
                            }
                            if (Object.prototype.hasOwnProperty.call(item, "label")) {
                                option.label = item.label;
                            }
                            if (Object.prototype.hasOwnProperty.call(item, "index")) {
                                option.index = item.index;
                            }
                            if (Object.keys(option).length === 0) {
                                throw new Error("selectOption object requires value, label, or index");
                            }
                            return option;
                        }
                        return { valueOrLabel: String(item) };
                    });
                    const result = __operitPw.selectOptions(el, optionsToSelect);
                    if (!Array.isArray(result)) throw new Error(String(result));
                    return result;
                },
                async textContent() {
                    const el = resolver();
                    if (!el) throw new Error(description + " not found");
                    return String(el.textContent || "");
                }
                };
                return new Proxy(locatorTarget, {
                    get(target, property, receiver) {
                        if (property in target) {
                            return Reflect.get(target, property, receiver);
                        }
                        if (typeof property === "symbol") {
                            return Reflect.get(target, property, receiver);
                        }
                        throw new Error(unsupportedPageApi("locator." + String(property)));
                    }
                });
            };
            const supportedKeyboardNamedKeys =
                new Set(${JSONArray(BrowserRunCodeContract.supportedKeyboardNamedKeys.toList())});
            const keyboardTarget = {
                async press(key) {
                    const keyValue = String(key);
                    if (keyValue.length !== 1 && !supportedKeyboardNamedKeys.has(keyValue)) {
                        throw new Error(unsupportedPageApi(
                            "keyboard.press(" + JSON.stringify(keyValue) + ")",
                            "Supported keys: one character, Enter, Backspace, Delete"
                        ));
                    }
                    const target = document.activeElement || document.body || document.documentElement;
                    if (!target) throw new Error("No active element");
                    const result = __operitPw.pressKey(target, keyValue);
                    if (result !== "done") throw new Error(String(result));
                    return null;
                }
            };
            const keyboard = new Proxy(keyboardTarget, {
                get(target, property, receiver) {
                    if (property in target) {
                        return Reflect.get(target, property, receiver);
                    }
                    if (typeof property === "symbol") {
                        return Reflect.get(target, property, receiver);
                    }
                    throw new Error(unsupportedPageApi("keyboard." + String(property)));
                }
            });
            let page = null;
            const pageTarget = {
                async title() { return String(document.title || ""); },
                async url() { return String(location.href || ""); },
                async evaluate(fn) {
                    if (typeof fn !== "function") throw new Error("page.evaluate expects a function");
                    const value = await fn();
                    await flushDialogHandlers();
                    return value;
                },
                async waitForTimeout(ms) {
                    await new Promise((resolve) => setTimeout(resolve, Number(ms) || 0));
                    await flushDialogHandlers();
                    return null;
                },
                async setContent(html, options) {
                    return await installParsedContent(html, options);
                },
                on(event, handler) {
                    registerDialogHandler(event, handler, false);
                    return page;
                },
                once(event, handler) {
                    registerDialogHandler(event, handler, true);
                    return page;
                },
                off(event, handler) {
                    if (String(event) !== "dialog") {
                        throw new Error(unsupportedPageApi(
                            "off(" + JSON.stringify(String(event)) + ")"
                        ));
                    }
                    removeDialogHandler(handler);
                    return page;
                },
                removeListener(event, handler) {
                    if (String(event) !== "dialog") {
                        throw new Error(unsupportedPageApi(
                            "removeListener(" + JSON.stringify(String(event)) + ")"
                        ));
                    }
                    removeDialogHandler(handler);
                    return page;
                },
                locator(selector) {
                    return makeLocator(() => document.querySelector(String(selector)), "locator(" + selector + ")");
                },
                getByRole(role, options) {
                    const name = options && Object.prototype.hasOwnProperty.call(options, "name") ? options.name : null;
                    return makeLocator(() => findByRole(role, name), "getByRole(" + role + ")");
                },
                keyboard: keyboard,
                async goto() {
                    throw new Error(unsupportedPageApi(
                        "goto",
                        "Use browser_navigate instead."
                    ));
                },
                async goBack() {
                    throw new Error(unsupportedPageApi(
                        "goBack",
                        "Use browser_navigate_back instead."
                    ));
                },
                async setViewportSize() {
                    throw new Error(unsupportedPageApi(
                        "setViewportSize",
                        "Use browser_resize instead."
                    ));
                }
            };
            page = new Proxy(pageTarget, {
                get(target, property, receiver) {
                    if (property in target) {
                        return Reflect.get(target, property, receiver);
                    }
                    if (typeof property === "symbol") {
                        return Reflect.get(target, property, receiver);
                    }
                    throw new Error(unsupportedPageApi(property));
                }
            });
            const originalAlert = window.alert;
            const originalConfirm = window.confirm;
            const originalPrompt = window.prompt;
            window.alert = function(message) {
                dispatchDialog("alert", message, "");
            };
            window.confirm = function(message) {
                const state = dispatchDialog("confirm", message, "");
                return state.handled && state.accepted;
            };
            window.prompt = function(message, defaultValue) {
                const state = dispatchDialog("prompt", message, defaultValue);
                return state.handled && state.accepted ? state.promptText : null;
            };
            // The caller supplies a function expression, which is inserted directly into the
            // WebView script. No eval-like constructor is involved, so strict CSP remains intact.
            const runCodeFunction = ($functionSource);

            try {
                if (typeof runCodeFunction !== "function") {
                    throw new Error(${quoteJs(BrowserRunCodeContract.FUNCTION_SOURCE_ERROR)});
                }
                const value = await runCodeFunction(page);
                await flushDialogHandlers();
                return value == null ? "" : value;
            } finally {
                window.alert = originalAlert;
                window.confirm = originalConfirm;
                window.prompt = originalPrompt;
            }
        })()
        """.trimIndent()
    return extractAsyncJsValue(
        evaluateJavascriptAsync(
            session.webView,
            expression,
            StandardBrowserSessionTools.DEFAULT_TIMEOUT_MS.coerceAtLeast(15_000L)
        )
    )
}

internal fun StandardBrowserSessionTools.parseHeaders(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) {
        return emptyMap()
    }

    return try {
        val json = JSONObject(raw)
        val keys = json.keys()
        val out = mutableMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            out[key] = json.optString(key, "")
        }
        out
    } catch (e: Exception) {
        AppLogger.w(EXECUTION_SUPPORT_TAG, "Invalid headers JSON: ${e.message}")
        emptyMap()
    }
}

internal fun StandardBrowserSessionTools.parseStringArrayParam(raw: String?): List<String>? {
    if (raw.isNullOrBlank()) {
        return emptyList()
    }

    return try {
        val array = JSONArray(raw)
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            if (value == null || value == JSONObject.NULL) {
                continue
            }
            out.add(value.toString())
        }
        out
    } catch (e: Exception) {
        AppLogger.w(EXECUTION_SUPPORT_TAG, "Invalid array JSON: ${e.message}")
        null
    }
}

internal fun StandardBrowserSessionTools.param(tool: AITool, name: String): String? =
    tool.parameters.find { it.name == name }?.value

internal fun StandardBrowserSessionTools.boolParam(
    tool: AITool,
    name: String,
    default: Boolean
): Boolean {
    return when (param(tool, name)?.trim()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> default
    }
}

internal fun StandardBrowserSessionTools.intParam(tool: AITool, name: String, default: Int): Int {
    return param(tool, name)?.trim()?.toIntOrNull() ?: default
}

internal fun StandardBrowserSessionTools.longParam(tool: AITool, name: String, default: Long): Long {
    return param(tool, name)?.trim()?.toLongOrNull() ?: default
}

internal fun StandardBrowserSessionTools.ok(toolName: String, payload: JSONObject): ToolResult {
    return ok(toolName, payload.toString())
}

internal fun StandardBrowserSessionTools.ok(toolName: String, payload: String): ToolResult {
    return ToolResult(
        toolName = toolName,
        success = true,
        result = StringResultData(payload)
    )
}

internal fun StandardBrowserSessionTools.error(toolName: String, message: String): ToolResult {
    val rendered = buildBrowserResponse(error = message)
    return ToolResult(
        toolName = toolName,
        success = false,
        result = StringResultData(rendered),
        error = rendered
    )
}
