package com.f119589.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.dto.AssetPairDto;
import com.f119589.dto.MarketSnapshotDto;
import com.f119589.repository.CryptoRepository;
import com.f119589.ui.PairDetailActivity;
import com.f119589.ui.adapters.MarketsAdapter;

import java.util.List;
import java.util.Locale;

public class MarketsFragment extends Fragment implements MarketsAdapter.OnMarketClick {

    private CryptoRepository repo;
    private MarketsAdapter adapter;
    private String currentQuery = "";
    private TextView txtSnapshotMcap;
    private TextView txtSnapshotVolume;
    private TextView txtSnapshotBtcDom;
    private View snapshotContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_markets, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        repo = CryptoRepository.get(requireContext());

        snapshotContainer = v.findViewById(R.id.cardMarketSnapshot);
        txtSnapshotMcap = v.findViewById(R.id.txtSnapshotMcap);
        txtSnapshotVolume = v.findViewById(R.id.txtSnapshotVolume);
        txtSnapshotBtcDom = v.findViewById(R.id.txtSnapshotBtcDom);
        renderSnapshot(null);

        SearchView searchView = v.findViewById(R.id.searchMarkets);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentQuery = newText != null ? newText : "";
                adapter.filter(currentQuery);
                return true;
            }
        });

        RecyclerView rv = v.findViewById(R.id.recyclerMarkets);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new MarketsAdapter(this);
        rv.setAdapter(adapter);

        // Observe markets list
        repo.markets().observe(getViewLifecycleOwner(), (List<AssetPairDto> list) -> {
            adapter.submit(list);
            if (!currentQuery.isEmpty()) {
                adapter.filter(currentQuery);
            }
        });
        repo.marketSnapshot().observe(getViewLifecycleOwner(), this::renderSnapshot);

        // Fetch latest
        repo.refreshAssetPairs();
        repo.refreshMarketSnapshot();
    }

    @Override
    public void onAddToFavorites(AssetPairDto pair) {
        repo.addFavorite(requireContext(), pair);
        repo.refreshTickerSnapshot(pair.wsName());
    }

    @Override
    public void onOpenDetails(AssetPairDto pair) {
        PairDetailActivity.launch(requireContext(), pair.wsName(), pair.display());
    }

    private void renderSnapshot(@Nullable MarketSnapshotDto snapshot) {
        if (snapshot == null) {
            setSnapshotTexts("—", "—", "—");
            if (snapshotContainer != null) {
                snapshotContainer.setAlpha(0.7f);
            }
            return;
        }

        setSnapshotTexts(
                formatUsd(snapshot.marketCapUsd()),
                formatUsd(snapshot.volume24hUsd()),
                formatPercent(snapshot.btcDominance())
        );
        if (snapshotContainer != null) {
            snapshotContainer.setAlpha(1f);
        }
    }

    private void setSnapshotTexts(String mcap, String volume, String btcDom) {
        if (txtSnapshotMcap != null) txtSnapshotMcap.setText(mcap);
        if (txtSnapshotVolume != null) txtSnapshotVolume.setText(volume);
        if (txtSnapshotBtcDom != null) txtSnapshotBtcDom.setText(btcDom);
    }

    private static String formatUsd(double v) {
        if (Double.isNaN(v) || v <= 0d) return "—";
        double abs = Math.abs(v);
        if (abs >= 1_000_000_000_000d) {
            return String.format(Locale.US, "$%.1fT", v / 1_000_000_000_000d);
        }
        if (abs >= 1_000_000_000d) {
            return String.format(Locale.US, "$%.1fB", v / 1_000_000_000d);
        }
        if (abs >= 1_000_000d) {
            return String.format(Locale.US, "$%.1fM", v / 1_000_000d);
        }
        return String.format(Locale.US, "$%,.0f", v);
    }

    private static String formatPercent(double v) {
        if (Double.isNaN(v)) return "—";
        return String.format(Locale.US, "%.1f%%", v);
    }
}
