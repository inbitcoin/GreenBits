package com.greenaddress.greenbits.ui.preferences;

import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.blockstream.libwally.Wally;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.greenaddress.greenapi.ISigningWallet;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.UI;

import java.util.ArrayList;

import javax.annotation.Nullable;

/**
 * Created by Antonio Parrella on 4/28/17.
 * by inbitcoin
 */

public class SubaccountsPreferenceFragment extends GAPreferenceFragment {

    private static final String TAG = GeneralPreferenceFragment.class.getSimpleName();
    private Preference hide2to2;

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

        addPreferencesFromResource(R.xml.preference_subaccounts);

        final Preference create2to2 = find("create2to2");
        create2to2.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                UI.popup(getActivity(), R.string.create2to2)
                        .inputRangeRes(1, 20, R.color.lightRed)
                        .content(R.string.subaccountLabel)
                        .input(null, null, new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                try {
                                    create2to2subaccount(input.toString());
                                } catch (UnsupportedOperationException e) {
                                    UI.popup(getActivity(), R.string.warning, R.string.confirm)
                                            .content(R.string.err_unsupported_wallet).build().show();
                                }

                            }
                        }).show();
                return false;
            }
        });

        hide2to2 = find("showHide2to2");
        if (mService.getSubaccountObjs().size() == 0) {
            final PreferenceScreen screen = getPreferenceScreen();
            screen.removePreference(hide2to2);
        }
        hide2to2.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(final Preference preference) {
                final ArrayList<GaService.Subaccount> subaccounts = mService.getSubaccountObjs();
                final ArrayList<String> names = new ArrayList<>(subaccounts.size());
                final ArrayList<Integer> selected = new ArrayList<>();

                for (int i = 0; i < subaccounts.size(); i++) {
                    names.add(subaccounts.get(i).mName);
                    if (subaccounts.get(i).mEnabled)
                           selected.add(i);
                }

                final Integer[] selectedItems = new Integer[selected.size()];
                for (int i = 0; i < selected.size(); i++)
                    selectedItems[i] = selected.get(i);

                new MaterialDialog.Builder(getActivity())
                        .title(R.string.showHide2to2)
                        .items(names)
                        .itemsCallbackMultiChoice(selectedItems, new MaterialDialog.ListCallbackMultiChoice() {
                            @Override
                            public boolean onSelection(MaterialDialog dialog, Integer[] which, CharSequence[] text) {
                                mService.setCurrentSubAccount(0); // set current subaccount to main
                                // TODO write better code
                                for(GaService.Subaccount subaccount:subaccounts) {
                                    mService.setSubaccountStatus(subaccount.mPointer, false);
                                }
                                for (Integer aWhich : which) {
                                    mService.setSubaccountStatus(subaccounts.get(aWhich).mPointer, true);
                                }
                                return true;
                            }
                        })
                        .positiveText(android.R.string.ok)
                        .show();


                return false;
            }
        });
    }

    private void create2to2subaccount(final String label) {
        final int pointer = mService.getSubaccountObjs().size() + 1;
        final ISigningWallet iSigningWallet = mService.getSigningWallet();
        final String user_public = Wally.hex_from_bytes(iSigningWallet.getMyPublicKey(pointer).getPubKey());
        final String user_chaincode = Wally.hex_from_bytes(iSigningWallet.getMyPublicKey(pointer).getChainCode());
        Futures.addCallback(mService.create2to2subaccount(pointer, label, user_public,
                user_chaincode, "simple"), new FutureCallback<String>()
        {
            @Override
            public void onSuccess(@Nullable final String result) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null || result.isEmpty() || !result.startsWith("GA")) {
                            UI.popup(getActivity(), R.string.warning, R.string.confirm)
                                    .content(R.string.create2to2error).build().show();
                        } else {
                            UI.toast(getActivity(), R.string.create2to2OK, Toast.LENGTH_LONG);
                            hide2to2.setEnabled(true);
                            final PreferenceScreen screen = getPreferenceScreen();
                            screen.addPreference(hide2to2);
                        }
                    }
                });
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
                ((GaPreferenceActivity) getActivity()).toast(throwable.getMessage());
            }
        });
    }
}
