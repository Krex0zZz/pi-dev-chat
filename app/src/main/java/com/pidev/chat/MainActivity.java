package com.pidev.chat;

import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private TextView statusText;
    private WebSocketClient ws;
    private static final String TAG = "PiChat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        statusText = findViewById(R.id.statusText);

        WebSettings ws2 = webView.getSettings();
        ws2.setJavaScriptEnabled(true);
        ws2.setDomStorageEnabled(true);
        ws2.setLoadWithOverviewMode(true);
        ws2.setUseWideViewPort(true);

        webView.addJavascriptInterface(new ChatBridge(), "ChatBridge");
        webView.loadUrl("file:///android_asset/chat.html");

        setStatus("◌ Connecting...", "#FFA726");
        connectWebSocket();
    }

    private void connectWebSocket() {
        String ip = AppSettings.getServerIp(this);
        int port = AppSettings.getPort(this);
        String url = "ws://" + ip + ":" + port;

        try {
            URI uri = new URI(url);
            ws = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake sh) {
                    Log.d(TAG, "WebSocket connected");
                    runOnUiThread(() -> {
                        setStatus("● Connected", "#00E676");
                        webView.evaluateJavascript("window.onWsOpen();", null);
                    });
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket closed: " + code);
                    runOnUiThread(() -> {
                        setStatus("✕ Disconnected", "#FF5252");
                        webView.evaluateJavascript("window.onWsClose();", null);
                    });
                    // Auto-reconnect after 2s
                    new android.os.Handler().postDelayed(() -> {
                        if (MainActivity.this.ws == ws && !MainActivity.this.isFinishing()) {
                            connectWebSocket();
                        }
                    }, 2000);
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error", ex);
                    runOnUiThread(() -> {
                        setStatus("✕ Error: " + ex.getMessage(), "#FF5252");
                        webView.evaluateJavascript("window.onWsError('" +
                                ex.getMessage().replace("'", "\\'") + "');", null);
                    });
                }

                @Override
                public void onMessage(String message) {
                    runOnUiThread(() -> {
                        String escaped = message.replace("\\", "\\\\").replace("\"", "\\\"")
                                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
                        webView.evaluateJavascript("window.onMessage('" + escaped + "');", null);
                    });
                }
            };
            ws.connect();
        } catch (URISyntaxException e) {
            setStatus("✕ Bad URL: " + url, "#FF5252");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ws != null && ws.isOpen()) {
            ws.close();
        }
    }

    private void setStatus(String text, String color) {
        statusText.setText(text);
        statusText.setTextColor(android.graphics.Color.parseColor(color));
    }

    public class ChatBridge {
        @JavascriptInterface
        public void send(String json) {
            if (ws != null && ws.isOpen()) {
                ws.send(json);
            }
        }
    }
}
