package com.hemanth.kekaautoclock;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/** Plain, visible WebView so the user can log into Keka once. The session
 *  cookie this creates is shared (via Android's WebView CookieManager) with
 *  the invisible ClockActionActivity used for the real geofence-triggered
 *  clock-in/out, so logging in here is all that's needed, one time. */
public class LoginActivity extends Activity {

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("attendance/logs")) {
                    Toast.makeText(LoginActivity.this,
                            "Logged in - you can close this screen now",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        setContentView(webView);
        webView.loadUrl(Config.KEKA_URL);
    }

    @Override
    public void onBackPressed() {
        // Let the WebView navigate back through its own history first (e.g. login -> OTP step)
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
