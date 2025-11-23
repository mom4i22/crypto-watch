package com.f119589.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.data.entity.FavouritePair;
import com.f119589.dto.TickEvent;
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

    private final Set<String> sparklineRequested = new HashSet<>();

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

        LiveData<List<FavouritePair>> live = repo.favorites();
        live.observe(getViewLifecycleOwner(), (List<FavouritePair> list) -> {
            // 1) Update UI
            adapter.submit(list);

            for (FavouritePair e : list) {
                if (e.getOhlc24hJson() == null || e.getOhlc24hJson().isEmpty()) {
                    if (sparklineRequested.add(e.getSymbol())) { // returns true if newly added
                        CryptoRepository.get(requireContext()).fetchAndCacheOhlc24h(e.getSymbol());
                    }
                }
            }
        });

        // Observe tick events for immediate UI updates
        repo.tickEvents().observe(getViewLifecycleOwner(), (TickEvent event) -> {
            if (event != null) {
                adapter.pushLiveTick(event.symbol(), event.price());
            }
        });
    }

    @Override
    public void onOpenDetails(FavouritePair e) {
        PairDetailActivity.launch(requireContext(), e.getSymbol(), e.getDisplayName() != null ? e.getDisplayName() : e.getSymbol());
    }

    @Override
    public void onRemove(FavouritePair e) {
        repo.removeFavorite(e.getSymbol());
        // Ask WS to refresh subscriptions
        Intent intent = new Intent(KrakenWebSocketService.ACTION_REFRESH_SUBSCRIPTIONS);
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent);
    }
}
