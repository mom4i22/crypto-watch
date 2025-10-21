package com.f119589.ui.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.f119589.ui.fragments.FavouritesFragment;
import com.f119589.ui.fragments.MarketsFragment;

public class HomePagerAdapter extends FragmentStateAdapter {

    public HomePagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return position == 0 ? new MarketsFragment() : new FavouritesFragment();
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
