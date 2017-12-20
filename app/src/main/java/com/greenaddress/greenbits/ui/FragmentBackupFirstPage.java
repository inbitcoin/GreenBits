package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.dd.CircularProgressButton;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.greenaddress.greenapi.LoginData;
import com.greenaddress.greenbits.GaService;

import java.util.ArrayList;
import java.util.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Created by Antonio Parrella on 10/23/17.
 * by inbitcoin
 */

public class FragmentBackupFirstPage extends GAFragment {

    GaService mService;
    private ListenableFuture<LoginData> mOnSignUp;
    private Activity mActivity = getGaActivity();
    private Button backupButton;
    private CircularProgressButton noBackupButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View mView = inflater.inflate(R.layout.fragment_backup_first, container, false);
        backupButton = UI.find(mView, R.id.backupButton);
        mService = getGAService();

        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getGaActivity().startActivity(new Intent(getActivity(), SignUpActivity.class));
            }
        });
        mActivity = getGaActivity();
        noBackupButton = UI.find(mView, R.id.noBackupButton);
        noBackupButton.setIndeterminateProgressMode(true);
        noBackupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (noBackupButton.getProgress() != 0)
                    return;
                MaterialDialog popup = UI.popup(getGaActivity(), "Ricordati che in seguito dovrai fare il backup.\nSicuro di continuare senza backup?")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                backupButton.setEnabled(false);
                                noBackupButton.setProgress(50);
                                login();
                            }
                        }).build();
                popup.show();
            }
        });
        return mView;
    }

    // TODO rimuovere questo codice duplicato
    private void login() {
        mOnSignUp = mService.signup(mService.getSignUpMnemonic());
        Futures.addCallback(mOnSignUp, new FutureCallback<LoginData>() {
            @Override
            public void onSuccess(final LoginData result) {
                //setComplete(true);
                mService.resetSignUp();
                mOnSignUp = null;
                final Intent savePin = PinSaveActivity.createIntent(getActivity(), mService.getMnemonic());
                startActivityForResult(savePin, SignUpActivity.PINSAVE);

                // set default inbitcoin setup
                mService.setUserConfig("replace_by_fee", false, false);
                mService.cfgEdit("advanced_options").putBoolean("enabled", false).apply();

                // get current system currency and if it's present in greenaddress, set this
                Futures.addCallback(mService.getCurrencyExchangePairs(), new CB.Op<List<List<String>>>() {
                    @Override
                    public void onSuccess(final List<List<String>> result) {
                        if (mActivity != null && result != null) {
                            mActivity.runOnUiThread(new Runnable() {
                                public void run() {
                                    final ArrayList<String> defaultExchanges = new ArrayList<>();
                                    defaultExchanges.add("BTCAVG");
                                    defaultExchanges.add("KRAKEN");
                                    defaultExchanges.add("BITSTAMP");

                                    String first_exchange = "";
                                    final String currentCurrency = Currency.getInstance(Locale.getDefault()).getCurrencyCode();
                                    boolean pairFound = false;
                                    final Iterator<List<String>> resultIterator = result.iterator();
                                    while (resultIterator.hasNext() && !pairFound) {
                                        List<String> pair = resultIterator.next();
                                        if (pair.get(0).equals(currentCurrency)) {
                                            // set first exchange found
                                            if (first_exchange.isEmpty())
                                                first_exchange = pair.get(1);

                                            // try to set exchange fround dafault list
                                            final Iterator exchangesIterator = defaultExchanges.iterator();
                                            while (exchangesIterator.hasNext() && !pairFound) {
                                                String exchange = (String) exchangesIterator.next();
                                                pairFound = pair.get(1).equals(exchange);
                                                if (pairFound)
                                                    mService.setPricingSource(currentCurrency, exchange);
                                            }
                                        }
                                    }

                                    // set first exchange if no default exchange found
                                    if (!pairFound)
                                        mService.setPricingSource(currentCurrency, first_exchange);
                                }
                            });
                        }
                    }
                });


            }

            @Override
            public void onFailure(final Throwable t) {
                //setComplete(false);
                mOnSignUp = null;
                t.printStackTrace();
                //toast(t.getMessage());
                backupButton.setEnabled(true);
                noBackupButton.setProgress(0);

            }
        }, mService.getExecutor());
    }
}
