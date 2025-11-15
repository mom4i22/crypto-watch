package com.f119589.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.dto.AssetPairDto;
import com.f119589.repository.CryptoRepository;
import com.f119589.service.KrakenWebSocketService;
import com.f119589.ui.PairDetailActivity;
import com.f119589.ui.adapters.MarketsAdapter;

import java.util.List;

public class MarketsFragment extends Fragment implements MarketsAdapter.OnMarketClick {

    private CryptoRepository repo;
    private MarketsAdapter adapter;

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

        RecyclerView rv = v.findViewById(R.id.recyclerMarkets);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new MarketsAdapter(this);
        rv.setAdapter(adapter);

        // Observe markets list
        repo.markets().observe(getViewLifecycleOwner(), (List<AssetPairDto> list) -> adapter.submit(list));

        // Fetch latest
        repo.refreshAssetPairs();
    }

    @Override
    public void onAddToFavorites(AssetPairDto pair) {
        repo.addFavorite(pair);
        repo.fetchAndCacheOhlc24h(pair.wsName());

        LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(new Intent(KrakenWebSocketService.ACTION_REFRESH_SUBSCRIPTIONS));
        // Cache sparkline data early (optional; also can be done in FavoritesFragment)

        new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(new Intent(KrakenWebSocketService.ACTION_REFRESH_SUBSCRIPTIONS));
        }, 200);

        repo.refreshTickerSnapshot(pair.wsName());
    }

    @Override
    public void onOpenDetails(AssetPairDto pair) {
        // optional — Details are implemented in PairDetailActivity (Favorites also can open it)
        PairDetailActivity.launch(requireContext(), pair.wsName(), pair.display());
    }
}
