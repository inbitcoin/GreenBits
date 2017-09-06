package com.greenaddress.greenbits.ui.preferences;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.support.v4.util.Pair;
import android.text.Editable;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDButton;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.greenaddress.greenapi.CryptoHelper;
import com.greenaddress.greenbits.QrBitmap;
import com.greenaddress.greenbits.ui.CB;
import com.greenaddress.greenbits.ui.PinSaveActivity;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.SetEmailActivity;
import com.greenaddress.greenbits.ui.UI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

public class GeneralPreferenceFragment extends GAPreferenceFragment
    implements Preference.OnPreferenceClickListener {
    private static final String TAG = GeneralPreferenceFragment.class.getSimpleName();

    private static final int PINSAVE = 1337;
    private static final String mMicroSymbol = Html.fromHtml("&micro;").toString();
    private Preference mToggleSW;
    private static final int PASSWORD_LENGTH = 12;
    private boolean mPassphraseVisible = false;
    private Observer mEmailSummaryObserver;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (mService == null || !mService.isLoggedIn()) {
            // If we are restored and our service has not been destroyed, its
            // state is unreliable and our parent activity should shortly
            // be calling finish(). Avoid accessing the service in this case.
            Log.d(TAG, "Avoiding create on logged out service");
            return;
        }

        addPreferencesFromResource(R.xml.preference_general);
        setHasOptionsMenu(true);

        // -- handle timeout
        int timeout = mService.getAutoLogoutMinutes();
        getPreferenceManager().getSharedPreferences().edit()
                              .putString("altime", Integer.toString(timeout))
                              .apply();
        final Preference altime = find("altime");
        altime.setSummary(String.format("%d %s", timeout, getResources().getString(R.string.autologout_time_default)));
        altime.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                try {
                    final Integer altimeout = Integer.parseInt(newValue.toString());
                    mService.setUserConfig("altimeout", altimeout, true);
                    preference.setSummary(String.format("%d %s", altimeout, getResources().getString(R.string.autologout_time_default)));
                    return true;
                } catch (final Exception e) {
                    // not set
                }
                return false;
            }
        });

        // -- handle mnemonics


        final String mnemonic = mService.getMnemonics();
        if (mnemonic != null) {
            final Preference passphrase = find("mnemonic_passphrase");
            passphrase.setSummary(getString(R.string.touch_to_display));
            passphrase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    if (mPassphraseVisible) {
                        mService.setFlagSecure(getActivity(), false);
                        passphrase.setSummary(getString(R.string.touch_to_display));
                        mPassphraseVisible = false;
                    } else {
                        new MaterialDialog.Builder(getActivity())
                                .title(R.string.warning)
                                .content(R.string.show_mnemonic_passphrase_alert)
                                .positiveText(R.string.ok)
                                .negativeText(R.string.cancel)
                                .onPositive(new MaterialDialog.SingleButtonCallback() {
                                    @Override
                                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                        mService.setFlagSecure(getActivity(), true);
                                        passphrase.setSummary(mnemonic);
                                        mPassphraseVisible = true;
                                    }
                                })
                                .show();
                    }
                    return false;
                }
            });

            final Preference backupPassphrase = find("backup_mnemonic_passphrase");
            backupPassphrase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    openDialogPassword(mnemonic);
                    return false;
                }
            });
        }

        // -- handle pin
        final Preference resetPin = find("reset_pin");
        if (mnemonic != null) {
            resetPin.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    final Intent savePin = PinSaveActivity.createIntent(getActivity(), mnemonic);
                    startActivityForResult(savePin, PINSAVE);
                    return false;
                }
            });
        } else {
            getPreferenceScreen().removePreference(resetPin);
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

        Observer mEmailSummaryObserver = new Observer() {
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

        // -- handle currency and bitcoin denomination
        final ListPreference fiatCurrency = find("fiat_key");
        final ListPreference bitcoinDenomination = find("denomination_key");
        final ArrayList<String> units;
        units = Lists.newArrayList("BTC", "mBTC", mMicroSymbol + "BTC", "bits");

        bitcoinDenomination.setEntries(units.toArray(new String[4]));
        bitcoinDenomination.setEntryValues(units.toArray(new String[4]));
        bitcoinDenomination.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object o) {
                mService.setUserConfig("unit", o.toString(), true);
                bitcoinDenomination.setSummary(o.toString());
                return true;
            }
        });
        final String btcUnit = (String) mService.getUserConfig("unit");
        bitcoinDenomination.setSummary(btcUnit == null ? "bits" : btcUnit);

        fiatCurrency.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(final Preference preference, final Object o) {
                final String[] split = o.toString().split(" ");
                mService.setPricingSource(split[0], split[1]);
                fiatCurrency.setSummary(o.toString());
                return true;
            }
        });
        final Preference watchOnlyLogin = find("watch_only_login");
        try {
            final String username = mService.getWatchOnlyUsername();
            if (username != null) {
                watchOnlyLogin.setSummary(getString(R.string.watchOnlyLoginStatus, username));
            } else {
                watchOnlyLogin.setSummary(R.string.watchOnlyLoginSetup);
            }
        } catch (final Exception e ) {
            e.printStackTrace();
        }

        watchOnlyLogin.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_set_watchonly, null, false);
                final EditText inputUser = UI.find(v, R.id.input_user);
                try {
                    // refetch username
                    inputUser.setText(mService.getWatchOnlyUsername());
                } catch (final Exception e) {}
                final EditText inputPassword = UI.find(v, R.id.input_password);
                final MaterialDialog dialog = UI.popup(getActivity(), R.string.watchOnlyLogin)
                        .customView(v, true)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(final MaterialDialog dlg, final DialogAction which) {
                                final String username = UI.getText(inputUser);
                                final String password = UI.getText(inputPassword);
                                if (username.isEmpty() && password.isEmpty()) {
                                    try {
                                        mService.disableWatchOnly();
                                        UI.toast(getActivity(), R.string.watchOnlyLoginDisabled, Toast.LENGTH_LONG);
                                        watchOnlyLogin.setSummary(R.string.watchOnlyLoginSetup);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    return;
                                }
                                try {
                                    mService.registerWatchOnly(username, password);
                                    watchOnlyLogin.setSummary(getString(R.string.watchOnlyLoginStatus, username));

                                } catch (final Exception e) {
                                    UI.toast(getActivity(), R.string.error_username_not_available, Toast.LENGTH_LONG);
                                }
                            }
                        }).build();
                UI.showDialog(dialog, true);
                return false;
            }
        });

        Futures.addCallback(mService.getCurrencyExchangePairs(), new CB.Op<List<List<String>>>() {
            @Override
            public void onSuccess(final List<List<String>> result) {
                final Activity activity = getActivity();
                if (activity != null && result != null) {
                    activity.runOnUiThread(new Runnable() {
                        public void run() {
                            final ArrayList<String> fiatPairs = new ArrayList<>(result.size());

                            for (final List<String> currency_exchange : result) {
                                final boolean current = currency_exchange.get(0).equals(mService.getFiatCurrency())
                                        && currency_exchange.get(1).equals(mService.getFiatExchange());
                                final String pair = String.format("%s %s", currency_exchange.get(0), currency_exchange.get(1));
                                if (current) {
                                    fiatCurrency.setSummary(pair);
                                }
                                fiatPairs.add(pair);
                            }
                            fiatCurrency.setEntries(fiatPairs.toArray(new String[result.size()]));
                            fiatCurrency.setEntryValues(fiatPairs.toArray(new String[result.size()]));
                        }
                    });
                }
            }
        });

        // -- handle opt-in rbf
        final CheckBoxPreference optInRbf = find("optin_rbf");
        final boolean rbf = mService.getLoginData().get("rbf");
        if (!rbf)
            getPreferenceScreen().removePreference(optInRbf);
        else {
            optInRbf.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, final Object newValue) {
                    // disable until server confirms set
                    optInRbf.setEnabled(false);

                    Futures.addCallback(
                            mService.setUserConfig("replace_by_fee", newValue, false),
                            new FutureCallback<Boolean>() {
                                @Override
                                public void onSuccess(final Boolean result) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        public void run() {
                                            optInRbf.setChecked((Boolean) newValue);
                                            optInRbf.setEnabled(true);
                                        }
                                    });
                                }

                                @Override
                                public void onFailure(final Throwable t) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        public void run() {
                                            optInRbf.setEnabled(true);
                                        }
                                    });
                                }
                            });
                    return false;
                }
            });
            final Boolean replace_by_fee = (Boolean) mService.getUserConfig("replace_by_fee");
            optInRbf.setChecked(replace_by_fee);
        }

        mToggleSW = find("toggle_segwit");
        mToggleSW.setOnPreferenceClickListener(this);
        setupSWToggle();

        final CheckBoxPreference advancedOptions = find("advanced_options");
        // set initial value
        Boolean advancedOptionsValue = mService.cfg("advanced_options").getBoolean("enabled", false);
        advancedOptions.setChecked(advancedOptionsValue);


        advancedOptions.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                mService.cfgEdit("advanced_options").putBoolean("enabled", (Boolean)newValue).apply();
                advancedOptions.setChecked((Boolean) newValue);
                return false;
            }
        });

        getActivity().setResult(Activity.RESULT_OK, null);
    }

    private void setupSWToggle() {
        final boolean segwit = mService.getLoginData().get("segwit_server");
        final boolean userSegwit = mService.isSegwitEnabled();

        mToggleSW.setTitle(userSegwit ? R.string.segwit_disable : R.string.segwit_enable);

        if (segwit &&
            (!userSegwit || !mService.isSegwitLocked())) {
            // User hasn't enabled segwit, or they have but we haven't
            // generated a segwit address yet (that we know of).
            mToggleSW.setEnabled(true);
        } else {
            mToggleSW.setEnabled(false);
        }
    }


    @Override
    public boolean onPreferenceClick(final Preference preference) {
        if (preference == mToggleSW)
            return onToggleSWClicked();
        return false;
    }

    private boolean onToggleSWClicked() {
        getActivity().runOnUiThread(new Runnable() {
            public void run() { mToggleSW.setEnabled(false); }
        });
        final boolean immediate = true;
        CB.after(mService.setUserConfig("use_segwit", !mService.isSegwitEnabled(), immediate),
                 new CB.Op<Boolean>() {
            @Override
            public void onSuccess(final Boolean result) {
                toggle();
            }
            @Override
            public void onFailure(final Throwable t) {
                super.onFailure(t);
                toggle();
            }
            private void toggle() {
                getActivity().runOnUiThread(new Runnable() {
                    public void run() { setupSWToggle(); }
                });
            }

        });
        return false;
    }

    @Override
    public void onDestroy() {
        mService.deleteTwoFactorObserver(mEmailSummaryObserver);
        super.onDestroy();
    }

    /**
     * Show dialog to insert password
     * @param mnemonic String
     */
    private void openDialogPassword(final String mnemonic) {
        final View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_backup_mnemonic_password, null, false);

        final TextInputEditText inputPassword1 = UI.find(view, R.id.input_password1);
        final TextInputEditText inputPassword2 = UI.find(view, R.id.input_password2);
        final TextView errMatchPassword = UI.find(view, R.id.errMatchPassword);
        final TextView errLengthPassword = UI.find(view, R.id.errLengthPassword);
        final TextView errPassword = UI.find(view, R.id.errPassword);
        errLengthPassword.setText(getResources().getString(R.string.err_password_length, PASSWORD_LENGTH));

        final MaterialDialog dialog = UI.popup(getActivity(), R.string.backup_mnemonic_passphrase, R.string.confirm, R.string.cancel)
                .customView(view, true)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        final String password1 = UI.getText(inputPassword1);
                        final String password2 = UI.getText(inputPassword2);
                        if (password1.isEmpty() || password2.isEmpty() ||
                                password1.contains(" ") ||
                                password1.compareTo(password2) != 0 ||
                                password1.length() < PASSWORD_LENGTH) {
                            Log.d(TAG, "error password");
                            UI.toast(getActivity(), R.string.err_password, Toast.LENGTH_LONG);
                        } else {
                            openDialogBackup(mnemonic, password1);
                        }
                    }
                }).build();
        UI.showDialog(dialog, true);

        // positive button, to show only on password match
        final MDButton positiveButton = dialog.getActionButton(DialogAction.POSITIVE);
        positiveButton.setEnabled(false);

        final UI.TextWatcher textWatcher = new UI.TextWatcher() {
            @Override
            public void afterTextChanged(final Editable s) {
                final String password1 = UI.getText(inputPassword1);
                final String password2 = UI.getText(inputPassword2);

                // empty password field or not matching
                if (password1.isEmpty() || password2.isEmpty() ||
                        password1.compareTo(password2) != 0 ||
                        password1.length() < PASSWORD_LENGTH) {
                    inputPassword2.setTextColor(getResources().getColor(R.color.lightRed));
                    positiveButton.setEnabled(false);
                    errMatchPassword.setVisibility(View.VISIBLE);
                } else {
                    inputPassword2.setTextColor(getResources().getColor(R.color.textColor));
                    positiveButton.setEnabled(true);
                    errMatchPassword.setVisibility(View.INVISIBLE);
                }

                // invalid password length or invalid character
                if (password1.length() < PASSWORD_LENGTH || password1.contains(" ")) { // FIXME allow spaces?
                    inputPassword1.setTextColor(getResources().getColor(R.color.lightRed));
                    positiveButton.setEnabled(false);
                    if (password1.contains(" ")) {
                        errLengthPassword.setVisibility(View.GONE);
                        errPassword.setVisibility(View.VISIBLE);
                    } else {
                        errPassword.setVisibility(View.GONE);
                        errLengthPassword.setVisibility(View.VISIBLE);
                    }
                } else {
                    inputPassword1.setTextColor(getResources().getColor(R.color.textColor));
                    errLengthPassword.setVisibility(View.INVISIBLE);
                    errPassword.setVisibility(View.GONE);
                }
            }
        };

        inputPassword1.addTextChangedListener(textWatcher);
        inputPassword2.addTextChangedListener(textWatcher);
    }

    /**
     * Open dialog with encrypted mnemonic qrcode and button to save also on NFC tag
     * @param mnemonic String
     * @param password String
     */
    private void openDialogBackup(final String mnemonic, final String password) {
        final View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_backup_mnemonic, null, false);

        final Dialog dialog = new Dialog(getActivity());
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(v);
        dialog.show();

        class BitmapWorkerTask extends AsyncTask<Object, Object, Pair<Bitmap, String>> {

            @Override
            protected Pair<Bitmap, String> doInBackground(Object... params) {
                final String encrypted = CryptoHelper.mnemonic_to_encrypted_mnemonic(mnemonic, password);
                final QrBitmap qrBitmap = new QrBitmap(encrypted, Color.WHITE, getActivity());
                return Pair.create(qrBitmap.getQRCode(), encrypted);
            }

            @Override
            protected void onPostExecute(final Pair pair) {

                final Bitmap bitmap = (Bitmap) pair.first;
                final String encryptedMnemonic = (String) pair.second;
                final ImageView qrCode = UI.find(v, R.id.inDialogImageView);
                qrCode.setLayoutParams(UI.getScreenLayout(getActivity(), 0.8));
                qrCode.setImageBitmap(bitmap);

                final ImageButton shareButton = UI.find(v, R.id.inDialogShare);
                shareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                        UI.shareImageWithText(getActivity(), bitmap, getString(R.string.mnemonic_passphrase_with_password));
                    }
                });

                if (Build.VERSION.SDK_INT >= 16) {
                    UI.show((View) UI.find(v, R.id.backupNfcView));
                    final ImageView nfcButton = UI.find(v, R.id.backupNfcIcon);
                    nfcButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            dialog.dismiss();
                            Intent intent = new Intent(getActivity(), SettingsActivity.class);
                            intent.setAction(SettingsActivity.INTENT_SHOW_NFC_DIALOG_REQUEST);
                            // Prevent activity to be re-instantiated if it is already running.
                            // Instead, the onNewEvent() is triggered
                            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            intent.putExtra("mnemonic", CryptoHelper.encrypted_mnemonic_to_bytes(encryptedMnemonic));
                            intent.putExtra("is_encrypted", true);
                            getActivity().startActivity(intent);
                        }
                    });
                }

                qrCode.setImageBitmap(bitmap);
                UI.hide((View) UI.find(v, R.id.loadingView));
                UI.show((View) UI.find(v, R.id.contentView));
            }

        }
        new BitmapWorkerTask().execute();
    }
}
