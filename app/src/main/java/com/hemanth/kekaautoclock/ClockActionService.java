package com.hemanth.kekaautoclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

/** Runs the actual Keka clock-in/out click script. A foreground SERVICE, not an
 *  Activity: Android 10+ blocks starting Activities from the background (from a
 *  BroadcastReceiver or a WorkManager Worker, both of which trigger this), which
 *  previously meant the real geofence-triggered clock action could silently never
 *  launch at all. A WebView works perfectly well headless (never attached to a
 *  window) inside a Service, and startForegroundService() IS reliably callable
 *  from the background - this is the Android-sanctioned path. */
public class ClockActionService extends Service {

    public static final String EXTRA_MODE = "mode"; // "in" or "out"
    private static final String CHANNEL_ID = "keka_auto_clock";
    private static final int FOREGROUND_NOTIFICATION_ID = 9001;
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 5000;
    private static final long SAFETY_TIMEOUT_MS = 45000;

    private WebView webView;
    private EventLog eventLog;
    private SettingsStore settings;
    private String mode = "in";
    private int retriesLeft = MAX_RETRIES;
    private boolean finished = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // not a bound service
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mode = (intent != null && intent.getStringExtra(EXTRA_MODE) != null)
                ? intent.getStringExtra(EXTRA_MODE) : "in";

        eventLog = new EventLog(this);
        settings = new SettingsStore(this);

        createNotificationChannel();
        // Must call startForeground() within a few seconds of onStartCommand or the
        // system kills the service - do it immediately, before any WebView work.
        startForeground(FOREGROUND_NOTIFICATION_ID, buildNotification(
                (mode.equals("in") ? "Clocking in..." : "Clocking out...")));

        setUpWebViewAndRun();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!finished) finishWithResult("timed out waiting for page");
        }, SAFETY_TIMEOUT_MS);

        return START_NOT_STICKY;
    }

    private void setUpWebViewAndRun() {
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true); // needed to keep the login session
        webView.addJavascriptInterface(new JsBridge(), "AndroidBridge");

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (finished) return;
                    String wrapped = "window.AndroidBridge.onResult((" + SettingsStore.clickScript(mode)
                            + "), document.title, '" + url.replace("'", "") + "')";
                    view.evaluateJavascript(wrapped, null);
                }, 3000);
            }
        });

        webView.loadUrl(SettingsStore.KEKA_URL);
    }

    private class JsBridge {
        @JavascriptInterface
        public void onResult(String result, String pageTitle, String pageUrl) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finished) return;
                boolean isFailure = "no-container".equals(result) || "no-op".equals(result);
                if (isFailure && retriesLeft > 0) {
                    retriesLeft--;
                    String retryMsg = (mode.equals("in") ? "Clock-in" : "Clock-out") + ": " + result
                            + " - retrying in " + (RETRY_DELAY_MS / 1000) + "s (" + retriesLeft + " left)";
                    eventLog.log(retryMsg);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!finished) webView.loadUrl(SettingsStore.KEKA_URL);
                    }, RETRY_DELAY_MS);
                    return;
                }

                String detail = result;
                if ("no-container".equals(result)) {
                    detail += " (page title='" + pageTitle + "', url=" + pageUrl + ")";
                }
                finishWithResult(detail);
            });
        }
    }

    private void finishWithResult(String detail) {
        if (finished) return;
        finished = true;
        String label = mode.equals("in") ? "Clock-in" : "Clock-out";
        String msg = label + ": " + detail;
        eventLog.log(msg);
        settings.setLastEvent(msg);
        showResultNotification(msg);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        stopForeground(true);
        stopSelf();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Keka Auto Clock", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Keka Auto Clock")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void showResultNotification(String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Keka Auto Clock")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify((int) System.currentTimeMillis(), builder.build());
    }
}
