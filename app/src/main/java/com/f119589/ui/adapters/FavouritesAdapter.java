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
import com.github.mikephil.charting.charts.LineChart;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FavouritesAdapter extends RecyclerView.Adapter<FavouritesAdapter.VH> {

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
                notifyItemChanged(i, "price_only");
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
        h.txtName.setText(e.getDisplayName() != null ? e.getDisplayName() : e.getSymbol());
        h.txtSub.setText(e.getSymbol());
        h.txtPrice.setText(e.getLastPrice() > 0 ? String.valueOf(e.getLastPrice()) : "—");
        bindChange(h.txtChange, e.getChange24hPercent());

        SparklineBinder.bind(h.chart, e.getOhlc24hJson());

        h.btnRemove.setOnClickListener(v -> listener.onRemove(e));
        h.itemView.setOnClickListener(v -> listener.onOpenDetails(e));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("price_only")) {
            FavouritePair e = items.get(pos);
            h.txtPrice.setText(e.getLastPrice() > 0 ? String.valueOf(e.getLastPrice()) : "—");
            bindChange(h.txtChange, e.getChange24hPercent());
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
        private final LineChart chart;
        private final ImageButton btnRemove;

        VH(@NonNull View v) {
            super(v);
            txtName = v.findViewById(R.id.txtName);
            txtSub = v.findViewById(R.id.txtSub);
            txtPrice = v.findViewById(R.id.txtPrice);
            txtChange = v.findViewById(R.id.txtChange);
            chart = v.findViewById(R.id.sparkline);
            btnRemove = v.findViewById(R.id.btnRemove);
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
}
