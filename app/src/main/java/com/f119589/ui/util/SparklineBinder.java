package com.f119589.ui.util;

import android.graphics.Color;

import androidx.core.content.ContextCompat;

import com.f119589.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.List;

/**
 * Configures a LineChart to look like a minimalist sparkline and binds data.
 */
public final class SparklineBinder {

    private SparklineBinder() {
        // Utility class - prevent instantiation
    }

    public static void bind(LineChart chart, String compactJson) {
        bind(chart, SparklineParser.parse(compactJson));
    }

    public static void bind(LineChart chart, List<Entry> entries) {

        // Basic, once-only view config
        LineData data = chart.getData();
        if (data == null) {
            chart.setTouchEnabled(false);
            chart.setDragEnabled(false);
            chart.setScaleEnabled(false);
            chart.setPinchZoom(false);
            chart.setDoubleTapToZoomEnabled(false);

            chart.getLegend().setEnabled(false);
            Description d = new Description();
            d.setText("");
            chart.setDescription(d);

            XAxis x = chart.getXAxis();
            x.setEnabled(false);

            YAxis yl = chart.getAxisLeft();
            yl.setEnabled(false);
            YAxis yr = chart.getAxisRight();
            yr.setEnabled(false);

            chart.setViewPortOffsets(0f, 0f, 0f, 0f); // edge-to-edge
            chart.setBackgroundColor(Color.TRANSPARENT);
            chart.setNoDataText("");
        }

        LineDataSet set = null;
        data = chart.getData();
        if (data != null && data.getDataSetCount() > 0) {
            set = (LineDataSet) data.getDataSetByIndex(0);
        }
        if (set != null) {
            set.setValues(entries);
            data.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.invalidate();
            return;
        }

        set = new LineDataSet(entries, "");
        set.setDrawValues(false);
        set.setDrawCircles(false);
        set.setLineWidth(1.8f);
        set.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        set.setCubicIntensity(0.2f);
        set.setHighLightColor(0); // disable highlight line
        set.setDrawHorizontalHighlightIndicator(false);
        set.setDrawVerticalHighlightIndicator(false);

        int accent = ContextCompat.getColor(chart.getContext(), R.color.sparkline_primary);
        set.setColor(accent);

        // Subtle fill under the line
        set.setDrawFilled(true);
        int top = (accent & 0x00FFFFFF) | 0x22000000;   // low alpha
        set.setFillAlpha(120);
        set.setFillColor(top);

        chart.setData(new LineData(set));
        chart.invalidate();
    }
}
