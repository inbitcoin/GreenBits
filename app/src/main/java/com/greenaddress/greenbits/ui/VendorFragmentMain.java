package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentResult;

import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;

import java.lang.reflect.Field;

import de.schildbach.wallet.ui.ScanActivity;

/**
 * Created by Antonio Parrella on 11/28/16.
 * by inbitcoin
 */

public class VendorFragmentMain extends SubaccountFragment {

    private AmountFields amountFields;
    private FontFitEditText amountFieldFiat;
    private FontFitEditText amountFieldBtc;
    private Button btnBtcSwitch;
    private Button btnFiatSwitch;
    private View fiatView;
    private View btcView;
    private GaActivity gaActivity;
    private static int FRAGMENT_CODE = 10;
    private Boolean backFromSummaryPay = false;

    @Override
    protected void onSubaccountChanged(int input) {
        // TODO
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View mView = inflater.inflate(R.layout.fragment_vendor_main, container, false);

        gaActivity = getGaActivity();


        amountFields = new AmountFields(getGAService(), getContext(), (View)UI.find(mView, R.id.amountView), null);

        amountFieldBtc = UI.find(mView, R.id.sendAmountEditText);
        amountFieldFiat = UI.find(mView, R.id.sendAmountFiatEditText);
        btnBtcSwitch = UI.find(mView, R.id.btnBtcSwitch);
        btnFiatSwitch = UI.find(mView, R.id.btnFiatSwitch);

        fiatView = UI.find(mView, R.id.fiatView);
        btcView = UI.find(mView, R.id.btcView);

        Boolean hidePrefilledBtn = false;

        // FIXME better implementation
        String currency = getGAService().getFiatCurrency();
        btnFiatSwitch.setText(currency);
        if (currency.equals("EUR")) {
            currency = "€";
        } else if (currency.equals("USD")) {
            currency = "$";
        } else if (currency.equals("GBP")) {
            currency = "£";
        } else {
            hidePrefilledBtn = true;
        }

        btnBtcSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UI.disable(btnBtcSwitch);
                UI.enable(btnFiatSwitch);
                UI.show(btcView);
                UI.hide(fiatView);
            }
        });

        btnFiatSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UI.disable(btnFiatSwitch);
                UI.enable(btnBtcSwitch);
                UI.show(fiatView);
                UI.hide(btcView);
            }
        });

        // set all numeric buttons
        for (int i = 0; i < 10; i++) {
            final Button btn;
            try {
                final Object obj = R.id.class;
                final Field field = R.id.class.getField("btn" +  i);
                btn = UI.find(mView, field.getInt(obj));
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        writeText(btn.getText().toString());
                    }
                });
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }

        final Button btnCancel = UI.find(mView, R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                amountFieldBtc.setText("");
            }
        });

        // set all pre-set fiat values
        Integer fiatValues[] = {10, 20, 50, 100, 150, 200};
        for (final int fiatValue:fiatValues) {
            final Button btn;
            try {
                final Object obj = R.id.class;
                final Field field = R.id.class.getField("fiat" + fiatValue);
                btn = UI.find(mView, field.getInt(obj));
                final String strValue = String.format("%s %s", fiatValue, currency);
                btn.setText(strValue);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        amountFieldFiat.setText(Integer.toString(fiatValue));
                    }
                });
                if (hidePrefilledBtn)
                    btn.setVisibility(View.INVISIBLE);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

        }

        final TextView payButton = UI.find(mView, R.id.payButton);
        payButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                String amountText = amountFieldFiat.getText().toString();
                if (amountText.isEmpty()) {
                    getGaActivity().toast(R.string.invalidAmount);
                    return;
                }
                Double amount = Double.valueOf(amountText);
                if (amount == 0) {
                    getGaActivity().toast(R.string.invalidAmount);
                    return;
                }

                final String scanned;
                if (Build.VERSION.SDK_INT >= 23) {
                    GaIntentIntegrator.scanQRCode(gaActivity, TabbedMainActivity.REQUEST_SEND_QR_SCAN_VENDOR);
                } else {
                    backFromSummaryPay = false;
                    final Intent qrcodeScanner = new Intent(gaActivity, ScanActivity.class);
                    gaActivity.startActivityForResult(qrcodeScanner, TabbedMainActivity.REQUEST_SEND_QR_SCAN_VENDOR);
                }
            }
        });

        return mView;
    }

    @Override
    public void onResume() {
        if (backFromSummaryPay)
            amountFieldFiat.setText("");
        super.onResume();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if(requestCode == FRAGMENT_CODE && resultCode == Activity.RESULT_OK) {
        if(data != null) {
           String value = data.getStringExtra("FROM_SUB_FRAGMENT");
           if(value != null) {
               backFromSummaryPay = true;
           }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}

    public void scanResult(String result) {
        // check valid bitcoin address
        try {
            if (!result.startsWith("bitcoin:")) {
                result = String.format("bitcoin:%s", result);
            }
            new BitcoinURI(result);
        } catch (final BitcoinURIParseException e) {
            gaActivity.toast(R.string.err_send_invalid_bitcoin_uri);
            return;
        }
        result = result.replaceFirst("^bitcoin:", "");
        result = result.replaceFirst("\\?.*", "");

        // show summary fragment
        final FragmentTransaction transaction = gaActivity.getSupportFragmentManager().beginTransaction();
        final SendFragment vendorFragment = new SendFragment();
        vendorFragment.setIsVendor(true);
        vendorFragment.setPageSelected(true);
        final Bundle bundle = new Bundle();
        bundle.putString("address", result);
        bundle.putString("fiat_amount", amountFieldFiat.getText().toString());
        vendorFragment.setTargetFragment(this, FRAGMENT_CODE);
        vendorFragment.setArguments(bundle);
        transaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left, R.anim.enter_from_left, R.anim.exit_to_right);
        transaction.replace(R.id.fragment_container, vendorFragment);
        transaction.addToBackStack("summary");
        transaction.commitAllowingStateLoss();
    }

    private void writeText(String value) {
        if (btnFiatSwitch.isEnabled()) {
            String text = amountFieldBtc.getText() + value;
            amountFieldBtc.setText(text);
        } else {
            String text = amountFieldFiat.getText() + value;
            amountFieldFiat.setText(text);
        }
    }
}
