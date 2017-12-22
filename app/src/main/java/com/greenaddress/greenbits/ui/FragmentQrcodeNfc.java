package com.greenaddress.greenbits.ui;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * Created by Antonio Parrella on 12/21/17.
 * by inbitcoin
 */

public class FragmentQrcodeNfc extends GAFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View mView = inflater.inflate(R.layout.fragment_export_qrcode_nfc, container, false);
        final Button continueButton = UI.find(mView, R.id.continueButton);
        final Button skipButton = UI.find(mView, R.id.skipButton);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Runnable callback = new Runnable() {
                    @Override
                    public void run() {
                        goToEmailFragment();
                    }
                };
                final String mnemonic = getGAService().getMnemonic();
                ExportMnemonic.openDialogPassword(mnemonic, getActivity(), callback);
            }
        });

        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // show email fragment
                goToEmailFragment();
            }
        });

        return mView;
    }

    private void goToEmailFragment() {
        final FragmentTransaction transaction = getGaActivity().getSupportFragmentManager().beginTransaction();
        final FragmentEmail fragmentEmail = new FragmentEmail();
        transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
        transaction.replace(R.id.fragment_container, fragmentEmail);
        transaction.addToBackStack("backup");
        transaction.commitAllowingStateLoss();
    }

}
