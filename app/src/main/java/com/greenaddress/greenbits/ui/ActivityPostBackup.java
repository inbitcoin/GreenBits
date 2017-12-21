package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;

import static com.greenaddress.greenbits.ui.FragmentEmail.FROM_EXPORT_ACTIVITY;
import static com.greenaddress.greenbits.ui.SignUpActivity.PINSAVE;

/**
 * Created by Antonio Parrella on 12/19/17.
 * by inbitcoin
 */

public class ActivityPostBackup extends GaActivity {
    @Override
    protected void onCreateWithService(Bundle savedInstanceState) {
        setContentView(R.layout.activity_signup2);

        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);

        final FragmentQrcodeNfc qrcodeNfcFragment = new FragmentQrcodeNfc();

        if (savedInstanceState == null) {
            final FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, qrcodeNfcFragment);
            fragmentTransaction.addToBackStack("export_qrcode_nfc");
            fragmentTransaction.commit();
        }
    }

    @Override
    public void onBackPressed() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count != 1) {
            getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case PINSAVE:
            case FROM_EXPORT_ACTIVITY:
                if (resultCode == RESULT_OK) {
                    // FIXME
                    final Intent intent = new Intent(this, TabbedMainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finishOnUiThread();
                    break;
                }
        }
    }
}
