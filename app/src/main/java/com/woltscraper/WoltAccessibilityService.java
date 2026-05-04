package com.woltscraper;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * WoltAccessibilityService - Calibrated from real screenshots
 *
 * Flow (based on actual Wolt Merchant Lite UI):
 * - Orders appear in "In progress" tab (workers accept manually)
 * - Each order card shows: #001  Kevin C.
 * - Tap card -> bottom sheet: "Order #001" + [Kevin C.] [Call button]
 * - Tap Call -> "Call customer" sheet with phone number
 * - Read phone -> send to Google Sheets
 */
public class WoltAccessibilityService extends AccessibilityService {

    private static final String TAG = "WoltScraper";

    private enum State {
        IDLE,
        WAITING_FOR_ORDER_SHEET,
        WAITING_FOR_CALL_SHEET,
        DONE
    }

    private State currentState = State.IDLE;
    private String currentOrderNumber = "";
    private String currentCustomerName = "";
    private String currentPhoneNumber = "";
    private String lastProcessedOrder = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        switch (currentState) {
            case IDLE:
                checkForNewOrder(root);
                break;
            case WAITING_FOR_ORDER_SHEET:
                tryReadOrderSheet(root);
                break;
            case WAITING_FOR_CALL_SHEET:
                tryReadCallSheet(root);
                break;
        }
        root.recycle();
    }

    /**
     * Step 1: Detect new order in "In progress" tab
     * Orders appear as cards with format: #001  Kevin C.
     * We look for order number pattern #XXX that we haven't processed yet
     */
    private void checkForNewOrder(AccessibilityNodeInfo root) {
        String orderNum = findOrderNumber(root);
        if (orderNum == null || orderNum.equals(lastProcessedOrder)) return;

        Log.d(TAG, "New order detected: " + orderNum);
        currentOrderNumber = orderNum;
        currentState = State.WAITING_FOR_ORDER_SHEET;
        handler.postDelayed(() -> tapOrderCard(orderNum), 800);
    }

    /**
     * Step 2: Tap the order card to open bottom sheet
     */
    private void tapOrderCard(String orderNum) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        AccessibilityNodeInfo card = findClickableContaining(root, orderNum);
        if (card != null) {
            card.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "Tapped order: " + orderNum);
        }
        root.recycle();
    }

    /**
     * Step 3: Read order sheet
     * Bottom sheet shows: "Order #001" title + [Kevin C.] [Call] button
     */
    private void tryReadOrderSheet(AccessibilityNodeInfo root) {
        String titleText = findTextStartingWith(root, "Order #");
        if (titleText != null) {
            currentOrderNumber = titleText.replace("Order ", "").trim();
        }

        String name = findCustomerNameNearCallButton(root);
        if (name != null && !name.isEmpty()) {
            currentCustomerName = name;
            Log.d(TAG, "Customer: " + name);
            currentState = State.WAITING_FOR_CALL_SHEET;
            handler.postDelayed(this::tapCallButton, 600);
        }
    }

    /**
     * Step 4: Tap "Call" button
     */
    private void tapCallButton() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        List<AccessibilityNodeInfo> callNodes = root.findAccessibilityNodeInfosByText("Call");
        if (callNodes != null) {
            for (AccessibilityNodeInfo node : callNodes) {
                if (node.isClickable()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    root.recycle();
                    return;
                }
                AccessibilityNodeInfo p = node.getParent();
                while (p != null) {
                    if (p.isClickable()) {
                        p.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        root.recycle();
                        return;
                    }
                    AccessibilityNodeInfo gp = p.getParent();
                    p.recycle();
                    p = gp;
                }
            }
        }
        root.recycle();
    }

    /**
     * Step 5: Read phone from "Call customer" sheet
     * Sheet shows: "Call customer" title + name + phone (+17147142820)
     */
    private void tryReadCallSheet(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> titles = root.findAccessibilityNodeInfosByText("Call customer");
        if (titles == null || titles.isEmpty()) return;

        String phone = findPhoneNumber(root);
        if (phone != null && !phone.isEmpty()) {
            currentPhoneNumber = phone;
            Log.d(TAG, "Phone: " + phone);
            lastProcessedOrder = currentOrderNumber;
            closeSheet(root);
            currentState = State.DONE;
            sendToSheets();
            handler.postDelayed(() -> currentState = State.IDLE, 3000);
        }
    }

    private void closeSheet(AccessibilityNodeInfo root) {
        AccessibilityNodeInfo btn = findNodeByContentDescription(root, "Close");
        if (btn == null) btn = findNodeByContentDescription(root, "Dismiss");
        if (btn != null) btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        else performGlobalAction(GLOBAL_ACTION_BACK);
    }

    private void sendToSheets() {
        final String o = currentOrderNumber, n = currentCustomerName, p = currentPhoneNumber;
        Log.d(TAG, "Sending: " + o + " | " + n + " | " + p);
        new Thread(() -> {
            boolean ok = GoogleSheetsManager.getInstance(getApplicationContext()).appendRow(o, n, p);
            handler.post(() -> {
                if (ok) NotificationHelper.showSuccess(getApplicationContext(), o + " — " + n + " saved");
                else NotificationHelper.showError(getApplicationContext(), "Failed: " + o);
            });
        }).start();
    }

    // Find order number pattern #001, #002 etc
    private String findOrderNumber(AccessibilityNodeInfo root) {
        return traverseFind(root, node -> {
            CharSequence t = node.getText();
            return (t != null && t.toString().matches("#\\d+")) ? t.toString() : null;
        });
    }

    private String findTextStartingWith(AccessibilityNodeInfo root, String prefix) {
        return traverseFind(root, node -> {
            CharSequence t = node.getText();
            return (t != null && t.toString().startsWith(prefix)) ? t.toString() : null;
        });
    }

    private String findCustomerNameNearCallButton(AccessibilityNodeInfo root) {
        return traverseFind(root, node -> {
            CharSequence t = node.getText();
            if (t != null && t.toString().equals("Call") && node.isClickable()) {
                AccessibilityNodeInfo parent = node.getParent();
                if (parent != null) {
                    AccessibilityNodeInfo gp = parent.getParent();
                    if (gp != null) {
                        for (int i = 0; i < gp.getChildCount(); i++) {
                            AccessibilityNodeInfo sib = gp.getChild(i);
                            if (sib != null) {
                                String name = findNameInSubtree(sib);
                                if (name != null) return name;
                                sib.recycle();
                            }
                        }
                    }
                }
            }
            return null;
        });
    }

    private String findNameInSubtree(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence t = node.getText();
        if (t != null) {
            String s = t.toString().trim();
            if (!s.isEmpty() && !s.equals("Call") && !s.equals("Call customer")
                    && !s.startsWith("+") && !s.startsWith("#")
                    && s.length() > 1 && s.length() < 60
                    && Character.isLetter(s.charAt(0)))
                return s;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            String r = findNameInSubtree(c);
            if (r != null) return r;
            if (c != null) c.recycle();
        }
        return null;
    }

    private String findPhoneNumber(AccessibilityNodeInfo root) {
        return traverseFind(root, node -> {
            CharSequence t = node.getText();
            if (t != null) {
                String s = t.toString().trim();
                if (s.matches("\\+?[\\d]{7,15}") || s.matches("\\+[\\d\\s\\-]{8,20}"))
                    return s.replaceAll("[\\s\\-]", "");
            }
            return null;
        });
    }

    private AccessibilityNodeInfo findClickableContaining(AccessibilityNodeInfo root, String text) {
        if (root == null) return null;
        CharSequence t = root.getText();
        if (t != null && t.toString().contains(text) && root.isClickable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo c = root.getChild(i);
            AccessibilityNodeInfo r = findClickableContaining(c, text);
            if (r != null) return r;
            if (c != null) c.recycle();
        }
        return null;
    }

    private AccessibilityNodeInfo findNodeByContentDescription(AccessibilityNodeInfo root, String desc) {
        if (root == null) return null;
        CharSequence cd = root.getContentDescription();
        if (cd != null && cd.toString().equalsIgnoreCase(desc) && root.isClickable()) return root;
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo c = root.getChild(i);
            AccessibilityNodeInfo r = findNodeByContentDescription(c, desc);
            if (r != null) return r;
            if (c != null) c.recycle();
        }
        return null;
    }

    interface NodeMatcher { String match(AccessibilityNodeInfo node); }

    private String traverseFind(AccessibilityNodeInfo node, NodeMatcher matcher) {
        if (node == null) return null;
        String r = matcher.match(node);
        if (r != null) return r;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo c = node.getChild(i);
            String res = traverseFind(c, matcher);
            if (res != null) return res;
            if (c != null) c.recycle();
        }
        return null;
    }

    @Override public void onInterrupt() { currentState = State.IDLE; }
}
