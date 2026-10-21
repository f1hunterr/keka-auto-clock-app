package com.hemanth.kekaautoclock;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

public class LogViewerActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EventLog eventLog = new EventLog(this);
        List<String> lines = eventLog.readAll();

        TextView tv = new TextView(this);
        tv.setPadding(30, 30, 30, 30);
        tv.setTextIsSelectable(true);
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
        scrollView.addView(tv);
        setContentView(scrollView);
        setTitle("Keka Auto Clock - Event Log");
    }
}
