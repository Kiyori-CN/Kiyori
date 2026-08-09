package com.ai.assistance.operit.core.tools.defaultTool.websession.browser

import android.webkit.JavascriptInterface
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardBrowserSessionTools
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.launch

private const val BROWSER_CREDENTIAL_TAG = "BrowserCredential"
internal const val BROWSER_CREDENTIAL_BRIDGE_NAME = "KiyoriCredentialBridge"

internal class BrowserCredentialBridge(
    private val tools: StandardBrowserSessionTools,
    private val session: BrowserToolSession,
) {
    @JavascriptInterface
    fun capture(documentToken: String, rawPayload: String) {
        if (documentToken != session.credentialDocumentToken) {
            return
        }
        if (
            session.profile != WebSessionProfile.NORMAL ||
                !tools.browserSettingsStore.current.websitePasswordSavingEnabled
        ) {
            return
        }
        val currentOrigin =
            normalizeBrowserCredentialOrigin(session.currentUrl)
                ?: return
        val capture =
            try {
                decodeBrowserCredentialCapturePayload(rawPayload)
            } catch (error: Exception) {
                AppLogger.w(
                    BROWSER_CREDENTIAL_TAG,
                    "Rejected malformed browser credential capture",
                    error,
                )
                return
            }
        if (normalizeBrowserCredentialOrigin(capture.pageUrl) != currentOrigin) {
            AppLogger.w(
                BROWSER_CREDENTIAL_TAG,
                "Rejected browser credential capture for a different top-level origin",
            )
            return
        }
        tools.ioScope.launch {
            try {
                val result = tools.browserCredentialVault.saveCapture(capture)
                if (result != BrowserCredentialMutationResult.UNCHANGED) {
                    tools.showToast("网站密码已安全保存")
                }
            } catch (error: Exception) {
                AppLogger.e(
                    BROWSER_CREDENTIAL_TAG,
                    "Unable to save captured browser credential",
                    error,
                )
                tools.showToast("网站密码保存失败")
            }
        }
    }
}

internal fun StandardBrowserSessionTools.injectBrowserCredentialSupport(
    session: BrowserToolSession,
) {
    val captureEnabled =
        session.profile == WebSessionProfile.NORMAL &&
            browserSettingsStore.current.websitePasswordSavingEnabled
    session.webView.evaluateJavascript(
        browserCredentialCaptureScript(
            documentToken = session.credentialDocumentToken,
            enabled = captureEnabled,
        ),
        null,
    )
    if (session.profile != WebSessionProfile.NORMAL) {
        return
    }
    val documentToken = session.credentialDocumentToken
    val pageUrl = session.currentUrl
    ioScope.launch {
        val credential =
            try {
                browserCredentialVault.credentialForPage(pageUrl)
            } catch (error: Exception) {
                AppLogger.e(
                    BROWSER_CREDENTIAL_TAG,
                    "Unable to read browser credential for autofill",
                    error,
                )
                return@launch
            }
        if (credential != null) {
            StandardBrowserSessionTools.mainHandler.post {
                if (
                    session.profile == WebSessionProfile.NORMAL &&
                        session.pageLoaded &&
                        session.credentialDocumentToken == documentToken &&
                        session.currentUrl == pageUrl
                ) {
                    session.webView.evaluateJavascript(
                        browserCredentialAutofillScript(credential),
                        null,
                    )
                }
            }
        }
    }
}

internal fun StandardBrowserSessionTools.applyWebsitePasswordSavingSettingOnMain() {
    StandardBrowserSessionTools.sessions.values.forEach { session ->
        if (session.pageLoaded) {
            session.webView.evaluateJavascript(
                browserCredentialCaptureScript(
                    documentToken = session.credentialDocumentToken,
                    enabled =
                        session.profile == WebSessionProfile.NORMAL &&
                            browserSettingsStore.current.websitePasswordSavingEnabled,
                ),
                null,
            )
        }
    }
}

internal fun browserCredentialCaptureScript(
    documentToken: String,
    enabled: Boolean,
): String =
    BROWSER_CREDENTIAL_CAPTURE_SCRIPT_TEMPLATE
        .replace("__KIYORI_DOCUMENT_TOKEN__", quoteBrowserJavascriptString(documentToken))
        .replace("__KIYORI_CAPTURE_ENABLED__", enabled.toString())

internal fun browserCredentialAutofillScript(credential: BrowserSavedCredential): String =
    BROWSER_CREDENTIAL_AUTOFILL_SCRIPT_TEMPLATE
        .replace(
            "__KIYORI_USERNAME_SELECTOR__",
            quoteBrowserJavascriptString(credential.usernameSelector),
        )
        .replace(
            "__KIYORI_PASSWORD_SELECTOR__",
            quoteBrowserJavascriptString(credential.passwordSelector),
        )
        .replace("__KIYORI_USERNAME__", quoteBrowserJavascriptString(credential.username))
        .replace("__KIYORI_PASSWORD__", quoteBrowserJavascriptString(credential.password))

internal fun quoteBrowserJavascriptString(value: String): String =
    buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028', '\u2029' ->
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                else ->
                    if (character.code < 0x20) {
                        append("\\u").append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
            }
        }
        append('"')
    }

private val BROWSER_CREDENTIAL_CAPTURE_SCRIPT_TEMPLATE =
    """
    (() => {
      const stateKey = "__kiyoriCredentialCaptureV1";
      const previous = window[stateKey];
      if (previous && typeof previous.dispose === "function") {
        previous.dispose();
      }
      if (!__KIYORI_CAPTURE_ENABLED__) {
        delete window[stateKey];
        return true;
      }

      const documentToken = __KIYORI_DOCUMENT_TOKEN__;
      let lastSignature = "";
      let lastCapturedAt = 0;

      const cssAttributeEscape = (value) => {
        let output = "";
        for (let index = 0; index < value.length; index += 1) {
          const code = value.charCodeAt(index);
          const character = value.charAt(index);
          if (code === 0) {
            output += "\ufffd";
          } else if (
            code < 32 ||
            code === 127 ||
            character === '"' ||
            character === "\\"
          ) {
            output += "\\" + code.toString(16) + " ";
          } else {
            output += character;
          }
        }
        return output;
      };

      const selectorFor = (input) => {
        if (input.id) {
          const idSelector = '[id="' + cssAttributeEscape(input.id) + '"]';
          if (document.querySelectorAll(idSelector).length === 1) {
            return idSelector;
          }
        }
        const segments = [];
        let element = input;
        while (element && element.nodeType === Node.ELEMENT_NODE) {
          let index = 1;
          let sibling = element.previousElementSibling;
          while (sibling) {
            if (sibling.localName === element.localName) {
              index += 1;
            }
            sibling = sibling.previousElementSibling;
          }
          segments.unshift(element.localName + ":nth-of-type(" + index + ")");
          if (element === document.documentElement) {
            break;
          }
          element = element.parentElement;
        }
        return segments.join(" > ");
      };

      const captureFrom = (scope) => {
        const root =
          scope && typeof scope.querySelectorAll === "function" ? scope : document;
        const inputs = Array.from(root.querySelectorAll("input"));
        const passwords = inputs.filter((input) => {
          return (
            input.type.toLowerCase() === "password" &&
            !input.disabled &&
            !input.readOnly &&
            input.value.length > 0
          );
        });
        if (passwords.length !== 1) {
          return;
        }
        const passwordInput = passwords[0];
        const passwordIndex = inputs.indexOf(passwordInput);
        const usernameCandidates = inputs.slice(0, passwordIndex).filter((input) => {
          const type = input.type.toLowerCase();
          return (
            (type === "text" ||
              type === "email" ||
              type === "tel" ||
              type === "search") &&
            !input.disabled &&
            !input.readOnly &&
            input.value.trim().length > 0
          );
        });
        if (usernameCandidates.length === 0) {
          return;
        }
        const usernameInput = usernameCandidates[usernameCandidates.length - 1];
        const payload = {
          pageUrl: window.location.href,
          username: usernameInput.value,
          password: passwordInput.value,
          usernameSelector: selectorFor(usernameInput),
          passwordSelector: selectorFor(passwordInput)
        };
        const signature =
          payload.pageUrl + "\n" + payload.username + "\n" + payload.password;
        const now = Date.now();
        if (signature === lastSignature && now - lastCapturedAt < 1500) {
          return;
        }
        lastSignature = signature;
        lastCapturedAt = now;
        window.KiyoriCredentialBridge.capture(documentToken, JSON.stringify(payload));
      };

      const onSubmit = (event) => {
        captureFrom(event.target);
      };
      const onClick = (event) => {
        const target =
          event.target instanceof Element
            ? event.target.closest("button,input[type='submit'],input[type='button']")
            : null;
        if (target) {
          captureFrom(target.form || target.closest("form") || document);
        }
      };
      const onKeyDown = (event) => {
        if (
          event.key === "Enter" &&
          event.target instanceof HTMLInputElement &&
          event.target.type.toLowerCase() === "password"
        ) {
          captureFrom(event.target.form || document);
        }
      };

      document.addEventListener("submit", onSubmit, true);
      document.addEventListener("click", onClick, true);
      document.addEventListener("keydown", onKeyDown, true);
      window[stateKey] = {
        dispose: () => {
          document.removeEventListener("submit", onSubmit, true);
          document.removeEventListener("click", onClick, true);
          document.removeEventListener("keydown", onKeyDown, true);
        }
      };
      return true;
    })();
    """.trimIndent()

private val BROWSER_CREDENTIAL_AUTOFILL_SCRIPT_TEMPLATE =
    """
    (() => {
      const stateKey = "__kiyoriCredentialAutofillV1";
      const previous = window[stateKey];
      if (previous && typeof previous.dispose === "function") {
        previous.dispose();
      }
      const usernameSelector = __KIYORI_USERNAME_SELECTOR__;
      const passwordSelector = __KIYORI_PASSWORD_SELECTOR__;
      const username = __KIYORI_USERNAME__;
      const password = __KIYORI_PASSWORD__;
      let observer = null;

      const fill = () => {
        let usernameInput;
        let passwordInput;
        try {
          usernameInput = document.querySelector(usernameSelector);
          passwordInput = document.querySelector(passwordSelector);
        } catch (error) {
          return false;
        }
        if (
          !(usernameInput instanceof HTMLInputElement) ||
          !(passwordInput instanceof HTMLInputElement) ||
          usernameInput.disabled ||
          usernameInput.readOnly ||
          passwordInput.disabled ||
          passwordInput.readOnly
        ) {
          return false;
        }

        const valueSetter =
          Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set;
        if (!usernameInput.value) {
          valueSetter.call(usernameInput, username);
          usernameInput.dispatchEvent(new Event("input", { bubbles: true }));
          usernameInput.dispatchEvent(new Event("change", { bubbles: true }));
        }
        if (!passwordInput.value) {
          valueSetter.call(passwordInput, password);
          passwordInput.dispatchEvent(new Event("input", { bubbles: true }));
          passwordInput.dispatchEvent(new Event("change", { bubbles: true }));
        }
        if (observer) {
          observer.disconnect();
        }
        return true;
      };

      if (!fill()) {
        observer = new MutationObserver(fill);
        observer.observe(document.documentElement, {
          childList: true,
          subtree: true
        });
      }
      window[stateKey] = {
        dispose: () => {
          if (observer) {
            observer.disconnect();
          }
        }
      };
      return true;
    })();
    """.trimIndent()
