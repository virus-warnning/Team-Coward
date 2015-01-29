package org.tacoball.sport.cowardlybike;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.tacoball.sport.signals.Settings;

public class SettingsFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName(Settings.DEFAULT_PREFERENCE);
        addPreferencesFromResource(R.xml.frag_settings);

        /*
        final Settings settings = new Settings(getActivity());
        final Handler mHandler = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                settings.dump();
                mHandler.postDelayed(this, 3000);
            }
        };
        mHandler.postDelayed(runnable,3000);
        */
    }

}
