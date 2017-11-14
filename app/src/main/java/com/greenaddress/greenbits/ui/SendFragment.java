package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.annotation.IdRes;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.Pair;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.androidadvance.topsnackbar.TSnackbar;
import com.dd.CircularProgressButton;
import com.blockstream.libwally.Wally;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.greenaddress.greenapi.ConfidentialAddress;
import com.greenaddress.greenapi.CryptoHelper;
import com.greenaddress.greenapi.ElementsTransaction;
import com.greenaddress.greenapi.ElementsTransactionOutput;
import com.greenaddress.greenapi.GAException;
import com.greenaddress.greenapi.GATx;
import com.greenaddress.greenapi.JSONMap;
import com.greenaddress.greenapi.Network;
import com.greenaddress.greenapi.Output;
import com.greenaddress.greenapi.PreparedTransaction;
import com.greenaddress.greenbits.FormatMemo;
import com.greenaddress.greenbits.GaService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.Script;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.schildbach.wallet.ui.ScanActivity;

public class SendFragment extends SubaccountFragment {

    private static final String TAG = SendFragment.class.getSimpleName();
    private static final Pattern PATTERN_LABEL = Pattern.compile(".*label=(.*?)(&\\w+=.*)?");
    private static final Pattern PATTERN_MESSAGE = Pattern.compile(".*message=(.*?)(&\\w+=.*)?");

    private Dialog mSummary;
    private Dialog mTwoFactor;
    private EditText mAmountEdit;
    private EditText mAmountFiatEdit;
    private TextView mAmountBtcWithCommission;
    private EditText mRecipientEdit;
    private EditText mNoteText;
    private Spinner mFeeTargetCombo;
    private EditText mFeeTargetEdit;
    private TextView mNoteIcon;
    private CircularProgressButton mSendButton;
    private Switch mMaxButton;
    private TextView mMaxLabel;
    private TextView mScanIcon;
    private Button mClearAllFields;
    private String [] mMerchantInvoiceData = null;
    private Map<?, ?> mPayreqData;
    private boolean mFromIntentURI;
    private final boolean mSummaryInBtc[] = new boolean[1]; // State for fiat/btc toggle

    // vendor
    private FontFitEditText amountFieldFiat;
    private FontFitEditText amountFieldFiatWithoutCommission;
    private TextView addressView;
    private Integer commission = 10; //TODO
    private String address;
    private String fiat_amount;
    private String mBip70Memo;

    public static final int VENDOR_MESSAGE_MAX = 5;

    private int mSubaccount;
    private int mTwoFactorAttemptsRemaining;
    private AmountFields mAmountFields;

    private boolean mIsExchanger;
    private boolean mIsVendor;
    private Exchanger mExchanger;

    private RadioGroup mRadioGroupFee;
    private TextView mFeeDesc;
    private TextView mShowFeeSelector;
    private String[] mPrioritySummaries;
    private CheckedTextView mCheckHurryFee;
    private TextView mFeeDescHurry;

    private void processBitcoinURI(final BitcoinURI URI) {
        processBitcoinURI(URI, null, null);
    }

    private void processBitcoinURI(final BitcoinURI URI, final String confidentialAddress, Coin amount) {
        final GaService service = getGAService();
        final GaActivity gaActivity = getGaActivity();

        if (URI != null && URI.getPaymentRequestUrl() != null) {
            mSendButton.setText(R.string.sendPay);
            mSendButton.setIdleText(getResources().getString(R.string.sendPay));
            final ProgressBar bip70Progress = UI.find(mView, R.id.sendBip70ProgressBar);
            UI.show(bip70Progress);
            mRecipientEdit.setEnabled(false);
            UI.hide(mNoteIcon);
            Futures.addCallback(service.processBip70URL(URI.getPaymentRequestUrl()),
                    new CB.Toast<Map<?, ?>>(gaActivity) {
                        @Override
                        public void onSuccess(final Map<?, ?> result) {
                            mPayreqData = result;

                            final String name, note;
                            if (result.get("merchant_cn") != null)
                                name = (String) result.get("merchant_cn");
                            else
                                name = (String) result.get("request_url");

                            if (result.get("memo") != null) {
                                note = (String) result.get("memo");
                                mMerchantInvoiceData = FormatMemo.sanitizeMemo(note);
                            }


                            long amount = 0;
                            for (final Map<?, ?> out : (ArrayList<Map>) result.get("outputs"))
                                amount += ((Number) out.get("amount")).longValue();
                            final CharSequence amountStr;
                            if (amount > 0) {
                                amountStr = UI.setCoinText(service, null, null, Coin.valueOf(amount));
                            } else
                                amountStr = "";

                            gaActivity.runOnUiThread(new Runnable() {
                                public void run() {
                                    mRecipientEdit.setText(name);
                                    mSendButton.setEnabled(true);
                                    if (!amountStr.toString().isEmpty()) {
                                        mAmountEdit.setText(amountStr);
                                        mAmountFields.convertBtcToFiat();
                                        mAmountEdit.setEnabled(false);
                                        mAmountFiatEdit.setEnabled(false);
                                        UI.hide(mMaxButton, mMaxLabel);
                                    }
                                    UI.hide(bip70Progress);
                                }
                            });
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            super.onFailure(t);
                            gaActivity.runOnUiThread(new Runnable() {
                                public void run() {
                                    UI.hide(bip70Progress);
                                    mRecipientEdit.setEnabled(true);
                                    mSendButton.setEnabled(true);
                                    UI.show(mNoteIcon);
                                    mSendButton.setText(R.string.send);
                                    mSendButton.setIdleText(getResources().getString(R.string.send));
                                }
                            });
                        }
                    });
        } else {

            if (URI.getLabel() != null) {
                gaActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mNoteText.setText(URI.getLabel());
                        UI.show(mNoteText);
                    }
                });
            }

            if (confidentialAddress != null) {
                mRecipientEdit.setText(confidentialAddress);
            } else {
                mRecipientEdit.setText(URI.getAddress().toString());
                amount = URI.getAmount();
            }
            if (amount == null || amount.isZero() || amount.isNegative())
                return;

            final Coin uriAmount = amount;

            Futures.addCallback(service.getSubaccountBalance(mSubaccount), new CB.Op<Map<String, Object>>() {
                @Override
                public void onSuccess(final Map<String, Object> result) {
                    gaActivity.runOnUiThread(new Runnable() {
                            public void run() {
                                UI.setCoinText(service, null, mAmountEdit, uriAmount);
                                mAmountFields.convertBtcToFiat();
                                UI.disable(mAmountEdit, mAmountFiatEdit);
                                UI.hide(mMaxButton, mMaxLabel);
                            }
                    });
                }
            }, service.getExecutor());
        }
    }

    private static String fixUriParameter(
            final String parameter, final Pattern PATTERN, String uri) {
        final Matcher m = PATTERN.matcher(uri);
        if (m.matches() && m.group(1) != null) {
            Log.d(TAG, parameter + ": " + m.group(1));
            final String value = m.group(1);
            final String fixed = value.replaceAll("&", "%26").replaceAll("=", "%3D");

            if(!value.equals(fixed)) {
                uri = uri.replace(parameter + "=" + value, parameter + "=" + fixed);
                Log.d(TAG, parameter + " old: " + value);
                Log.d(TAG, parameter + " new: " + fixed);
                Log.d(TAG, "New uri: " + uri);
            }
        }
        return uri;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {

        Log.d(TAG, "onCreateView -> " + TAG);
        if (isZombieNoView())
            return null;

        final GaService service = getGAService();
        final GaActivity gaActivity = getGaActivity();

        if (savedInstanceState != null) {
            mIsExchanger = savedInstanceState.getBoolean("isExchanger", false);
            mIsVendor = savedInstanceState.getBoolean("isVendor", false);
        }

        final int viewId;
        if (mIsExchanger)
            viewId = R.layout.fragment_exchanger_sell;
        else if(mIsVendor)
            viewId = R.layout.fragment_vendor_summary;
        else
            viewId = R.layout.fragment_send;
        mView = inflater.inflate(viewId, container, false);

        if (mIsExchanger)
            mExchanger = new Exchanger(getContext(), service, mView, false, null);
        else
            mExchanger = null;
        mAmountFields = new AmountFields(service, getContext(), mView, mExchanger);

        mMerchantInvoiceData = null;

        if (savedInstanceState != null) {
            final Boolean pausing = savedInstanceState.getBoolean("pausing", false);
            mAmountFields.setIsPausing(pausing);
        }
        mSubaccount = service.getCurrentSubAccount();

        mSendButton = UI.find(mView, R.id.sendSendButton);
        mMaxButton = UI.find(mView, R.id.sendMaxButton);
        mMaxLabel = UI.find(mView, R.id.sendMaxLabel);
        mNoteText = UI.find(mView, R.id.sendToNoteText);
        mNoteIcon = UI.find(mView, R.id.sendToNoteIcon);
        mFeeTargetEdit = UI.find(mView, R.id.feerateTextEdit);
        mFeeTargetCombo = UI.find(mView, R.id.feeTargetCombo);

        final RadioButton customIcon = UI.find(mView, R.id.customIcon);
        final RadioButton instantIcon = UI.find(mView, R.id.instantIcon);
        instantIcon.setTextColor(Color.parseColor("#46d150"));
        final RadioButton btnEco = UI.find(mView, R.id.btnEco);
        final RadioButton btnSuperEco = UI.find(mView, R.id.btnSuperEco);
        final RadioButton btnNormal= UI.find(mView, R.id.btnNormal);
        final RadioButton btnHi = UI.find(mView, R.id.btnHi);
        final Typeface typeface = Typeface.createFromAsset(getActivity().getAssets(), "fonts/fontawesome-webfont.ttf");
        customIcon.setTypeface(typeface);
        btnEco.setTypeface(typeface);
        btnSuperEco.setTypeface(typeface);
        btnNormal.setTypeface(typeface);
        btnHi.setTypeface(typeface);

        final boolean isDev = service.cfg("dev_mode").getBoolean("enabled", false);
        UI.showIf(isDev, customIcon);

        mPrioritySummaries = getResources().getStringArray(R.array.fee_target_summaries);
        mFeeDescHurry = UI.find(mView, R.id.feeDescHurry);
        mFeeDescHurry.setText(mPrioritySummaries[1]);
        mCheckHurryFee = UI.find(mView, R.id.checkHurryFee);
        mCheckHurryFee.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCheckHurryFee.isChecked()) {
                    mCheckHurryFee.setChecked(false);
                    // set eco
                    mFeeTargetCombo.setSelection(3);
                    onNewFeeTargetSelected(3);
                    mFeeDescHurry.setVisibility(View.INVISIBLE);
                } else {
                    mCheckHurryFee.setChecked(true);
                    // set hurry
                    mFeeTargetCombo.setSelection(1);
                    onNewFeeTargetSelected(1);
                    UI.show(mFeeDescHurry);
                }
            }
        });

        final Boolean advancedOptionsValue = service.cfg("advanced_options").getBoolean("enabled", false);
        mRadioGroupFee = UI.find(mView, R.id.radiogroupFee);
        mShowFeeSelector = UI.find(mView, R.id.showFeeSelector);
        mShowFeeSelector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mRadioGroupFee.getVisibility() == View.VISIBLE) {
                    if (mRadioGroupFee.getCheckedRadioButtonId() == R.id.customIcon) {
                        mRadioGroupFee.setVisibility(View.GONE);
                        UI.show(mFeeTargetEdit);
                    } else {
                        mRadioGroupFee.setVisibility(View.INVISIBLE);
                    }
                } else {
                    mRadioGroupFee.setVisibility(View.VISIBLE);
                    UI.hide(mFeeTargetEdit);
                }
            }
        });
        mFeeDesc = UI.find(mView, R.id.feeDesc);
        mRadioGroupFee.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, @IdRes int i) {
                radioGroup.setVisibility(View.INVISIBLE);
                mFeeDesc.setTextColor(getResources().getColor(R.color.secondaryTextColor));
                switch (i) {
                    case R.id.btnHi:
                        mFeeTargetCombo.setSelection(0);
                        onNewFeeTargetSelected(0);
                        mShowFeeSelector.setText(getResources().getStringArray(R.array.send_fee_target_choices_instant)[0]);
                        mFeeDesc.setText(mPrioritySummaries[0]);
                        break;
                    case R.id.btnNormal:
                        mFeeTargetCombo.setSelection(1);
                        onNewFeeTargetSelected(1);
                        mShowFeeSelector.setText(getResources().getStringArray(R.array.send_fee_target_choices_instant)[1]);
                        mFeeDesc.setText(mPrioritySummaries[1]);
                        break;
                    case R.id.btnEco:
                        setDefaultFee();
                        break;
                    case R.id.customIcon:
                        mFeeTargetCombo.setSelection(4);
                        onNewFeeTargetSelected(4);
                        mShowFeeSelector.setText(getResources().getStringArray(R.array.send_fee_target_choices_instant)[4]);
                        mFeeDesc.setText(mPrioritySummaries[4]);
                        radioGroup.setVisibility(View.GONE);
                        break;
                    case R.id.btnSuperEco:
                        mFeeTargetCombo.setSelection(5);
                        onNewFeeTargetSelected(5);
                        mShowFeeSelector.setText(getResources().getStringArray(R.array.send_fee_target_choices_instant)[5]);
                        mFeeDesc.setText(mPrioritySummaries[5]);
                        mFeeDesc.setTextColor(getResources().getColor(R.color.lightRed));
                        break;
                    case R.id.instantIcon:
                        mFeeTargetCombo.setSelection(6);
                        onNewFeeTargetSelected(6);
                        mShowFeeSelector.setText(getResources().getStringArray(R.array.send_fee_target_choices_instant)[6]);
                        mFeeDesc.setText(mPrioritySummaries[6]);
                        break;
                }
            }
        });
        populateFeeCombo();

        // set eco priority
        setDefaultFee();
        if (advancedOptionsValue) {
            UI.show(UI.find(mView, R.id.layoutFeePro));
            UI.hide(UI.find(mView, R.id.layoutFeeBase));
        } else {
            UI.show(UI.find(mView, R.id.layoutFeeBase));
            UI.hide(UI.find(mView, R.id.layoutFeePro));
        }


        if (mIsVendor) {
            address = this.getArguments().getString("address");
            fiat_amount = this.getArguments().getString("fiat_amount");

            new AmountFields(getGAService(), getContext(), mView, null);
            amountFieldFiat = UI.find(mView, R.id.sendAmountFiatEditText);
            amountFieldFiatWithoutCommission = UI.find(mView, R.id.sendAmountFiatEditTextWithoutCommission);
            addressView = UI.find(mView, R.id.btcAddress);

            String addressTwoLines = String.format("%s\n%s", address.substring(0, 18), address.substring(18));
            addressView.setText(addressTwoLines);  // TODO sanitize address

            amountFieldFiatWithoutCommission.setText(fiat_amount);

            final Double amount = Double.valueOf(fiat_amount);
            final Double amountWithCommission = amount * 0.90; // TODO
            amountFieldFiat.setText(amountWithCommission.toString());
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // pre-Material Design the label was already a part of the switch
            UI.hide(mMaxLabel);
        }

        mAmountEdit = UI.find(mView, R.id.sendAmountEditText);
        mAmountFiatEdit = UI.find(mView, R.id.sendAmountFiatEditText);
        if (mIsExchanger)
            mAmountBtcWithCommission = UI.find(mView, R.id.amountBtcWithCommission);
        mRecipientEdit = UI.find(mView, R.id.sendToEditText);
        mScanIcon = UI.find(mView, R.id.sendScanIcon);
        mClearAllFields = UI.find(mView, R.id.clearAllFields);

        if (mIsExchanger && GaService.IS_ELEMENTS) {
            mRecipientEdit.setHint(R.string.send_to_address);
            UI.hide(mAmountFiatEdit);
        }

        final FontAwesomeTextView bitcoinUnitText = UI.find(mView, R.id.sendBitcoinUnitText);
        UI.setCoinText(service, bitcoinUnitText, null, null);

        if (container.getTag(R.id.tag_amount) != null)
            mAmountEdit.setText((String) container.getTag(R.id.tag_amount));

        if (container.getTag(R.id.tag_bitcoin_uri) != null) {
            String uri = ((Uri) container.getTag(R.id.tag_bitcoin_uri)).toString();
            BitcoinURI bitcoinUri = null;
            if (GaService.IS_ELEMENTS) {
                String addr = null;
                Coin amount = null;
                try {
                    final Pair<String, Coin> res = ConfidentialAddress.parseBitcoinURI(Network.NETWORK, uri.toString());
                    addr = res.first;
                    amount = res.second;
                } catch (final BitcoinURIParseException e) {
                    gaActivity.toast(R.string.err_send_invalid_bitcoin_uri);
                }
                if (addr != null)
                    processBitcoinURI(null, addr, amount);
            } else {
                // Fix label and message parameters
                uri = fixUriParameter("label", PATTERN_LABEL, uri);
                uri = fixUriParameter("message", PATTERN_MESSAGE, uri);

                try {
                    bitcoinUri = new BitcoinURI(uri.toString());
                } catch (final BitcoinURIParseException e) {
                    gaActivity.toast(R.string.err_send_invalid_bitcoin_uri);
                }
                if (bitcoinUri != null)
                    processBitcoinURI(bitcoinUri);
            }
            // set intent uri flag only if the call arrives from non internal qr scan
            if (container.getTag(R.id.internal_qr) == null) {
                mFromIntentURI = true;
                container.setTag(R.id.internal_qr, null);
            }
            container.setTag(R.id.tag_bitcoin_uri, null);
        }

        mSendButton.setIndeterminateProgressMode(true);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mSendButton.getProgress() != 0) {
                    Log.d(TAG, "double tap on send button, ignored");
                    return;
                }
                mSendButton.setProgress(50);
                // FIXME: Instead of checking the state here, enable/disable sendButton when state changes
                if (!service.isLoggedIn()) {
                    gaActivity.toast(R.string.err_send_not_connected_will_resume);
                    mSendButton.setProgress(0);
                    return;
                }
                final String recipient = mIsVendor ? address : UI.getText(mRecipientEdit);

                if (recipient.isEmpty()) {
                    gaActivity.toast(R.string.err_send_need_recipient);
                    mSendButton.setProgress(0);
                    return;
                }
                onSendButtonClicked(recipient);
            }
        });

        if (GaService.IS_ELEMENTS) {
            UI.disable(mMaxButton); // FIXME: Sweeping not available in elements
            UI.hide(mMaxButton, mMaxLabel, mFeeTargetCombo);
        } else {

            // warning dialog about max amount
            final Dialog maxDialog = UI.popup(getGaActivity(), R.string.warning)
                    .cancelable(false)
                    .content(R.string.warningMaxAmount)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(final MaterialDialog dialog, final DialogAction which) {
                            final Boolean isChecked = mMaxButton.isChecked();
                            UI.disableIf(isChecked, mAmountEdit, mAmountFiatEdit);
                            mAmountEdit.setText(getString(R.string.all));
                        }
                    })
                    .onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(final MaterialDialog dialog, final DialogAction which) {
                            mMaxButton.setChecked(!mMaxButton.isChecked());
                            dialog.cancel();
                        }
                    }).build();
            mMaxButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    final Boolean isChecked = mMaxButton.isChecked();
                    UI.disableIf(isChecked, mAmountEdit, mAmountFiatEdit);
                    if (!isChecked) {
                        mAmountEdit.setText(R.string.empty);
                        return;
                    }
                    UI.showDialog(maxDialog);
                }
            });
        }

        mScanIcon.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(final View v) {
                                            //New Marshmallow permissions paradigm
                                            final String[] perms = {"android.permission.CAMERA"};
                                            if (Build.VERSION.SDK_INT>Build.VERSION_CODES.LOLLIPOP_MR1 &&
                                                    gaActivity.checkSelfPermission(perms[0]) != PackageManager.PERMISSION_GRANTED) {
                                                final int permsRequestCode = 100;
                                                gaActivity.requestPermissions(perms, permsRequestCode);
                                            } else {
                                                final Intent qrcodeScanner = new Intent(gaActivity, ScanActivity.class);
                                                qrcodeScanner.putExtra("sendAmount", UI.getText(mAmountEdit));
                                                int requestCode = TabbedMainActivity.REQUEST_SEND_QR_SCAN;
                                                if (mIsExchanger)
                                                    requestCode = TabbedMainActivity.REQUEST_SEND_QR_SCAN_EXCHANGER;
                                                gaActivity.startActivityForResult(qrcodeScanner, requestCode);
                                            }
                                        }
                                    }
        );

        if (!mIsExchanger && !mIsVendor) {
            mClearAllFields.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                // do nothing during loading
                if (mSendButton.getProgress() == 0)
                    resetAllFields();
                }
            });
        }

        mNoteIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mNoteText.getVisibility() == View.VISIBLE) {
                    mNoteIcon.setText(R.string.fa_pencil);
                    UI.clear(mNoteText);
                    UI.hide(mNoteText);
                } else {
                    mNoteIcon.setText(R.string.fa_remove);
                    UI.show(mNoteText);
                    mNoteText.requestFocus();
                }
            }
        });

        makeBalanceObserver(mSubaccount);
        if (service.getCoinBalance(mSubaccount) != null)
            onBalanceUpdated();

        registerReceiver();
        return mView;
    }

    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        Log.d(TAG, "onViewStateRestored -> " + TAG);
        super.onViewStateRestored(savedInstanceState);
        if (mAmountFields != null)
            mAmountFields.setIsPausing(false);
        if (mIsExchanger)
            mExchanger.conversionFinish();
    }

    @Override
    protected void onBalanceUpdated() {
        final GaService service = getGAService();
        final FontAwesomeTextView sendSubAccountBalanceUnit = UI.find(mView, R.id.sendSubAccountBalanceUnit);
        final TextView sendSubAccountBalance = UI.find(mView, R.id.sendSubAccountBalance);
        final Coin balance = service.getCoinBalance(mSubaccount);
        UI.setCoinText(service, sendSubAccountBalanceUnit, sendSubAccountBalance, balance);

        final int nChars = sendSubAccountBalance.getText().length() + sendSubAccountBalanceUnit.getText().length();
        final int size = Math.min(50 - nChars, 24);
        sendSubAccountBalance.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        sendSubAccountBalanceUnit.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (service.showBalanceInTitle())
            UI.hide(sendSubAccountBalance, sendSubAccountBalanceUnit);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume -> " + TAG);

        if (isZombie())
            return;

        if (mAmountFields != null)
            mAmountFields.setIsPausing(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause -> " + TAG);
        if (mAmountFields != null)
            mAmountFields.setIsPausing(true);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mAmountFields != null)
            outState.putBoolean("pausing", mAmountFields.isPausing());
        outState.putBoolean("isExchanger", mIsExchanger);
        outState.putBoolean("isVendor", mIsVendor);
    }

    public void onDestroyView() {
        super.onDestroyView();
        Log.d(TAG, "onDestroyView -> " + TAG);
        mSummary = UI.dismiss(getActivity(), mSummary);
        mTwoFactor = UI.dismiss(getActivity(), mTwoFactor);
    }

    @Override
    protected void onSubaccountChanged(final int newSubAccount) {
        mSubaccount = newSubAccount;

        if (!isPageSelected()) {
            Log.d(TAG, "Subaccount changed while page hidden");
            setIsDirty(true);
            return;
        }
        updateBalance();
    }

    private void updateBalance() {
        Log.d(TAG, "Updating balance");
        if (isZombie())
            return;

        makeBalanceObserver(mSubaccount);
        getGAService().updateBalance(mSubaccount);
        populateFeeCombo();
    }

    public void setPageSelected(final boolean isSelected) {
        final boolean needReload = isDirty();
        super.setPageSelected(isSelected);
        if (!isZombie() && isSelected)
            getGaActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (needReload && isSelected) {
            Log.d(TAG, "Dirty, reloading");
            updateBalance();
            if (!isZombie())
                setIsDirty(false);
        } else if (!isZombie() && isSelected && getGAService().getTotalBalance() > 0) {
            // show vendor snackbar on top only for 5 times
            final Integer vendorMessageCount = getGAService().cfg("vendor_message").getInt("count", 0);
            if (vendorMessageCount < VENDOR_MESSAGE_MAX) {
                showVendorSnackbar();
                getGAService().cfgEdit("vendor_message").putInt("count", vendorMessageCount + 1).apply();
            }
        }
    }

    private void populateFeeCombo() {
        if (GaService.IS_ELEMENTS)
            return; // FIXME: No custom fees for elements

        // Make the dropdown exclude instant if not available
        final GaService service = getGAService();
        final boolean is2of3 = service.findSubaccountByType(mSubaccount, "2of3") != null;
        final int id = is2of3 ? R.array.send_fee_target_choices : R.array.send_fee_target_choices_instant;
        UI.showIf(!is2of3, UI.find(mView, R.id.instantIcon));
        final ArrayAdapter<CharSequence> a;
        a = ArrayAdapter.createFromResource(getActivity(), id, android.R.layout.simple_spinner_item);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mFeeTargetCombo.setAdapter(a);

        mFeeTargetCombo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                //onNewFeeTargetSelected(pos);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        // Default priority to the users default priority from settings
        /*final int currentPriority = service.getDefaultTransactionPriority();
        for (int i = 0; i < UI.FEE_TARGET_VALUES.length; ++i) {
            if (currentPriority == UI.FEE_TARGET_VALUES[i].getBlock()) {
                mFeeTargetCombo.setSelection(i);
                switch (i) {
                    case 0:
                        mRadioGroupFee.check(R.id.btnHi);
                        break;
                    case 1:
                        mRadioGroupFee.check(R.id.btnNormal);
                        break;
                    case 3:
                        mRadioGroupFee.check(R.id.btnEco);
                        break;
                    case 4:
                        mRadioGroupFee.check(R.id.customIcon);
                        break;
                    case 5:
                        mRadioGroupFee.check(R.id.instantIcon);
                        break;
                }
            }
        }*/
        // ignore user default priority and use eco
        mRadioGroupFee.check(R.id.btnEco);
    }

    private void onNewFeeTargetSelected(final int index) {
        // Show custom fee entry when custom fee is selected
        final boolean isCustom = UI.FEE_TARGET_VALUES[index].equals(UI.FEE_TARGET.CUSTOM);
        UI.showIf(isCustom, mFeeTargetEdit);
        if (isCustom)
            mFeeTargetEdit.setText(getGAService().cfg().getString("default_feerate", ""));

        final boolean isSuperEco = UI.FEE_TARGET_VALUES[index].equals(UI.FEE_TARGET.SUPER_ECONOMY);
        if (isSuperEco)
            mFeeTargetEdit.setText(UI.SUPER_ECONOMY_FEERATE);
    }

    public void showVendorSnackbar() {
        final TSnackbar tsnackbar = TSnackbar.make(getActivity().findViewById(R.id.container), R.string.vendorMessage, TSnackbar.LENGTH_INDEFINITE);
        tsnackbar.setAction(R.string.sellBitcoin, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(getActivity(), VendorActivity.class);
                startActivity(intent);
                getActivity().overridePendingTransition(R.anim.slide_from_right, R.anim.fade_out);
            }
        });
        tsnackbar.setActionTextColor(Color.WHITE);
        final View snackbarView = tsnackbar.getView();
        snackbarView.setBackgroundColor(getResources().getColor(R.color.accent));
        TextView textView = (TextView) snackbarView.findViewById(com.androidadvance.topsnackbar.R.id.snackbar_text);
        textView.setMaxLines(5);
        textView.setTextColor(Color.BLACK);
        tsnackbar.show();
        new CountDownTimer(7*1000, 1000) {
            @Override
            public void onTick(long l) {
            }

            public void onFinish() {
                tsnackbar.dismiss();
            }
        }.start();
    }

    private void resetAllFields() {
        mAmountEdit.setText("");
        mAmountFiatEdit.setText("");
        mRecipientEdit.setText("");
        UI.enable(mAmountEdit, mAmountFiatEdit,  mRecipientEdit);
        mMaxButton.setChecked(false);
        UI.show(mMaxButton, mMaxLabel);

        mNoteIcon.setText(R.string.fa_pencil);
        mNoteText.setText("");
        mNoteText.setVisibility(View.INVISIBLE);

        mSendButton.setText(R.string.send);
        mSendButton.setIdleText(getResources().getString(R.string.send));
        mSendButton.setProgress(0);
        mSendButton.setEnabled(true);

        mMerchantInvoiceData = null;
        mPayreqData = null;

        UI.clear(mFeeTargetEdit);
        UI.hide(mFeeTargetEdit);
        mFeeDescHurry.setVisibility(View.INVISIBLE);
        mCheckHurryFee.setChecked(false);
        mRadioGroupFee.setVisibility(View.INVISIBLE);
        mFeeDesc.setTextColor(getResources().getColor(R.color.secondaryTextColor));
        setDefaultFee();
    }

    private Coin getSendAmount() {
        try {
            final TextView amountEdit = mIsExchanger ? mAmountBtcWithCommission : mAmountEdit;
            return UI.parseCoinValue(getGAService(), UI.getText(amountEdit));
        } catch (final IllegalArgumentException e) {
            return Coin.ZERO;
        }
    }

    private void onSendButtonClicked(String recipient) {
        final GaService service = getGAService();
        final GaActivity gaActivity = getGaActivity();

        final JSONMap privateData = new JSONMap();
        String memo = UI.getText(mNoteText);
        if (mIsVendor) {
            final String currency = getGAService().getFiatCurrency();
            memo = String.format("%s %s +%s%% via %s",
                    currency, fiat_amount, commission, getGAService().getString(R.string.app_name));
        }
        if (!memo.isEmpty())
            privateData.mData.put("memo", memo);

        if (mIsExchanger)
            privateData.mData.put("memo", Exchanger.TAG_EXCHANGER_TX_MEMO);

        if (mSubaccount != 0)
            privateData.mData.put("subaccount", mSubaccount);

        final UI.FEE_TARGET feeTarget = UI.FEE_TARGET_VALUES[mFeeTargetCombo.getSelectedItemPosition()];
        if (feeTarget.equals(UI.FEE_TARGET.INSTANT))
            privateData.mData.put("instant", true);

        Coin amount = getSendAmount();

        final boolean isBip70 = mPayreqData != null;
        final Integer bip70Amount;
        final String bip70Recipient;
        final String bip70Script;
        final String bip70MerchantData;
        final String bip70PayreqUrl;
        if (feeTarget.equals(UI.FEE_TARGET.CUSTOM) || feeTarget.equals(UI.FEE_TARGET.ECONOMY) || feeTarget.equals(UI.FEE_TARGET.SUPER_ECONOMY)) {
            final Object rbf_optin = service.getUserConfig("replace_by_fee");
            if (rbf_optin == null || !((Boolean) rbf_optin)) {
                gaActivity.toast(R.string.forcedRbf);
                privateData.mData.put("rbf_optin", true);
            }
        }


        final String fRecipient;
        final Coin fAmount;

        if (isBip70) {

            ArrayList outputs = (ArrayList) mPayreqData.get("outputs");
            if (outputs.size() != 1) {
                // gaActivity.toast("Only payment requests with 1 output are supported", mSendButton);
                Log.e(TAG, "Only bip70 payment requests with 1 output are supported");
                return;
            }
            final HashMap output = (HashMap) outputs.get(0);
            bip70Amount = (Integer) output.get("amount");
            bip70Recipient = (String) output.get("address");
            bip70Script = (String) output.get("script");

            mBip70Memo = (String) mPayreqData.get("memo");
            bip70MerchantData = (String) mPayreqData.get("merchant_data");
            bip70PayreqUrl = (String) mPayreqData.get("payreq_url");

            fRecipient = bip70Recipient;
            fAmount = Coin.valueOf(bip70Amount);
        } else {
            bip70Amount = null;
            bip70Recipient = null;
            bip70Script = null;
            mBip70Memo = null;
            bip70MerchantData = null;
            bip70PayreqUrl = null;

            fRecipient = recipient;
            fAmount = amount;
        }

        final boolean sendAll = mMaxButton.isChecked();
        final boolean validAddress = GaService.isValidAddress(fRecipient);
        final boolean validAmount = sendAll || fAmount.isGreaterThan(Coin.ZERO);

        int messageId = 0;
        if (!validAddress && !validAmount)
            messageId = R.string.invalidAmountAndAddress;
        else if (!validAddress)
            messageId = R.string.invalidAddress;
        else if (!validAmount)
            messageId = R.string.invalidAmount;

        if (messageId != 0) {
            mSendButton.setProgress(0);
            gaActivity.toast(messageId);
            return;
        }

        mSendButton.setProgress(50);
        final int numConfs;
        if (feeTarget.equals(UI.FEE_TARGET.INSTANT))
            numConfs = 6; // Instant requires at least 6 confs
        else if (Network.NETWORK == MainNetParams.get())
            numConfs = 1; // Require 1 conf before spending on mainnet
        else
            numConfs = 0; // Allow 0 conf for networks with no real-world value

        // For 2of2 accounts we first try to spend older coins to avoid
        // having to re-deposit them. If that fails (and always for 2of3
        // accounts) we try to use the minimum number of utxos instead.
        final boolean is2Of3 = service.findSubaccountByType(mSubaccount, "2of3") != null;
        final boolean minimizeInputs = is2Of3;
        final boolean filterAsset = true;

        final Coin feeRate;
        try {
            final String userRate = UI.getText(mFeeTargetEdit);
            /*if (feeTarget.equals(UI.FEE_TARGET.CUSTOM)) {
                final Object rbf_optin = service.getUserConfig("replace_by_fee");
                if (rbf_optin == null || !((Boolean) rbf_optin)) {
                    gaActivity.toast(R.string.custom_requires_rbf, mSendButton);
                    return;
                }
            }*/

            if ((feeTarget.equals(UI.FEE_TARGET.CUSTOM) || (feeTarget.equals(UI.FEE_TARGET.SUPER_ECONOMY))) &&
                (userRate.isEmpty() || !service.isValidFeeRate(userRate))) {
                // Change invalid feerates to the minimum
                feeRate = service.getMinFeeRate();
                final String message = getString(R.string.feerate_changed, feeRate.longValue());
                gaActivity.toast(message);
            } else {
                if (feeTarget.equals(UI.FEE_TARGET.NORMAL)) {
                    // add 10% on the normal TX to be different from GA xD
                    feeRate = Coin.valueOf((long)(getFeeRate(feeTarget).getValue() * 1.1));
                } else {
                    feeRate = getFeeRate(feeTarget);

                }
            }
        } catch (final GAException e) {
            gaActivity.toast(R.string.instantUnavailable, mSendButton);
            return;
        }

        CB.after(service.getAllUnspentOutputs(numConfs, mSubaccount, filterAsset),
                 new CB.Toast<List<JSONMap>>(gaActivity, mSendButton) {
            @Override
            public void onSuccess(final List<JSONMap> utxos) {
                int ret = R.string.insufficientFundsText;
                if (!utxos.isEmpty()) {
                    GATx.sortUtxos(utxos, minimizeInputs);
                    ret = createRawTransaction(utxos, fRecipient, fAmount, privateData, sendAll, feeRate);
                    if (ret == R.string.insufficientFundsText && !minimizeInputs && utxos.size() > 1) {
                        // Not enough money using nlocktime outputs first:
                        // Try again using the largest values first
                        GATx.sortUtxos(utxos, true);
                        ret = createRawTransaction(utxos, fRecipient, fAmount, privateData, sendAll, feeRate);
                    }
                }
                if (ret != 0)
                    gaActivity.toast(ret, mSendButton);
            }
        });
    }

    private void onTransactionPrepared(final PreparedTransaction ptx,
                                       final String recipient, final Coin amount,
                                       final JSONMap privateData) {
        final GaService service = getGAService();
        final GaActivity gaActivity = getGaActivity();

        final Coin verifyAmount = mMaxButton.isChecked() ? null : amount;
        CB.after(service.validateTx(ptx, recipient, verifyAmount), new CB.Toast<Coin>(gaActivity, mSendButton) {
            @Override
            public void onSuccess(final Coin fee) {
                final boolean haveTwoFactor = service.hasAnyTwoFactor();
                // can be non-UI because validation talks to USB if hw wallet is used
                gaActivity.runOnUiThread(new Runnable() {
                    public void run() {
                        mSendButton.setProgress(0);
                        final Coin sendAmount, sendFee;
                        if (mMaxButton.isChecked()) {
                            // 'fee' is actually the sent amount when passed amount=null
                            sendAmount = fee;
                            sendFee = service.getCoinBalance(mSubaccount).subtract(sendAmount);
                        } else {
                            sendAmount = amount;
                            sendFee = fee;
                        }
                        final boolean skipChoice = !ptx.mRequiresTwoFactor || !haveTwoFactor;
                        mTwoFactor = UI.popupTwoFactorChoice(gaActivity, service, skipChoice,
                                                             new CB.Runnable1T<String>() {
                            public void run(final String method) {
                                onTransactionValidated(ptx, null, recipient, sendAmount, method,
                                                       sendFee, privateData, null);
                            }
                        });
                        if (mTwoFactor != null)
                            mTwoFactor.show();
                    }
                });
            }
        });
    }

    private void onTransactionValidated(final PreparedTransaction ptx,
                                        final Transaction signedRawTx,
                                        final String recipient, final Coin amount,
                                        final String method, final Coin fee,
                                        final JSONMap privateData, final JSONMap underLimits) {
        Log.i(TAG, "onTransactionValidated( params " + method + ' ' + fee + ' ' + amount + ' ' + recipient + ')');
        final GaService service = getGAService();
        final GaActivity gaActivity = getGaActivity();

        final Map<String, Object> twoFacData;

        if (method == null)
            twoFacData = null;
        else if (method.equals("limit")) {
            twoFacData = new HashMap<>();
            twoFacData.put("try_under_limits_spend", underLimits.mData);
        } else {
            twoFacData = new HashMap<>();
            twoFacData.put("method", method);
            if (!method.equals("gauth")) {
                if (underLimits != null)
                    for (final String key : underLimits.mData.keySet())
                        twoFacData.put("send_raw_tx_" + key, underLimits.get(key));
                if (GaService.IS_ELEMENTS) {
                    underLimits.mData.remove("ephemeral_privkeys");
                    underLimits.mData.remove("blinding_pubkeys");
                }
                final Map<String, Object> twoFactorData;
                twoFactorData = underLimits == null ? null : underLimits.mData;;
                service.requestTwoFacCode(method, ptx == null ? "send_raw_tx" : "send_tx", twoFactorData);
            }
        }

        final View v = gaActivity.getLayoutInflater().inflate(R.layout.dialog_new_transaction, null, false);
        final Button showFiatBtcButton = UI.find(v, R.id.newTxShowFiatBtcButton);
        final TextView recipientText = UI.find(v, R.id.newTxRecipientText);
        final EditText newTx2FACodeText = UI.find(v, R.id.newTx2FACodeText);
        final String fiatAmount = service.coinToFiat(amount);
        final String fiatFee = service.coinToFiat(fee);
        final String fiatCurrency = service.getFiatCurrency();

        mSummaryInBtc[0] = true;
        UI.setCoinText(service, v, R.id.newTxAmountUnitText, R.id.newTxAmountText, amount);
        UI.setCoinText(service, v, R.id.newTxFeeUnit, R.id.newTxFeeText, fee);
        if (!GaService.IS_ELEMENTS) {
            showFiatBtcButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View btn) {
                    // Toggle display between fiat and BTC
                    if (mSummaryInBtc[0]) {
                        AmountFields.changeFiatIcon((FontAwesomeTextView) UI.find(v, R.id.newTxAmountUnitText), fiatCurrency, false);
                        AmountFields.changeFiatIcon((FontAwesomeTextView) UI.find(v, R.id.newTxFeeUnit), fiatCurrency, false);
                        UI.setAmountText((TextView) UI.find(v, R.id.newTxAmountText), fiatAmount);
                        UI.setAmountText((TextView) UI.find(v, R.id.newTxFeeText), fiatFee);
                    } else {
                        UI.setCoinText(service, v, R.id.newTxAmountUnitText, R.id.newTxAmountText, amount);
                        UI.setCoinText(service, v, R.id.newTxFeeUnit, R.id.newTxFeeText, fee);
                    }
                    mSummaryInBtc[0] = !mSummaryInBtc[0];
                    showFiatBtcButton.setText(mSummaryInBtc[0] ? R.string.show_fiat : R.string.show_btc);
                }
            });
        }

        if (mPayreqData != null)
            recipientText.setText(recipient);
        else
            recipientText.setText(String.format("%s\n%s\n%s",
                                  recipient.substring(0, 12),
                                  recipient.substring(12, 24),
                                  recipient.substring(24)));

        if (method != null && !method.equals("limit")) {
            final TextView twoFAText = UI.find(v, R.id.newTx2FATypeText);
            UI.show(twoFAText, newTx2FACodeText);
            twoFAText.setText(String.format("2FA %s code", method));
        }

        mTwoFactorAttemptsRemaining = 3;
        mSummary = UI.popup(gaActivity, R.string.newTxTitle, R.string.send, R.string.cancel)
                .customView(v, true)
                .autoDismiss(false)
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(final MaterialDialog dialog, final DialogAction which) {
                        UI.dismiss(null, SendFragment.this.mSummary);
                    }
                })
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(final MaterialDialog dialog, final DialogAction which) {
                        mSendButton.setProgress(50);
                        final String code = UI.getText(newTx2FACodeText);
                        if (twoFacData != null && !method.equals("limit")) {
                            if (code.length() < 6) {
                                UI.toast(getActivity(), getString(R.string.malformed_code), mSendButton);
                                return;
                            }
                            twoFacData.put("code", code);
                        }

                        final ListenableFuture<String> sendFn;
                        if (signedRawTx != null) {
                            if (mBip70Memo != null)
                                privateData.mData.put("memo", mBip70Memo);
                            sendFn = service.sendRawTransaction(signedRawTx, twoFacData, privateData);
                        }
                        else
                            sendFn = service.signAndSendTransaction(ptx, twoFacData);

                        Futures.addCallback(sendFn, new CB.Toast<String>(gaActivity, mSendButton) {
                            @Override
                            public void onSuccess(final String txHash) {
                                if (mMerchantInvoiceData != null) {
                                    final String invoiceInfo = mMerchantInvoiceData[0];
                                    final String paymentProcessor = mMerchantInvoiceData[1];
                                    final String merchantName;
                                    if (mMerchantInvoiceData.length == 3)
                                        merchantName = mMerchantInvoiceData[2];
                                    else
                                        merchantName = null;
                                    final GaService service = getGAService();
                                    if (txHash != null)
                                        service.saveMerchantInvoiceData(txHash, merchantName, invoiceInfo, paymentProcessor);
                                }
                                UI.dismiss(SendFragment.this.getActivity(), SendFragment.this.mSummary);
                                onTransactionSent();
                            }

                            @Override
                            public void onFailure(final Throwable t) {
                                final SendFragment fragment = SendFragment.this;
                                final Activity activity = fragment.getActivity();
                                if (t instanceof GAException) {
                                    final GAException e = (GAException) t;
                                    if (e.mUri.equals(GAException.AUTH)) {
                                        final int n = --fragment.mTwoFactorAttemptsRemaining;
                                        if (n > 0) {
                                            final Resources r = fragment.getResources();
                                            final String msg = r.getQuantityString(R.plurals.attempts_remaining, n, n);
                                            UI.toast(activity, e.mMessage + "\n(" + msg + ')', mSendButton);
                                            return; // Allow re-trying
                                        }
                                    }
                                }
                                UI.toast(activity, t, mSendButton);
                                 // Out of 2FA attempts, or another exception; give up
                                UI.dismiss(activity, fragment.mSummary);
                            }
                        }, service.getExecutor());
                    }
                }).build();
        UI.mapEnterToPositive(mSummary, R.id.newTx2FACodeText);
        mSummary.show();
    }

    private void onTransactionSent() {
        final GaActivity gaActivity = getGaActivity();

        if (gaActivity == null)
            return; // App was paused/deleted while callback ran

        gaActivity.runOnUiThread(new Runnable() {
            public void run() {
                UI.toast(gaActivity, R.string.transactionSubmitted, Toast.LENGTH_LONG);

                if (mIsExchanger)
                    mExchanger.sellBtc(Double.valueOf(UI.getText(mAmountFiatEdit)));

                if (mFromIntentURI) {
                    gaActivity.finish();
                    return;
                }

                UI.clear(mAmountEdit, mRecipientEdit);
                UI.enable(mAmountEdit, mRecipientEdit);
                if (!GaService.IS_ELEMENTS) {
                    mMaxButton.setChecked(false);
                    UI.show(mMaxButton, mMaxLabel);
                }

                mNoteIcon.setText(R.string.fa_pencil);
                UI.clear(mNoteText);
                UI.hide(mNoteText);

                resetAllFields();

                if (!mIsExchanger && !mIsVendor) {
                    final ViewPager viewPager = UI.find(gaActivity, R.id.container);
                    viewPager.setCurrentItem(1);
                } else {
                    gaActivity.toast(R.string.transactionSubmitted);
                    if (mIsVendor)
                        goBack();
                    else
                        gaActivity.finish();
                }
            }
        });
    }

    private void goBack() {
        Intent intent = new Intent();
        intent.putExtra("FROM_SUB_FRAGMENT", "ok");
        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);

        getFragmentManager().popBackStack();
    }

    Coin getFeeRate(final UI.FEE_TARGET feeTarget) throws GAException {
        if (!GaService.IS_ELEMENTS && (feeTarget.equals(UI.FEE_TARGET.CUSTOM) || feeTarget.equals(UI.FEE_TARGET.SUPER_ECONOMY))) {
            // FIXME: Custom fees for elements
            final Double feeRate = Double.valueOf(UI.getText(mFeeTargetEdit));
            return Coin.valueOf(feeRate.longValue());
        }

        // 1 is not possible yet as we always get 2 as the fastest estimate,
        // but try it anyway in case that improves in the future.
        final int forBlock;
        if (GaService.IS_ELEMENTS)
            forBlock = 6; // FIXME: feeTarget for elements
        else
            forBlock = feeTarget.equals(UI.FEE_TARGET.INSTANT) ? 1 : feeTarget.getBlock();
        return GATx.getFeeEstimate(getGAService(), feeTarget.equals(UI.FEE_TARGET.INSTANT), forBlock);
    }

    private int createRawTransaction(final List<JSONMap> utxos, final String recipient,
                                     final Coin amount, final JSONMap privateData,
                                     final boolean sendAll, final Coin feeRate) {

        if (GaService.IS_ELEMENTS)
            return createRawElementsTransaction(utxos, recipient, amount, privateData, sendAll, feeRate);

        final GaActivity gaActivity = getGaActivity();
        final GaService service = getGAService();
        final List<JSONMap> usedUtxos = new ArrayList<>();

        final Transaction tx = new Transaction(Network.NETWORK);
        tx.addOutput(amount, Address.fromBase58(Network.NETWORK, recipient));

        Coin total = Coin.ZERO;
        Coin fee;
        boolean randomizedChange = false;
        GATx.ChangeOutput changeOutput = null;

        // First add inputs until we cover the amount to send
        while ((sendAll || total.isLessThan(amount)) && !utxos.isEmpty())
            total = total.add(GATx.addUtxo(service, tx, utxos, usedUtxos));

        // Then add inputs until we cover amount + fee/change
        while (true) {
            fee = GATx.getTxFee(service, tx, feeRate);

            final Coin minChange = changeOutput == null ? Coin.ZERO : service.getDustThreshold();
            final int cmp = sendAll ? 0 : total.compareTo(amount.add(fee).add(minChange));
            if (cmp < 0) {
                // Need more inputs to cover amount + fee/change
                if (utxos.isEmpty())
                    return R.string.insufficientFundsText; // None left, fail

                total = total.add(GATx.addUtxo(service, tx, utxos, usedUtxos));
                continue;
            }

            if (cmp == 0 || changeOutput != null) {
                // Inputs exactly match amount + fee/change, or are greater
                // and we have a change output for the excess
                break;
            }

            // Inputs greater than amount + fee, add a change output and try again
            changeOutput = GATx.addChangeOutput(service, tx, mSubaccount);
            if (changeOutput == null)
                return R.string.unable_to_create_change;
        }

        if (changeOutput != null) {
            // Set the value of the change output
            changeOutput.mOutput.setValue(total.subtract(amount).subtract(fee));
            randomizedChange = GATx.randomizeChangeOutput(tx);
        }

        final Coin actualAmount;
        if (!sendAll)
            actualAmount = amount;
        else {
            actualAmount = total.subtract(fee);
            if (!actualAmount.isGreaterThan(Coin.ZERO))
                return R.string.insufficientFundsText;
            tx.getOutput(randomizedChange ? 1 : 0).setValue(actualAmount);
        }

        tx.setLockTime(service.getCurrentBlock()); // Prevent fee sniping

        final PreparedTransaction ptx;
        ptx = GATx.signTransaction(service, tx, usedUtxos, mSubaccount, changeOutput);

        final int changeIndex = changeOutput == null ? -1 : (randomizedChange ? 0 : 1);
        final JSONMap underLimits = GATx.makeLimitsData(actualAmount.add(fee), fee, changeIndex);

        final boolean skipChoice = service.isUnderLimit(underLimits.getCoin("amount"));
        final Coin sendFee = fee;
        gaActivity.runOnUiThread(new Runnable() {
            public void run() {
                mSendButton.setProgress(0);
                mTwoFactor = UI.popupTwoFactorChoice(gaActivity, service, skipChoice,
                                                     new CB.Runnable1T<String>() {
                    public void run(String method) {
                        if (skipChoice && service.hasAnyTwoFactor())
                            method = "limit";
                        onTransactionValidated(null, tx, recipient, actualAmount,
                                               method, sendFee, privateData, underLimits);
                    }
                });
                if (mTwoFactor != null)
                    mTwoFactor.show();
            }
        });
        return 0;
    }

    private static void arraycpy(final byte[] dest, final int i, final byte[] src) {
        System.arraycopy(src, 0, dest, src.length * i, src.length);
    }

    private int createRawElementsTransaction(final List<JSONMap> utxos, final String recipient,
                                             final Coin amount, final JSONMap privateData,
                                             final boolean sendAll, final Coin feeRate) {
        // FIXME: sendAll
        final GaService service = getGAService();
        final GaActivity gaActivity = getGaActivity();

        final List<JSONMap> usedUtxos = new ArrayList<>();

        final ElementsTransaction tx = new ElementsTransaction(Network.NETWORK);

        final ElementsTransactionOutput feeOutput = new ElementsTransactionOutput(Network.NETWORK, tx, Coin.ZERO);

        feeOutput.setUnblindedAssetTagFromAssetId(service.mAssetId);
        feeOutput.setValue(Coin.valueOf(1));  // updated below, necessary for serialization for fee calculation
        tx.addOutput(feeOutput);
        TransactionOutput changeOutput = null;

        tx.addOutput(service.mAssetId, amount, ConfidentialAddress.fromBase58(Network.NETWORK, recipient));

        Coin total = Coin.ZERO;
        Coin fee;

        final List<Long> inValues = new ArrayList<>();
        final List<byte[]> inAssetIds = new ArrayList<>();
        final List<byte[]> inAbfs = new ArrayList<>();
        final List<byte[]> inVbfs = new ArrayList<>();

        // First add inputs until we cover the amount to send
        while (total.isLessThan(amount) && !utxos.isEmpty())
            total = total.add(GATx.addUtxo(service, tx, utxos, usedUtxos, inValues, inAssetIds, inAbfs, inVbfs));

        // Then add inputs until we cover amount + fee/change
        while (true) {
            fee = GATx.getTxFee(service, tx, feeRate);

            final Coin minChange = changeOutput == null ? Coin.ZERO : service.getDustThreshold();
            final int cmp = total.compareTo(amount.add(fee).add(minChange));
            if (cmp < 0) {
                // Need more inputs to cover amount + fee/change
                if (utxos.isEmpty())
                    return R.string.insufficientFundsText; // None left, fail
                total = total.add(GATx.addUtxo(service, tx, utxos, usedUtxos, inValues, inAssetIds, inAbfs, inVbfs));
                continue;
            }

            if (cmp == 0 || changeOutput != null) {
                // Inputs exactly match amount + fee/change, or are greater
                // and we have a change output for the excess
                break;
            }

            // Inputs greater than amount + fee, add a change output and try again
            final JSONMap addr = service.getNewAddress(mSubaccount);
            if (addr == null)
                return R.string.unable_to_create_change;

            final byte[] script = addr.getBytes("script");
            changeOutput = tx.addOutput(
                    service.mAssetId, Coin.ZERO,
                    ConfidentialAddress.fromP2SHHash(
                            Network.NETWORK, Wally.hash160(script),
                            service.getBlindingPubKey(mSubaccount, addr.getInt("pointer"))
                    )
            );
        }

        if (changeOutput != null) {
            // Set the value of the change output
            ((ElementsTransactionOutput)changeOutput).setUnblindedValue(total.subtract(amount).subtract(fee).getValue());
            // TODO: randomize change
            // GATx.randomizeChangeOutput(tx);
        }

        feeOutput.setValue(fee);

        // FIXME: tx.setLockTime(latestBlock); // Prevent fee sniping

        // Fetch previous outputs
        final List<Output> prevOuts = GATx.createPrevouts(service, usedUtxos);

        final int numInputs = tx.getInputs().size();
        final int numOutputs = tx.getOutputs().size();
        final int numInOuts = numInputs + numOutputs;

        final long[] values = new long[numInOuts];
        final byte[] abfs = new byte[32 * (numInOuts)];
        final byte[] vbfs = new byte[32 * (numInOuts - 1)];
        final byte[] assetids = new byte[32 * numInputs];
        final byte[] ags = new byte[33 * numInputs];

        for (int i = 0; i < numInputs; ++i) {
            values[i] = inValues.get(i);
            arraycpy(abfs, i, inAbfs.get(i));
            arraycpy(vbfs, i, inVbfs.get(i));
            arraycpy(assetids, i, inAssetIds.get(i));
            arraycpy(ags, i, Wally.asset_generator_from_bytes(inAssetIds.get(i), inAbfs.get(i)));
        }

        for (int i = 0; i < numOutputs; ++i) {
            final ElementsTransactionOutput output = (ElementsTransactionOutput) tx.getOutput(i);

            // Fee: FIXME: Assumes fee is the first output
            values[numInputs + i] = i == 0 ? output.getValue().getValue() : output.getUnblindedValue();
            arraycpy(abfs, numInputs + i, output.getAbf());
            if (i == numOutputs - 1) {
                // Compute the final VBF
                output.setAbfVbf(null, Wally.asset_final_vbf(values, numInputs, abfs, vbfs), service.mAssetId);
            } else
                arraycpy(vbfs, numInputs + i, output.getVbf());
        }

        final boolean isSegwitEnabled = service.isSegwitEnabled();

        // fee output:
        tx.addOutWitness(new byte[0], new byte[0], new byte[0]);

        final ArrayList<String> ephemeralKeys = new ArrayList<>();
        final ArrayList<String> blindingKeys = new ArrayList<>();

        ephemeralKeys.add("00");
        blindingKeys.add("00");

        for (int i = 1; i < numOutputs; ++i) {
            final ElementsTransactionOutput out = (ElementsTransactionOutput) tx.getOutput(i);

            final byte[] ephemeral = CryptoHelper.randomBytes(32);
            ephemeralKeys.add(Wally.hex_from_bytes(ephemeral));
            blindingKeys.add(Wally.hex_from_bytes(out.getBlindingPubKey()));
            final byte[] rangeproof = Wally.asset_rangeproof(
                    out.getUnblindedValue(), out.getBlindingPubKey(), ephemeral,
                    out.getAssetId(), out.getAbf(), out.getVbf(),
                    out.getCommitment(), null, out.getAssetTag(), 1
            );
            final byte[] surjectionproof = Wally.asset_surjectionproof(
                    out.getAssetId(), out.getAbf(), out.getAssetTag(),
                    CryptoHelper.randomBytes(32),
                    assetids, Arrays.copyOf(abfs, 32 * numInputs), ags
            );
            final byte[] nonceCommitment = Wally.ec_public_key_from_private_key(ephemeral);
            tx.addOutWitness(surjectionproof, rangeproof, nonceCommitment);
        }

        // FIXME: Implement HW Signing
        /*
        final PreparedTransaction ptx = new PreparedTransaction(
                changeOutput, mSubaccount, tx, service.findSubaccountByType(mSubaccount, "2of3")
        );
        ptx.mPrevoutRawTxs = new HashMap<>();
        for (final Transaction prevTx : GATx.getPreviousTransactions(service, tx))
            ptx.mPrevoutRawTxs.put(Wally.hex_from_bytes(prevTx.getHash().getBytes()), prevTx);
        */
        final PreparedTransaction ptx = null;

        // Sign the tx
        final List<byte[]> signatures = service.signTransaction(tx, ptx, prevOuts);
        for (int i = 0; i < signatures.size(); ++i) {
            final byte[] sig = signatures.get(i);
            // FIXME: Massive duplication with TransactionActivity
            final JSONMap utxo = usedUtxos.get(i);
            final int scriptType = utxo.getInt("script_type");
            final byte[] outscript = GATx.createOutScript(service, utxo);
            final List<byte[]> userSigs = ImmutableList.of(new byte[]{0}, sig);
            final byte[] inscript = GATx.createInScript(userSigs, outscript, scriptType);

            tx.getInput(i).setScriptSig(new Script(inscript));
            if (isSegwitEnabled && scriptType == GATx.P2SH_P2WSH_FORTIFIED_OUT) {
                final TransactionWitness witness = new TransactionWitness(1);
                witness.setPush(0, sig);
                tx.setWitness(i, witness);
            }
        }

        final int changeIndex = changeOutput == null ? -1 : 2;
        final JSONMap underLimits = GATx.makeLimitsData(amount.add(fee), fee, changeIndex);
        underLimits.putBytes("asset_id", service.mAssetId); // FIXME: Others
        underLimits.mData.put("ephemeral_privkeys", ephemeralKeys);
        underLimits.mData.put("blinding_pubkeys", blindingKeys);
        final boolean skipChoice = service.isUnderLimit(underLimits.getCoin("amount"));
        final Coin sendFee = fee;

        gaActivity.runOnUiThread(new Runnable() {
            public void run() {
                mSendButton.setProgress(0);
                mTwoFactor = UI.popupTwoFactorChoice(gaActivity, service, skipChoice,
                        new CB.Runnable1T<String>() {
                            public void run(String method) {
                                if (skipChoice && service.hasAnyTwoFactor())
                                    method = "limit";
                                onTransactionValidated(null, tx, recipient, amount, method,
                                                       sendFee, privateData, underLimits);
                            }
                        });
                if (mTwoFactor != null)
                    mTwoFactor.show();
            }
        });
        return 0;
    }

    public void setIsVendor(final boolean isVendor) {
        mIsVendor = isVendor;
    }

    public void setIsExchanger(final boolean isExchanger) {
        mIsExchanger = isExchanger;
    }

    /**
     * Set the default fee for the wallet
     */
    private void setDefaultFee() {
        mFeeTargetCombo.setSelection(3);
        onNewFeeTargetSelected(3);
        mShowFeeSelector.setText(getResources().getStringArray(R.array.send_fee_target_choices_instant)[3]);
        mFeeDesc.setText(mPrioritySummaries[3]);
        mRadioGroupFee.check(R.id.btnEco);
    }
}
