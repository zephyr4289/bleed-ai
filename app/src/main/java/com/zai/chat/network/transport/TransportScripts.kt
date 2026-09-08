package com.zai.chat.network.transport

/**
 * Embedded JavaScript scripts evaluated in the headless WebView engine.
 * Implements the SSE bridge, session injection, and fetch automation.
 */
object TransportScripts {

    const val BRIDGE_NAME: String = "ZaiBridge"

    fun injectAuthJs(token: String): String = """
        (function() {
            try {
                localStorage.setItem('token', '$token');
                return true;
            } catch(e) {
                return false;
            }
        })();
    """.trimIndent()

    val READY_PROBE_JS: String = """
        (function() {
            try {
                return !!document.body && (localStorage.getItem('token') !== null || typeof window.initAliyunCaptcha !== 'undefined');
            } catch(e) {
                return false;
            }
        })();
    """.trimIndent()

    val ABORT_JS: String = """
        (function() {
            if (window.__zaiAbort) {
                try { window.__zaiAbort.abort(); } catch(e){}
                window.__zaiAbort = null;
            }
        })();
    """.trimIndent()

    fun buildSendJs(
        url: String,
        headersJson: String,
        bodyJson: String
    ): String = """
        (async function() {
            if (window.__zaiAbort) {
                try { window.__zaiAbort.abort(); } catch(e){}
            }
            const controller = new AbortController();
            window.__zaiAbort = controller;

            let partialContent = "";
            let partialReasoning = "";

            try {
                const headers = $headersJson;
                const body = $bodyJson;

                console.log('[ZaiBridge] Starting completion fetch to: ' + '$url');
                console.log('[ZaiBridge] Request Model: ' + body.model + ', Messages count: ' + (body.messages ? body.messages.length : 0));

                // If page has initialized captcha and minted token, attach if missing
                if (!body.captcha_verify_param && typeof window.__zaiCaptchaParam !== 'undefined' && window.__zaiCaptchaParam) {
                    body.captcha_verify_param = window.__zaiCaptchaParam;
                    console.log('[ZaiBridge] Attached captive verify param');
                }

                const response = await fetch('$url', {
                    method: 'POST',
                    headers: headers,
                    credentials: 'include',
                    signal: controller.signal,
                    body: JSON.stringify(body)
                });

                console.log('[ZaiBridge] HTTP Response: ' + response.status + ' ' + response.statusText);

                if (response.status === 401) {
                    console.error('[ZaiBridge] HTTP 401 Unauthorized - Session expired');
                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: 401, msg: "Session expired", partialContent, partialReasoning }));
                    return;
                }

                if (!response.ok) {
                    const errText = await response.text();
                    console.error('[ZaiBridge] HTTP Error ' + response.status + ': ' + errText.slice(0, 300));
                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: response.status, msg: errText.slice(0, 300), partialContent, partialReasoning }));
                    return;
                }

                const reader = response.body.getReader();
                const decoder = new TextDecoder('utf-8');
                let buffer = '';

                while (true) {
                    const { done, value } = await reader.read();
                    if (done) break;

                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop() || '';

                    for (const line of lines) {
                        const trimmed = line.trim();
                        if (!trimmed || trimmed.startsWith(':')) continue;

                        if (trimmed === 'data: [DONE]') {
                            console.log('[ZaiBridge] Received data: [DONE]');
                            window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "done" }));
                            return;
                        }

                        if (trimmed.startsWith('data: ')) {
                            const jsonStr = trimmed.slice(6).trim();
                            try {
                                const parsed = JSON.parse(jsonStr);

                                // Check for captcha / server error payloads inside SSE
                                if (parsed.error || parsed.data?.error || parsed.data?.data?.error) {
                                    const errObj = parsed.error || parsed.data?.error || parsed.data?.data?.error;
                                    const errDetail = errObj.detail || errObj.message || JSON.stringify(errObj);
                                    console.error('[ZaiBridge] Payload error: ' + errDetail);
                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: errObj.code || 400, msg: errDetail, partialContent, partialReasoning }));
                                    return;
                                }

                                // Open WebUI / Z.AI choices structure
                                const choice = parsed.choices?.[0] || parsed.data?.choices?.[0];
                                if (choice) {
                                    const delta = choice.delta;
                                    if (delta) {
                                        if (delta.reasoning_content) {
                                            partialReasoning += delta.reasoning_content;
                                            window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "reasoning", text: delta.reasoning_content }));
                                        }
                                        if (delta.content) {
                                            partialContent += delta.content;
                                            window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "content", text: delta.content }));
                                        }
                                    }
                                }

                                // Direct delta structures
                                if (parsed.delta?.content) {
                                    partialContent += parsed.delta.content;
                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "content", text: parsed.delta.content }));
                                }
                                if (parsed.delta?.reasoning_content) {
                                    partialReasoning += parsed.delta.reasoning_content;
                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "reasoning", text: parsed.delta.reasoning_content }));
                                }

                                // Citations & Usage
                                if (parsed.citations) {
                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "citations", citations: parsed.citations }));
                                }
                                if (parsed.usage) {
                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "usage", totalTokens: parsed.usage.total_tokens || 0 }));
                                }
                            } catch(e) {
                                console.warn('[ZaiBridge] Non-JSON SSE line: ' + trimmed.slice(0, 100));
                            }
                        }
                    }
                }
                console.log('[ZaiBridge] Stream ended cleanly');
                window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "done" }));
            } catch(err) {
                if (err.name === 'AbortError') {
                    console.log('[ZaiBridge] Fetch aborted by client');
                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "done" }));
                } else {
                    console.error('[ZaiBridge] Stream fetch error: ' + err.message);
                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: 0, msg: err.message || 'Stream error', partialContent, partialReasoning }));
                }
            } finally {
                window.__zaiAbort = null;
            }
        })();
    """.trimIndent()
}
