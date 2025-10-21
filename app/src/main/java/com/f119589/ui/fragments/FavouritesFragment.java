package com.f119589.ui.fragments;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.data.entity.FavouritePair;
import com.f119589.repository.CryptoRepository;
import com.f119589.service.KrakenWebSocketService;
import com.f119589.ui.PairDetailActivity;
import com.f119589.ui.adapters.FavouritesAdapter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FavouritesFragment extends Fragment implements FavouritesAdapter.OnFavoriteClick {

    private CryptoRepository repo;
    private FavouritesAdapter adapter;
    private LiveData<List<FavouritePair>> live;

    private final Set<String> sparklineRequested = new HashSet<>();

    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (KrakenWebSocketService.ACTION_TICK.equals(intent.getAction())) {
                String sym = intent.getStringExtra(KrakenWebSocketService.EXTRA_SYMBOL);
                double price = intent.getDoubleExtra(KrakenWebSocketService.EXTRA_PRICE, Double.NaN);
                adapter.pushLiveTick(sym, price); // smooth UI without waiting for Room emit
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favourites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);
        repo = CryptoRepository.get(requireContext());

        RecyclerView rv = v.findViewById(R.id.recyclerFavorites);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new FavouritesAdapter(this);
        rv.setAdapter(adapter);

        live = repo.favorites();
        live.observe(getViewLifecycleOwner(), (List<FavouritePair> list) -> {
            // 1) Update UI
            adapter.submit(list);

            for (FavouritePair e : list) {
                if (e.ohlc24hJson == null || e.ohlc24hJson.isEmpty()) {
                    if (sparklineRequested.add(e.symbol)) { // returns true if newly added
                        CryptoRepository.get(requireContext()).fetchAndCacheOhlc24h(e.symbol);
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                tickReceiver, new IntentFilter(KrakenWebSocketService.ACTION_TICK));
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(tickReceiver);
        super.onPause();
    }

    @Override
    public void onOpenDetails(FavouritePair e) {
        PairDetailActivity.launch(requireContext(), e.symbol, e.displayName != null ? e.displayName : e.symbol);
    }

    @Override
    public void onRemove(FavouritePair e) {
        repo.removeFavorite(e.symbol);
        // Ask WS to refresh subscriptions
        LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(new Intent(KrakenWebSocketService.ACTION_REFRESH_SUBSCRIPTIONS));
    }
}
