package com.woltscraper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private EditText etSpreadsheetId, etServiceAccountEmail, etPrivateKey;
    private TextView tvStatus;
    private ImageView ivStatusIcon;
    private Button btnSave, btnEnableAccessibility, btnTestConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etSpreadsheetId = findViewById(R.id.et_spreadsheet_id);
        etServiceAccountEmail = findViewById(R.id.et_service_account_email);
        etPrivateKey = findViewById(R.id.et_private_key);
        tvStatus = findViewById(R.id.tv_status);
        ivStatusIcon = findViewById(R.id.iv_status_icon);
        btnSave = findViewById(R.id.btn_save);
        btnEnableAccessibility = findViewById(R.id.btn_enable_accessibility);
        btnTestConnection = findViewById(R.id.btn_test_connection);
        etSpreadsheetId.setText(GoogleSheetsManager.getInstance(this).getSpreadsheetId());
        btnSave.setOnClickListener(v -> saveSettings());
        btnEnableAccessibility.setOnClickListener(v -> { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); });
        btnTestConnection.setOnClickListener(v -> testConnection());
        NotificationHelper.createNotificationChannel(this);
    }

    @Override protected void onResume() { super.onResume(); updateStatus(); }

    private void saveSettings() {
        String id = etSpreadsheetId.getText().toString().trim();
        String email = etServiceAccountEmail.getText().toString().trim();
        String key = etPrivateKey.getText().toString().trim();
        if (id.isEmpty() || email.isEmpty() || key.isEmpty()) { Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show(); return; }
        GoogleSheetsManager.getInstance(this).saveSettings(id, email, key);
        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void testConnection() {
        if (!GoogleSheetsManager.getInstance(this).isConfigured()) { Toast.makeText(this, "Save settings first", Toast.LENGTH_SHORT).show(); return; }
        tvStatus.setText("Testing...");
        new Thread(() -> {
            boolean ok = GoogleSheetsManager.getInstance(this).appendRow("TEST", "Test Customer", "0501234567");
            runOnUiThread(() -> { tvStatus.setText(ok ? "Connection: OK" : "Connection: FAILED"); });
        }).start();
    }

    private void updateStatus() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        boolean enabled = false;
        if (am != null) {
            for (AccessibilityServiceInfo i : am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK))
                if (i.getId().contains("com.woltscraper")) { enabled = true; break; }
        }
        boolean configured = GoogleSheetsManager.getInstance(this).isConfigured();
        if (enabled && configured) tvStatus.setText("Active - monitoring Wolt orders");
        else if (!enabled) tvStatus.setText("Accessibility service not enabled");
        else tvStatus.setText("Accessibility OK - configure Google Sheets");
    }
}
