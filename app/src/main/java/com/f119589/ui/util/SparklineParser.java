package com.f119589.ui.util;

import android.util.Log;

import com.github.mikephil.charting.data.Entry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import lombok.Getter;

/**
 * Parses a compact JSON string like: [[tsSec, close], [tsSec, close], ...]
 * into MPAndroidChart Entry list. Uses the INDEX as X for simplicity.
 */
public final class SparklineParser {

    private SparklineParser() {
    }

    private static final String TAG = "SparklineParser";

    @Getter
    public static final class MinMax {
        private final Double low;
        private final Double high;

        private MinMax(Double low, Double high) {
            this.low = low;
            this.high = high;
        }

    }

    @Getter
    public static final class ParseResult {
        private final List<Entry> entries;
        private final MinMax minMax;

        private ParseResult(List<Entry> entries, MinMax minMax) {
            List<Entry> safeEntries = entries == null ? new ArrayList<>() : entries;
            this.entries = Collections.unmodifiableList(safeEntries);
            this.minMax = minMax == null ? new MinMax(null, null) : minMax;
        }

    }

    public static List<Entry> parse(String compactJson) {
        return parseWithMinMax(compactJson).getEntries();
    }

    public static ParseResult parseWithMinMax(String compactJson) {
        if (compactJson == null || compactJson.isEmpty()) {
            return new ParseResult(new ArrayList<>(), new MinMax(null, null));
        }
        try {
            JsonArray arr = asArray(JsonParser.parseString(compactJson));
            if (arr == null) {
                return new ParseResult(new ArrayList<>(), new MinMax(null, null));
            }

            // Optional: downsample if very long (keep ~150 points for smooth UI)
            int maxPoints = 150;
            int step = Math.max(1, arr.size() / maxPoints);

            List<Entry> out = new ArrayList<>();
            int i = 0;
            Double min = null;
            Double max = null;
            for (int idx = 0; idx < arr.size(); idx += step) {
                JsonArray row = asArray(arr.get(idx));
                if (row == null || row.size() < 2 || !row.get(1).isJsonPrimitive()) continue;
                double v = row.get(1).getAsDouble();
                if (min == null || v < min) min = v;
                if (max == null || v > max) max = v;
                out.add(new Entry(i++, (float) v));
            }
            return new ParseResult(out, new MinMax(min, max));
        } catch (Exception ex) {
            Log.w(TAG, "Failed to parse sparkline json", ex);
            return new ParseResult(new ArrayList<>(), new MinMax(null, null));
        }
    }

    private static JsonArray asArray(JsonElement el) {
        return el != null && el.isJsonArray() ? el.getAsJsonArray() : null;
    }
}
