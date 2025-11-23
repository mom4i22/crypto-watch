package com.f119589.ui.util;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;

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
        // Parse entries
        List<Entry> entries = SparklineParser.parse(compactJson);

        // Basic, once-only view config
        if (chart.getData() == null) {
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

        LineDataSet set;
        if (chart.getData() != null && chart.getData().getDataSetCount() > 0) {
            set = (LineDataSet) chart.getData().getDataSetByIndex(0);
            set.setValues(entries);
            chart.getData().notifyDataChanged();
            chart.notifyDataSetChanged();
        } else {
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

            // Subtle gradient fill under the line
            set.setDrawFilled(true);
            // Create a simple vertical gradient based on base color
            Paint p = chart.getRenderer().getPaintRender();
            int top = (accent & 0x00FFFFFF) | 0x22000000;   // low alpha
            int bottom = (accent & 0x00FFFFFF); // transparent
            LinearGradient lg = new LinearGradient(0, 0, 0, chart.getHeight(),
                    top, bottom, Shader.TileMode.CLAMP);
            set.setFillAlpha(120);
            set.setFillDrawable(null); // not using drawable; fallback to paint's shader
            // MPAndroidChart uses setFillColor / setFillDrawable, but we can simply:
            set.setFillColor(top);

            LineData data = new LineData(set);
            chart.setData(data);
        }

        chart.invalidate();
    }
}
