package com.woltscraper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private EditText etWebAppUrl;
    private TextView tvStatus;
    private Button btnSave, btnEnableAccessibility, btnTestConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etWebAppUrl = findViewById(R.id.et_web_app_url);
        tvStatus = findViewById(R.id.tv_status);
        btnSave = findViewById(R.id.btn_save);
        btnEnableAccessibility = findViewById(R.id.btn_enable_accessibility);
        btnTestConnection = findViewById(R.id.btn_test_connection);
        etWebAppUrl.setText(GoogleSheetsManager.getInstance(this).getWebAppUrl());
        btnSave.setOnClickListener(v -> saveSettings());
        btnEnableAccessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        btnTestConnection.setOnClickListener(v -> testConnection());
        NotificationHelper.createNotificationChannel(this);
    }

    @Override protected void onResume() { super.onResume(); updateStatus(); }

    private void saveSettings() {
        String url = etWebAppUrl.getText().toString().trim();
        if (url.isEmpty()) { Toast.makeText(this, "Enter URL", Toast.LENGTH_SHORT).show(); return; }
        GoogleSheetsManager.getInstance(this).saveWebAppUrl(url);
        Toast.makeText(this, "Saved!", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void testConnection() {
        if (!GoogleSheetsManager.getInstance(this).isConfigured()) { Toast.makeText(this, "Save URL first", Toast.LENGTH_SHORT).show(); return; }
        tvStatus.setText("Testing...");
        new Thread(() -> {
            boolean ok = GoogleSheetsManager.getInstance(this).appendRow("TEST", "Test Customer", "0501234567");
            runOnUiThread(() -> tvStatus.setText(ok ? "Connection OK!" : "Connection FAILED"));
        }).start();
    }

    private void updateStatus() {
        boolean accessible = isAccessibilityEnabled();
        boolean configured = GoogleSheetsManager.getInstance(this).isConfigured();
        if (accessible && configured) tvStatus.setText("Active - monitoring Wolt orders");
        else if (!accessible) tvStatus.setText("Enable Accessibility Service");
        else tvStatus.setText("Enter Web App URL");
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        for (AccessibilityServiceInfo i : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
            if (i.getId().contains("com.woltscraper")) return true;
        return false;
    }
}
