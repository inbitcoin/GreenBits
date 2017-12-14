package com.greenaddress.greenbits.ui.preferences;
import com.greenaddress.greenapi.CryptoHelper;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.NfcWriteMnemonic;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.SignUpActivity;
import com.greenaddress.greenbits.ui.UI;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;

import java.util.List;

public class SettingsActivity extends GaPreferenceActivity {

    public static String INTENT_SHOW_NFC_DIALOG_REQUEST = "intent_show_nfc_dialog";

    private NfcAdapter mNfcAdapter;
    private NfcWriteMnemonic mNfcWriteMnemonic;
    private PendingIntent mNfcPendingIntent;

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(final List<Header> target) {
        if (GaService.IS_ELEMENTS)
            loadHeadersFromResource(R.xml.pref_headers_elements, target);
        else
            loadHeadersFromResource(R.xml.pref_headers, target);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mNfcPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
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
    public void onPause() {
        super.onPause();
        if (mNfcAdapter != null)
            mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mNfcAdapter != null) {
            final IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            final IntentFilter[] filters = new IntentFilter[]{filter};
            mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, filters, null);
        }
    }

    @Override
    public void onHeaderClick(Header header, int position) {
        super.onHeaderClick(header, position);
        if (header.id == R.id.backupHeader) {
            final Intent intent = new Intent(this, SignUpActivity.class);
            intent.putExtra("from_settings_page", true);
            startActivity(intent);
        }
    }
}
