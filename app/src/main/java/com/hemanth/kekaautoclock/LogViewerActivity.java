package com.hemanth.kekaautoclock;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;

public class LogViewerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EventLog eventLog = new EventLog(this);
        List<String> lines = eventLog.readAll();

        int dp16 = dp(16);

        TextView tv = new TextView(this);
        tv.setPadding(dp16, dp16, dp16, dp16);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(13);
        tv.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        if (lines.isEmpty()) {
            tv.setText("No events logged yet.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append("\n\n");
            }
            tv.setText(sb.toString());
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_dark));
        scrollView.addView(tv);
        setContentView(scrollView);
        setTitle("Keka Auto Clock - Event Log");
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
