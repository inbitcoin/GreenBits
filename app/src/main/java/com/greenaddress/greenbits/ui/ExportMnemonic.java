package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.design.widget.TextInputEditText;
import android.text.Editable;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDButton;
import com.greenaddress.greenapi.CryptoHelper;
import com.greenaddress.greenbits.QrBitmap;
import com.greenaddress.greenbits.ui.preferences.SettingsActivity;

/**
 * Created by Antonio Parrella on 12/19/17.
 * by inbitcoin
 */

public class ExportMnemonic {

    private static final int PASSWORD_LENGTH = 12;
    private static final String TAG = ExportMnemonic.class.getSimpleName();

    /**
     * Show dialog to insert password
     * @param mnemonic String
     * @param activity the activity
     */
    static public void openDialogPassword(final String mnemonic, final Activity activity) {
        openDialogPassword(mnemonic, activity, null);
    }

    /**
     * Show dialog to insert password
     * @param mnemonic String
     * @param activity the activity
     * @param callback to call onPositive
     */
    static public void openDialogPassword(final String mnemonic, final Activity activity, final Runnable callback) {
        final View view = activity.getLayoutInflater().inflate(R.layout.dialog_backup_mnemonic_password, null, false);

        final TextInputEditText inputPassword1 = UI.find(view, R.id.input_password1);
        final TextInputEditText inputPassword2 = UI.find(view, R.id.input_password2);
        final TextView errMatchPassword = UI.find(view, R.id.errMatchPassword);
        final TextView errLengthPassword = UI.find(view, R.id.errLengthPassword);
        final TextView errPassword = UI.find(view, R.id.errPassword);
        errLengthPassword.setText(activity.getResources().getString(R.string.err_password_length, PASSWORD_LENGTH));

        final MaterialDialog dialog = UI.popup(activity, R.string.export_mnemonic_passphrase, R.string.confirm, R.string.cancel)
                .customView(view, true)
                .cancelable(false)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        final String password1 = UI.getText(inputPassword1);
                        final String password2 = UI.getText(inputPassword2);
                        if (password1.isEmpty() || password2.isEmpty() ||
                                password1.contains(" ") ||
                                password1.compareTo(password2) != 0 ||
                                password1.length() < PASSWORD_LENGTH) {
                            Log.d(TAG, "error password");
                            UI.toast(activity, R.string.err_password, Toast.LENGTH_LONG);
                        } else {
                            openDialogBackup(mnemonic, password1, activity, callback);
                        }
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                    }
                }).build();
        UI.showDialog(dialog, true);

        // positive button, to show only on password match
        final MDButton positiveButton = dialog.getActionButton(DialogAction.POSITIVE);
        positiveButton.setEnabled(false);

        final UI.TextWatcher textWatcher = new UI.TextWatcher() {
            @Override
            public void afterTextChanged(final Editable s) {
                final String password1 = UI.getText(inputPassword1);
                final String password2 = UI.getText(inputPassword2);

                // empty password field or not matching
                if (password1.isEmpty() || password2.isEmpty() ||
                        password1.compareTo(password2) != 0 ||
                        password1.length() < PASSWORD_LENGTH) {
                    inputPassword2.setTextColor(activity.getResources().getColor(R.color.lightRed));
                    positiveButton.setEnabled(false);
                    errMatchPassword.setVisibility(View.VISIBLE);
                } else {
                    inputPassword2.setTextColor(activity.getResources().getColor(R.color.textColor));
                    positiveButton.setEnabled(true);
                    errMatchPassword.setVisibility(View.INVISIBLE);
                }

                // invalid password length or invalid character
                if (password1.length() < PASSWORD_LENGTH || password1.contains(" ")) { // FIXME allow spaces?
                    inputPassword1.setTextColor(activity.getResources().getColor(R.color.lightRed));
                    positiveButton.setEnabled(false);
                    if (password1.contains(" ")) {
                        errLengthPassword.setVisibility(View.GONE);
                        errPassword.setVisibility(View.VISIBLE);
                    } else {
                        errPassword.setVisibility(View.GONE);
                        errLengthPassword.setVisibility(View.VISIBLE);
                    }
                } else {
                    inputPassword1.setTextColor(activity.getResources().getColor(R.color.textColor));
                    errLengthPassword.setVisibility(View.INVISIBLE);
                    errPassword.setVisibility(View.GONE);
                }
            }
        };

        inputPassword1.addTextChangedListener(textWatcher);
        inputPassword2.addTextChangedListener(textWatcher);
    }

    /**
     * Open dialog with encrypted mnemonic qrcode and button to save also on NFC tag
     * @param mnemonic String
     * @param password String
     * @param activity the activity
     */
    static void openDialogBackup(final String mnemonic, final String password, final Activity activity) {
        openDialogBackup(mnemonic, password, null);
    }

    /**
     * Open dialog with encrypted mnemonic qrcode and button to save also on NFC tag
     * @param mnemonic String
     * @param password String
     * @param activity the activity
     * @param callback to call onPositive
     */
    static void openDialogBackup(final String mnemonic, final String password, final Activity activity, final Runnable callback) {
        final View v = activity.getLayoutInflater().inflate(R.layout.dialog_backup_mnemonic, null, false);

        final MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(activity)
                .customView(v, true)
                .positiveText(R.string.continueText)
                .cancelable(false);
        if (callback != null) {
            dialogBuilder.onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    callback.run();
                }
            });
        }
        final MaterialDialog dialog = dialogBuilder.build();
        dialog.show();
        final MDButton positiveButton = dialog.getActionButton(DialogAction.POSITIVE);
        positiveButton.setEnabled(false);

        class BitmapWorkerTask extends AsyncTask<Object, Object, Pair<Bitmap, String>> {

            @Override
            protected Pair<Bitmap, String> doInBackground(Object... params) {
                final String encrypted = CryptoHelper.mnemonic_to_encrypted_mnemonic(mnemonic, password);
                final QrBitmap qrBitmap = new QrBitmap(encrypted, Color.WHITE, activity);
                return Pair.create(qrBitmap.getQRCode(), encrypted);
            }

            @Override
            protected void onPostExecute(final Pair pair) {

                final Bitmap bitmap = (Bitmap) pair.first;
                final String encryptedMnemonic = (String) pair.second;
                final ImageView qrCode = UI.find(v, R.id.inDialogImageView);
                qrCode.setLayoutParams(UI.getScreenLayout(activity, 0.8));
                qrCode.setImageBitmap(bitmap);

                final ImageButton shareButton = UI.find(v, R.id.inDialogShare);
                shareButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        UI.shareImageWithText(activity, bitmap, activity.getString(R.string.mnemonic_passphrase_with_password));
                    }
                });

                if (Build.VERSION.SDK_INT >= 16) {
                    final NfcAdapter mNfcAdapter = NfcAdapter.getDefaultAdapter(activity);
                    if (mNfcAdapter != null && mNfcAdapter.isEnabled()) {
                        UI.show((View) UI.find(v, R.id.backupNfcView));
                        final ImageView nfcButton = UI.find(v, R.id.backupNfcIcon);
                        nfcButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent intent = new Intent(activity, activity.getClass());
                                intent.setAction(SettingsActivity.INTENT_SHOW_NFC_DIALOG_REQUEST);
                                // Prevent activity to be re-instantiated if it is already running.
                                // Instead, the onNewEvent() is triggered
                                intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                intent.putExtra("mnemonic", CryptoHelper.encrypted_mnemonic_to_bytes(encryptedMnemonic));
                                intent.putExtra("is_encrypted", true);
                                activity.startActivity(intent);
                            }
                        });
                    }
                }

                qrCode.setImageBitmap(bitmap);
                UI.hide((View) UI.find(v, R.id.loadingView));
                UI.show((View) UI.find(v, R.id.contentView));
                positiveButton.setEnabled(true);
            }

        }
        new BitmapWorkerTask().execute();
    }
}
