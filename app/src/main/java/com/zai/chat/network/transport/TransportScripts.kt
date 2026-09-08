package com.zai.chat.network.transport

/**
 * Embedded JavaScript scripts and HTML signer environment evaluated in the WebView runtime.
 * Implements the decoupled Aliyun Captcha 2.0 signer harness.
 */
object TransportScripts {

    const val BRIDGE_NAME: String = "ZaiBridge"

    val SIGNER_HTML_PAYLOAD: String = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <script src="https://o.alicdn.com/captcha-frontend/aliyunCaptcha/AliyunCaptcha.js"></script>
    <style>
        * { box-sizing: border-box; }
        html, body {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            background: transparent;
            overflow: hidden;
        }
        #aliyun-viewport {
            width: 100%;
            height: 100%;
            min-width: 320px;
            min-height: 260px;
            display: flex;
            align-items: center;
            justify-content: center;
        }
    </style>
</head>
<body>
    <div id="aliyun-viewport"></div>
    <script>
        window.__ZaiSigner = {
            instance: null,
            isReady: false,
            pendingResolver: null,
            init: function() {
                if (this.instance || typeof window.initAliyunCaptcha !== 'function') return;
                window.AliyunCaptchaConfig = { region: 'sgp', prefix: 'no8xfe' };
                try {
                    window.initAliyunCaptcha({
                        SceneId: 'didk33e0',
                        prefix: 'no8xfe',
                        mode: 'popup',
                        element: '#aliyun-viewport',
                        button: null,
                        timeout: 10000,
                        immediate: false,
                        success: (res) => {
                            const ticket = res?.captchaVerifyParam || (typeof res === 'string' ? res : '');
                            console.log('[ZaiSigner] Ticket minted successfully: ' + (typeof ticket === 'string' ? ticket.substring(0, 20) : ''));
                            if (window.ZaiBridge) {
                                window.ZaiBridge.onInteractiveDismiss();
                                window.ZaiBridge.onTicketSuccess(ticket);
                            }
                            if (this.pendingResolver) {
                                this.pendingResolver(ticket);
                                this.pendingResolver = null;
                            }
                        },
                        fail: (err) => {
                            console.warn('[ZaiSigner] Escalated to interactive slider challenge', err);
                            if (window.ZaiBridge) {
                                window.ZaiBridge.onInteractiveRequired();
                            }
                        },
                        onError: (err) => {
                            console.error('[ZaiSigner] Ticket error', err);
                            if (window.ZaiBridge) {
                                window.ZaiBridge.onInteractiveDismiss();
                                window.ZaiBridge.onTicketError(JSON.stringify(err || ''));
                            }
                            if (this.pendingResolver) {
                                this.pendingResolver('');
                                this.pendingResolver = null;
                            }
                        },
                        onClose: () => {
                            if (window.ZaiBridge) {
                                window.ZaiBridge.onInteractiveDismiss();
                            }
                            if (this.pendingResolver) {
                                this.pendingResolver('');
                                this.pendingResolver = null;
                            }
                        },
                        getInstance: (inst) => {
                            this.instance = inst;
                            this.isReady = true;
                            console.log('[ZaiSigner] Aliyun Captcha instance ready');
                            if (window.ZaiBridge) {
                                window.ZaiBridge.onSignerReady();
                            }
                        }
                    });
                } catch (e) {
                    console.error('[ZaiSigner] init exception', e);
                    if (window.ZaiBridge) {
                        window.ZaiBridge.onTicketError(e.message || 'init_failed');
                    }
                }
            },
            mint: function() {
                return new Promise((resolve) => {
                    this.pendingResolver = resolve;
                    if (this.instance && typeof this.instance.verify === 'function') {
                        this.instance.verify();
                    } else {
                        if (typeof window.initAliyunCaptcha === 'function' && !this.instance) {
                            this.init();
                        }
                        setTimeout(() => {
                            if (this.pendingResolver === resolve) {
                                this.pendingResolver = null;
                                resolve('');
                            }
                        }, 5000);
                    }
                });
            }
        };

        window.addEventListener('DOMContentLoaded', () => {
            window.__ZaiSigner.init();
        });
        if (document.readyState === 'complete' || document.readyState === 'interactive') {
            window.__ZaiSigner.init();
        }
    </script>
</body>
</html>
    """.trimIndent()
}
