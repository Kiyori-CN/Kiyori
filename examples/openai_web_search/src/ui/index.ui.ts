import type { ComposeDslContext, ComposeNode } from "../../../types/compose-dsl";
import { errorMessage, strings } from "../shared";

function stateValue<T>(
  ctx: ComposeDslContext,
  key: string,
  initialValue: T
): { value: T; set: (value: T) => void } {
  const state = ctx.useState<T>(key, initialValue);
  return { value: state[0], set: state[1] };
}

function infoLine(
  ctx: ComposeDslContext,
  label: string,
  value: string
): ComposeNode {
  return ctx.UI.Row(
    {
      fillMaxWidth: true,
      horizontalArrangement: "spaceBetween",
      verticalAlignment: "center",
    },
    [
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
    ]
  );
}

function statusLines(status: ToolPkg.OpenAIWebSearchStatus): Array<[string, string]> {
  const compatibilityTime =
    status.compatibility.tested_at_epoch_millis === null
      ? "—"
      : new Date(status.compatibility.tested_at_epoch_millis).toISOString();
  const providerContract =
    status.provider_contract === null ? "—" : status.provider_contract;
  const endpointHost =
    status.endpoint_host === null ? "—" : status.endpoint_host;
  const model = status.model === null ? "—" : status.model;
  const mode = status.mode === null ? "—" : status.mode;
  const reasoningEffort =
    status.reasoning_effort === null ? "—" : status.reasoning_effort;
  const contextSize =
    status.context_size === null ? "—" : status.context_size;
  const returnTokenBudget =
    status.return_token_budget === null ? "—" : status.return_token_budget;
  const queueTimeout =
    status.queue_timeout_seconds === null
      ? "—"
      : `${status.queue_timeout_seconds}s`;
  const httpTimeout =
    status.timeout_seconds === null ? "—" : `${status.timeout_seconds}s`;
  const authSchemePresent =
    status.auth_scheme_present === null
      ? "—"
      : status.auth_scheme_present
        ? "yes"
        : "no";
  const authSchemeKind =
    status.auth_scheme_kind === null ? "—" : status.auth_scheme_kind;
  const apiKeyRevision =
    status.api_key_revision === null ? "—" : status.api_key_revision;
  return [
    ["Provider contract", providerContract],
    ["Endpoint host", endpointHost],
    ["Model", model],
    ["Web access", mode],
    ["Reasoning", reasoningEffort],
    ["Context", contextSize],
    ["Return budget", returnTokenBudget],
    ["Queue timeout", queueTimeout],
    ["HTTP timeout", httpTimeout],
    ["Header names", status.header_names.join(", ") || "—"],
    ["Auth scheme configured", authSchemePresent],
    ["Auth scheme kind", authSchemeKind],
    ["API key configured", status.api_key_configured ? "yes" : "no"],
    ["API key revision", apiKeyRevision],
    ["Probe time", compatibilityTime],
  ];
}

function readinessLines(
  status: ToolPkg.OpenAIWebSearchStatus
): Array<[string, string]> {
  return [
    ["Provider contract", status.readiness.provider_contract.state],
    ["Endpoint", status.readiness.endpoint.state],
    ["Model", status.readiness.model.state],
    ["Credential", status.readiness.credential.state],
    ["Authentication", status.readiness.auth.state],
    ["Additional headers", status.readiness.extra_headers.state],
    ["Search options", status.readiness.search_options.state],
    ["Admission", status.readiness.admission.state],
    ["Compatibility", status.readiness.compatibility.state],
  ];
}

function compatibilityLabel(
  text: ReturnType<typeof strings>,
  state: ToolPkg.OpenAIWebSearchCompatibilityStatus["state"]
): string {
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

function compatibilityHint(
  text: ReturnType<typeof strings>,
  state: ToolPkg.OpenAIWebSearchCompatibilityStatus["state"]
): string {
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

function evidenceModeLabel(
  text: ReturnType<typeof strings>,
  mode: ToolPkg.OpenAIWebSearchEvidenceMode
): string {
  if (mode === "url_citations_and_action_sources") {
    return text.evidenceModeCitationsAndSources;
  }
  if (mode === "url_citations") {
    return text.evidenceModeCitations;
  }
  if (mode === "action_sources") {
    return text.evidenceModeSources;
  }
  if (mode === "structured_feeds") {
    return text.evidenceModeFeeds;
  }
  return text.evidenceModeNone;
}

export default function Screen(ctx: ComposeDslContext): ComposeNode {
  const text = strings();
  const statusState = stateValue<ToolPkg.OpenAIWebSearchStatus | null>(
    ctx,
    "status",
    null
  );
  const messageState = stateValue(ctx, "message", "");
  const errorState = stateValue(ctx, "error", "");
  const loadingState = stateValue(ctx, "loading", false);
  const probingState = stateValue(ctx, "probing", false);
  const openingConfigurationState = stateValue(
    ctx,
    "openingConfiguration",
    false
  );
  const loadedState = stateValue(ctx, "loaded", false);

  const loadStatus = async (): Promise<void> => {
    try {
      const result = await ToolPkg.services.openAIWebSearch.getStatus();
      statusState.set(result.status);
      errorState.set("");
    } catch (error) {
      console.error("[openai_web_search] status load failed", error);
      statusState.set(null);
      errorState.set(errorMessage(error));
    }
  };

  const validate = async (): Promise<void> => {
    loadingState.set(true);
    messageState.set("");
    try {
      const result =
        await ToolPkg.services.openAIWebSearch.validateLocalConfiguration();
      statusState.set(result.status);
      errorState.set("");
      messageState.set(
        result.valid ? text.validationPassed : text.configurationIncomplete
      );
    } catch (error) {
      console.error("[openai_web_search] local validation failed", error);
      errorState.set(errorMessage(error));
    } finally {
      loadingState.set(false);
    }
  };

  const runProbe = async (): Promise<void> => {
    probingState.set(true);
    messageState.set("");
    try {
      await ToolPkg.services.openAIWebSearch.runCompatibilityProbe();
      await loadStatus();
      errorState.set("");
      messageState.set(text.probePassed);
    } catch (error) {
      console.error("[openai_web_search] compatibility probe failed", error);
      await loadStatus();
      errorState.set(errorMessage(error));
    } finally {
      probingState.set(false);
    }
  };

  const openConfiguration = async (): Promise<void> => {
    openingConfigurationState.set(true);
    try {
      await ToolPkg.services.openAIWebSearch.openConfiguration();
      errorState.set("");
    } catch (error) {
      console.error("[openai_web_search] native configuration open failed", error);
      errorState.set(errorMessage(error));
    } finally {
      openingConfigurationState.set(false);
    }
  };

  const children: ComposeNode[] = [
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
    ctx.UI.Card(
      {
        fillMaxWidth: true,
        containerColor: "secondaryContainer",
      },
      [
        ctx.UI.Text({
          text: text.independenceNotice,
          style: "bodySmall",
          color: "onSecondaryContainer",
          padding: 14,
        }),
      ]
    ),
    ctx.UI.Text({
      text: text.currentBinding,
      style: "titleMedium",
      fontWeight: "bold",
    }),
    ctx.UI.Button({
      text: openingConfigurationState.value
        ? text.openingConfiguration
        : text.openConfiguration,
      enabled:
        !openingConfigurationState.value &&
        !loadingState.value &&
        !probingState.value,
      fillMaxWidth: true,
      onClick: openConfiguration,
    }),
  ];

  if (statusState.value === null) {
    children.push(
      ctx.UI.Card({ fillMaxWidth: true }, [
        ctx.UI.Text({
          text: errorState.value.trim()
            ? text.configurationIncomplete
            : text.noStatus,
          style: "bodyMedium",
          padding: 14,
        }),
      ])
    );
  } else {
    const compatibility = statusState.value.compatibility;
    const hint = compatibilityHint(text, compatibility.state);
    children.push(
      ctx.UI.Text({
        text: text.readiness,
        style: "titleMedium",
        fontWeight: "bold",
      })
    );
    children.push(
      ctx.UI.Card({ fillMaxWidth: true }, [
        ctx.UI.Column(
          { fillMaxWidth: true, padding: 14, spacing: 10 },
          readinessLines(statusState.value).map((entry) =>
            infoLine(ctx, entry[0], entry[1])
          )
        ),
      ])
    );
    children.push(
      ctx.UI.Card({ fillMaxWidth: true }, [
        ctx.UI.Column(
          { fillMaxWidth: true, padding: 14, spacing: 10 },
          statusLines(statusState.value).map((entry, index) =>
            infoLine(ctx, entry[0], entry[1])
          )
        ),
      ])
    );
    children.push(
      ctx.UI.Card(
        {
          fillMaxWidth: true,
          containerColor:
            compatibility.state === "failed" ? "errorContainer" : "secondaryContainer",
        },
        [
          ctx.UI.Column(
            { fillMaxWidth: true, padding: 14, spacing: 8 },
            [
              infoLine(
                ctx,
                "Compatibility",
                compatibilityLabel(text, compatibility.state)
              ),
              ...(compatibility.state === "failed" && compatibility.error_code !== null
                ? [infoLine(ctx, text.probeErrorCode, compatibility.error_code)]
                : []),
              ...(compatibility.state === "valid" &&
              compatibility.evidence_mode !== null
                ? [
                    infoLine(
                      ctx,
                      text.evidenceMode,
                      evidenceModeLabel(text, compatibility.evidence_mode)
                    ),
                  ]
                : []),
              ...(compatibility.state === "failed" && compatibility.http_status !== null
                ? [
                    infoLine(
                      ctx,
                      text.probeHttpStatus,
                      `${compatibility.http_status}`
                    ),
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
                    infoLine(
                      ctx,
                      text.providerErrorType,
                      compatibility.provider_error_type
                    ),
                  ]
                : []),
              ...(compatibility.state === "failed" &&
              compatibility.provider_error_code !== null
                ? [
                    infoLine(
                      ctx,
                      text.providerErrorCode,
                      compatibility.provider_error_code
                    ),
                  ]
                : []),
              ...(compatibility.state === "failed" &&
              compatibility.provider_request_id !== null
                ? [
                    infoLine(
                      ctx,
                      text.providerRequestId,
                      compatibility.provider_request_id
                    ),
                  ]
                : []),
              ...(hint
                ? [
                    ctx.UI.Text({
                      text: hint,
                      style: "bodySmall",
                      color:
                        compatibility.state === "failed"
                          ? "onErrorContainer"
                          : "onSecondaryContainer",
                    }),
                  ]
                : []),
            ]
          ),
        ]
      )
    );
  }

  children.push(
    ctx.UI.Text({
      text: text.localValidation,
      style: "titleMedium",
      fontWeight: "bold",
    }),
    ctx.UI.Button({
      text: loadingState.value ? text.validating : text.validateButton,
      enabled:
        !loadingState.value &&
        !probingState.value &&
        !openingConfigurationState.value,
      fillMaxWidth: true,
      onClick: validate,
    }),
    ctx.UI.Text({
      text: text.probeTitle,
      style: "titleMedium",
      fontWeight: "bold",
    }),
    ctx.UI.Card(
      {
        fillMaxWidth: true,
        containerColor: "errorContainer",
      },
      [
        ctx.UI.Text({
          text: text.probeWarning,
          style: "bodySmall",
          color: "onErrorContainer",
          padding: 14,
        }),
      ]
    ),
    ctx.UI.Button({
      text: probingState.value ? text.probing : text.probeButton,
      enabled:
        !probingState.value &&
        !loadingState.value &&
        !openingConfigurationState.value,
      fillMaxWidth: true,
      onClick: runProbe,
    })
  );

  if (messageState.value.trim()) {
    children.push(
      ctx.UI.Card({ fillMaxWidth: true, containerColor: "primaryContainer" }, [
        ctx.UI.Text({
          text: messageState.value,
          style: "bodyMedium",
          color: "onPrimaryContainer",
          padding: 14,
        }),
      ])
    );
  }
  if (errorState.value.trim()) {
    children.push(
      ctx.UI.Card({ fillMaxWidth: true, containerColor: "errorContainer" }, [
        ctx.UI.Text({
          text: errorState.value,
          style: "bodyMedium",
          color: "onErrorContainer",
          padding: 14,
        }),
      ])
    );
  }

  return ctx.UI.LazyColumn(
    {
      fillMaxSize: true,
      padding: 16,
      spacing: 16,
      onLoad: async () => {
        if (!loadedState.value) {
          loadedState.set(true);
          await loadStatus();
        }
      },
    },
    children
  );
}
