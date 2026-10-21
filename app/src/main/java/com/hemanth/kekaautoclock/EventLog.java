package com.hemanth.kekaautoclock;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** Simple append-only event log, capped at MAX_LINES, newest-last on disk
 *  (readAll() returns newest-first for display). No adb/USB needed to see
 *  what the background triggers actually did. */
public final class EventLog {
    private static final String FILE_NAME = "keka_events.log";
    private static final int MAX_LINES = 200;

    private final File file;

    public EventLog(Context context) {
        file = new File(context.getFilesDir(), FILE_NAME);
    }

    public synchronized void log(String message) {
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String line = ts + " | " + message;

        List<String> lines = readRaw();
        lines.add(line);
        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }

        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            StringBuilder sb = new StringBuilder();
            for (String l : lines) {
                sb.append(l).append('\n');
            }
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    /** Newest-first, for display. */
    public List<String> readAll() {
        List<String> lines = readRaw();
        Collections.reverse(lines);
        return lines;
    }

    private List<String> readRaw() {
        List<String> lines = new ArrayList<>();
        if (!file.exists()) return lines;
        try (Scanner scanner = new Scanner(file, StandardCharsets.UTF_8)) {
            while (scanner.hasNextLine()) {
                lines.add(scanner.nextLine());
            }
        } catch (IOException ignored) {
        }
        return lines;
    }
}
