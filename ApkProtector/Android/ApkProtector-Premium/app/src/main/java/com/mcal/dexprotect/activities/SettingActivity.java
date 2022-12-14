package com.mcal.dexprotect.activities;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.mcal.dexprotect.R;
import com.mcal.dexprotect.view.CenteredToolBar;

import org.jetbrains.annotations.NotNull;

public class SettingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        setupToolbar(getString(R.string.settings));

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.frame_container, new com.mcal.dexprotect.fragment.SettingFragment())
                .commit();
    }

    @SuppressWarnings("ConstantConditions")
    private void setupToolbar(String title) {
        CenteredToolBar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (title != null) {
            getSupportActionBar().setTitle(title);
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NotNull MenuItem item) {
        // Respond to the action bar's Up/Home button
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}