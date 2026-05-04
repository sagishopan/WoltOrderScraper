package com.woltscraper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GoogleSheetsManager {
    private static final String TAG = "GoogleSheetsManager";
    private static final String PREFS_NAME = "wolt_scraper_prefs";
    private static GoogleSheetsManager instance;
    private final Context context;

    public static GoogleSheetsManager getInstance(Context context) {
        if (instance == null) instance = new GoogleSheetsManager(context.getApplicationContext());
        return instance;
    }
    private GoogleSheetsManager(Context context) { this.context = context; }

    public boolean appendRow(String orderNumber, String customerName, String phoneNumber) {
        try {
            String webAppUrl = getWebAppUrl();
            if (webAppUrl == null || webAppUrl.isEmpty()) return false;
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String params = "timestamp=" + URLEncoder.encode(timestamp, "UTF-8")
                    + "&order=" + URLEncoder.encode(orderNumber, "UTF-8")
                    + "&name=" + URLEncoder.encode(customerName, "UTF-8")
                    + "&phone=" + URLEncoder.encode(phoneNumber, "UTF-8");
            String fullUrl = webAppUrl + (webAppUrl.contains("?") ? "&" : "?") + params;
            HttpURLConnection conn = (HttpURLConnection) new URL(fullUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            Log.d(TAG, "Response: " + code);
            return code == 200;
        } catch (Exception e) { Log.e(TAG, "appendRow failed", e); return false; }
    }

    public String getWebAppUrl() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("web_app_url", "");
    }
    public void saveWebAppUrl(String url) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString("web_app_url", url).apply();
    }
    public boolean isConfigured() { return !getWebAppUrl().isEmpty(); }
    public String getSpreadsheetId() { return ""; }
    public void saveSettings(String id, String email, String key) {}
}
