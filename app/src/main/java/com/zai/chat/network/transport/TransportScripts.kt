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

    val CAPTCHA_HOOK_JS: String = """
        (function() {
            if (window.__zaiGlobalHooked) return;
            window.__zaiGlobalHooked = true;

            function wrapInitAliyunCaptcha(fn) {
                if (!fn || typeof fn !== 'function') return fn;
                return function(options) {
                    console.log('[ZaiBridge] initAliyunCaptcha called');
                    if (options) {
                        const origGetInstance = options.getInstance;
                        options.getInstance = function(inst) {
                            window.__zaiCaptchaInstance = inst;
                            console.log('[ZaiBridge] Captured Aliyun Captcha instance');
                            if (origGetInstance) origGetInstance(inst);
                        };
                        const origVerifyCallback = options.captchaVerifyCallback;
                        options.captchaVerifyCallback = function(params) {
                            console.log('[ZaiBridge] Captcha verify callback called:', params);
                            if (params && params.captchaVerifyParam) {
                                window.__zaiCaptchaParam = params.captchaVerifyParam;
                            }
                            if (origVerifyCallback) return origVerifyCallback(params);
                        };
                    }
                    return fn.call(this, options);
                };
            }

            if (typeof window.initAliyunCaptcha === 'function') {
                window.initAliyunCaptcha = wrapInitAliyunCaptcha(window.initAliyunCaptcha);
            }

            let _initAliyunCaptcha = window.initAliyunCaptcha;
            try {
                Object.defineProperty(window, 'initAliyunCaptcha', {
                    get() { return _initAliyunCaptcha; },
                    set(fn) {
                        _initAliyunCaptcha = wrapInitAliyunCaptcha(fn);
                    },
                    configurable: true
                });
            } catch(e) {}

            // 2. Global fetch interceptor for completions stream
            const _origFetch = window.fetch;
            window.fetch = async function(...args) {
                const url = args[0] ? (typeof args[0] === 'string' ? args[0] : args[0].url) : '';
                if (typeof url === 'string' && url.includes('/api/v2/chat/completions')) {
                    console.log('[ZaiBridge] Intercepted completions fetch to: ' + url);
                    const response = await _origFetch.apply(this, args);
                    if (response.status === 401) {
                        window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: "401", msg: "Session expired", partialContent: "", partialReasoning: "" }));
                        return response;
                    }
                    if (!response.ok) {
                        return response;
                    }
                    const clone = response.clone();
                    (async () => {
                        try {
                            const reader = clone.body.getReader();
                            const decoder = new TextDecoder('utf-8');
                            let buffer = '';
                            let partialContent = '';
                            let partialReasoning = '';

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
                                        console.log('[ZaiBridge] Interceptor stream done');
                                        window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "done" }));
                                        return;
                                    }
                                    if (trimmed.startsWith('data: ')) {
                                        const jsonStr = trimmed.slice(6).trim();
                                        try {
                                            const parsed = JSON.parse(jsonStr);
                                            if (parsed.error || parsed.data?.error || parsed.data?.data?.error || parsed.code === "FRONTEND_CAPTCHA_REQUIRED") {
                                                const errObj = parsed.error || parsed.data?.error || parsed.data?.data?.error || parsed;
                                                const errDetail = errObj.detail || errObj.message || errObj.msg || JSON.stringify(errObj);
                                                const errCode = String(errObj.code || "FRONTEND_CAPTCHA_REQUIRED");
                                                console.error('[ZaiBridge] Interceptor payload error: ' + errCode + ' - ' + errDetail);
                                                window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: errCode, msg: errDetail, partialContent, partialReasoning }));
                                                return;
                                            }
                                            const choice = parsed.choices?.[0] || parsed.data?.choices?.[0];
                                            if (choice && choice.delta) {
                                                if (choice.delta.reasoning_content) {
                                                    partialReasoning += choice.delta.reasoning_content;
                                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "reasoning", text: choice.delta.reasoning_content }));
                                                }
                                                if (choice.delta.content) {
                                                    partialContent += choice.delta.content;
                                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "content", text: choice.delta.content }));
                                                }
                                            }
                                            if (parsed.delta?.content) {
                                                partialContent += parsed.delta.content;
                                                window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "content", text: parsed.delta.content }));
                                            }
                                            if (parsed.delta?.reasoning_content) {
                                                partialReasoning += parsed.delta.reasoning_content;
                                                window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "reasoning", text: parsed.delta.reasoning_content }));
                                            }
                                            if (parsed.citations) {
                                                window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "citations", citations: parsed.citations }));
                                            }
                                            if (parsed.usage) {
                                                window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "usage", totalTokens: parsed.usage.total_tokens || 0 }));
                                            }
                                        } catch(e) {}
                                    }
                                }
                            }
                            window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "done" }));
                        } catch(e) {
                            window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: "0", msg: e.message || "Stream error", partialContent, partialReasoning }));
                        }
                    })();
                    return response;
                }
                return _origFetch.apply(this, args);
            };
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

                // Obtain captcha param if possible
                if (!body.captcha_verify_param && typeof window.__zaiCaptchaParam !== 'undefined' && window.__zaiCaptchaParam) {
                    body.captcha_verify_param = window.__zaiCaptchaParam;
                    console.log('[ZaiBridge] Attached cached captcha verify param');
                }

                console.log('[ZaiBridge] Starting completion fetch to: ' + '$url');
                console.log('[ZaiBridge] Request Model: ' + body.model + ', Messages count: ' + (body.messages ? body.messages.length : 0));

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
                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: "401", msg: "Session expired", partialContent, partialReasoning }));
                    return;
                }

                if (!response.ok) {
                    const errText = await response.text();
                    console.error('[ZaiBridge] HTTP Error ' + response.status + ': ' + errText.slice(0, 300));
                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: String(response.status), msg: errText.slice(0, 300), partialContent, partialReasoning }));
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
                                if (parsed.error || parsed.data?.error || parsed.data?.data?.error || parsed.code === "FRONTEND_CAPTCHA_REQUIRED") {
                                    const errObj = parsed.error || parsed.data?.error || parsed.data?.data?.error || parsed;
                                    const errDetail = errObj.detail || errObj.message || errObj.msg || JSON.stringify(errObj);
                                    const errCode = String(errObj.code || "FRONTEND_CAPTCHA_REQUIRED");
                                    console.error('[ZaiBridge] Payload error: ' + errCode + ' - ' + errDetail);

                                    // If captcha error, attempt DOM-driven dispatch fallback
                                    if (errCode.includes("CAPTCHA")) {
                                        console.log('[ZaiBridge] Captcha required -> attempting DOM send fallback');
                                        const textarea = document.querySelector('#chat-textarea, textarea, [contenteditable="true"]');
                                        if (textarea) {
                                            const userMsgs = body.messages || [];
                                            const promptText = userMsgs.length > 0 ? userMsgs[userMsgs.length - 1].content : "";
                                            textarea.focus();
                                            if (textarea.tagName.toLowerCase() === 'textarea') {
                                                textarea.value = promptText;
                                            } else {
                                                textarea.textContent = promptText;
                                            }
                                            textarea.dispatchEvent(new Event('input', { bubbles: true }));
                                            textarea.dispatchEvent(new Event('change', { bubbles: true }));
                                            setTimeout(() => {
                                                const sendBtn = document.querySelector('#send-message-button, button[type="submit"], button[aria-label*="Send"], button[aria-label*="Submit"]') ||
                                                                document.querySelector('button svg.lucide-arrow-up, button svg.lucide-send')?.closest('button');
                                                if (sendBtn && !sendBtn.disabled) {
                                                    sendBtn.click();
                                                    console.log('[ZaiBridge] DOM Send clicked successfully');
                                                }
                                            }, 150);
                                            return;
                                        }
                                    }

                                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: errCode, msg: errDetail, partialContent, partialReasoning }));
                                    return;
                                }

                                // Open WebUI / Z.AI choices structure
                                const choice = parsed.choices?.[0] || parsed.data?.choices?.[0];
                                if (choice && choice.delta) {
                                    if (choice.delta.reasoning_content) {
                                        partialReasoning += choice.delta.reasoning_content;
                                        window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "reasoning", text: choice.delta.reasoning_content }));
                                    }
                                    if (choice.delta.content) {
                                        partialContent += choice.delta.content;
                                        window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "content", text: choice.delta.content }));
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
                    window.$BRIDGE_NAME.onEvent(JSON.stringify({ v: 1, t: "error", code: "0", msg: err.message || 'Stream error', partialContent, partialReasoning }));
                }
            } finally {
                window.__zaiAbort = null;
            }
        })();
    """.trimIndent()
}

