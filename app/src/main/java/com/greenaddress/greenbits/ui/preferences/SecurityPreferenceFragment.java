package com.greenaddress.greenbits.ui.preferences;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import com.greenaddress.greenbits.ui.ExportMnemonic;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.SignUpActivity;

/**
 * Created by Antonio Parrella on 12/19/17.
 * by inbitcoin
 */

public class SecurityPreferenceFragment extends GAPreferenceFragment {


    private static final int BACKUP_ACTIVITY = 1;
    private Preference mBackupWallet;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_security);

        mBackupWallet = find("backup_wallet");
        mBackupWallet.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent intent = new Intent(getActivity(), SignUpActivity.class);
                intent.putExtra(GaPreferenceActivity.FROM_PREFERENCE_ACTIVITY, true);
                startActivityForResult(intent, BACKUP_ACTIVITY);
                return false;
            }
        });
        updateBackupStatus();

        final Preference backupPassphrase = find("export_mnemonic_passphrase");
        backupPassphrase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final String mnemonic = mService.getMnemonic();
                ExportMnemonic.openDialogPassword(mnemonic, getActivity());
                return false;
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BACKUP_ACTIVITY)
            updateBackupStatus();
    }

    private void updateBackupStatus() {
        final Boolean backupDone = mService.cfg().getBoolean("backup_done", false);
        if (!backupDone) {
            Spannable summary = new SpannableString(getString(R.string.warning_backup));
            summary.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.lightRed)), 0, summary.length(), 0);
            mBackupWallet.setSummary(summary);
        } else {
            mBackupWallet.setSummary("");
        }
    }
}
