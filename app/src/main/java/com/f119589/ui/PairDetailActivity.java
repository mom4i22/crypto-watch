package com.f119589.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.f119589.R;
import com.f119589.repository.CryptoRepository;
import com.google.android.material.appbar.MaterialToolbar;

public class PairDetailActivity extends AppCompatActivity {

    private static final String EXTRA_SYMBOL = "symbol";
    private static final String EXTRA_DISPLAY = "display";

    public static void launch(Context ctx, String wsSymbol, String display) {
        Intent i = new Intent(ctx, PairDetailActivity.class);
        i.putExtra(EXTRA_SYMBOL, wsSymbol);
        i.putExtra(EXTRA_DISPLAY, display);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_pair_detail);

        String symbol = getIntent().getStringExtra(EXTRA_SYMBOL);
        String display = getIntent().getStringExtra(EXTRA_DISPLAY);

        MaterialToolbar toolbar = findViewById(R.id.detailToolbar);
        toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed());

        TextView title = findViewById(R.id.txtTitle);
        TextView subtitle = findViewById(R.id.txtSubtitle);
        String prettyName = display != null ? display : symbol;
        if (prettyName == null) {
            prettyName = getString(R.string.app_name);
        }
        title.setText(prettyName);
        subtitle.setText(symbol != null ? symbol : "");
        toolbar.setTitle(prettyName);
        toolbar.setSubtitle(symbol != null ? symbol : "");

        if (symbol != null) {
            // Optionally, fetch fresh 24h OHLC for this pair for a larger chart
            CryptoRepository.get(this).fetchAndCacheOhlc24h(symbol);
        }
    }
}
