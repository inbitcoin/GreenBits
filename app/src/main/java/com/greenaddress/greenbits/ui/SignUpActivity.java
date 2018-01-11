package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.dd.CircularProgressButton;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.greenaddress.greenapi.LoginData;
import com.greenaddress.greenapi.Network;
import com.greenaddress.greenbits.NfcWriteMnemonic;
import com.greenaddress.greenbits.ui.preferences.GaPreferenceActivity;
import com.wefika.flowlayout.FlowLayout;

import org.bitcoinj.core.NetworkParameters;

import java.util.ArrayList;
import java.util.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.Collections;
import java.util.Locale;


public class SignUpActivity extends LoginActivity implements View.OnClickListener {
    public static final int PINSAVE = 1337;
    private static final int VERIFY_COUNT = 4;

    private Dialog mMnemonicDialog;
    private Dialog mVerifyDialog;
    private NfcAdapter mNfcAdapter;
    private PendingIntent mNfcPendingIntent;
    private ImageView mNfcSignupIcon;

    private TextView mMnemonicText;
    private Activity mActivity;
    private NfcWriteMnemonic mNfcWriteMnemonic;
    private CheckBox mAcceptCheckBox;
    private CircularProgressButton mContinueButton;
    private TextView mQrCodeIcon;
    private ImageView mQrCodeBitmap;

    private ArrayList<Integer> mWordChoices;
    private boolean[] mChoiceIsValid;

    private ListenableFuture<LoginData> mOnSignUp;
    private final Runnable mVerifyDialogCB = new Runnable() { public void run() { onVerifyDismissed(); } };
    private Boolean mFromSettingsPage = false;

    private String mMnemonic;
    /** text size in SP */
    private final static int WORDS_TEXT_SIZE = 14;
    /** padding in pixel */
    private final static int WORDS_PADDING = 5;

    @Override
    protected int getMainViewId() { return R.layout.activity_sign_up; }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {

        mService.setFlagSecure(this, true);

        mFromSettingsPage = getIntent().getBooleanExtra(GaPreferenceActivity.FROM_PREFERENCE_ACTIVITY, false);

        mActivity = this;
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        mNfcPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, SignUpActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        mMnemonicText = UI.find(this, R.id.signupMnemonicText);
        mQrCodeIcon = UI.find(this, R.id.signupQrCodeIcon);
        mAcceptCheckBox = UI.find(this, R.id.signupAcceptCheckBox);
        mContinueButton = UI.find(this, R.id.signupContinueButton);
        mNfcSignupIcon = UI.find(this, R.id.signupNfcIcon);

        if (mFromSettingsPage) {
            mMnemonic = mService.getMnemonic();
        } else {
            mMnemonic = mService.getSignUpMnemonic();
        }
        mMnemonicText.setText(mMnemonic.replace(" ", "  "));

        if (mOnSignUp != null) {
            UI.disable(mAcceptCheckBox);
            mAcceptCheckBox.setChecked(true);
            UI.enable(mContinueButton);
        }

        final TextView termsText = UI.find(this, R.id.textTosLink);
        termsText.setMovementMethod(LinkMovementMethod.getInstance());

        mQrCodeIcon.setOnClickListener(this);
        mContinueButton.setOnClickListener(this);

        if (Build.VERSION.SDK_INT < 16)
            UI.hide(mNfcSignupIcon);
        else
            mNfcSignupIcon.setOnClickListener(this);

        mNfcWriteMnemonic = new NfcWriteMnemonic(mService.getSignUpMnemonic(), this, false);

        mWordChoices = new ArrayList<>(24);
        for (int i = 0; i < 24; ++i)
            mWordChoices.add(i);

        mChoiceIsValid = new boolean[VERIFY_COUNT];

        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);
        if (mFromSettingsPage) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.backup_wallet);
        }
    }

    @Override
    public void onResumeWithService() {
        if (mNfcAdapter != null) {
            final IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
            final IntentFilter[] filters = new IntentFilter[]{filter};
            mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, filters, null);
        }
        UI.showIf(mNfcAdapter != null && mNfcAdapter.isEnabled(), mNfcSignupIcon);
    }

    @Override
    public void onPauseWithService() {
        if (mNfcAdapter != null)
            mNfcAdapter.disableForegroundDispatch(this);
        if (mContinueButton != null) {
            mContinueButton.setIndeterminateProgressMode(false);
            mContinueButton.setProgress(0);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UI.unmapClick(mQrCodeIcon);
        UI.unmapClick(mContinueButton);
        UI.unmapClick(mNfcSignupIcon);

        mMnemonicDialog = UI.dismiss(this, mMnemonicDialog);
        if (mChoiceIsValid != null)
            mChoiceIsValid[0] = false;
        mVerifyDialog = UI.dismiss(this, mVerifyDialog);
    }

    @Override
    public void onClick(final View v) {
        if (v == mQrCodeIcon)
            onQrCodeButtonClicked();
        else if (v == mContinueButton)
            onContinueButtonClicked();
        else if (v == mNfcSignupIcon)
            onNfcSignupButtonClicked();
    }

    private void onQrCodeButtonClicked() {
        if (mMnemonicDialog == null) {
            final View v = getLayoutInflater().inflate(R.layout.dialog_qrcode, null, false);
            mQrCodeBitmap = UI.find(v, R.id.qrInDialogImageView);
            mQrCodeBitmap.setLayoutParams(UI.getScreenLayout(SignUpActivity.this, 0.8));
            mMnemonicDialog = new Dialog(SignUpActivity.this);
            mMnemonicDialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            mMnemonicDialog.setContentView(v);
        }
        mMnemonicDialog.show();
        final BitmapDrawable bd = new BitmapDrawable(getResources(), mService.getSignUpQRCode());
        bd.setFilterBitmap(false);
        mQrCodeBitmap.setImageDrawable(bd);
    }

    private void onContinueButtonClicked() {
        int errorId = 0;
        if (!mService.isConnected() && !mService.isLoggedOrLoggingIn())
            errorId = R.string.notConnected;
        //else if (!mAcceptCheckBox.isChecked())
        //    errorId = R.string.securePassphraseMsg;
        else if (mOnSignUp != null)
            errorId = R.string.signupInProgress;

        if (errorId != 0) {
            toast(errorId);
            return;
        }

        mContinueButton.setIndeterminateProgressMode(true);
        mContinueButton.setProgress(50);
        //UI.hide(mMnemonicText, mQrCodeIcon);
        mMnemonicText.setVisibility(View.INVISIBLE);

        // Create a random shuffle of word orders; the user will be asked
        // to verify the first VERIFY_COUNT words.
        Collections.shuffle(mWordChoices);

        for (int i = 0; i < mChoiceIsValid.length; ++i)
            mChoiceIsValid[i] = false;

        // Show the verification dialog
        final View v = getLayoutInflater().inflate(R.layout.dialog_verify_words, null, false);
        final MaterialDialog.Builder verifyDialogBuilder = new MaterialDialog.Builder(SignUpActivity.this)
                .title(R.string.enter_matching_words)
                .customView(v, true)
                .titleColorRes(R.color.textColor)
                .contentColorRes(android.R.color.white)
                .theme(Theme.LIGHT)
                .negativeText(R.string.cancel);

        // on debug and !mainnet add skip button
        if (BuildConfig.DEBUG || !Network.NETWORK.getId().equals(NetworkParameters.ID_MAINNET)) {
            verifyDialogBuilder.positiveText(R.string.pinSkipText);
            verifyDialogBuilder.onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    onMnemonicVerified();
                }
            });
        }
        mVerifyDialog = verifyDialogBuilder.build();
        UI.setDialogCloseHandler(mVerifyDialog, mVerifyDialogCB, false);
        final String[] words = mMnemonic.split(" ");

        final int index0 = mWordChoices.get(0);
        final int index1 = mWordChoices.get(1);
        final int index2 = mWordChoices.get(2);
        final int index3 = mWordChoices.get(3);
        final FlowLayout flowLayout = UI.find(v, R.id.flowLayout);
        flowLayout.setGravity(Gravity.CENTER);
        int col = 0;
        boolean firstFound = false;
        FlowLayout.LayoutParams params = new FlowLayout.LayoutParams(FlowLayout.LayoutParams.WRAP_CONTENT,
                FlowLayout.LayoutParams.WRAP_CONTENT);

        ArrayList <AutoCompleteTextView> autoCompleteTextViewList = new ArrayList<>();
        for (int i = 0; i < 24; ++i) {
            if (col == 3)
                col = 0;
            boolean first = false;
            if (i == index0) {
                if (!firstFound) {
                    first = true;
                    firstFound = true;
                }
                autoCompleteTextViewList.add(setupWord(words[i], flowLayout, 0, first));
            } else if (i == index1) {
                if (!firstFound) {
                    first = true;
                    firstFound = true;
                }
                autoCompleteTextViewList.add(setupWord(words[i], flowLayout, 1, first));
            } else if (i == index2) {
                if (!firstFound) {
                    first = true;
                    firstFound = true;
                }
                autoCompleteTextViewList.add(setupWord(words[i], flowLayout, 2, first));
            } else if (i == index3) {
                if (!firstFound) {
                    first = true;
                    firstFound = true;
                }
                autoCompleteTextViewList.add(setupWord(words[i], flowLayout, 3, first));
            } else {
                final AutoCompleteTextView textView = new AutoCompleteTextView(this);
                textView.setText(words[i]);
                textView.setFocusable(false);
                textView.setBackgroundColor(Color.TRANSPARENT);
                textView.setTextSize(WORDS_TEXT_SIZE);
                textView.setSingleLine();
                textView.setTextColor(getResources().getColor(R.color.secondaryTextColor));

                float density = getResources().getDisplayMetrics().density;
                int paddingDp = (int)(WORDS_PADDING * density);

                textView.setPadding(textView.getPaddingLeft() + paddingDp, textView.getPaddingTop(),
                        textView.getPaddingRight() + paddingDp, textView.getPaddingBottom());
                textView.setLayoutParams(params);
                //final GridLayout.Spec spec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                //final GridLayout.LayoutParams param = new GridLayout.LayoutParams(spec, spec);
                //textView.setLayoutParams(param);
                /*if (col == 0)
                    textView.setGravity(Gravity.CENTER | Gravity.END);
                else if (col == 1)*/
                    textView.setGravity(Gravity.CENTER);
                //else
                //    textView.setGravity(Gravity.CENTER | Gravity.START);
                flowLayout.addView(textView);
            }
            //final TextView space = new TextView(this);
            //space.setPadding(20,0,20,0);
            //space.setLayoutParams(params);
            //flowLayout.addView(space);
            col++;
            /*setupWord(v, R.id.verify_label_2, R.id.verify_word_2, words, 1);
            setupWord(v, R.id.verify_label_3, R.id.verify_word_3, words, 2);
            setupWord(v, R.id.verify_label_4, R.id.verify_word_4, words, 3);*/
        }

        setUniqueId(autoCompleteTextViewList.get(0));
        setUniqueId(autoCompleteTextViewList.get(1));
        setUniqueId(autoCompleteTextViewList.get(2));
        setUniqueId(autoCompleteTextViewList.get(3));
        autoCompleteTextViewList.get(0).setNextFocusForwardId(autoCompleteTextViewList.get(1).getId());
        autoCompleteTextViewList.get(1).setNextFocusForwardId(autoCompleteTextViewList.get(2).getId());
        autoCompleteTextViewList.get(2).setNextFocusForwardId(autoCompleteTextViewList.get(3).getId());

        UI.showDialog(mVerifyDialog, true);
        // TODO FIXME temp code, verification will be enabled in the future
        //onMnemonicVerified();
    }

    private void setUniqueId(final View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
            int id = 0;
            View v;
            do {
                ++id;
                v = findViewById(id);
            } while (v != null);
            view.setId(id);
        } else {
            view.setId(View.generateViewId());
            Log.d("TEMP_", "ID: " + view.getId());
        }
    }

    private void onMnemonicVerified() {
        if (mFromSettingsPage) {
            mService.setBackupState(true);
            finish();
            return;
        }
        mOnSignUp = mService.signup(mMnemonic);
        Futures.addCallback(mOnSignUp, new FutureCallback<LoginData>() {
            @Override
            public void onSuccess(final LoginData result) {
                onSignUpCompleted();
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
                                                final ArrayList<String> defaultExchanges = new ArrayList<>();
                                                defaultExchanges.add("BTCAVG");
                                                defaultExchanges.add("KRAKEN");
                                                defaultExchanges.add("BITSTAMP");

                                                String first_exchange = "";
                                                final String currentCurrency = Currency.getInstance(Locale.getDefault()).getCurrencyCode();
                                                boolean pairFound = false;
                                                final Iterator<List<String>> resultIterator = result.iterator();
                                                while (resultIterator.hasNext() && !pairFound) {
                                                    List<String> pair = resultIterator.next();
                                                    if (pair.get(0).equals(currentCurrency)) {
                                                        // set first exchange found
                                                        if (first_exchange.isEmpty())
                                                            first_exchange = pair.get(1);

                                                        // try to set exchange fround dafault list
                                                        final Iterator exchangesIterator = defaultExchanges.iterator();
                                                        while (exchangesIterator.hasNext() && !pairFound) {
                                                            String exchange = (String) exchangesIterator.next();
                                                            pairFound = pair.get(1).equals(exchange);
                                                            if (pairFound)
                                                                mService.setPricingSource(currentCurrency, exchange);
                                                        }
                                                    }
                                                }

                                                // set first exchange if no default exchange found
                                                if (!pairFound)
                                                    mService.setPricingSource(currentCurrency, first_exchange);
                                            }
                                        });
                                    }
                                }
                            });


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

    private void onNfcSignupButtonClicked() {
        if (mNfcWriteMnemonic != null) {
            mNfcWriteMnemonic.showDialog();
        }
    }

    private void setComplete(final boolean isComplete) {
        runOnUiThread(new Runnable() {
            public void run() {
                mContinueButton.setProgress(isComplete ? 100 : 0);
            }
        });
    }

    private void onSignUpCompleted() {
        mService.setBackupState(true);
        setComplete(true);
        mService.resetSignUp();
        mOnSignUp = null;
        final Intent activityPostBackup = new Intent(this, ActivityPostBackup.class);
        startActivity(activityPostBackup);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            mNfcWriteMnemonic.write(intent);
        }
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
        if (item.getItemId() == android.R.id.home) {
            // Respond to the action bar's Up/Home button
            onBackPressed();
            return true;
        } else {
            return item.getItemId() == R.id.action_settings || super.onOptionsItemSelected(item);
        }
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

    private AutoCompleteTextView setupWord(final String validWord, final FlowLayout flowLayout, final int index, final boolean first)  {
        final AutoCompleteTextView text = new AutoCompleteTextView(this);
        //final GridLayout.Spec spec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        //final GridLayout.LayoutParams param = new GridLayout.LayoutParams(spec, spec);
        //text.setLayoutParams(param);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine();
        float density = getResources().getDisplayMetrics().density;
        int paddingDp = (int)(WORDS_PADDING * density);

        text.setPadding(text.getPaddingLeft() + paddingDp, text.getPaddingTop(),
                text.getPaddingRight() + paddingDp, text.getPaddingBottom());

        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, WORDS_TEXT_SIZE, getResources().getDisplayMetrics());
        text.setWidth((px * validWord.length()) + paddingDp);  // FIXME: calcolo empirico
        text.setDropDownWidth(px * validWord.length() + paddingDp + 100);

        text.setTextSize(WORDS_TEXT_SIZE);
        //text.setText(validWord);
        if (first)
            text.requestFocus();

        FlowLayout.LayoutParams params = new FlowLayout.LayoutParams(FlowLayout.LayoutParams.WRAP_CONTENT,
                FlowLayout.LayoutParams.WRAP_CONTENT);
        text.setLayoutParams(params);

        flowLayout.addView(text);
        final ArrayAdapter<String> adapter;
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, MnemonicHelper.mWordsArray);
        text.setAdapter(adapter);
        text.setThreshold(1);
        text.addTextChangedListener(new UI.TextWatcher() {
            @Override
            public void onTextChanged(final CharSequence t, final int start,
                                      final int before, final int count) {
                final AutoCompleteTextView tv = text;
                onWordChanged(tv, index, validWord, true);
            }
        });
        text.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
                final AutoCompleteTextView tv = text;
                onWordChanged(text, index, validWord, false);
            }
        });
        return text;
    }

    private void onWordChanged(final AutoCompleteTextView text,
                               final int index, final String validWord, final boolean isTextChange) {
        if (isTextChange && text.isPerformingCompletion())
            return; // Let the call from onItemClick handle it
        final boolean isValid = UI.getText(text).equals(validWord);
        mChoiceIsValid[index] = isValid;
        if (isValid) {
            //UI.hide(label, text);
            text.dismissDropDown();
            if (text.getNextFocusForwardId() != View.NO_ID) {
                View nextElement = UI.find((View) text.getParent(), text.getNextFocusForwardId());
                nextElement.requestFocus();
            }
            UI.disable(text);
            if (areAllChoicesValid())
                UI.dismiss(this, mVerifyDialog); // Dismiss callback will continue
        }
    }

    private boolean areAllChoicesValid() {
        if (mChoiceIsValid == null)
            return false;
        for (final boolean isValid : mChoiceIsValid)
            if (!isValid)
                return false;
        return true;
    }

    private void onVerifyDismissed() {
        if (mVerifyDialog != null) {
            UI.show(mMnemonicText, mQrCodeIcon);
            mVerifyDialog = null;
            if (areAllChoicesValid()) {
                onMnemonicVerified();
            } else {
                mContinueButton.setIndeterminateProgressMode(false);
                mContinueButton.setProgress(0);
            }
        }
    }
}
