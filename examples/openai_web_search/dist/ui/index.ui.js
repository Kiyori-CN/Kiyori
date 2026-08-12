"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.default = Screen;
const shared_1 = require("../shared");
function stateValue(ctx, key, initialValue) {
    const state = ctx.useState(key, initialValue);
    return { value: state[0], set: state[1] };
}
function infoLine(ctx, label, value) {
    return ctx.UI.Row({
        fillMaxWidth: true,
        horizontalArrangement: "spaceBetween",
        verticalAlignment: "center",
    }, [
        ctx.UI.Text({
            text: label,
            style: "bodySmall",
            color: "onSurfaceVariant",
        }),
        ctx.UI.Spacer({ width: 12 }),
        ctx.UI.Text({
            text: value,
            style: "bodyMedium",
        }),
    ]);
}
function statusLines(status) {
    const compatibilityTime = status.compatibility.tested_at_epoch_millis === null
        ? "—"
        : new Date(status.compatibility.tested_at_epoch_millis).toISOString();
    return [
        ["Config source", status.config_source],
        ["Provider contract", status.provider_contract],
        ["Endpoint", status.endpoint],
        ["Model", status.model],
        ["Model config ID", status.model_config_id ?? "—"],
        ["Web access", status.mode],
        ["Reasoning", status.reasoning_effort],
        ["Context", status.context_size],
        ["Return budget", status.return_token_budget],
        ["Timeout", `${status.timeout_seconds}s`],
        ["Header names", status.header_names.join(", ") || "—"],
        ["Auth scheme configured", status.auth_scheme_present ? "yes" : "no"],
        ["Auth scheme kind", status.auth_scheme_kind],
        ["API key configured", status.api_key_configured ? "yes" : "no"],
        ["API key revision", status.api_key_revision],
        ["Probe time", compatibilityTime],
    ];
}
function compatibilityLabel(text, state) {
    if (state === "not_required") {
        return text.compatibilityNotRequired;
    }
    if (state === "missing") {
        return text.compatibilityMissing;
    }
    if (state === "stale") {
        return text.compatibilityStale;
    }
    if (state === "valid") {
        return text.compatibilityValid;
    }
    return text.compatibilityFailed;
}
function compatibilityHint(text, state) {
    if (state === "missing") {
        return text.compatibilityMissingHint;
    }
    if (state === "stale") {
        return text.compatibilityStaleHint;
    }
    if (state === "failed") {
        return text.compatibilityFailedHint;
    }
    return "";
}
function evidenceModeLabel(text, mode) {
    if (mode === "url_citations_and_action_sources") {
        return text.evidenceModeCitationsAndSources;
    }
    if (mode === "url_citations") {
        return text.evidenceModeCitations;
    }
    if (mode === "action_sources") {
        return text.evidenceModeSources;
    }
    return text.evidenceModeFeeds;
}
function Screen(ctx) {
    const text = (0, shared_1.strings)();
    const statusState = stateValue(ctx, "status", null);
    const messageState = stateValue(ctx, "message", "");
    const errorState = stateValue(ctx, "error", "");
    const loadingState = stateValue(ctx, "loading", false);
    const probingState = stateValue(ctx, "probing", false);
    const loadedState = stateValue(ctx, "loaded", false);
    const loadStatus = async () => {
        try {
            const result = await ToolPkg.services.openAIWebSearch.getStatus();
            statusState.set(result.status);
            errorState.set("");
        }
        catch (error) {
            console.error("[openai_web_search] status load failed", error);
            statusState.set(null);
            errorState.set((0, shared_1.errorMessage)(error));
        }
    };
    const validate = async () => {
        loadingState.set(true);
        messageState.set("");
        try {
            const result = await ToolPkg.services.openAIWebSearch.validateLocalConfiguration();
            statusState.set(result.status);
            errorState.set("");
            messageState.set(text.validationPassed);
        }
        catch (error) {
            console.error("[openai_web_search] local validation failed", error);
            errorState.set((0, shared_1.errorMessage)(error));
        }
        finally {
            loadingState.set(false);
        }
    };
    const runProbe = async () => {
        probingState.set(true);
        messageState.set("");
        try {
            await ToolPkg.services.openAIWebSearch.runCompatibilityProbe();
            await loadStatus();
            errorState.set("");
            messageState.set(text.probePassed);
        }
        catch (error) {
            console.error("[openai_web_search] compatibility probe failed", error);
            await loadStatus();
            errorState.set((0, shared_1.errorMessage)(error));
        }
        finally {
            probingState.set(false);
        }
    };
    const children = [
        ctx.UI.Row({ verticalAlignment: "center" }, [
            ctx.UI.Icon({ name: "search", tint: "primary", size: 24 }),
            ctx.UI.Spacer({ width: 8 }),
            ctx.UI.Text({
                text: text.title,
                style: "headlineSmall",
                fontWeight: "bold",
            }),
        ]),
        ctx.UI.Text({
            text: text.subtitle,
            style: "bodyMedium",
            color: "onSurfaceVariant",
        }),
        ctx.UI.Card({
            fillMaxWidth: true,
            containerColor: "secondaryContainer",
        }, [
            ctx.UI.Text({
                text: text.independenceNotice,
                style: "bodySmall",
                color: "onSecondaryContainer",
                padding: 14,
            }),
        ]),
        ctx.UI.Text({
            text: text.currentBinding,
            style: "titleMedium",
            fontWeight: "bold",
        }),
    ];
    if (statusState.value === null) {
        children.push(ctx.UI.Card({ fillMaxWidth: true }, [
            ctx.UI.Text({
                text: errorState.value.trim()
                    ? text.configurationIncomplete
                    : text.noStatus,
                style: "bodyMedium",
                padding: 14,
            }),
        ]));
    }
    else {
        const compatibility = statusState.value.compatibility;
        const hint = compatibilityHint(text, compatibility.state);
        children.push(ctx.UI.Card({ fillMaxWidth: true }, [
            ctx.UI.Column({ fillMaxWidth: true, padding: 14, spacing: 10 }, statusLines(statusState.value).map((entry, index) => infoLine(ctx, entry[0], entry[1]))),
        ]));
        children.push(ctx.UI.Card({
            fillMaxWidth: true,
            containerColor: compatibility.state === "failed" ? "errorContainer" : "secondaryContainer",
        }, [
            ctx.UI.Column({ fillMaxWidth: true, padding: 14, spacing: 8 }, [
                infoLine(ctx, "Compatibility", compatibilityLabel(text, compatibility.state)),
                ...(compatibility.state === "failed" && compatibility.error_code !== null
                    ? [infoLine(ctx, text.probeErrorCode, compatibility.error_code)]
                    : []),
                ...(compatibility.state === "valid" &&
                    compatibility.evidence_mode !== null
                    ? [
                        infoLine(ctx, text.evidenceMode, evidenceModeLabel(text, compatibility.evidence_mode)),
                    ]
                    : []),
                ...(compatibility.state === "failed" && compatibility.http_status !== null
                    ? [
                        infoLine(ctx, text.probeHttpStatus, `${compatibility.http_status}`),
                    ]
                    : []),
                ...(compatibility.state === "failed" && compatibility.message !== null
                    ? [
                        ctx.UI.Text({
                            text: `${text.probeFailureMessage}: ${compatibility.message}`,
                            style: "bodySmall",
                            color: "onErrorContainer",
                        }),
                    ]
                    : []),
                ...(compatibility.state === "failed" &&
                    compatibility.provider_error_type !== null
                    ? [
                        infoLine(ctx, text.providerErrorType, compatibility.provider_error_type),
                    ]
                    : []),
                ...(compatibility.state === "failed" &&
                    compatibility.provider_error_code !== null
                    ? [
                        infoLine(ctx, text.providerErrorCode, compatibility.provider_error_code),
                    ]
                    : []),
                ...(compatibility.state === "failed" &&
                    compatibility.provider_request_id !== null
                    ? [
                        infoLine(ctx, text.providerRequestId, compatibility.provider_request_id),
                    ]
                    : []),
                ...(hint
                    ? [
                        ctx.UI.Text({
                            text: hint,
                            style: "bodySmall",
                            color: compatibility.state === "failed"
                                ? "onErrorContainer"
                                : "onSecondaryContainer",
                        }),
                    ]
                    : []),
            ]),
        ]));
    }
    children.push(ctx.UI.Text({
        text: text.localValidation,
        style: "titleMedium",
        fontWeight: "bold",
    }), ctx.UI.Button({
        text: loadingState.value ? text.validating : text.validateButton,
        enabled: !loadingState.value && !probingState.value,
        fillMaxWidth: true,
        onClick: validate,
    }), ctx.UI.Text({
        text: text.probeTitle,
        style: "titleMedium",
        fontWeight: "bold",
    }), ctx.UI.Card({
        fillMaxWidth: true,
        containerColor: "errorContainer",
    }, [
        ctx.UI.Text({
            text: text.probeWarning,
            style: "bodySmall",
            color: "onErrorContainer",
            padding: 14,
        }),
    ]), ctx.UI.Button({
        text: probingState.value ? text.probing : text.probeButton,
        enabled: !probingState.value && !loadingState.value,
        fillMaxWidth: true,
        onClick: runProbe,
    }));
    if (messageState.value.trim()) {
        children.push(ctx.UI.Card({ fillMaxWidth: true, containerColor: "primaryContainer" }, [
            ctx.UI.Text({
                text: messageState.value,
                style: "bodyMedium",
                color: "onPrimaryContainer",
                padding: 14,
            }),
        ]));
    }
    if (errorState.value.trim()) {
        children.push(ctx.UI.Card({ fillMaxWidth: true, containerColor: "errorContainer" }, [
            ctx.UI.Text({
                text: errorState.value,
                style: "bodyMedium",
                color: "onErrorContainer",
                padding: 14,
            }),
        ]));
    }
    return ctx.UI.LazyColumn({
        fillMaxSize: true,
        padding: 16,
        spacing: 16,
        onLoad: async () => {
            if (!loadedState.value) {
                loadedState.set(true);
                await loadStatus();
            }
        },
    }, children);
}
