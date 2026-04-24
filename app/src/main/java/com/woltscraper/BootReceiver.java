package com.woltscraper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d("WoltScraper", "Boot completed - accessibility service will auto-resume");
            if (!GoogleSheetsManager.getInstance(context).isConfigured()) {
                NotificationHelper.showError(context, "Wolt Scraper: Google Sheets not configured. Open app to set up.");
            }
        }
    }
}
