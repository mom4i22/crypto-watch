package com.f119589.ui.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.data.entity.FavouritePair;
import com.f119589.ui.util.SparklineBinder;
import com.f119589.ui.util.SparklineParser;
import com.github.mikephil.charting.charts.LineChart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FavouritesAdapter extends RecyclerView.Adapter<FavouritesAdapter.VH> {
    private static final String PAYLOAD_PRICE_ONLY = "price_only";

    public interface OnFavoriteClick {
        void onOpenDetails(FavouritePair e);

        void onRemove(FavouritePair e);
    }

    private final OnFavoriteClick listener;
    private final List<FavouritePair> items = new ArrayList<>();

    public FavouritesAdapter(OnFavoriteClick listener) {
        this.listener = listener;
    }

    public void submit(List<FavouritePair> list) {
        List<FavouritePair> oldItems = new ArrayList<>(items);
        List<FavouritePair> newItems = list != null ? new ArrayList<>(list) : new ArrayList<>();
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return newItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).getSymbol()
                        .equals(newItems.get(newItemPosition).getSymbol());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).equals(newItems.get(newItemPosition));
            }
        });
        items.clear();
        items.addAll(newItems);
        diff.dispatchUpdatesTo(this);
    }

    /**
     * Optional real-time visual nudge before Room emits the updated row.
     */
    public void pushLiveTick(String wsSymbol, double price) {
        for (int i = 0; i < items.size(); i++) {
            FavouritePair e = items.get(i);
            if (e.getSymbol().equals(wsSymbol)) {
                e.setLastPrice(price);
                Double baseline = e.getOhlc24hFirstClose();
                if (baseline != null && baseline != 0d) {
                    double change = ((price - baseline) / baseline) * 100.0;
                    e.setChange24hPercent(change);
                }
                notifyItemChanged(i, PAYLOAD_PRICE_ONLY);
                break;
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.row_favourite, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        FavouritePair e = items.get(pos);
        h.bindFull(e, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_PRICE_ONLY)) {
            FavouritePair e = items.get(pos);
            h.bindPriceOnly(e);
            return; // skip full bind
        }
        super.onBindViewHolder(h, pos, payloads);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final TextView txtName;
        private final TextView txtSub;
        private final TextView txtPrice;
        private final TextView txtChange;
        private final TextView txtLowHigh;
        private final TextView txtBadge;
        private final LineChart chart;
        private final ImageButton btnRemove;

        VH(@NonNull View v) {
            super(v);
            txtName = v.findViewById(R.id.txtName);
            txtSub = v.findViewById(R.id.txtSub);
            txtPrice = v.findViewById(R.id.txtPrice);
            txtChange = v.findViewById(R.id.txtChange);
            txtLowHigh = v.findViewById(R.id.txtLowHigh);
            txtBadge = v.findViewById(R.id.txtBadge);
            chart = v.findViewById(R.id.sparkline);
            btnRemove = v.findViewById(R.id.btnRemove);
        }

        void bindFull(FavouritePair e, OnFavoriteClick listener) {
            String name = e.getDisplayName();
            txtName.setText(name != null ? name : e.getSymbol());
            txtSub.setText(e.getSymbol());
            bindPriceOnly(e);
            txtBadge.setText(buildBadge(e));
            SparklineParser.ParseResult parsed = SparklineParser.parseWithMinMax(e.getOhlc24hJson());
            txtLowHigh.setText(buildLowHigh(parsed.getMinMax()));
            SparklineBinder.bind(chart, parsed.getEntries());
            btnRemove.setOnClickListener(v -> listener.onRemove(e));
            itemView.setOnClickListener(v -> listener.onOpenDetails(e));
        }

        void bindPriceOnly(FavouritePair e) {
            txtPrice.setText(e.getLastPrice() > 0 ? String.valueOf(e.getLastPrice()) : "—");
            bindChange(txtChange, e.getChange24hPercent());
        }
    }

    private static void bindChange(TextView view, Double change) {
        if (change == null) {
            view.setText("—");
            view.setTextColor(Color.GRAY);
            return;
        }
        String formatted = String.format(Locale.US, "%+.2f%%", change);
        view.setText(formatted);
        int color = change > 0 ? 0xFF2E7D32 : (change < 0 ? 0xFFC62828 : Color.GRAY);
        view.setTextColor(color);
    }

    private static String buildBadge(FavouritePair pair) {
        String display = pair.getDisplayName();
        if (display == null || display.isEmpty()) {
            display = pair.getSymbol();
        }
        if (display.isEmpty()) {
            return "—";
        }
        int slash = display.indexOf('/');
        if (slash > 0) {
            display = display.substring(0, slash);
        }
        display = display.trim();
        if (display.length() > 4) {
            display = display.substring(0, 4);
        }
        return display.toUpperCase(Locale.US);
    }

    private static String buildLowHigh(SparklineParser.MinMax mm) {
        String low = formatPrice(mm.getLow());
        String high = formatPrice(mm.getHigh());
        return String.format(Locale.US, "24h L: %s  H: %s", low, high);
    }

    private static String formatPrice(Double v) {
        if (v == null) return "—";
        double abs = Math.abs(v);
        if (abs >= 1_000_000d) {
            return String.format(Locale.US, "%.2fM", v / 1_000_000d);
        }
        if (abs >= 1_000d) {
            return String.format(Locale.US, "%.2fk", v / 1_000d);
        }
        if (abs >= 1d) {
            return String.format(Locale.US, "%.2f", v);
        }
        if (abs >= 0.01d) {
            return String.format(Locale.US, "%.4f", v);
        }
        return String.format(Locale.US, "%.6f", v);
    }
}
