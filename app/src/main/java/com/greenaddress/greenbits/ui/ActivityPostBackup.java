package com.greenaddress.greenbits.ui;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;

import com.greenaddress.greenapi.CryptoHelper;
import com.greenaddress.greenbits.NfcWriteMnemonic;

import static com.greenaddress.greenbits.ui.FragmentEmail.FROM_EXPORT_ACTIVITY;
import static com.greenaddress.greenbits.ui.SignUpActivity.PINSAVE;
import static com.greenaddress.greenbits.ui.preferences.SettingsActivity.INTENT_SHOW_NFC_DIALOG_REQUEST;

/**
 * Created by Antonio Parrella on 12/19/17.
 * by inbitcoin
 */

public class ActivityPostBackup extends GaActivity {

    private NfcAdapter mNfcAdapter;
    private NfcWriteMnemonic mNfcWriteMnemonic;
    private PendingIntent mNfcPendingIntent;

    @Override
    protected void onCreateWithService(Bundle savedInstanceState) {
        setContentView(R.layout.activity_signup2);

        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mNfcPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

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

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()) && mNfcWriteMnemonic != null) {
            mNfcWriteMnemonic.write(intent);
        } else if (INTENT_SHOW_NFC_DIALOG_REQUEST.equals(intent.getAction())) {
            byte [] mnemonic = intent.getByteArrayExtra("mnemonic");
            boolean isEncrypted = intent.getBooleanExtra("is_encrypted", false);
            final String mnemonicText = CryptoHelper.mnemonic_from_bytes(mnemonic);
            mNfcWriteMnemonic = new NfcWriteMnemonic(mnemonicText, this, isEncrypted);
            mNfcWriteMnemonic.showDialog();
        }
    }

    @Override
    public void onPauseWithService() {
        if (mNfcAdapter != null)
            mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResumeWithService() {
        if (mNfcAdapter != null) {
            final IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            final IntentFilter[] filters = new IntentFilter[]{filter};
            mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, filters, null);
        }
    }
}
