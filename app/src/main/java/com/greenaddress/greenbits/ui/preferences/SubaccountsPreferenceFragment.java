package com.greenaddress.greenbits.ui.preferences;

import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.blockstream.libwally.Wally;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.greenaddress.greenapi.SWWallet;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.UI;

import java.util.ArrayList;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Created by Antonio Parrella on 4/28/17.
 * by inbitcoin
 */

public class SubaccountsPreferenceFragment extends GAPreferenceFragment {

    private static final String TAG = GeneralPreferenceFragment.class.getSimpleName();

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
                                create2to2subaccount(input.toString());
                            }
                        }).show();
                return false;
            }
        });
    }

    private void create2to2subaccount(final String label) {
        final ArrayList subaccounts = mService.getSubaccounts();
        final int subaccount_len = subaccounts.size() + 1;
        final ArrayList<Integer> pointers = new ArrayList<>(subaccount_len);

        // main
        pointers.add(0);

        // add all subaccount
        for (final Object s : subaccounts) {
            final Map<String, ?> m = (Map) s;
            pointers.add((Integer) m.get("pointer"));
        }

        // TODO support all wallet type?
        final SWWallet sw = (SWWallet) mService.getSigningWallet();
        final String user_public = Wally.hex_from_bytes(sw.getPubKey().getPubKey());
        final String user_chaincode = Wally.hex_from_bytes(sw.getMasterKey().getChainCode());
        Futures.addCallback(mService.create2to2subaccount(pointers.size(), label, user_public,
                user_chaincode, "simple"), new FutureCallback<String>()
        {
            @Override
            public void onSuccess(@Nullable final String result) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null || result.isEmpty() || !result.startsWith("GA")) {
                            UI.popup(getActivity(), R.string.warning, android.R.string.ok)
                                    .content(R.string.create2to2error).build().show();
                        } else {
                            UI.toast(getActivity(), R.string.create2to2OK, Toast.LENGTH_LONG);
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
