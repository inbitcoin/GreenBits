package com.greenaddress.greenbits.ui;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;

/**
 * Created by Antonio Parrella on 10/23/17.
 * by inbitcoin
 */

public class FragmentTos extends GAFragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View mView = inflater.inflate(R.layout.fragment_tos, container, false);
        final CheckBox checkBox = UI.find(mView, R.id.acceptCheckBox);
        final Button continueButton = UI.find(mView, R.id.continueButton);

        checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (b)
                    continueButton.setEnabled(true);
                else
                    continueButton.setEnabled(false);
            }
        });

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // show summary fragment
                final FragmentTransaction transaction = getGaActivity().getSupportFragmentManager().beginTransaction();
                final FragmentBackupFirstPage fragmentBackupFirstPage = new FragmentBackupFirstPage();
                transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
                transaction.replace(R.id.fragment_container, fragmentBackupFirstPage);
                transaction.addToBackStack("backup");
                transaction.commitAllowingStateLoss();
            }
        });

        return mView;
    }
}
