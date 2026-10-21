package com.hemanth.kekaautoclock;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.NotificationCompat;

public class ClockActionActivity extends Activity {

    public static final String EXTRA_MODE = "mode"; // "in" or "out"
    private static final String CHANNEL_ID = "keka_auto_clock";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = "in";
        final String finalMode = mode;

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true); // needed to keep the login session
        webView.addJavascriptInterface(new JsBridge(finalMode), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Give the Angular SPA a moment to mount the attendance button,
                // then run the click script and report the result back via JsBridge.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    String wrapped = "window.AndroidBridge.onResult((" + Config.clickScript(finalMode) + "))";
                    view.evaluateJavascript(wrapped, null);
                }, 3000);
            }
        });

        setContentView(webView);
        webView.loadUrl(Config.KEKA_URL);

        // Safety timeout: if nothing reports back within 20s (e.g. page never loaded,
        // no network), finish anyway so the invisible activity doesn't linger.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            showNotification("Keka " + finalMode + ": timed out waiting for page");
            finish();
        }, 20000);
    }

    private class JsBridge {
        private final String mode;
        JsBridge(String mode) { this.mode = mode; }

        @JavascriptInterface
        public void onResult(String result) {
            runOnUiThread(() -> {
                String label = mode.equals("in") ? "Clock-in" : "Clock-out";
                showNotification(label + ": " + result);
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
