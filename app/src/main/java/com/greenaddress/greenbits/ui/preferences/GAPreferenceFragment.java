package com.greenaddress.greenbits.ui.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.GreenAddressApplication;
import com.greenaddress.greenbits.ui.R;

public class GAPreferenceFragment extends PreferenceFragment {
    protected GaService mService = null;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final GreenAddressApplication app;
        app = ((GreenAddressApplication) getActivity().getApplication());
        mService = app.mService;
    }

    private static final Preference.OnPreferenceChangeListener onPreferenceChanged = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, final Object value) {
            preference.setSummary(value.toString());
            return true;
        }
    };

    protected void bindPreferenceSummaryToValue(final Preference preference) {
        preference.setOnPreferenceChangeListener(onPreferenceChanged);
        // Trigger the listener immediately with the preference's
        // current value.
        final String currentVal = mService.cfg().getString(preference.getKey(), "");
        onPreferenceChanged.onPreferenceChange(preference, currentVal);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            startActivity(new Intent(getActivity(), SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public <T> T find(final String preferenceName) {
        return (T) findPreference(preferenceName);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final ListView listView = (ListView) view.findViewById(android.R.id.list);
        // force white background
        listView.setBackgroundColor(getResources().getColor(R.color.white));

        // add margins
        final ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) listView.getLayoutParams();
        int margin = getResources().getDimensionPixelSize(R.dimen.activity_content_margin);
        layoutParams.setMargins(margin, margin, margin, margin);
    }
}
