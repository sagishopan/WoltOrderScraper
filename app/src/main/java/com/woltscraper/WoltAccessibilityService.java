package com.woltscraper;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class WoltAccessibilityService extends AccessibilityService {
    private static final String TAG = "WoltScraper";
    private enum State { IDLE, ORDER_DETECTED, WAITING_FOR_ORDER_DETAILS, CLICKING_CALL_BUTTON, WAITING_FOR_PHONE_POPUP, COLLECTING_DONE }
    private State currentState = State.IDLE;
    private String currentOrderNumber = "", currentCustomerName = "", currentPhoneNumber = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastProcessedOrder = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        switch (currentState) {
            case IDLE: checkForNewOrder(root); break;
            case WAITING_FOR_ORDER_DETAILS: tryReadOrderDetails(root); break;
            case WAITING_FOR_PHONE_POPUP: tryReadPhonePopup(root); break;
        }
        root.recycle();
    }

    private void checkForNewOrder(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> btns = root.findAccessibilityNodeInfosByText("Accept");
        if (btns == null || btns.isEmpty()) btns = root.findAccessibilityNodeInfosByText("\u05e7\u05d1\u05dc");
        if (btns != null && !btns.isEmpty()) {
            String orderNum = findOrderNumber(root);
            if (orderNum != null && !orderNum.equals(lastProcessedOrder)) {
                currentOrderNumber = orderNum;
                currentState = State.ORDER_DETECTED;
                final AccessibilityNodeInfo rootRef = root;
                handler.postDelayed(() -> clickOrderCard(rootRef), 800);
            }
        }
    }

    private void clickOrderCard(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo card = findClickableContaining(root, currentOrderNumber);
        if (card != null) { card.performAction(AccessibilityNodeInfo.ACTION_CLICK); currentState = State.WAITING_FOR_ORDER_DETAILS; }
    }

    private void tryReadOrderDetails(AccessibilityNodeInfo root) {
        String name = findCustomerName(root);
        String num = findOrderNumber(root);
        if (num != null) currentOrderNumber = num;
        if (name != null && !name.isEmpty()) {
            currentCustomerName = name;
            currentState = State.CLICKING_CALL_BUTTON;
            handler.postDelayed(this::clickCallButton, 600);
        }
    }

    private String findCustomerName(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.wolt.picker:id/customer_name");
        if (nodes != null && !nodes.isEmpty() && nodes.get(0).getText() != null) return nodes.get(0).getText().toString();
        return traverseForName(root);
    }

    private String traverseForName(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence text = node.getText();
        if (text != null) {
            String t = text.toString().trim();
            if (!t.isEmpty() && !t.startsWith("#") && t.length() > 2 && t.length() < 50 && Character.isLetter(t.charAt(0)) && !t.equalsIgnoreCase("Accept") && !t.equalsIgnoreCase("Call customer")) {
                AccessibilityNodeInfo p = node.getParent();
                if (p != null && p.getViewIdResourceName() != null && p.getViewIdResourceName().toString().contains("customer")) return t;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            String r = traverseForName(child); if (r != null) return r;
            if (child != null) child.recycle();
        }
        return null;
    }

    private void clickCallButton() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        String[] labels = {"Call customer", "Call", "\u05e9\u05d9\u05d7\u05d4 \u05dc\u05dc\u05e7\u05d5\u05d7", "Phone"};
        for (String label : labels) {
            List<AccessibilityNodeInfo> btns = root.findAccessibilityNodeInfosByText(label);
            if (btns != null && !btns.isEmpty()) { btns.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK); currentState = State.WAITING_FOR_PHONE_POPUP; root.recycle(); return; }
        }
        root.recycle();
    }

    private void tryReadPhonePopup(AccessibilityNodeInfo root) {
        String phone = traverseForPhone(root);
        if (phone != null && !phone.isEmpty()) {
            currentPhoneNumber = phone;
            lastProcessedOrder = currentOrderNumber;
            closePopup(root);
            currentState = State.COLLECTING_DONE;
            sendToSheets();
            handler.postDelayed(() -> currentState = State.IDLE, 2000);
        }
    }

    private String traverseForPhone(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence text = node.getText();
        if (text != null && text.toString().trim().matches("[+\\d][\\d\\s\\-\\.]{6,14}")) return text.toString().trim().replaceAll("[\\s\\-\\.]", "");
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo c = node.getChild(i); String r = traverseForPhone(c); if (r != null) return r; if (c != null) c.recycle(); }
        return null;
    }

    private void closePopup(AccessibilityNodeInfo root) {
        for (String label : new String[]{"Close", "Cancel", "\u05e1\u05d2\u05d5\u05e8", "OK"}) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
            if (nodes != null && !nodes.isEmpty()) { nodes.get(0).performAction(AccessibilityNodeInfo.ACTION_CLICK); return; }
        }
        performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void sendToSheets() {
        final String o = currentOrderNumber, n = currentCustomerName, p = currentPhoneNumber;
        new Thread(() -> {
            boolean ok = GoogleSheetsManager.getInstance(getApplicationContext()).appendRow(o, n, p);
            handler.post(() -> {
                if (ok) NotificationHelper.showSuccess(getApplicationContext(), "Order " + o + " saved");
                else NotificationHelper.showError(getApplicationContext(), "Failed to save order " + o);
            });
        }).start();
    }

    private String findOrderNumber(AccessibilityNodeInfo root) { return traverseForPattern(root, "#"); }

    private String traverseForPattern(AccessibilityNodeInfo node, String pattern) {
        if (node == null) return null;
        CharSequence text = node.getText();
        if (text != null && text.toString().contains(pattern)) {
            String t = text.toString(); int idx = t.indexOf("#");
            if (idx >= 0) { StringBuilder sb = new StringBuilder("#"); for (int i = idx+1; i < t.length() && Character.isDigit(t.charAt(i)); i++) sb.append(t.charAt(i)); if (sb.length() > 1) return sb.toString(); }
        }
        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo c = node.getChild(i); String r = traverseForPattern(c, pattern); if (r != null) return r; if (c != null) c.recycle(); }
        return null;
    }

    private AccessibilityNodeInfo findClickableContaining(AccessibilityNodeInfo root, String text) {
        if (root == null) return null;
        CharSequence t = root.getText();
        if (t != null && t.toString().contains(text) && root.isClickable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) { AccessibilityNodeInfo c = root.getChild(i); AccessibilityNodeInfo r = findClickableContaining(c, text); if (r != null) return r; if (c != null) c.recycle(); }
        return null;
    }

    @Override public void onInterrupt() { currentState = State.IDLE; }
}
