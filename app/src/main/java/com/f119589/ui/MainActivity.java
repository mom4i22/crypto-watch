package com.f119589.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.f119589.R;
import com.f119589.service.KrakenWebSocketService;
import com.f119589.ui.adapters.HomePagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<String> notifPerm =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                // No-op: if denied, service can still start on some devices,
                // but notification may be suppressed. We just proceed.
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        ViewPager2 pager = findViewById(R.id.pager);
        pager.setAdapter(new HomePagerAdapter(this));

        TabLayout tabs = findViewById(R.id.tabs);
        new TabLayoutMediator(tabs, pager, (tab, pos) ->
                tab.setText(pos == 0 ? getString(R.string.markets_tab)
                        : getString(R.string.favorites_tab))
        ).attach();

        // Ensure foreground WS service is running for live prices
        ContextCompat.startForegroundService(
                this, new Intent(this, KrakenWebSocketService.class)
        );
    }
}
