package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import static com.greenaddress.greenbits.ui.SignUpActivity.PINSAVE;

/**
 * Created by Antonio Parrella on 12/21/17.
 * by inbitcoin
 */

public class FragmentEmail extends GAFragment {

    public static final int FROM_EXPORT_ACTIVITY = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View mView = inflater.inflate(R.layout.fragment_email, container, false);
        final Button continueButton = UI.find(mView, R.id.continueButton);
        final CircularButton skipButton = UI.find(mView, R.id.skipButton);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(getActivity(), SetEmailActivity.class);
                getGaActivity().startActivityForResult(intent, FROM_EXPORT_ACTIVITY);
            }
        });

        skipButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent savePin = PinSaveActivity.createIntent(getGaActivity(), getGAService().getMnemonic());
                getGaActivity().startActivityForResult(savePin, PINSAVE);
            }
        });

        return mView;
    }
}
