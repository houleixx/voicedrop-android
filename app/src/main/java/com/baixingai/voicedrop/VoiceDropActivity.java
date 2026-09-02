package com.baixingai.voicedrop;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.baixingai.voicedrop.ui.I18n;

/** Base activity required for AppCompat's API 23+ per-app locale support. */
public abstract class VoiceDropActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // AppCompat restores persisted locales while attaching this activity, which can happen
        // after Application.onCreate initialized I18n for this process.
        I18n.syncLanguage(this);
    }
}
