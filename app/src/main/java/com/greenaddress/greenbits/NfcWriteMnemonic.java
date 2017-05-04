package com.greenaddress.greenbits;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.greenaddress.greenapi.CryptoHelper;
import com.greenaddress.greenbits.ui.R;
import com.greenaddress.greenbits.ui.UI;

import java.io.IOException;

/**
 * Created by Antonio Parrella on 11/8/16.
 * inbitcoin
 */

public class NfcWriteMnemonic {
    private String mnemonicText;
    private Activity mActivity;
    private Dialog nfcDialog;
    private TextView nfcTagsWritten;
    private boolean mWriteMode = false;
    private final Runnable mDialogCB = new Runnable() { public void run() { mWriteMode = false; } };

    public NfcWriteMnemonic(String mnemonic, Activity activity) {
        mnemonicText = mnemonic;
        mActivity = activity;

        final View nfcView = activity.getLayoutInflater().inflate(R.layout.dialog_nfc_write, null, false);
        nfcTagsWritten = UI.find(nfcView, R.id.nfcTagsWrittenText);

        nfcDialog = new MaterialDialog.Builder(activity)
                .title("Hold your NFC tag close to the device")
                .customView(nfcView, true)
                .titleColorRes(R.color.white)
                .contentColorRes(android.R.color.white)
                .theme(Theme.DARK).build();

        UI.setDialogCloseHandler(nfcDialog, mDialogCB, true /* cancelOnly */);
    }

    public void showDialog() {
        mWriteMode = true;
        nfcDialog.show();
    }

    public void dismissDialog() {
        if (nfcDialog != null) {
            nfcDialog.dismiss();
        }
    }

    @SuppressLint("NewApi") // signupNfcIcon is hidden for API < 16
    public void write(Intent intent) {
        if (!mWriteMode) {
            return;
        }

        final Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        final NdefRecord[] record = new NdefRecord[1];

        record[0] = NdefRecord.createMime("x-gait/mnc",
                CryptoHelper.mnemonic_to_bytes(mnemonicText));

        final NdefMessage message = new NdefMessage(record);
        final int size = message.toByteArray().length;
        try {
            final Ndef ndef = Ndef.get(detectedTag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable())
                    UI.toast(mActivity, R.string.err_sign_up_nfc_not_writable, Toast.LENGTH_SHORT);
                if (ndef.getMaxSize() < size)
                    UI.toast(mActivity, R.string.err_sign_up_nfc_too_small, Toast.LENGTH_SHORT);
                ndef.writeNdefMessage(message);
                nfcTagsWritten.setText(String.valueOf(Integer.parseInt(UI.getText(nfcTagsWritten)) + 1));

            } else {
                final NdefFormatable format = NdefFormatable.get(detectedTag);
                if (format != null)
                    try {
                        format.connect();
                        format.format(message);
                        nfcTagsWritten.setText(String.valueOf(Integer.parseInt(UI.getText(nfcTagsWritten)) + 1));
                    } catch (final IOException e) {
                    }
            }
        } catch (final Exception e) {
        }
    }
}
