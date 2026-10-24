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
    private static final int MAX_RETRIES = 2; // bumped from 1: 'clicked-not-confirmed' is now a
                                                // real, meaningful failure signal (click fired but
                                                // Keka's state didn't change) worth retrying harder
    private static final long RETRY_DELAY_MS = 3000;
    private static final long SAFETY_TIMEOUT_MS = 80000; // accounts for the added 2.5s post-click
                                                           // verification wait per attempt

    private WebView webView;
    private EventLog eventLog;
    private SettingsStore settings;
    private String mode = "in";
    private int retriesLeft = MAX_RETRIES;
    private boolean finished = false;
    private boolean busy = false;
    private boolean scriptEvaluated = false;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // not a bound service
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String requestedMode = (intent != null && intent.getStringExtra(EXTRA_MODE) != null)
                ? intent.getStringExtra(EXTRA_MODE) : "in";

        // This Service is a single shared instance across calls - if a second trigger
        // arrives while the first is still running (confirmed via real-device testing:
        // a manual test overlapped with a queued confirm-delay firing and the result
        // came back as the WRONG mode because the shared 'mode' field got overwritten
        // mid-flight), ignore the new one instead of corrupting the in-progress request.
        // A genuine wrong clock-in/out is worse than a skipped duplicate trigger.
        if (busy) {
            new EventLog(this).log("ClockActionService busy (mode=" + mode
                    + " in progress) - ignoring overlapping request for mode=" + requestedMode);
            return START_NOT_STICKY;
        }
        busy = true;
        mode = requestedMode;

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

        // Lets us inspect this exact WebView live via chrome://inspect over USB -
        // needed to actually see the rendered DOM (incl. any Shadow DOM) instead
        // of guessing why a selector isn't matching.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (finished) return;
                // Guard against onPageFinished firing more than once for the SAME
                // navigation (a real, confirmed-via-production-log WebView quirk with
                // SPAs - saw the click script run 2-3 times for one trigger, resulting
                // in duplicate 'done' log entries). Only inject once per page load;
                // reset when we deliberately reload for a retry.
                if (scriptEvaluated) return;
                scriptEvaluated = true;
                // The script self-polls for up to 15s and calls AndroidBridge.onResult()
                // itself once it resolves - no fixed Java-side delay needed anymore.
                view.evaluateJavascript(SettingsStore.clickScript(mode), null);
            }
        });

        webView.loadUrl(SettingsStore.KEKA_URL);
    }

    private class JsBridge {
        @JavascriptInterface
        public void onResult(String result, String pageTitle, String pageUrl) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (finished) return;
                boolean isFailure = result.startsWith("no-container") || result.startsWith("no-op")
                        || result.startsWith("clicked-not-confirmed");
                if (isFailure && retriesLeft > 0) {
                    retriesLeft--;
                    String retryMsg = (mode.equals("in") ? "Clock-in" : "Clock-out") + ": " + result
                            + " - retrying in " + (RETRY_DELAY_MS / 1000) + "s (" + retriesLeft + " left)";
                    eventLog.log(retryMsg);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!finished) {
                            scriptEvaluated = false;
                            webView.loadUrl(SettingsStore.KEKA_URL);
                        }
                    }, RETRY_DELAY_MS);
                    return;
                }

                String detail = result;
                if (result.startsWith("no-container")) {
                    detail += " (page title='" + pageTitle + "', url=" + pageUrl + ")";
                }
                finishWithResult(detail);
            });
        }
    }

    private void finishWithResult(String detail) {
        if (finished) return;
        finished = true;
        busy = false;
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
