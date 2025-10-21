package com.f119589.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.data.entity.FavouritePair;
import com.f119589.ui.util.SparklineBinder;
import com.github.mikephil.charting.charts.LineChart;

import java.util.ArrayList;
import java.util.List;

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
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    /**
     * Optional real-time visual nudge before Room emits the updated row.
     */
    public void pushLiveTick(String wsSymbol, double price) {
        for (int i = 0; i < items.size(); i++) {
            FavouritePair e = items.get(i);
            if (e.symbol.equals(wsSymbol)) {
                e.lastPrice = price;
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
        h.txtName.setText(e.displayName != null ? e.displayName : e.symbol);
        h.txtSub.setText(e.symbol);
        h.txtPrice.setText(e.lastPrice > 0 ? String.valueOf(e.lastPrice) : "—");

        SparklineBinder.bind(h.chart, e.ohlc24hJson);

        h.btnRemove.setOnClickListener(v -> listener.onRemove(e));
        h.itemView.setOnClickListener(v -> listener.onOpenDetails(e));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty() && payloads.contains("price_only")) {
            FavouritePair e = items.get(pos);
            h.txtPrice.setText(e.lastPrice > 0 ? String.valueOf(e.lastPrice) : "—");
            return; // skip full bind
        }
        super.onBindViewHolder(h, pos, payloads);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView txtName, txtSub, txtPrice;
        LineChart chart;
        ImageButton btnRemove;

        VH(@NonNull View v) {
            super(v);
            txtName = v.findViewById(R.id.txtName);
            txtSub = v.findViewById(R.id.txtSub);
            txtPrice = v.findViewById(R.id.txtPrice);
            chart = v.findViewById(R.id.sparkline);
            btnRemove = v.findViewById(R.id.btnRemove);
        }
    }
}
