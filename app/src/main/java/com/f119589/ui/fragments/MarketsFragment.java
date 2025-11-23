package com.f119589.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.f119589.R;
import com.f119589.dto.AssetPairDto;
import com.f119589.repository.CryptoRepository;
import com.f119589.ui.PairDetailActivity;
import com.f119589.ui.adapters.MarketsAdapter;

import java.util.List;

public class MarketsFragment extends Fragment implements MarketsAdapter.OnMarketClick {

    private CryptoRepository repo;
    private MarketsAdapter adapter;
    private String currentQuery = "";

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

        // Fetch latest
        repo.refreshAssetPairs();
    }

    @Override
    public void onAddToFavorites(AssetPairDto pair) {
        repo.addFavorite(requireContext(), pair);
        repo.refreshTickerSnapshot(pair.wsName());
    }

    @Override
    public void onOpenDetails(AssetPairDto pair) {
        // optional — Details are implemented in PairDetailActivity (Favorites also can open it)
        PairDetailActivity.launch(requireContext(), pair.wsName(), pair.display());
    }
}
