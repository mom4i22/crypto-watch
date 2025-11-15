package com.f119589.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.dto.AssetPairDto;

import java.util.ArrayList;
import java.util.List;

public class MarketsAdapter extends RecyclerView.Adapter<MarketsAdapter.VH> {

    public interface OnMarketClick {
        void onAddToFavorites(AssetPairDto pair);

        void onOpenDetails(AssetPairDto pair);
    }

    private final OnMarketClick listener;
    private final List<AssetPairDto> items = new ArrayList<>();

    public MarketsAdapter(OnMarketClick listener) {
        this.listener = listener;
    }

    public void submit(List<AssetPairDto> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_market, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        AssetPairDto p = items.get(pos);
        h.txtName.setText(p.display());
        h.txtSub.setText(p.wsName());
        h.btnAdd.setOnClickListener(v -> listener.onAddToFavorites(p));
        h.itemView.setOnClickListener(v -> listener.onOpenDetails(p));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView txtName, txtSub;
        ImageButton btnAdd;

        VH(@NonNull View v) {
            super(v);
            txtName = v.findViewById(R.id.txtName);
            txtSub = v.findViewById(R.id.txtSub);
            btnAdd = v.findViewById(R.id.btnAdd);
        }
    }
}
