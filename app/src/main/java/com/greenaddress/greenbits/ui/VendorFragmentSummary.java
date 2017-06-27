package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.dd.CircularProgressButton;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.greenaddress.greenapi.PreparedTransaction;
import com.greenaddress.greenbits.GaService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import java.util.HashMap;
import java.util.Map;


/**
 * Created by Antonio Parrella on 11/29/16.
 * by inbitcoin
 */

public class VendorFragmentSummary extends SubaccountFragment {

    private static final String TAG = SendFragment.class.getSimpleName();
    private AmountFields amountFields;
    private FontFitEditText amountFieldFiat;
    private FontFitEditText amountFieldBtc;
    private FontFitEditText amountFieldFiatWithoutCommission;
    private TextView addressView;
    private Integer commission = 10; //TODO
    private String address;
    private Dialog mTwoFactor;
    private CircularProgressButton sendButton;
    private MonetaryFormat mBitcoinFormat;
    private Dialog mSummary;
    private String fiat_amount;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View mView = inflater.inflate(R.layout.fragment_vendor_summary, container, false);
        address = this.getArguments().getString("address");
        fiat_amount = this.getArguments().getString("fiat_amount");

        amountFields = new AmountFields(getGAService(), getContext(), mView, null);
        amountFieldFiat = UI.find(mView, R.id.sendAmountFiatEditText);
        amountFieldBtc = UI.find(mView, R.id.sendAmountEditText);
        amountFieldFiatWithoutCommission = UI.find(mView, R.id.sendAmountFiatEditTextWithoutCommission);
        addressView = UI.find(mView, R.id.btcAddress);

        String addressTwoLines = String.format("%s\n%s", address.substring(0, 18), address.substring(18));
        addressView.setText(addressTwoLines);  // TODO sanitize address

        amountFieldFiatWithoutCommission.setText(fiat_amount);

        final Double amount = Double.valueOf(fiat_amount);
        final Double amountWithCommission = amount * 0.90; // TODO
        amountFieldFiat.setText(amountWithCommission.toString());



        final String btcUnit = (String) getGAService().getUserConfig("unit");
        mBitcoinFormat = null;// CurrencyMapper.mapBtcUnitToFormat(btcUnit);

        // TODO refactor code
        sendButton = UI.find(mView, R.id.sendBtc);
        sendButton.setIndeterminateProgressMode(true);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // FIXME: Instead of checking the state here, enable/disable sendButton when state changes
                if (!getGAService().isLoggedIn()) {
                    getGaActivity().toast(R.string.err_send_not_connected_will_resume);
                    return;
                }
                final String recipient = address;
                final Coin amount;
                Coin nonFinalAmount;
                try {
                    nonFinalAmount = mBitcoinFormat.parse(UI.getText(amountFieldBtc));
                } catch (final IllegalArgumentException e) {
                    nonFinalAmount = Coin.ZERO;
                }
                amount = nonFinalAmount;

                final boolean validAddress = getGAService().isValidAddress(recipient);

                final boolean validAmount = !(amount.compareTo(Coin.ZERO) <= 0);
                String message = null;

                final Map<String, Object> privateData = new HashMap<>();

                // set memo
                final String currency = getGAService().getFiatCurrency();
                final String memo = String.format("%s %s +%s%% via %s",
                        currency, fiat_amount, commission, getGAService().getString(R.string.app_name));
                if (!memo.isEmpty())
                    privateData.put("memo", memo);

                int mSubaccount = getGAService().getCurrentSubAccount();
                if (mSubaccount != 0)
                    privateData.put("subaccount", mSubaccount);

                ListenableFuture<PreparedTransaction> ptxFn;
                if (!validAddress && !validAmount) {
                    message = getGaActivity().getString(R.string.invalidAmountAndAddress);
                } else if (!validAddress) {
                    message = getActivity().getString(R.string.invalidAddress);
                } else if (!validAmount) {
                    message = getGaActivity().getString(R.string.invalidAmount);
                }
                if (message == null) {
                    //ptxFn = getGAService().prepareTx(amount, recipient, privateData);
                } else {
                    ptxFn = null;
                }
                ptxFn = null;

                if (ptxFn != null) {
                    sendButton.setProgress(50);
                    CB.after(ptxFn,
                            new CB.Toast<PreparedTransaction>(getGaActivity(), sendButton) {
                                @Override
                                public void onSuccess(final PreparedTransaction ptx) {
                                    // final Coin fee = Coin.parseCoin("0.0001");        //FIXME: pass real fee
                                    final Coin verifyAmount = amount;
                                    CB.after(getGAService().validateTx(ptx, recipient, verifyAmount),
                                            new CB.Toast<Coin>(getGaActivity(), sendButton) {
                                                @Override
                                                public void onSuccess(final Coin fee) {
                                                    final Map<?, ?> twoFacConfig = getGAService().getTwoFactorConfig();
                                                    // can be non-UI because validation talks to USB if hw wallet is used
                                                    getActivity().runOnUiThread(new Runnable() {
                                                        public void run() {
                                                            sendButton.setProgress(0);
                                                            final Coin dialogAmount, dialogFee;
                                                            dialogAmount = amount;
                                                            dialogFee = fee;
                                                            final boolean skipChoice = !ptx.mRequiresTwoFactor ||
                                                                    twoFacConfig == null || !((Boolean) twoFacConfig.get("any"));
                                                            mTwoFactor = UI.popupTwoFactorChoice(getGaActivity(), getGAService(), skipChoice,
                                                                    new CB.Runnable1T<String>() {
                                                                        @Override
                                                                        public void run(final String method) {
                                                                            showTransactionSummary(method, dialogFee, dialogAmount, recipient, ptx);
                                                                        }
                                                                    });
                                                            if (mTwoFactor != null)
                                                                mTwoFactor.show();
                                                        }
                                                    });
                                                }
                                            });
                                }

                                @Override
                                public void onFailure(Throwable t) {
                                    super.onFailure(t);
                                    sendButton.setProgress(0);
                                }
                            });
                }

                if (message != null)
                    getGaActivity().toast(message);
            }
        });

        return mView;
    }


    private void showTransactionSummary(final String method, final Coin fee, final Coin amount, final String recipient, final PreparedTransaction ptx) {
        Log.i(TAG, "showTransactionSummary( params " + method + " " + fee + " " + amount + " " + recipient + ")");
        final GaService service = getGAService();
        final GaActivity gaActivity = getGaActivity();

        final View v = gaActivity.getLayoutInflater().inflate(R.layout.dialog_new_transaction, null, false);

        final TextView amountText = UI.find(v, R.id.newTxAmountText);
        final TextView amountUnit = UI.find(v, R.id.newTxAmountUnitText);
        final TextView feeText = UI.find(v, R.id.newTxFeeText);
        final TextView feeUnit = UI.find(v, R.id.newTxFeeUnit);

        final TextView recipientText = UI.find(v, R.id.newTxRecipientText);
        final TextView twoFAText = UI.find(v, R.id.newTx2FATypeText);
        final EditText newTx2FACodeText = UI.find(v, R.id.newTx2FACodeText);
        final String prefix = "";//CurrencyMapper.mapBtcFormatToPrefix(mBitcoinFormat);

        //amountScale.setText(Html.fromHtml(prefix));
        //feeScale.setText(Html.fromHtml(prefix));
        if (mBitcoinFormat.code().equals("bits")) {
            amountUnit.setText("bits ");
            feeUnit.setText("bits ");
        } else {
            amountUnit.setText(R.string.fa_btc_space);
            feeUnit.setText(R.string.fa_btc_space);
        }
        amountText.setText(mBitcoinFormat.noCode().format(amount));
        feeText.setText(mBitcoinFormat.noCode().format(fee));

        recipientText.setText(String.format("%s\n%s\n%s",
                recipient.substring(0, 12),
                recipient.substring(12, 24),
                recipient.substring(24)));

        UI.showIf(method != null, twoFAText, newTx2FACodeText);

        final Map<String, String> twoFacData;

        if (method == null)
            twoFacData = null;
        else {
            twoFacData = new HashMap<>();
            twoFacData.put("method", method);
            twoFAText.setText(String.format("2FA %s code", method));
            if (!method.equals("gauth"))
                service.requestTwoFacCode(method, "send_tx", null);
        }

        mSummary = UI.popup(gaActivity, R.string.newTxTitle, R.string.confirm, R.string.cancel)
                .customView(v, true)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(final MaterialDialog dialog, final DialogAction which) {
                        if (twoFacData != null)
                            twoFacData.put("code", UI.getText(newTx2FACodeText));

                        final ListenableFuture<String> sendFuture = service.signAndSendTransaction(ptx, twoFacData);
                        Futures.addCallback(sendFuture, new CB.Toast<String>(gaActivity) {
                            @Override
                            public void onSuccess(final String result) {
                                gaActivity.runOnUiThread(new Runnable() {
                                    public void run() {
                                        UI.toast(gaActivity, R.string.transactionCompleted, Toast.LENGTH_LONG);
                                        goBack();
                                    }
                                });
                            }
                        }, service.getExecutor());
                    }
                }).build();

        mSummary.show();
    }

    @Override
    protected void onSubaccountChanged(int input) {
    }

    private void goBack() {
        Intent intent = new Intent();
        intent.putExtra("FROM_SUB_FRAGMENT", "ok");
        getTargetFragment().onActivityResult(getTargetRequestCode(), Activity.RESULT_OK, intent);

        getFragmentManager().popBackStack();
    }
}
