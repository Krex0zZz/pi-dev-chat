package com.pidev.chat;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.net.URI;
import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private TextView statusText;
    private ImageButton reconnectBtn;
    private WebSocketClient ws;
    private static final String TAG = "PiChat";

    private Handler mainHandler;
    private int reconnectDelay = 2000;
    private Runnable reconnectRunnable;
    private ConnectivityManager.NetworkCallback networkCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainHandler = new Handler(Looper.getMainLooper());

        webView = findViewById(R.id.webView);
        statusText = findViewById(R.id.statusText);
        reconnectBtn = findViewById(R.id.reconnectBtn);

        WebSettings wsSettings = webView.getSettings();
        wsSettings.setJavaScriptEnabled(true);
        wsSettings.setDomStorageEnabled(true);
        wsSettings.setLoadWithOverviewMode(true);
        wsSettings.setUseWideViewPort(true);

        webView.addJavascriptInterface(new ChatBridge(), "ChatBridge");
        webView.loadUrl("file:///android_asset/chat.html");

        reconnectBtn.setVisibility(View.GONE);
        reconnectBtn.setOnClickListener(v -> {
            cancelScheduledReconnect();
            setStatus("◌ Connecting...", "#FFA726");
            reconnectBtn.setVisibility(View.GONE);
            connectWebSocket();
        });

        setStatus("◌ Connecting...", "#FFA726");
        connectWebSocket();
        setupNetworkMonitoring();
    }

    private volatile boolean isConnecting = false;

    private void connectWebSocket() {
        if (isConnecting) return;
        if (ws != null && ws.isOpen()) return;

        String ip = AppSettings.getServerIp(this);
        int port = AppSettings.getPort(this);
        String url = "ws://" + ip + ":" + port;

        cancelScheduledReconnect();
        isConnecting = true;

        // Close stale connection if any
        if (ws != null && !ws.isOpen()) {
            try { ws.close(); } catch (Exception ignored) {}
        }

        try {
            URI uri = new URI(url);
            ws = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake sh) {
                    Log.d(TAG, "WebSocket connected");
                    isConnecting = false;
                    reconnectDelay = 2000;
                    mainHandler.post(() -> {
                        setStatus("● Connected", "#6C63FF");
                        reconnectBtn.setVisibility(View.GONE);
                        webView.evaluateJavascript("window.onWsOpen();", null);
                    });
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.d(TAG, "WebSocket closed: " + code + ", remote=" + remote);
                    isConnecting = false;
                    mainHandler.post(() -> {
                        setStatus("✕ Disconnected", "#FF4757");
                        webView.evaluateJavascript("window.onWsClose();", null);
                        if (!isFinishing() && !isDestroyed()) {
                            scheduleReconnect();
                        }
                    });
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error", ex);
                    isConnecting = false;
                    mainHandler.post(() -> {
                        setStatus("✕ Error", "#FF4757");
                        webView.evaluateJavascript(
                                "window.onWsError('" + ex.getMessage().replace("'", "\\'") + "');", null);
                        if (!isFinishing() && !isDestroyed()) {
                            scheduleReconnect();
                        }
                    });
                }

                @Override
                public void onMessage(String message) {
                    mainHandler.post(() -> {
                        // Escape only backslash and single-quote for single-quoted JS string
                        // Do NOT escape double-quote — JSON " chars are fine inside single quotes
                        String escaped = message.replace("\\", "\\\\")
                                .replace("'", "\\'");
                        webView.evaluateJavascript(
                                "window.onMessage('" + escaped + "')", null);
                    });
                }
            };
            ws.connect();
        } catch (URISyntaxException e) {
            setStatus("✕ Bad URL: " + url, "#FF4757");
        }
    }

    private void scheduleReconnect() {
        cancelScheduledReconnect();
        reconnectBtn.setVisibility(View.VISIBLE);
        setStatus("↻ Reconnecting in " + (reconnectDelay / 1000) + "s", "#FFA726");

        reconnectRunnable = () -> {
            if (ws != null && ws.isOpen()) return;
            if (isFinishing() || isDestroyed()) return;
            if (!isNetworkAvailable()) {
                reconnectDelay = Math.min(reconnectDelay * 2, 30000);
                scheduleReconnect();
                return;
            }
            setStatus("◌ Connecting...", "#FFA726");
            connectWebSocket();
        };
        mainHandler.postDelayed(reconnectRunnable, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, 30000);
    }

    private void cancelScheduledReconnect() {
        if (reconnectRunnable != null) {
            mainHandler.removeCallbacks(reconnectRunnable);
            reconnectRunnable = null;
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm != null) {
            if (Build.VERSION.SDK_INT >= 23) {
                return cm.getActiveNetwork() != null;
            } else {
                // @SuppressWarnings("deprecation")
                Network n = cm.getActiveNetwork();
                return n != null || (cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected());
            }
        }
        return false;
    }

    private void setupNetworkMonitoring() {
        if (Build.VERSION.SDK_INT >= 21) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            if (cm != null) {
                networkCallback = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(@NonNull Network network) {
                        mainHandler.post(() -> {
                            if (ws == null || !ws.isOpen()) {
                                reconnectDelay = 2000;
                                cancelScheduledReconnect();
                                setStatus("◌ Network back", "#FFA726");
                                connectWebSocket();
                            }
                        });
                    }
                    @Override
                    public void onLost(@NonNull Network network) {
                        mainHandler.post(() -> {
                            if (ws != null && ws.isOpen())
                                setStatus("◌ Network lost", "#FFA726");
                        });
                    }
                };
                NetworkRequest request = new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build();
                cm.registerNetworkCallback(request, networkCallback);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ws != null && !ws.isOpen() && isNetworkAvailable()) {
            cancelScheduledReconnect();
            connectWebSocket();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelScheduledReconnect();
        if (networkCallback != null) {
            try {
                ((ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE))
                        .unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {}
        }
        if (isFinishing() && ws != null && ws.isOpen()) ws.close();
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
            } else {
                mainHandler.post(() ->
                        Toast.makeText(MainActivity.this, "Not connected", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void openConfig() {
            mainHandler.post(() -> {
                Intent intent = new Intent(MainActivity.this, ConfigActivity.class);
                intent.putExtra("return_to_main", true);
                startActivity(intent);
            });
        }
    }
}
