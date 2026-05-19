package com.pidev.chat;

import android.content.Context;
import android.content.SharedPreferences;

public class AppSettings {
    private static final String PREFS = "pidev_prefs";

    private static SharedPreferences get(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String getServerIp(Context ctx) {
        return get(ctx).getString("server_ip", "192.168.1.190");
    }
    public static void setServerIp(Context ctx, String ip) {
        get(ctx).edit().putString("server_ip", ip).apply();
    }

    public static int getPort(Context ctx) {
        return get(ctx).getInt("port", 8765);
    }
    public static void setPort(Context ctx, int port) {
        get(ctx).edit().putInt("port", port).apply();
    }

    public static boolean isConfigured(Context ctx) {
        return get(ctx).getBoolean("configured", false);
    }
    public static void setConfigured(Context ctx, boolean v) {
        get(ctx).edit().putBoolean("configured", v).apply();
    }
}
