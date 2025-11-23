package com.f119589.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.dto.AssetPairDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketsAdapter extends RecyclerView.Adapter<MarketsAdapter.VH> {

    public interface OnMarketClick {
        void onAddToFavorites(AssetPairDto pair);

        void onOpenDetails(AssetPairDto pair);
    }

    private final OnMarketClick listener;
    private final List<AssetPairDto> items = new ArrayList<>();
    private final List<AssetPairDto> filteredItems = new ArrayList<>();
    private String currentQuery = "";

    public MarketsAdapter(OnMarketClick listener) {
        this.listener = listener;
    }

    public void submit(List<AssetPairDto> list) {
        items.clear();
        if (list != null) items.addAll(list);
        applyFilter();
    }

    public void filter(String query) {
        currentQuery = query != null ? query.trim().toLowerCase() : "";
        applyFilter();
    }

    private void applyFilter() {
        List<AssetPairDto> oldItems = new ArrayList<>(filteredItems);
        List<AssetPairDto> newItems = new ArrayList<>();
        if (currentQuery.isEmpty()) {
            newItems.addAll(items);
        } else {
            for (AssetPairDto dto : items) {
                String display = dto.display() != null ? dto.display().toLowerCase() : "";
                String ws = dto.wsName() != null ? dto.wsName().toLowerCase() : "";
                if (display.contains(currentQuery) || ws.contains(currentQuery)) {
                    newItems.add(dto);
                }
            }
        }
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
                String oldWs = oldItems.get(oldItemPosition).wsName();
                String newWs = newItems.get(newItemPosition).wsName();
                if (oldWs == null) {
                    return newWs == null;
                }
                return oldWs.equals(newWs);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).equals(newItems.get(newItemPosition));
            }
        });
        filteredItems.clear();
        filteredItems.addAll(newItems);
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_market, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AssetPairDto p = filteredItems.get(pos);
        h.txtName.setText(p.display());
        h.txtSub.setText(p.wsName());
        h.txtBadge.setText(buildBadge(p.display()));
        h.btnAdd.setOnClickListener(v -> listener.onAddToFavorites(p));
        h.itemView.setOnClickListener(v -> listener.onOpenDetails(p));
    }

    @Override
    public int getItemCount() {
        return filteredItems.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        private final TextView txtName;
        private final TextView txtSub;
        private final TextView txtBadge;
        private final ImageButton btnAdd;

        VH(@NonNull View v) {
            super(v);
            txtName = v.findViewById(R.id.txtName);
            txtSub = v.findViewById(R.id.txtSub);
            txtBadge = v.findViewById(R.id.txtBadge);
            btnAdd = v.findViewById(R.id.btnAdd);
        }
    }

    private static String buildBadge(String display) {
        if (display == null || display.isEmpty()) {
            return "—";
        }
        String primary = display;
        int slash = display.indexOf('/');
        if (slash > 0) {
            primary = display.substring(0, slash);
        }
        primary = primary.trim();
        if (primary.length() > 4) {
            primary = primary.substring(0, 4);
        }
        return primary.toUpperCase(Locale.US);
    }
}
