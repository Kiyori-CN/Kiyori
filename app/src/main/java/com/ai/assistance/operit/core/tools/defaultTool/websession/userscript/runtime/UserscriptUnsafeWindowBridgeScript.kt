package com.ai.assistance.operit.core.tools.defaultTool.websession.userscript.runtime

/**
 * Builds the DOM-backed synchronous channel used only for page JavaScript object access.
 *
 * Native GM messages never enter this channel. The page world can observe or alter page-object
 * operations, which is an intentional property of unsafeWindow, but it never receives a native
 * bridge authorization token.
 */
internal object UserscriptUnsafeWindowBridgeScript {
    fun runtimeSource(pageWorldRuntime: Boolean): String =
        """
        const unsafeWindowPageRequestEvent = "__kiyori_userscript_page_request_v1";
        const unsafeWindowCallbackEvent = "__kiyori_userscript_callback_v1";
        const unsafeWindowRequestAttribute = "data-kiyori-userscript-request";
        const unsafeWindowResponseAttribute = "data-kiyori-userscript-response";
        const unsafeWindowDomReferenceAttribute = "data-kiyori-userscript-node-reference";
        const unsafeWindowRemoteDescriptors = new WeakMap();
        const unsafeWindowRemoteProxyCache = new Map();
        const unsafeWindowCallbacks = new Map();
        const unsafeWindowCallbackIds = new WeakMap();
        const unsafeWindowPageObjects = new Map();
        const unsafeWindowPageObjectIds = new WeakMap();
        const unsafeWindowPageCallbacks = new Map();
        let unsafeWindowPageObjectSequence = 1;

        const unsafeWindowRandomId = function(prefix) {
            if (typeof crypto !== "object" || typeof crypto.getRandomValues !== "function") {
                throw new Error("unsafeWindow requires secure random identifiers");
            }
            const values = new Uint32Array(4);
            crypto.getRandomValues(values);
            return String(prefix || "") + Array.from(values).map((value) => value.toString(16)).join("");
        };

        const unsafeWindowCreateCarrier = function() {
            const root = document.documentElement;
            if (!root) {
                throw new Error("unsafeWindow page bridge requires documentElement");
            }
            const carrier = document.createElement("kiyori-userscript-call");
            carrier.hidden = true;
            root.appendChild(carrier);
            return carrier;
        };

        const unsafeWindowDispatch = function(eventName, payload) {
            const carrier = unsafeWindowCreateCarrier();
            try {
                const rawPayload = serialize(payload);
                if (rawPayload.length > 1048576) {
                    throw new Error("unsafeWindow bridge payload exceeds 1 MiB");
                }
                carrier.setAttribute(unsafeWindowRequestAttribute, rawPayload);
                carrier.dispatchEvent(new Event(eventName, { bubbles: true }));
                const rawResponse = carrier.getAttribute(unsafeWindowResponseAttribute);
                if (rawResponse === null) {
                    throw new Error("unsafeWindow page bridge did not return a response");
                }
                if (rawResponse.length > 1048576) {
                    throw new Error("unsafeWindow bridge response exceeds 1 MiB");
                }
                const response = JSON.parse(rawResponse);
                if (!response || response.ok !== true) {
                    throw new Error(String(response && response.error || "unsafeWindow page bridge failed"));
                }
                return response.value;
            } finally {
                carrier.remove();
                document
                    .querySelectorAll("[" + unsafeWindowDomReferenceAttribute + "]")
                    .forEach((node) => node.removeAttribute(unsafeWindowDomReferenceAttribute));
            }
        };

        const unsafeWindowEncodeNumber = function(value) {
            if (Number.isNaN(value)) {
                return { kind: "number", value: "nan" };
            }
            if (value === Infinity) {
                return { kind: "number", value: "positive_infinity" };
            }
            if (value === -Infinity) {
                return { kind: "number", value: "negative_infinity" };
            }
            if (Object.is(value, -0)) {
                return { kind: "number", value: "negative_zero" };
            }
            return { kind: "value", value: value };
        };

        const unsafeWindowDecodeNumber = function(value) {
            if (value === "nan") {
                return NaN;
            }
            if (value === "positive_infinity") {
                return Infinity;
            }
            if (value === "negative_infinity") {
                return -Infinity;
            }
            if (value === "negative_zero") {
                return -0;
            }
            throw new Error("Unknown unsafeWindow number encoding");
        };

        const unsafeWindowPageReference = function(value, ownerId) {
            let objectId = unsafeWindowPageObjectIds.get(value);
            if (!objectId) {
                if (unsafeWindowPageObjectSequence >= 4096) {
                    throw new Error("unsafeWindow page reference limit exceeded");
                }
                objectId = ++unsafeWindowPageObjectSequence;
                unsafeWindowPageObjectIds.set(value, objectId);
                unsafeWindowPageObjects.set(objectId, value);
            }
            return {
                kind: "reference",
                id: objectId,
                ownerId: Number(ownerId || 0),
                callable: typeof value === "function"
            };
        };

        const unsafeWindowPageEncode = function(value, ownerId) {
            if (typeof value === "undefined") {
                return { kind: "undefined" };
            }
            if (value === null || typeof value === "string" || typeof value === "boolean") {
                return { kind: "value", value: value };
            }
            if (typeof value === "number") {
                return unsafeWindowEncodeNumber(value);
            }
            if (typeof value === "bigint") {
                return { kind: "bigint", value: value.toString() };
            }
            if (typeof value === "symbol") {
                throw new Error("unsafeWindow cannot transfer Symbol values");
            }
            return unsafeWindowPageReference(value, ownerId);
        };

        const unsafeWindowFindDomReference = function(referenceId) {
            const candidates = document.querySelectorAll("[" + unsafeWindowDomReferenceAttribute + "]");
            for (let index = 0; index < candidates.length; index += 1) {
                const candidate = candidates[index];
                if (candidate.getAttribute(unsafeWindowDomReferenceAttribute) === referenceId) {
                    candidate.removeAttribute(unsafeWindowDomReferenceAttribute);
                    return candidate;
                }
            }
            throw new Error("unsafeWindow DOM reference is unavailable");
        };

        const unsafeWindowPageDecode = function(encoded) {
            if (!encoded || typeof encoded !== "object") {
                throw new Error("Invalid unsafeWindow value encoding");
            }
            if (encoded.kind === "undefined") {
                return undefined;
            }
            if (encoded.kind === "value") {
                return encoded.value;
            }
            if (encoded.kind === "number") {
                return unsafeWindowDecodeNumber(encoded.value);
            }
            if (encoded.kind === "bigint") {
                return BigInt(String(encoded.value));
            }
            if (encoded.kind === "date") {
                return new Date(String(encoded.value));
            }
            if (encoded.kind === "reference") {
                const referenced = unsafeWindowPageObjects.get(Number(encoded.id || 0));
                if (typeof referenced === "undefined") {
                    throw new Error("unsafeWindow page reference is unavailable");
                }
                return referenced;
            }
            if (encoded.kind === "dom") {
                return unsafeWindowFindDomReference(String(encoded.id || ""));
            }
            if (encoded.kind === "array") {
                return (encoded.items || []).map(unsafeWindowPageDecode);
            }
            if (encoded.kind === "object") {
                const value = {};
                Object.entries(encoded.entries || {}).forEach(([key, entry]) => {
                    value[key] = unsafeWindowPageDecode(entry);
                });
                return value;
            }
            if (encoded.kind === "callback") {
                const callbackId = String(encoded.id || "");
                let callback = unsafeWindowPageCallbacks.get(callbackId);
                if (!callback) {
                    callback = function() {
                        const args = Array.from(arguments).map((value) => unsafeWindowPageEncode(value, 0));
                        const result =
                            unsafeWindowDispatch(unsafeWindowCallbackEvent, {
                                callbackId: callbackId,
                                args: args
                            });
                        return unsafeWindowPageDecode(result);
                    };
                    unsafeWindowPageCallbacks.set(callbackId, callback);
                }
                return callback;
            }
            throw new Error("Unsupported unsafeWindow value encoding");
        };

        const unsafeWindowPageOperation = function(request) {
            const operation = String(request.operation || "");
            const targetId = Number(request.targetId || 0);
            const target = unsafeWindowPageObjects.get(targetId);
            if (typeof target === "undefined") {
                throw new Error("unsafeWindow target is unavailable");
            }
            if (operation === "get") {
                const value = Reflect.get(target, String(request.property || ""));
                return unsafeWindowPageEncode(value, targetId);
            }
            if (operation === "set") {
                const accepted =
                    Reflect.set(
                        target,
                        String(request.property || ""),
                        unsafeWindowPageDecode(request.value)
                    );
                return { kind: "value", value: accepted };
            }
            if (operation === "apply") {
                if (typeof target !== "function") {
                    throw new Error("unsafeWindow target is not callable");
                }
                const ownerId = Number(request.ownerId || 0);
                const owner = ownerId > 0 ? unsafeWindowPageObjects.get(ownerId) : undefined;
                const args = (request.args || []).map(unsafeWindowPageDecode);
                return unsafeWindowPageEncode(Reflect.apply(target, owner, args), 0);
            }
            if (operation === "construct") {
                if (typeof target !== "function") {
                    throw new Error("unsafeWindow target is not constructable");
                }
                const args = (request.args || []).map(unsafeWindowPageDecode);
                return unsafeWindowPageEncode(Reflect.construct(target, args), 0);
            }
            if (operation === "has") {
                return {
                    kind: "value",
                    value: String(request.property || "") in target
                };
            }
            if (operation === "delete") {
                return {
                    kind: "value",
                    value: Reflect.deleteProperty(target, String(request.property || ""))
                };
            }
            throw new Error("Unknown unsafeWindow page operation");
        };

        const unsafeWindowIsPlainObject = function(value) {
            if (!value || typeof value !== "object") {
                return false;
            }
            const prototype = Object.getPrototypeOf(value);
            return prototype === Object.prototype || prototype === null;
        };

        const unsafeWindowEncode = function(value, seen) {
            const remote = unsafeWindowRemoteDescriptors.get(value);
            if (remote) {
                return {
                    kind: "reference",
                    id: remote.id,
                    ownerId: remote.ownerId,
                    callable: remote.callable
                };
            }
            if (typeof value === "undefined") {
                return { kind: "undefined" };
            }
            if (value === null || typeof value === "string" || typeof value === "boolean") {
                return { kind: "value", value: value };
            }
            if (typeof value === "number") {
                return unsafeWindowEncodeNumber(value);
            }
            if (typeof value === "bigint") {
                return { kind: "bigint", value: value.toString() };
            }
            if (typeof value === "symbol") {
                throw new Error("unsafeWindow cannot transfer Symbol values");
            }
            if (typeof value === "function") {
                let callbackId = unsafeWindowCallbackIds.get(value);
                if (!callbackId) {
                    callbackId = unsafeWindowRandomId("callback_");
                    unsafeWindowCallbackIds.set(value, callbackId);
                    unsafeWindowCallbacks.set(callbackId, value);
                }
                return { kind: "callback", id: callbackId };
            }
            if (
                value &&
                Number(value.nodeType || 0) > 0 &&
                typeof value.setAttribute === "function"
            ) {
                const referenceId = unsafeWindowRandomId("node_");
                value.setAttribute(unsafeWindowDomReferenceAttribute, referenceId);
                return { kind: "dom", id: referenceId };
            }
            if (value instanceof Date) {
                return { kind: "date", value: value.toISOString() };
            }
            const active = seen || new WeakSet();
            if (active.has(value)) {
                throw new Error("unsafeWindow cannot transfer circular objects");
            }
            active.add(value);
            try {
                if (Array.isArray(value)) {
                    return {
                        kind: "array",
                        items: value.map((item) => unsafeWindowEncode(item, active))
                    };
                }
                if (unsafeWindowIsPlainObject(value)) {
                    const entries = {};
                    Object.entries(value).forEach(([key, entry]) => {
                        entries[key] = unsafeWindowEncode(entry, active);
                    });
                    return { kind: "object", entries: entries };
                }
            } finally {
                active.delete(value);
            }
            throw new Error("unsafeWindow cannot transfer this isolated-world object");
        };

        const unsafeWindowCreateRemoteProxy = function(encoded) {
            const key =
                String(encoded.id || 0) + ":" +
                String(encoded.ownerId || 0) + ":" +
                String(!!encoded.callable);
            const existing = unsafeWindowRemoteProxyCache.get(key);
            if (existing) {
                return existing;
            }
            if (typeof Proxy !== "function") {
                throw new Error("unsafeWindow requires Proxy support");
            }
            const descriptor = {
                id: Number(encoded.id || 0),
                ownerId: Number(encoded.ownerId || 0),
                callable: !!encoded.callable
            };
            const target = descriptor.callable ? function() {} : Object.create(null);
            const proxy =
                new Proxy(target, {
                    get(currentTarget, property) {
                        if (property === Symbol.toStringTag) {
                            return descriptor.id === 1 ? "Window" : "KiyoriUnsafeWindow";
                        }
                        if (property === Symbol.toPrimitive) {
                            return function() {
                                return descriptor.id === 1
                                    ? "[object Window]"
                                    : "[object KiyoriUnsafeWindow]";
                            };
                        }
                        if (typeof property === "symbol") {
                            throw new Error("unsafeWindow cannot read Symbol properties");
                        }
                        return unsafeWindowDecode(
                            unsafeWindowDispatch(
                                unsafeWindowPageRequestEvent,
                                {
                                    operation: "get",
                                    targetId: descriptor.id,
                                    property: String(property)
                                }
                            )
                        );
                    },
                    set(currentTarget, property, value) {
                        if (typeof property === "symbol") {
                            throw new Error("unsafeWindow cannot write Symbol properties");
                        }
                        return !!unsafeWindowDecode(
                            unsafeWindowDispatch(
                                unsafeWindowPageRequestEvent,
                                {
                                    operation: "set",
                                    targetId: descriptor.id,
                                    property: String(property),
                                    value: unsafeWindowEncode(value)
                                }
                            )
                        );
                    },
                    apply(currentTarget, thisArgument, argumentList) {
                        const thisDescriptor = unsafeWindowRemoteDescriptors.get(thisArgument);
                        const ownerId =
                            thisDescriptor
                                ? thisDescriptor.id
                                : descriptor.ownerId;
                        return unsafeWindowDecode(
                            unsafeWindowDispatch(
                                unsafeWindowPageRequestEvent,
                                {
                                    operation: "apply",
                                    targetId: descriptor.id,
                                    ownerId: ownerId,
                                    args: argumentList.map((value) => unsafeWindowEncode(value))
                                }
                            )
                        );
                    },
                    construct(currentTarget, argumentList) {
                        return unsafeWindowDecode(
                            unsafeWindowDispatch(
                                unsafeWindowPageRequestEvent,
                                {
                                    operation: "construct",
                                    targetId: descriptor.id,
                                    args: argumentList.map((value) => unsafeWindowEncode(value))
                                }
                            )
                        );
                    },
                    has(currentTarget, property) {
                        if (typeof property === "symbol") {
                            return false;
                        }
                        return !!unsafeWindowDecode(
                            unsafeWindowDispatch(
                                unsafeWindowPageRequestEvent,
                                {
                                    operation: "has",
                                    targetId: descriptor.id,
                                    property: String(property)
                                }
                            )
                        );
                    },
                    deleteProperty(currentTarget, property) {
                        if (typeof property === "symbol") {
                            return false;
                        }
                        return !!unsafeWindowDecode(
                            unsafeWindowDispatch(
                                unsafeWindowPageRequestEvent,
                                {
                                    operation: "delete",
                                    targetId: descriptor.id,
                                    property: String(property)
                                }
                            )
                        );
                    }
                });
            unsafeWindowRemoteDescriptors.set(proxy, descriptor);
            unsafeWindowRemoteProxyCache.set(key, proxy);
            return proxy;
        };

        const unsafeWindowDecode = function(encoded) {
            if (!encoded || typeof encoded !== "object") {
                throw new Error("Invalid unsafeWindow value encoding");
            }
            if (encoded.kind === "undefined") {
                return undefined;
            }
            if (encoded.kind === "value") {
                return encoded.value;
            }
            if (encoded.kind === "number") {
                return unsafeWindowDecodeNumber(encoded.value);
            }
            if (encoded.kind === "bigint") {
                return BigInt(String(encoded.value));
            }
            if (encoded.kind === "date") {
                return new Date(String(encoded.value));
            }
            if (encoded.kind === "array") {
                return (encoded.items || []).map(unsafeWindowDecode);
            }
            if (encoded.kind === "object") {
                const value = {};
                Object.entries(encoded.entries || {}).forEach(([key, entry]) => {
                    value[key] = unsafeWindowDecode(entry);
                });
                return value;
            }
            if (encoded.kind === "reference") {
                return unsafeWindowCreateRemoteProxy(encoded);
            }
            throw new Error("Unsupported unsafeWindow response encoding");
        };

        const createUnsafeWindowProxy = function() {
            return unsafeWindowCreateRemoteProxy({
                kind: "reference",
                id: 1,
                ownerId: 0,
                callable: false
            });
        };

        if ($pageWorldRuntime) {
            unsafeWindowPageObjects.set(1, window);
            unsafeWindowPageObjectIds.set(window, 1);
            document.addEventListener(
                unsafeWindowPageRequestEvent,
                function(event) {
                    const carrier = event && event.target;
                    if (!carrier || typeof carrier.getAttribute !== "function") {
                        return;
                    }
                    const rawRequest = carrier.getAttribute(unsafeWindowRequestAttribute);
                    if (rawRequest === null) {
                        return;
                    }
                    try {
                        const request = JSON.parse(rawRequest);
                        carrier.setAttribute(
                            unsafeWindowResponseAttribute,
                            serialize({
                                ok: true,
                                value: unsafeWindowPageOperation(request)
                            })
                        );
                    } catch (error) {
                        carrier.setAttribute(
                            unsafeWindowResponseAttribute,
                            serialize({
                                ok: false,
                                error: String(error && (error.stack || error.message || error) || "unsafeWindow page operation failed")
                            })
                        );
                    }
                },
                true
            );
        } else {
            document.addEventListener(
                unsafeWindowCallbackEvent,
                function(event) {
                    const carrier = event && event.target;
                    if (!carrier || typeof carrier.getAttribute !== "function") {
                        return;
                    }
                    const rawRequest = carrier.getAttribute(unsafeWindowRequestAttribute);
                    if (rawRequest === null) {
                        return;
                    }
                    let request;
                    try {
                        request = JSON.parse(rawRequest);
                    } catch (error) {
                        return;
                    }
                    const callback = unsafeWindowCallbacks.get(String(request.callbackId || ""));
                    if (typeof callback !== "function") {
                        return;
                    }
                    try {
                        const args = (request.args || []).map(unsafeWindowDecode);
                        carrier.setAttribute(
                            unsafeWindowResponseAttribute,
                            serialize({
                                ok: true,
                                value: unsafeWindowEncode(callback.apply(undefined, args))
                            })
                        );
                    } catch (error) {
                        carrier.setAttribute(
                            unsafeWindowResponseAttribute,
                            serialize({
                                ok: false,
                                error: String(error && (error.stack || error.message || error) || "unsafeWindow callback failed")
                            })
                        );
                    }
                },
                true
            );
        }
        """.trimIndent()
}
