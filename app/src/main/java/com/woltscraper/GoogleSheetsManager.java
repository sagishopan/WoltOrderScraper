package com.woltscraper;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class GoogleSheetsManager {
    private static final String TAG = "GoogleSheetsManager";
    private static final String PREFS_NAME = "wolt_scraper_prefs";
    private static GoogleSheetsManager instance;
    private final Context context;
    private String accessToken = null;
    private long tokenExpiryTime = 0;

    public static GoogleSheetsManager getInstance(Context context) {
        if (instance == null) instance = new GoogleSheetsManager(context.getApplicationContext());
        return instance;
    }
    private GoogleSheetsManager(Context context) { this.context = context; }

    public boolean appendRow(String orderNumber, String customerName, String phoneNumber) {
        try {
            String spreadsheetId = getSpreadsheetId();
            if (spreadsheetId == null || spreadsheetId.isEmpty()) return false;
            String token = getValidAccessToken();
            if (token == null) return false;
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            JSONObject body = new JSONObject();
            JSONArray values = new JSONArray();
            JSONArray row = new JSONArray();
            row.put(timestamp); row.put(orderNumber); row.put(customerName); row.put(phoneNumber);
            values.put(row); body.put("values", values);
            String urlStr = "https://sheets.googleapis.com/v4/spreadsheets/" + spreadsheetId + "/values/Sheet1!A:D:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS";
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true); conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes("UTF-8")); }
            return conn.getResponseCode() == 200;
        } catch (Exception e) { Log.e(TAG, "appendRow failed", e); return false; }
    }

    private String getValidAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime - 60000) return accessToken;
        return refreshAccessToken();
    }

    private String refreshAccessToken() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String email = prefs.getString("service_account_email", "");
            String privateKey = prefs.getString("private_key", "");
            if (email.isEmpty() || privateKey.isEmpty()) return null;
            long now = System.currentTimeMillis() / 1000;
            String jwt = JwtHelper.buildServiceAccountJwt(email, privateKey, "https://www.googleapis.com/auth/spreadsheets", now, now + 3600);
            if (jwt == null) return null;
            String postData = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=" + jwt;
            HttpURLConnection conn = (HttpURLConnection) new URL("https://oauth2.googleapis.com/token").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true); conn.setConnectTimeout(10000);
            try (OutputStream os = conn.getOutputStream()) { os.write(postData.getBytes("UTF-8")); }
            int code = conn.getResponseCode();
            BufferedReader br = new BufferedReader(new InputStreamReader(code == 200 ? conn.getInputStream() : conn.getErrorStream()));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line);
            if (code == 200) {
                JSONObject json = new JSONObject(sb.toString());
                accessToken = json.getString("access_token");
                tokenExpiryTime = System.currentTimeMillis() + json.optInt("expires_in", 3600) * 1000L;
                return accessToken;
            }
            return null;
        } catch (Exception e) { Log.e(TAG, "refreshAccessToken failed", e); return null; }
    }

    public String getSpreadsheetId() { return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("spreadsheet_id", ""); }

    public void saveSettings(String spreadsheetId, String email, String privateKey) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("spreadsheet_id", spreadsheetId)
                .putString("service_account_email", email)
                .putString("private_key", privateKey).apply();
        accessToken = null; tokenExpiryTime = 0;
    }

    public boolean isConfigured() {
        SharedPreferences p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !p.getString("spreadsheet_id", "").isEmpty() && !p.getString("service_account_email", "").isEmpty() && !p.getString("private_key", "").isEmpty();
    }
}
