package com.greenaddress.greenbits.ui.preferences;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Toast;

import com.greenaddress.greenbits.ui.ExportMnemonic;
import com.greenaddress.greenbits.ui.PinSaveActivity;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.SetEmailActivity;
import com.greenaddress.greenbits.ui.SignUpActivity;

import java.util.Map;
import java.util.Observable;
import java.util.Observer;

/**
 * Created by Antonio Parrella on 12/19/17.
 * by inbitcoin
 */

public class SecurityPreferenceFragment extends GAPreferenceFragment {

    private static final String TAG = SecurityPreferenceFragment.class.getSimpleName();

    private static final int PINSAVE = 1337;
    private static final int BACKUP_ACTIVITY = 1;
    private Preference mBackupWallet;
    private Observer mEmailSummaryObserver;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preference_security);

        final String mnemonic = mService.getMnemonic();

        mBackupWallet = find("backup_wallet");
        final Preference backupPassphrase = find("export_mnemonic_passphrase");
        if (mnemonic != null) {
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

            backupPassphrase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ExportMnemonic.openDialogPassword(mnemonic, getActivity());
                    return false;
                }
            });
        } else {
            mBackupWallet.setEnabled(false);
            backupPassphrase.setEnabled(false);
        }

        // PIN
        final Preference resetPin = find("reset_pin");
        if (mnemonic == null)
            removePreference(resetPin);
        else {
            resetPin.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    final Intent savePin = PinSaveActivity.createIntent(getActivity(), mnemonic);
                    savePin.putExtra(GaPreferenceActivity.FROM_PREFERENCE_ACTIVITY, true);
                    startActivityForResult(savePin, PINSAVE);
                    return false;
                }
            });
        }

        // -- handle email address
        final Preference email = find("email");
        final Map<?, ?> twoFactorConfig = mService.getTwoFactorConfig();
        if (twoFactorConfig != null) {
            Log.d(TAG, "twoFactorConfig = " + twoFactorConfig);
            final String emailAddr = (String) twoFactorConfig.get("email_addr");
            if (emailAddr != null) {
                final Boolean email_confirmed = (Boolean) twoFactorConfig.get("email_confirmed");
                if (email_confirmed) {
                    email.setSummary(emailAddr);
                }
            }

        }
        final Boolean emailTwoFac = (Boolean) twoFactorConfig.get("email");
        if (emailTwoFac) {
            // Disable
            email.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    Toast.makeText(getActivity(), R.string.no_change_email, Toast.LENGTH_LONG)
                            .show();
                    return false;
                }
            });
        } else {
            email.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    final Intent intent = new Intent(getActivity(), SetEmailActivity.class);
                    // intent.putExtra("method", "email");
                    final int REQUEST_ENABLE_2FA = 0;
                    startActivityForResult(intent, REQUEST_ENABLE_2FA);
                    return false;
                }
            });
        }

        mEmailSummaryObserver = new Observer() {
            @Override
            public void update(Observable o, Object arg) {
                Log.d(TAG, "Update the email address into the menu");
                final Map<?, ?> twoFactorConfig = mService.getTwoFactorConfig();
                final String emailAddr = (String) twoFactorConfig.get("email_addr");
                final Boolean emailConfirmed = (Boolean) twoFactorConfig.get("email_confirmed");

                final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            if (emailConfirmed) {
                                email.setSummary(emailAddr);
                            } else {
                                email.setSummary("");
                            }
                        }
                    });
                }
            }
        };
        mService.addTwoFactorObserver(mEmailSummaryObserver);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BACKUP_ACTIVITY)
            updateBackupStatus();
    }

    private void updateBackupStatus() {
        final Boolean backupDone = mService.getBackupDone();
        if (!backupDone) {
            Spannable summary = new SpannableString(getString(R.string.warning_backup));
            summary.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.lightRed)), 0, summary.length(), 0);
            mBackupWallet.setSummary(summary);
        } else {
            mBackupWallet.setSummary("");
        }
    }

    @Override
    public void onDestroy() {
        mService.deleteTwoFactorObserver(mEmailSummaryObserver);
        super.onDestroy();
    }
}
