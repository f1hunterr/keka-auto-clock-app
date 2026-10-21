package com.hemanth.kekaautoclock;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.NotificationCompat;

public class ClockActionActivity extends Activity {

    public static final String EXTRA_MODE = "mode"; // "in" or "out"
    private static final String CHANNEL_ID = "keka_auto_clock";

    private WebView webView;
    private EventLog eventLog;
    private SettingsStore settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        eventLog = new EventLog(this);
        settings = new SettingsStore(this);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = "in";
        final String finalMode = mode;

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true); // needed to keep the login session
        webView.addJavascriptInterface(new JsBridge(finalMode), "AndroidBridge");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Give the Angular SPA a moment to mount the attendance button,
                // then run the click script and report the result back via JsBridge.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    String wrapped = "window.AndroidBridge.onResult((" + SettingsStore.clickScript(finalMode)
                            + "), document.title, '" + url.replace("'", "") + "')";
                    view.evaluateJavascript(wrapped, null);
                }, 3000);
            }
        });

        setContentView(webView);
        webView.loadUrl(SettingsStore.KEKA_URL);

        // Safety timeout: if nothing reports back within 20s (e.g. page never loaded,
        // no network), finish anyway so the invisible activity doesn't linger.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            String msg = "Keka " + finalMode + ": timed out waiting for page";
            showNotification(msg);
            eventLog.log(msg);
            settings.setLastEvent(msg);
            finish();
        }, 20000);
    }

    private class JsBridge {
        private final String mode;
        JsBridge(String mode) { this.mode = mode; }

        @JavascriptInterface
        public void onResult(String result, String pageTitle, String pageUrl) {
            runOnUiThread(() -> {
                String label = mode.equals("in") ? "Clock-in" : "Clock-out";
                String msg = label + ": " + result;
                if ("no-container".equals(result)) {
                    // self-diagnosing: tells us WHICH page we actually landed on
                    // (e.g. "Log in" means the session didn't carry over - a cookie problem,
                    // not a click-logic problem)
                    msg += " (page title='" + pageTitle + "', url=" + pageUrl + ")";
                }
                showNotification(msg);
                eventLog.log(msg);
                settings.setLastEvent(msg);
                finish();
            });
        }
    }

    private void showNotification(String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Keka Auto Clock", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Keka Auto Clock")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((int) System.currentTimeMillis(), builder.build());
    }
}
