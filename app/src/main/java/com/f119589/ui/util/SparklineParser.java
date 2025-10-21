package com.f119589.ui.util;

import android.util.Log;

import com.github.mikephil.charting.data.Entry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a compact JSON string like: [[tsSec, close], [tsSec, close], ...]
 * into MPAndroidChart Entry list. Uses the INDEX as X for simplicity.
 */
public class SparklineParser {

    private static final String TAG = "SparklineParser";

    public static List<Entry> parse(String compactJson) {
        List<Entry> out = new ArrayList<>();
        if (compactJson == null || compactJson.isEmpty()) return out;

        try {
            JsonElement root = JsonParser.parseString(compactJson);
            if (!root.isJsonArray()) return out;

            JsonArray arr = root.getAsJsonArray();

            // Optional: downsample if very long (keep ~150 points for smooth UI)
            int maxPoints = 150;
            int step = Math.max(1, arr.size() / maxPoints);

            int i = 0;
            for (int idx = 0; idx < arr.size(); idx += step) {
                JsonArray row = arr.get(idx).getAsJsonArray();
                // long t = row.get(0).getAsLong(); // not used for X (we use index)
                float close = (float) row.get(1).getAsDouble();
                out.add(new Entry(i++, close));
            }
        } catch (Exception ex) {
            Log.w(TAG, "Failed to parse sparkline json", ex);
        }
        return out;
    }
}
