package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.BitmapDrawable;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.dd.CircularProgressButton;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.greenaddress.greenapi.LoginData;
import com.greenaddress.greenbits.NfcWriteMnemonic;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class SignUpActivity extends LoginActivity {
    private static final String TAG = SignUpActivity.class.getSimpleName();
    private static final int PINSAVE = 1337;

    private Dialog mMnemonicDialog;
    private NfcAdapter mNfcAdapter;
    private PendingIntent mNfcPendingIntent;
    private ImageView mSignupNfcIcon;

    private TextView mMnemonicText;
    private Activity mActivity;
    private NfcWriteMnemonic mNfcWriteMnemonic;
    private CheckBox mAcceptCheckBox;
    private CircularProgressButton mContinueButton;

    private ListenableFuture<LoginData> mOnSignUp = null;

    @Override
    protected int getMainViewId() { return R.layout.activity_sign_up; }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {

        mService.setFlagSecure(this, true);

        mActivity = this;

        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mNfcPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, SignUpActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        mMnemonicText = UI.find(this, R.id.signupMnemonicText);
        mMnemonicText.setText(mService.getSignUpMnemonic());

        mAcceptCheckBox = UI.find(this, R.id.signupAcceptCheckBox);
        mContinueButton = UI.find(this, R.id.signupContinueButton);

        if (mOnSignUp != null) {
            mAcceptCheckBox.setEnabled(false);
            mAcceptCheckBox.setChecked(true);
            UI.enable(mContinueButton);
        }

        final TextView termsText = UI.find(this, R.id.textTosLink);
        termsText.setMovementMethod(LinkMovementMethod.getInstance());

        final View qrView = getLayoutInflater().inflate(R.layout.dialog_qrcode, null, false);

        final ImageView qrCodeMnemonic = UI.find(qrView, R.id.qrInDialogImageView);
        final TextView qrCodeIcon = UI.find(this, R.id.signupQrCodeIcon);

        mNfcWriteMnemonic = new NfcWriteMnemonic(mService.getSignUpMnemonic(), this, false);

        qrCodeIcon.setOnClickListener(new View.OnClickListener() {
            public void onClick(final View v) {
                qrCodeIcon.clearAnimation();
                if (mMnemonicDialog == null) {
                    qrCodeMnemonic.setLayoutParams(UI.getScreenLayout(SignUpActivity.this, 0.8));

                    mMnemonicDialog = new Dialog(SignUpActivity.this);
                    mMnemonicDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
                    mMnemonicDialog.setContentView(qrView);
                }
                mMnemonicDialog.show();
                final BitmapDrawable bd = new BitmapDrawable(getResources(), mService.getSignUpQRCode());
                bd.setFilterBitmap(false);
                qrCodeMnemonic.setImageDrawable(bd);
            }
        });

        mContinueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                int errorId = 0;
                if (!mService.isConnected())
                    errorId = R.string.notConnected;
                else if (!mAcceptCheckBox.isChecked())
                    errorId = R.string.securePassphraseMsg;
                else if (mOnSignUp != null)
                    errorId = R.string.signupInProgress;

                if (errorId != 0) {
                    toast(errorId);
                    return;
                }

                mContinueButton.setIndeterminateProgressMode(true);
                mContinueButton.setProgress(50);

                mOnSignUp = mService.signup(UI.getText(mMnemonicText));
                Futures.addCallback(mOnSignUp, new FutureCallback<LoginData>() {
                    @Override
                    public void onSuccess(final LoginData result) {
                        setComplete(true);
                        mService.resetSignUp();
                        mOnSignUp = null;
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
                                                final ArrayList<String> currencies = new ArrayList<>(result.size());

                                                for (final List<String> currency_exchange : result) {
                                                    final String pair = String.format("%s", currency_exchange.get(0));
                                                    currencies.add(pair);
                                                }
                                                String currentCurrency = Currency.getInstance(Locale.getDefault()).getCurrencyCode();
                                                if (currencies.contains(currentCurrency)) {
                                                    // set currency based on system setup and fixed
                                                    // BTCAVG exchange service
                                                    mService.setPricingSource(currentCurrency, "BTCAVG");
                                                }
                                            }
                                        });
                                    }
                                }
                            });


                        final Intent savePin = PinSaveActivity.createIntent(SignUpActivity.this, mService.getMnemonics());
                        startActivityForResult(savePin, PINSAVE);
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        setComplete(false);
                        mOnSignUp = null;
                        t.printStackTrace();
                        toast(t.getMessage());
                    }
                }, mService.getExecutor());
            }
        });

        mSignupNfcIcon = UI.find(this, R.id.signupNfcIcon);
        if (Build.VERSION.SDK_INT < 16) {
            UI.hide(mSignupNfcIcon);
            return;
        }


        mSignupNfcIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mNfcWriteMnemonic != null) {
                    mNfcWriteMnemonic.showDialog();
                }
            }
        });

        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    private void setComplete(final boolean isComplete) {
        runOnUiThread(new Runnable() {
            public void run() {
                mContinueButton.setProgress(isComplete ? 100 : 0);
            }
        });
    }


    @Override
    public void onResumeWithService() {
        if (mNfcAdapter != null) {
            final IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            final IntentFilter[] filters = new IntentFilter[]{filter};
            mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, filters, null);
        }
        UI.showIf(mNfcAdapter != null && mNfcAdapter.isEnabled(), mSignupNfcIcon);
    }

    @Override
    public void onPauseWithService() {
        if (mNfcAdapter != null)
            mNfcAdapter.disableForegroundDispatch(this);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            mNfcWriteMnemonic.write(intent);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMnemonicDialog != null)
            mMnemonicDialog.dismiss();
        if (mNfcWriteMnemonic != null)
            mNfcWriteMnemonic.dismissDialog();
    }

    @Override
    public void onBackPressed() {

        if (mOnSignUp != null) {
            mService.resetSignUp();
            mOnSignUp = null;
            mService.disconnect(true);
        }
        super.onBackPressed();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.common_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return item.getItemId() == R.id.action_settings || super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case PINSAVE:
                onLoginSuccess();
                break;
        }
    }
}
