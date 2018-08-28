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
    private CircularButton noBackupButton;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View mView = inflater.inflate(R.layout.fragment_backup_first, container, false);
        backupButton = UI.find(mView, R.id.backupButton);
        mService = getGAService();

        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UI.popup(getActivity(), R.string.warning, R.string.ok, R.string.cancel)
                        .content(R.string.warning_show_mnemonic)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                getGaActivity().startActivity(new Intent(getActivity(), SignUpActivity.class));
                            }
                        })
                        .build()
                        .show();
            }
        });
        mActivity = getGaActivity();
        noBackupButton = UI.find(mView, R.id.noBackupButton);
        noBackupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (noBackupButton.isLoading())
                    return;
                MaterialDialog popup = UI.popup(getGaActivity(), getString(R.string.warning))
                        .content(getString(R.string.warning_skip_backup))
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                backupButton.setEnabled(false);
                                noBackupButton.startLoading();
                                login();
                            }
                        }).build();
                popup.show();
            }
        });
        return mView;
    }

    /**
     * Do login and set default account data
     */
    private void login() {
        mOnSignUp = mService.signup(mService.getSignUpMnemonic());
        Futures.addCallback(mOnSignUp, new FutureCallback<LoginData>() {
            @Override
            public void onSuccess(final LoginData result) {
                mService.resetSignUp();
                mOnSignUp = null;
                final Intent savePin = PinSaveActivity.createIntent(getActivity(), mService.getMnemonic());
                startActivityForResult(savePin, SignUpActivity.PINSAVE);

                SignUpActivity.setDefaultAccountData(mService, getActivity());
            }

            @Override
            public void onFailure(final Throwable t) {
                mOnSignUp = null;
                t.printStackTrace();
                backupButton.setEnabled(true);
                noBackupButton.stopLoading();

            }
        }, mService.getExecutor());
    }
}
