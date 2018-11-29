package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDButton;
import com.blockstream.libwally.Wally;
import com.google.common.util.concurrent.FutureCallback;
import com.google.zxing.integration.android.IntentResult;
import com.greenaddress.bitid.BitID;
import com.greenaddress.bitid.BitidSignIn;
import com.greenaddress.bitid.SignInResponse;
import com.greenaddress.greenapi.ISigningWallet;
import com.greenaddress.greenbits.ApplicationService;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.GreenAddressApplication;
import com.greenaddress.greenbits.ui.monitor.NetworkMonitorActivity;
import com.greenaddress.greenbits.ui.preferences.GaPreferenceActivity;
import com.greenaddress.greenbits.ui.preferences.SettingsActivity;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.DumpedPrivateKey;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import de.schildbach.wallet.ui.ScanActivity;
import com.greenaddress.greenbits.ui.preferences.TwoFactorPreferenceFragment;

// Problem with the above is that in the horizontal orientation the tabs don't go in the top bar
public class TabbedMainActivity extends GaActivity implements Observer, View.OnClickListener {

    private static final String TAG = TabbedMainActivity.class.getSimpleName();


    private static final int REQUEST_ENABLE_2FA = 0;

    public static final int
            REQUEST_SEND_QR_SCAN = 0,
            REQUEST_SWEEP_PRIVKEY = 1,
            REQUEST_BITCOIN_URL_LOGIN = 2,
            REQUEST_SETTINGS = 3,
            REQUEST_TX_DETAILS = 4,
            REQUEST_SEND_QR_SCAN_EXCHANGER = 5,
            REQUEST_SEND_QR_SCAN_NO_LOGIN = 6,
            REQUEST_SEND_QR_SCAN_VENDOR = 7,
            REQUEST_CLEAR_ACTIVITY = 8,
            REQUEST_VISIU = 9,
            REQUEST_BITID_URL_LOGIN = 10;
    public static final String REQUEST_RELOAD = "request_reload";
    private ViewPager mViewPager;
    private Boolean mInternalQr = false;
    private String mSendAmount;
    private Snackbar snackbar;
    private final int mSnackbarDuration = 10 * 1000;
    private Activity mActivity;
    private Boolean mForcedLogoutFromCreate = false;
    private Dialog mTwoFactorDialog;
    private Dialog mTwoFactorResetDialog;
    private MaterialDialog mSubaccountDialog;
    private FloatingActionButton mSubaccountButton;
    private boolean mTwoFactorResetShowing = false;
    private boolean mIsBitcoinUri = false;
    private boolean mIsBitidUri = false;

    private final Runnable mSubaccountCB = new Runnable() { public void run() { mDialogCB.run(); mSubaccountDialog = null; } };
    private final Runnable mDialogCB = new Runnable() { public void run() { setBlockWaitDialog(false); } };

    // workaround to manage only the create/onresume when is not connected
    private Boolean firstRun = true;

    private static int COUNTDOWN_DIALOG_WARNING_BACKUP = 7;

    private final Observer mTwoFactorObserver = new Observer() {
        @Override
        public void update(final Observable o, final Object data) {
            runOnUiThread(new Runnable() { public void run() { onTwoFactorConfigChange(); } });
        }
    };

    static boolean isBitcoinScheme(final Intent intent) {
        final Uri uri = intent.getData();
        return uri != null && uri.getScheme() != null && uri.getScheme().equals("bitcoin");
    }

    private boolean isBitidcheme(final Intent intent) {
        final Uri uri = intent.getData();
        return uri != null && uri.getScheme() != null && uri.getScheme().equals("bitid");
    }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {
        final Intent intent = getIntent();
        mActivity = this;

        mInternalQr = intent.getBooleanExtra("internal_qr", false);
        mSendAmount = intent.getStringExtra("sendAmount");
        final int flags = getIntent().getFlags();
        if ((flags & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            Log.d(TAG, "onCreate arrives from history, clear data and category");
            // The activity was launched from history
            // remove extras here
            intent.setData(null);
            intent.removeCategory(Intent.CATEGORY_BROWSABLE);
        }

        mIsBitidUri = isBitidcheme(intent);
        mIsBitcoinUri = (isBitcoinScheme(intent) ||
                intent.hasCategory(Intent.CATEGORY_BROWSABLE) ||
                NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) && !mIsBitidUri;

        if (!mService.isLoggedOrLoggingIn()) {
            // Not logged in, force the user to login
            mForcedLogoutFromCreate = true;
            Log.d(TAG, "onCreateWithService - forced logout, nothing to do onResumeWithService");
            mService.disconnect(false);
            final Intent login = new Intent(this, RequestLoginActivity.class);
            if (mIsBitcoinUri)
                startActivityForResult(login, REQUEST_BITCOIN_URL_LOGIN);
            else if (mIsBitidUri)
                startActivityForResult(login, REQUEST_BITID_URL_LOGIN);
            else
                startActivityForResult(login, REQUEST_CLEAR_ACTIVITY);
            return;
        }
        firstRun = false;

        launch();

        // TODO disabled, should be unuseful
        //startService(new Intent(this, ApplicationService.class));
    }

    private TextView showWarningBanner(final int messageId, final String hideCfgName, final Boolean emailWarning) {
        return showWarningBanner(getString(messageId), hideCfgName, emailWarning);
    }

    private TextView showWarningBanner(final String message, final String hideCfgName, final Boolean emailWarning) {
        if (hideCfgName != null && mService.cfg().getBoolean(hideCfgName, false))
            return null;

        // if total amount is less then 0 BTC hide snackbar
        if (mService.getTotalBalance() == 0) {
            if (snackbar != null) {
                snackbar.dismiss();
            }
            return null;
        }

        snackbar = Snackbar
                .make(findViewById(R.id.main_content), message, mSnackbarDuration);

        final String actionString;
        if (emailWarning)
            actionString = getString(R.string.setEmail);
        else
            actionString = getString(R.string.set2FA);

        if (hideCfgName != null) {
            snackbar.setActionTextColor(Color.RED);
            snackbar.setAction(actionString, new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    final Intent intent;
                    final int requestCode;
                    if (emailWarning) {
                        intent = new Intent(TabbedMainActivity.this, SetEmailActivity.class);
                        requestCode = SetEmailActivity.REQUEST_ENABLE_EMAIL;
                    } else {
                        intent = new Intent(TabbedMainActivity.this, SettingsActivity.class);
                        requestCode = REQUEST_SETTINGS;
                    }
                    intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, TwoFactorPreferenceFragment.class.getName());
                    startActivityForResult(intent, requestCode);
                }
            });
        }

        if (mActivity == null) {
            Log.d(TAG, "mActivity is null");
            return null;
        }

        final View snackbarView = snackbar.getView();
        snackbarView.setBackgroundColor(Color.DKGRAY);
        final TextView textView = UI.find(snackbarView, android.support.design.R.id.snackbar_text);
        textView.setTextColor(Color.WHITE);
        snackbar.show();
        return textView;
    }

    private void onTwoFactorConfigChange() {
        if (mTwoFactorResetShowing || mService.getTwoFactorConfig() == null ||
            mService.isWatchOnly())
            return; // Not loaded, watch only, or reset in progress

        final boolean shown = !mService.hasEmailConfirmed() && showWarningBanner(R.string.noEmailWarning, "hideNoEmailWarning", true) != null;
        
        if (!shown && (!mService.hasAnyTwoFactor() || mService.getEnabledTwoFactorMethods().size() == 1)) {
            final Intent toTwoFactor = new Intent(TabbedMainActivity.this, SettingsActivity.class);
            toTwoFactor.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, TwoFactorPreferenceFragment.class.getName());
            toTwoFactor.putExtra(SettingsActivity.EXTRA_NO_HEADERS, true);

            // if no hide two fac warning set, set default to true
            if (!mService.cfg().contains("hideTwoFacWarning"))
                mService.cfg().edit().putBoolean("hideTwoFacWarning", true).apply();
            if (!mService.cfg().contains("hideSingleTwoFacWarning"))
                mService.cfg().edit().putBoolean("hideSingleTwoFacWarning", true).apply();

            if (!mService.hasAnyTwoFactor())
                showWarningBanner(R.string.noTwoFactorWarning, "hideTwoFacWarning", false);
            else
                showWarningBanner(R.string.singleTwoFactorWarning, "hideSingleTwoFacWarning", false);
        }
    }

    private String formatValuePostfix(final Coin value) {
        final String formatted = UI.setCoinText(mService, null, null, value);
        return String.format("%s %s", formatted, mService.getBitcoinUnit());
    }

    private void setAccountTitle(final int subAccount) {
        final boolean doLookup = subAccount != 0 && mService.haveSubaccounts();
        final Map<String, ?> details;
        details = doLookup ? mService.findSubaccount(subAccount) : null;
        final String accountName;
        if (details == null)
            accountName = getString(R.string.main_account);
        else
            accountName = (String) details.get("name");

        if (!mService.showBalanceInTitle()) {
            setTitle(accountName);
            return;
        }
        Coin balance = mService.getCoinBalance(subAccount);
        if (balance == null)
            balance = Coin.ZERO;
        setTitle(formatValuePostfix(balance) + " (" + accountName + ')');
    }

    private void setBlockWaitDialog(final boolean doBlock) {
        getPagerAdapter().setBlockWaitDialog(doBlock);
    }

    private void configureSubaccountsFooter(final int subAccount) {
        setAccountTitle(subAccount);
        if (!mService.haveSubaccounts())
            return;

        mSubaccountButton = UI.find(this, R.id.fab);
        UI.show(mSubaccountButton);
        mSubaccountButton.setOnClickListener(this);

        final ArrayList<GaService.Subaccount> subaccounts = mService.getSubaccountObjs();
        boolean subaccountEnabledFound = false;
        for(final GaService.Subaccount subaccount : subaccounts) {
            subaccountEnabledFound = subaccount.mEnabled;
            if (subaccount.mEnabled)
                break;
        }
        if (!subaccountEnabledFound)
            return;
    }


    @Override
    public void onClick(final View v) {
        if (v == mSubaccountButton)
            onSubaccountButtonClicked();
    }

    public void onSubaccountButtonClicked() {
        setBlockWaitDialog(true);
        final ArrayList<GaService.Subaccount> subaccounts = mService.getSubaccountObjs();
        final int subaccount_len = subaccounts.size() + 1;
        final ArrayList<String> names = new ArrayList<>(subaccount_len);
        final ArrayList<Integer> pointers = new ArrayList<>(subaccount_len);

        names.add(getString(R.string.main_account));
        pointers.add(0);

        for(final GaService.Subaccount subaccount : subaccounts) {
            if (subaccount.mEnabled) {
                names.add(subaccount.mName);
                pointers.add(subaccount.mPointer);
            }
        }

        final AccountItemAdapter adapter = new AccountItemAdapter(names, pointers, mService);
        mSubaccountDialog = new MaterialDialog.Builder(TabbedMainActivity.this)
                .title(R.string.footerAccount)
                .adapter(adapter, null)
                .show();
        UI.setDialogCloseHandler(mSubaccountDialog, mSubaccountCB);

        adapter.setCallback(new AccountItemAdapter.OnAccountSelected() {
            @Override
            public void onAccountSelected(final int account) {
                mSubaccountDialog = UI.dismiss(TabbedMainActivity.this, mSubaccountDialog);
                final int pointer = pointers.get(account);
                if (pointer == mService.getCurrentSubAccount())
                    return;
                setAccountTitle(pointer);
                onSubaccountUpdate(pointer);
            }
        });
    }

    private void onSubaccountUpdate(final int subAccount) {
        mService.setCurrentSubAccount(subAccount);

        final Intent data = new Intent("fragmentupdater");
        data.putExtra("sub", subAccount);
        sendBroadcast(data);
        mTwoFactorObserver.update(null, null);
    }

    private void launch() {

        setContentView(R.layout.activity_tabbed_main);
        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);

        // Set up the action bar.
        final SectionsPagerAdapter sectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = UI.find(this, R.id.container);

        // Keep all of our tabs in memory while paging. This helps any races
        // left where broadcasts/callbacks are called on the pager when its not
        // shown.
        mViewPager.setOffscreenPageLimit(3);

        TextView banner = null;
        if (mService.isTwoFactorResetDisputed())
            banner = showWarningBanner(R.string.twofactor_reset_disputed_banner, null, false);
        else {
            final Integer days = mService.getTwoFactorResetDaysRemaining();
            if (days != null) {
                final String message = getString(R.string.twofactor_reset_banner, days);
                banner = showWarningBanner(message, null, false);
            } else {
                // Show a warning if the user has unacked messages
                if (mService.haveUnackedMessages()) {
                    final int msgId;
                    if (mService.isWatchOnly())
                       msgId = R.string.unacked_system_messages_wo;
                    else
                       msgId = R.string.unacked_system_messages;
                    banner = showWarningBanner(msgId, null, false);
                }
            }
        }
        if (banner != null) {
            mTwoFactorResetShowing = true;
            banner.setTextColor(Color.RED);
        } else {
            // Re-show our 2FA warning if config is changed to remove all methods
            // Fake a config change to show the warning if no current 2FA method
            mTwoFactorObserver.update(null, null);
        }

        configureSubaccountsFooter(mService.getCurrentSubAccount());

        // by default go to center tab
        final boolean isResetActive = mService.isTwoFactorResetActive();
        int goToTab = isResetActive ? 0 : 1;

        if (mIsBitcoinUri && !isResetActive) {
            // go to send page tab
            goToTab = 2;

            // Started by clicking on a bitcoin URI, show the send tab initially.
            if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
                mViewPager.setTag(R.id.tag_bitcoin_uri, getIntent().getData());
            } else {
                final Parcelable[] rawMessages;
                rawMessages = getIntent().getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                for (final Parcelable parcel : rawMessages) {
                    final NdefMessage ndefMsg = (NdefMessage) parcel;
                    for (final NdefRecord record : ndefMsg.getRecords())
                        if (record.getTnf() == NdefRecord.TNF_WELL_KNOWN &&
                            Arrays.equals(record.getType(), NdefRecord.RTD_URI)) {
                            mViewPager.setTag(R.id.tag_bitcoin_uri, record.toUri());
                        }
                }
            }
            // if arrives from internal QR scan
            if (mInternalQr) {
                mViewPager.setTag(R.id.internal_qr, "internal_qr");
            }
            if (mSendAmount != null) {
                mViewPager.setTag(R.id.tag_amount, mSendAmount);
            }
            mInternalQr = false;
            mSendAmount = null;
        }

        // set adapter and tabs only after all setTag in ViewPager container
        mViewPager.setAdapter(sectionsPagerAdapter);
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(final int index) {
                sectionsPagerAdapter.onViewPageSelected(index);
            }
        });
        final TabLayout tabLayout = UI.find(this, R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        mViewPager.setCurrentItem(goToTab);
        if (isResetActive) {
            sectionsPagerAdapter.onViewPageSelected(0);
            return;
        }

        if (!Boolean.TRUE.equals(mService.getUserConfig("use_segwit"))) {
            // Set SegWit to true if it's false or not set
            mService.setUserConfig("use_segwit", true, false);
        }

        if (mIsBitidUri) {
            final Intent intent = getIntent();
            final Uri uri = intent.getData();
            bitidAuth(uri.toString());
        }

        final String mnemonic = mService.getMnemonic();
        final Boolean backupDone = mService.getBackupDone();
        if (!mService.isWatchOnly() && mnemonic != null && !backupDone)
            showBackupWarningDialog();
    }

    @Override
    public void onResumeWithService() {
        // FIXME: workaround to avoid multiple login instance, exists a better implementation?
        if (mForcedLogoutFromCreate) {
            Log.d(TAG, "onResumeWithService - Forced logout on onCreateWithService, nothing to do");
            // reset flag
            mForcedLogoutFromCreate = false;
            return;
        }

        final SectionsPagerAdapter adapter = getPagerAdapter();

        if ((adapter == null || mService.isForcedOff()) && !mIsBitcoinUri) {
            // FIXME: Should pass flag to activity so it shows it was forced logged out
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
            return;
        }

        if (!firstRun && !mService.isLoggedOrLoggingIn()) {
            // Not logged in, force the user to login
            mService.disconnect(false);
            final Intent login = new Intent(this, RequestLoginActivity.class);
            startActivity(login);
            return;
        }

        mService.addConnectionObserver(this);
        mService.addTwoFactorObserver(mTwoFactorObserver);

        invalidateOptionsMenu();
     }

    @Override
    public void onPauseWithService() {
        mService.deleteTwoFactorObserver(mTwoFactorObserver);
        mService.deleteConnectionObserver(this);
        mSubaccountDialog = UI.dismiss(this, mSubaccountDialog);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UI.unmapClick(mSubaccountButton);
        mTwoFactorDialog = UI.dismiss(this, mTwoFactorDialog);
        mTwoFactorResetDialog = UI.dismiss(this, mTwoFactorResetDialog);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        final TabbedMainActivity caller = TabbedMainActivity.this;

        switch (requestCode) {
            case REQUEST_TX_DETAILS:
                if (data != null && data.getBooleanExtra(REQUEST_RELOAD, false)) {
                    mService.updateBalance(mService.getCurrentSubAccount());
                    startActivity(new Intent(this, TabbedMainActivity.class));
                    finish();
                }
                break;
            case REQUEST_SETTINGS:
                mService.updateBalance(mService.getCurrentSubAccount());
                startActivity(new Intent(this, TabbedMainActivity.class));
                finish();
                break;
            case REQUEST_BITID_URL_LOGIN:
                if (resultCode != RESULT_OK) {
                    // The user failed to login after clicking on a bitcoin Uri
                    finish();
                    return;
                }
                mIsBitcoinUri = false;
                mIsBitidUri = true;
                launch();
                break;
            case REQUEST_BITCOIN_URL_LOGIN:
                if (resultCode != RESULT_OK) {
                    // The user failed to login after clicking on a bitcoin Uri
                    finish();
                    return;
                }
                mIsBitcoinUri = true;
                mIsBitidUri = false;
                launch();
                break;
            case REQUEST_CLEAR_ACTIVITY:
                recreate();
                break;
            case REQUEST_SWEEP_PRIVKEY:
                if (data == null)
                    break;

                final String qrText;
                if (Build.VERSION.SDK_INT >= 23) {
                    final IntentResult result = GaIntentIntegrator.parseActivityResult(resultCode, data);
                    qrText = result.getContents();
                } else {
                    qrText = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                }

                ECKey keyNonFinal = null;
                try {
                    keyNonFinal = DumpedPrivateKey.fromBase58(mService.getNetworkParameters(),
                            qrText).getKey();
                } catch (final AddressFormatException e) {
                    try {
                        Wally.bip38_to_private_key(qrText, null, Wally.BIP38_KEY_COMPRESSED | Wally.BIP38_KEY_QUICK_CHECK);
                    } catch (final IllegalArgumentException e2) {

                        String qrTextPaperwallet = qrText;
                        if (qrTextPaperwallet.startsWith("bitcoin:")) {
                            qrTextPaperwallet = qrTextPaperwallet.replaceFirst("^bitcoin:", "").replace("?.*$", "");
                        }
                        if (!isPublicKey(qrTextPaperwallet)) {
                            toast(R.string.invalid_paperwallet);
                            return;
                        }
                        // open webview to verify the wallet content
                        Intent intent = new Intent(caller, VisiuWebview.class);
                        intent.putExtra("public_address", qrTextPaperwallet);
                        startActivityForResult(intent, REQUEST_VISIU);
                        overridePendingTransition(R.anim.slide_from_right, R.anim.fade_out);
                        return;
                    }
                }

                final MaterialDialog dialogLoading = UI.popupWait(TabbedMainActivity.this, R.string.sweep_wait_message);
                dialogLoading.hide();
                dialogLoading.setCancelable(false);

                final ECKey keyNonBip38 = keyNonFinal;
                final FutureCallback<Map<?, ?>> callback = new CB.Toast<Map<?, ?>>(caller) {
                    @Override
                    public void onSuccess(final Map<?, ?> sweepResult) {
                        dialogLoading.dismiss();
                        final View v = UI.inflateDialog(TabbedMainActivity.this, R.layout.dialog_sweep_address);
                        final TextView passwordPrompt = UI.find(v, R.id.sweepAddressPasswordPromptText);
                        final TextView mainText = UI.find(v, R.id.sweepAddressMainText);
                        final TextView addressText = UI.find(v, R.id.sweepAddressAddressText);
                        final EditText passwordEdit = UI.find(v, R.id.sweepAddressPasswordText);
                        final Transaction txNonBip38;
                        final String address;

                        if (keyNonBip38 != null) {
                            UI.hide(passwordPrompt, passwordEdit);
                            txNonBip38 = getSweepTx(sweepResult);
                            Coin outputsValue = Coin.ZERO;
                            for (final TransactionOutput output : txNonBip38.getOutputs())
                                outputsValue = outputsValue.add(output.getValue());
                            final String valueStr = formatValuePostfix(outputsValue);
                            final String areYouSureText = String.format(
                                    getResources().getText(R.string.sweep_are_you_sure).toString(),
                                    valueStr);
                            address = keyNonBip38.toAddress(mService.getNetworkParameters()).toString();
                            mainText.setText(Html.fromHtml(areYouSureText));
                        } else {
                            passwordPrompt.setText(R.string.sweep_bip38_passphrase_prompt);
                            txNonBip38 = null;
                            // amount not known until decrypted
                            mainText.setText(Html.fromHtml(getResources().getText(R.string.sweep_bip38_are_you_sure).toString()));
                            address = qrText;
                        }


                        addressText.setText(String.format("%s\n%s\n%s", address.substring(0, 12), address.substring(12, 24), address.substring(24)));

                        final MaterialDialog.Builder builder = UI.popup(caller, R.string.sweepAddressTitle, R.string.sweep, R.string.cancel)
                            .customView(v, true)
                            .onPositive(new MaterialDialog.SingleButtonCallback() {
                                Transaction tx;
                                ECKey key;

                                private void doSweep() {
                                    final ArrayList<String> scripts = (ArrayList) sweepResult.get("prevout_scripts");
                                    final Integer outPointer = (Integer) sweepResult.get("out_pointer");
                                    CB.after(mService.verifySpendableBy(tx.getOutputs().get(0), 0, outPointer),
                                             new CB.Toast<Boolean>(caller) {
                                        @Override
                                        public void onSuccess(final Boolean isSpendable) {
                                            if (!isSpendable) {
                                                caller.toast(R.string.err_tabbed_sweep_failed);
                                                return;
                                            }
                                            final List<byte[]> signatures = new ArrayList<>();
                                            for (int i = 0; i < tx.getInputs().size(); ++i) {
                                                final byte[] script = Wally.hex_to_bytes(scripts.get(i));
                                                final TransactionSignature sig;
                                                sig = tx.calculateSignature(i, key, script, Transaction.SigHash.ALL, false);
                                                signatures.add(sig.encodeToBitcoin());
                                            }
                                            CB.after(mService.sendTransaction(signatures, null),
                                                     new CB.Toast<String>(caller) { });
                                        }
                                    });
                                }

                                @Override
                                public void onClick(final MaterialDialog dialog, final DialogAction which) {
                                    if (keyNonBip38 != null) {
                                        tx = txNonBip38;
                                        key = keyNonBip38;
                                        doSweep();
                                        return;
                                    }
                                    try {
                                        final String password = UI.getText(passwordEdit);
                                        final byte[] passbytes = password.getBytes();
                                        final byte[] decryptedPKey = Wally.bip38_to_private_key(qrText, passbytes, mService.getNetwork().getBip38Flags());
                                        key = ECKey.fromPrivate(decryptedPKey);

                                        CB.after(mService.prepareSweepSocial(key.getPubKey(), true),
                                                 new CB.Toast<Map<?, ?>>(caller) {
                                            @Override
                                            public void onSuccess(final Map<?, ?> sweepResult) {
                                                tx = getSweepTx(sweepResult);
                                                doSweep();
                                            }
                                        });
                                    } catch (final IllegalArgumentException e) {
                                        caller.toast(R.string.invalid_passphrase);
                                    }
                                }
                            });

                        runOnUiThread(new Runnable() { public void run() { builder.build().show(); } });
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        super.onFailure(t);
                        dialogLoading.dismiss();
                    }
                };


                // FIXME workaround to get info about paperwallet before call WAMP GA server because is too slow the GA server to reply
                @Deprecated
                class GetPublicKeyBalance extends AsyncTask<String, Void, String> {

                    private String pubKey;

                    @Override
                    protected String doInBackground(String... strings) {
                        String balance = "";
                        try {
                            pubKey = strings[0];
                            final String apikey = "9e3043e0226a7f5e94f881c4bc37340efb265f1e";
                            final String apiurl = "http://api.blocktrail.com/v1/" +
                                    (mService.getNetworkParameters().getId().equals(NetworkParameters.ID_MAINNET)? "BTC" : "tBTC") +
                                    "/address/";
                            URL url = new URL(apiurl + pubKey + "?api_key=" + apikey);
                            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

                            InputStream stream = new BufferedInputStream(urlConnection.getInputStream());
                            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream));
                            StringBuilder builder = new StringBuilder();

                            String inputString;
                            while ((inputString = bufferedReader.readLine()) != null) {
                                builder.append(inputString);
                            }

                            JSONObject topLevel = new JSONObject(builder.toString());
                            balance = topLevel.getString("balance");
                            urlConnection.disconnect();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        return balance;
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        if (result.isEmpty()) {
                            toast(R.string.invalid_paperwallet);
                            return;
                        }
                        final Float balanceBtc = Float.valueOf(result)/100000000;
                        if (balanceBtc == 0) {
                            // open webview to verify the wallet content
                            Intent intent = new Intent(caller, VisiuWebview.class);
                            intent.putExtra("public_address", pubKey);
                            startActivity(intent);
                            overridePendingTransition(R.anim.slide_from_right, R.anim.fade_out);
                            return;
                        }
                        final String warningSweepPrivKey = String.format(mActivity.getResources().getString(R.string.warningSweepPrivKey), balanceBtc.toString());
                        final Dialog confirmDialog = UI.popup(mActivity, R.string.warning)
                                .content(warningSweepPrivKey)
                                .onPositive(new MaterialDialog.SingleButtonCallback() {
                                    @Override
                                    public void onClick(final MaterialDialog dialog, final DialogAction which) {
                                        dialogLoading.show();
                                        if (keyNonBip38 != null)
                                            CB.after(mService.prepareSweepSocial(keyNonBip38.getPubKey(), true), callback);
                                        else
                                            callback.onSuccess(null);
                                    }
                                })
                                .onNegative(new MaterialDialog.SingleButtonCallback() {
                                    @Override
                                    public void onClick(final MaterialDialog dialog, final DialogAction which) {
                                        dialog.cancel();
                                    }
                                }).build();

                        confirmDialog.show();
                    }
                }
                final String pubKey = getPublicKeyStringFromHash(keyNonBip38.getPubKeyHash());
                new GetPublicKeyBalance().execute(pubKey);

                break;
            case REQUEST_VISIU:
                if (data != null && isBitcoinScheme(data)) {
                    final Intent browsable = new Intent(this, TabbedMainActivity.class);
                    browsable.setData(data.getData());
                    browsable.addCategory(Intent.CATEGORY_BROWSABLE);
                    browsable.putExtra("internal_qr", true);
                    // start new activity and finish old one
                    startActivity(browsable);
                    this.finish();
                }
                break;
        }
    }

    /**
     * get public key from the byte array hash
     * @param pubKeyBytes the byte array hash of the public key
     * @return String of public key
     */
    @Deprecated
    private String getPublicKeyStringFromHash(byte [] pubKeyBytes) {
        byte [] pubKeyBytes2 = new byte [pubKeyBytes.length + 1];
        byte [] pubKeyBytes3 = new byte [pubKeyBytes2.length + 4];

        if (mService.getNetwork() != null) {
            if (mService.getNetworkParameters().getId().equals(NetworkParameters.ID_MAINNET)) {
                pubKeyBytes2[0] = 0;
            } else {
                pubKeyBytes2[0] = 0x6F;
            }
        } else {
            return "";
        }
        System.arraycopy(pubKeyBytes, 0, pubKeyBytes2, 1, pubKeyBytes.length);

        byte[] sha256 = Sha256Hash.hash(Sha256Hash.hash(pubKeyBytes2));

        // get checksum and put 4 bytes in the end
        System.arraycopy(pubKeyBytes2, 0, pubKeyBytes3, 0, pubKeyBytes2.length);
        System.arraycopy(sha256, 0, pubKeyBytes3, pubKeyBytes3.length - 4, 4);

        return Base58.encode(pubKeyBytes3);
    }

    private Transaction getSweepTx(final Map<?, ?> sweepResult) {
        return GaService.buildTransaction((String) sweepResult.get("tx"), mService.getNetworkParameters());
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.camera_menu, menu);
        final boolean isResetActive = mService.isTwoFactorResetActive();
        final boolean isWatchOnly = mService.isWatchOnly();
        final int id;
        if (isResetActive)
            id = R.menu.reset_active;
        else if (isWatchOnly)
           id = R.menu.watchonly;
        else
            id = R.menu.main;
        getMenuInflater().inflate(id, menu);

        if (isResetActive) {
            setMenuItemVisible(menu, R.id.action_dispute_twofactor_reset, !isWatchOnly);
            setMenuItemVisible(menu, R.id.action_cancel_twofactor_reset, !isWatchOnly);
        } else {
            setMenuItemVisible(menu, R.id.action_network,
                               !mService.isElements() && mService.isSPVEnabled());
            setMenuItemVisible(menu, R.id.action_sweep, !mService.isElements());

            final boolean isExchanger = mService.cfg().getBoolean("show_exchanger_menu", false);
            setMenuItemVisible(menu, R.id.action_exchanger, isExchanger);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final SectionsPagerAdapter adapter = getPagerAdapter();
        setMenuItemVisible(menu, R.id.network_unavailable, !mService.isLoggedIn());
        //setMenuItemVisible(menu, R.id.action_share, adapter != null && adapter.mSelectedPage == 0);

        // get advanced_options flag and show/hide menu items
        setMenuItemVisible(menu, R.id.action_network, !mService.isWatchOnly() && !mService.hasAdvancedOption());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        final TabbedMainActivity caller = TabbedMainActivity.this;

        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivityForResult(new Intent(caller, SettingsActivity.class), REQUEST_SETTINGS);
                return true;
            case R.id.action_exchanger:
                startActivity(new Intent(caller, MainExchanger.class));
                return true;
            case R.id.action_sweep:
                if (Build.VERSION.SDK_INT >= 23) {
                    GaIntentIntegrator.scanQRCode(TabbedMainActivity.this, REQUEST_SWEEP_PRIVKEY);
                } else {
                    final Intent scanner = new Intent(caller, ScanActivity.class);
                    startActivityForResult(scanner, REQUEST_SWEEP_PRIVKEY);
                }
                return true;
            case R.id.network_unavailable:
                return true;
            case R.id.action_share:
                getPagerAdapter().onOptionsItemSelected(item);
                return true;
            case R.id.action_logout:
                mService.disconnect(false);
                finish();
                return true;
            case R.id.action_network:
                startActivity(new Intent(caller, NetworkMonitorActivity.class));
                return true;
            case R.id.action_about:
                startActivity(new Intent(caller, AboutActivity.class));
                return true;
            case R.id.action_cancel_twofactor_reset:
                onCancelTwoFactorResetSelected();
                return true;
             case R.id.action_dispute_twofactor_reset:
                onDisputeTwoFactorResetSelected();
                return true;
         }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (mViewPager.getCurrentItem() == 1)
            finish();
        else
            mViewPager.setCurrentItem(1);
    }

    @Override
    public void update(final Observable observable, final Object data) {
        final GaService.State state = (GaService.State) data;
        if (state.isForcedOff()) {
            // FIXME: Should pass flag to activity so it shows it was forced logged out
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
        }
        invalidateOptionsMenu();
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] granted) {
        if (requestCode == 200 &&
            isPermissionGranted(granted, R.string.err_tabbed_sweep_requires_camera_permissions))
            startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_SWEEP_PRIVKEY);
        else if (requestCode == 100 &&
                 isPermissionGranted(granted, R.string.err_qrscan_requires_camera_permissions))
            startActivityForResult(new Intent(this, ScanActivity.class), REQUEST_SEND_QR_SCAN);
    }

    /**
     * Try to manage BitID url and login/authenticate
     * @param bitidUrl String
     */
    private void bitidAuth(final String bitidUrl) {
        final BitID bitId;
        try {
            bitId = BitID.parse(bitidUrl);
        } catch (URISyntaxException e) {
            toast(getResources().getString(R.string.err_unsupported_bitid));
            return;
        }

        final String bitIdHost = bitId.getUri().getHost();

        final ISigningWallet signingWallet;
        try {
            signingWallet = mService.getBitidWallet(bitidUrl, 0);
        } catch (UnsupportedOperationException | IOException e) {
            toast(getResources().getString(R.string.err_unsupported_wallet));
            return;
        } catch (URISyntaxException e) {
            new QrcodeScanDialog(this, bitidUrl).show();
            return;
        }

        // wait login popup
        final MaterialDialog waitLoginPopup = UI.popupWaitCustom(mActivity,R.string.bitid_dialog_title)
                .content(getResources().getString(R.string.bitid_login_to, bitId.getUri().getHost())).build();

        // callback to manage thw sign in response
        final BitidSignIn.OnPostSignInListener callback = new BitidSignIn.OnPostSignInListener() {
            @Override
            public void postExecuteSignIn(SignInResponse response) {
                waitLoginPopup.cancel();
                if (response == null) {
                    UI.popup(mActivity, R.string.bitid_dialog_title, android.R.string.ok)
                            .content(getResources().getString(R.string.err_unsupported_bitid))
                            .build().show();
                } else if (response.getResultCode() == 0) {
                    UI.toast(mActivity, getResources().getString(R.string.bitid_login_successful, bitIdHost), Toast.LENGTH_LONG);
                    Log.d(TAG, "bitid succsssful login." + " message: " + (!response.getMessage().isEmpty() ? response.getMessage() : "no message received"));
                    if (!mInternalQr) {
                        // when arrives from external call, close activity and back to the caller
                        finish();
                    }
                } else {
                    final String errMessage = getResources().getString(R.string.err_bitid_login_error, response.getResultCode());

                    final View bitidErrorDialogView = getLayoutInflater().inflate(R.layout.dialog_bitid_error, null, false);

                    final TextView bitIdErrorMessage = UI.find(bitidErrorDialogView, R.id.bitIdErrorMessage);

                    String serverMessageError = null;
                    String jsonData = response.getMessage();
                    try {
                        final JSONObject json = new JSONObject(jsonData);
                        serverMessageError = (String) json.get("message");
                    } catch (JSONException e) {
                        Log.d(TAG, "bitid login error. Not valid error: " + response.getMessage());
                        UI.hide(bitIdErrorMessage);
                    } finally {
                        bitIdErrorMessage.setText(serverMessageError);
                    }
                    ((TextView) UI.find(bitidErrorDialogView, R.id.bitIdError)).setText(errMessage);

                    UI.popup(mActivity, R.string.bitid_dialog_title, android.R.string.ok)
                            .customView(bitidErrorDialogView, true)
                            .build().show();
                    Log.d(TAG, "bitid login error. code: " + response.getResultCode() + " message:" + response.getMessage());
                }
            }
        };

        final View bitidDialogView = getLayoutInflater().inflate(R.layout.dialog_bitid_request, null, false);

        final TextView bitidHostView = UI.find(bitidDialogView, R.id.bitidHost);
        bitidHostView.setText(bitIdHost);

        // warning message if callback is http (insecure)
        final TextView bitidInsecureText = UI.find(bitidDialogView, R.id.bitidInsecureText);
        if (!bitId.isSecured())
            UI.show(bitidInsecureText);

        UI.popup(this, R.string.bitid_dialog_title, R.string.confirm, R.string.cancel)
                .customView(bitidDialogView, true)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        new BitidSignIn().execute(mActivity, bitId, signingWallet, callback, mService);
                        waitLoginPopup.show();
                    }
                }).build().show();
    }

    private void onCancelTwoFactorResetSelected() {
        mTwoFactorDialog = UI.popupTwoFactorChoice(this, mService, false, new CB.Runnable1T<String>() {
            public void run(final String method) {
                onCancelTwoFactorReset(method);
            }
        });
        if (mTwoFactorDialog != null)
            mTwoFactorDialog.show();
    }

    private void onDisputeTwoFactorResetSelected() {
        final Intent intent = new Intent(this, TwoFactorActivity.class);
        intent.putExtra("method", "reset");
        startActivityForResult(intent, REQUEST_ENABLE_2FA);
    }

    private void onCancelTwoFactorReset(final String method) {
        // Request a two factor code for the 2FA reset
        if (!method.equals("gauth"))
            mService.requestTwoFacCode(method, "cancel_reset", null);

        // Prompt the user to enter the code
        final View v = UI.inflateDialog(this, R.layout.dialog_btchip_pin);

        UI.hide(UI.find(v, R.id.btchipPinPrompt));
        final TextView codeText = UI.find(v, R.id.btchipPINValue);

        mTwoFactorResetDialog = UI.popup(this, R.string.pref_header_twofactor, R.string.continueText, R.string.cancel)
            .customView(v, true)
            .autoDismiss(false)
            .onNegative(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(final MaterialDialog dialog, final DialogAction which) {
                    mTwoFactorResetDialog = UI.dismiss(null, mTwoFactorResetDialog);
                }
            })
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(final MaterialDialog dialog, final DialogAction which) {
                    final String enteredCode = UI.getText(codeText).trim();
                    if (enteredCode.length() != 6)
                        return;
                    // Cancel the reset and exit
                    try {
                        mTwoFactorResetDialog = UI.dismiss(null, mTwoFactorResetDialog);
                        mService.cancelTwoFactorReset(mService.make2FAData(method, enteredCode));
                        UI.toast(TabbedMainActivity.this, R.string.twofactor_reset_cancelled, Toast.LENGTH_LONG);
                        exitApp();
                    } catch (final Exception e) {
                        UI.toast(TabbedMainActivity.this, e.getMessage(), (Button) null);
                        e.printStackTrace();
                    }
                }
            }).build();
        UI.mapEnterToPositive(mTwoFactorResetDialog, R.id.btchipPINValue);
        mTwoFactorResetDialog.show();
      }

    SectionsPagerAdapter getPagerAdapter() {
        if (mViewPager == null)
            return null;
        return (SectionsPagerAdapter) mViewPager.getAdapter();
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        private final SubaccountFragment[] mFragments = new SubaccountFragment[3];
        public int mSelectedPage = -1;
        private int mInitialSelectedPage = -1;
        private boolean mInitialPage = true;

        public SectionsPagerAdapter(final FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(final int index) {
            Log.d(TAG, "SectionsPagerAdapter -> getItem " + index);
            if (mService.isTwoFactorResetActive())
                return new MainFragment();

            switch (index) {
                case 0: return new ReceiveFragment();
                case 1: return new MainFragment();
                case 2: return new SendFragment();
            }
            return null;
        }

        @Override
        public Object instantiateItem(final ViewGroup container, final int index) {
            Log.d(TAG, "SectionsPagerAdapter -> instantiateItem " + index);

            mFragments[index] = (SubaccountFragment) super.instantiateItem(container, index);

            if (mInitialPage && index == mInitialSelectedPage) {
                // Call setPageSelected() on the first page, now that it is created
                Log.d(TAG, "SectionsPagerAdapter -> selecting first page " + index);
                mFragments[index].setPageSelected(true);
                mInitialSelectedPage = -1;
                mInitialPage = false;
            }
            return mFragments[index];
        }

        @Override
        public void destroyItem(final ViewGroup container, final int index, final Object object) {
            Log.d(TAG, "SectionsPagerAdapter -> destroyItem " + index);
            if (index >=0 && index <=2 && mFragments[index] != null) {
                // Make sure the fragment is not kept alive and does not
                // try to process any callbacks it registered for.
                mFragments[index].detachObservers();
                // Make sure any wait dialog being shown is dismissed
                mFragments[index].setPageSelected(false);
                mFragments[index] = null;
            }
            super.destroyItem(container, index, object);
        }

        @Override
        public int getCount() {
            // Only show the tx list when 2FA reset is active
            if (mService.isTwoFactorResetActive())
                return 1;
            // We don't show the send tab in watch only mode
            return mService.isWatchOnly() ? 2 : 3;
        }

        @Override
        public CharSequence getPageTitle(final int index) {
            final Locale l = Locale.getDefault();
            if (mService.isTwoFactorResetActive())
                return getString(R.string.main_title).toUpperCase(l);
             switch (index) {
                case 0: return getString(R.string.receive_title).toUpperCase(l);
                case 1: return getString(R.string.main_title).toUpperCase(l);
                case 2: return getString(R.string.send_title).toUpperCase(l);
            }
            return null;
        }

        public void onViewPageSelected(final int index) {
            Log.d(TAG, "SectionsPagerAdapter -> onViewPageSelected " + index +
                       " current is " + mSelectedPage + " initial " + mInitialPage);

            if (mInitialPage)
                mInitialSelectedPage = index; // Store so we can notify it when constructed

            if (index == mSelectedPage)
                return; // No change to the selected page

            // Un-select any old selected page
            if (mSelectedPage != -1 && mFragments[mSelectedPage] != null)
                mFragments[mSelectedPage].setPageSelected(false);

            // Select the current page
            mSelectedPage = index;
            if (mFragments[mSelectedPage] != null)
                mFragments[mSelectedPage].setPageSelected(true);

            invalidateOptionsMenu();
        }

        public void setBlockWaitDialog(final boolean doBlock) {
            for (final SubaccountFragment fragment : mFragments)
                if (fragment != null)
                    fragment.setBlockWaitDialog(doBlock);
        }

        public void onOptionsItemSelected(final MenuItem item) {
            if (item.getItemId() == R.id.action_share)
                if (mSelectedPage == 0 && mFragments[0] != null)
                    mFragments[0].onShareClicked();
        }
    }

    /**
     * Show backup warning dialog to remember to the user to do a backup
     */
    private void showBackupWarningDialog() {
        final boolean isWarningAlreadyShowed = mService.cfg().getBoolean("session_backup_warning_showed", false);
        if (isWarningAlreadyShowed)
            return;

        final View view = getLayoutInflater().inflate(R.layout.dialog_backup_warning, null, false);
        final TextView smile1 = UI.find(view, R.id.smile1);
        final TextView smile2 = UI.find(view, R.id.smile2);
        final Button backupButton = UI.find(view, R.id.backupButton);

        // set typeface for smile icons
        final Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/NotoEmoji-Regular.ttf");
        smile1.setTypeface(typeface);
        smile2.setTypeface(typeface);

        final MaterialDialog warningDialog = UI.popup(this, R.string.backup_wallet, R.string.skip_backup)
                .customView(view, false)
                .cancelable(false)
                .build();

        // try to hide title
        final View parent = (View) warningDialog.getTitleView().getParent();
        if (parent != null)
            parent.setVisibility(View.GONE);


        // set backup button
        final Intent intent = new Intent(this, SignUpActivity.class);
        backupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                UI.popup(mActivity, R.string.warning, R.string.ok, R.string.cancel)
                        .content(R.string.warning_show_mnemonic)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                warningDialog.dismiss();
                                intent.putExtra(GaPreferenceActivity.FROM_PREFERENCE_ACTIVITY, true);
                                startActivity(intent);
                            }
                        })
                        .build()
                        .show();
            }
        });

        // positive button, to show only for !dev_mode && mainnet and !debug
        final MDButton positiveButton = warningDialog.getActionButton(DialogAction.POSITIVE);

        final boolean isDev = mService.cfg("dev_mode", "Bitcoin").getBoolean("enabled", false);
        if (!BuildConfig.DEBUG && !isDev)
            positiveButton.setEnabled(false);

        new CountDownTimer(COUNTDOWN_DIALOG_WARNING_BACKUP * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                positiveButton.setText(String.format(Locale.getDefault(), "%d", millisUntilFinished / 1000));
            }
            public void onFinish() {
                positiveButton.setText(getString(R.string.skip_backup));
                positiveButton.setEnabled(true);
            }
        }.start();

        UI.showDialog(warningDialog);
        mService.cfg().edit().putBoolean("session_backup_warning_showed", true).apply();
    }
}
