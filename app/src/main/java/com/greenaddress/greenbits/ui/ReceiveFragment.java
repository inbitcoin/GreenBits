package com.greenaddress.greenbits.ui;


import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.blockstream.libwally.Wally;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.greenaddress.greenapi.ConfidentialAddress;
import com.greenaddress.greenapi.JSONMap;
import com.greenaddress.greenapi.Network;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.QrBitmap;

import java.util.concurrent.Callable;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Observer;

import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;


public class ReceiveFragment extends SubaccountFragment implements OnDiscoveredTagListener, AmountFields.OnConversionFinishListener, Exchanger.OnCalculateCommissionFinishListener {
    private static final String TAG = ReceiveFragment.class.getSimpleName();

    private FutureCallback<QrBitmap> mNewAddressCallback;
    private FutureCallback<Void> mNewAddressFinished;
    private QrBitmap mQrCodeBitmap;
    private int mSubaccount;
    private Dialog mQrCodeDialog;
    private TagDispatcher mTagDispatcher;
    private TextView mAddressText;
    private ImageView mAddressImage;
    private TextView mShareIcon = null;
    private TextView mCopyIcon;
    private LinearLayout mReceiveAddressLayout = null;
    private final Runnable mDialogCB = new Runnable() { public void run() { mQrCodeDialog = null; } };

    private EditText mAmountEdit;
    private EditText mAmountFiatEdit;
    private TextView mAmountFiatWithCommission;
    private String mCurrentAddress = "";
    private Coin mCurrentAmount;
    private BitmapWorkerTask mBitmapWorkerTask;
    private AmountFields mAmountFields;

    private Exchanger mExchanger;
    private boolean mIsExchanger;
    private Button mShowQrCode;
    private Observer mNewTxObserver;

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume -> " + TAG);

        if (mIsExchanger && getGAService() != null)
            attachObservers();

        if (mAmountFields != null)
            mAmountFields.setIsPausing(false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAmountFields != null)
            mAmountFields.setIsPausing(true);
        Log.d(TAG, "onPause -> " + TAG);
        mQrCodeDialog = UI.dismiss(getActivity(), mQrCodeDialog);
        if (mTagDispatcher != null)
            mTagDispatcher.disableExclusiveNfc();
        detachObservers();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView -> " + TAG);

        if (isZombieNoView())
            return null;

        final GaActivity gaActivity = getGaActivity();

        mTagDispatcher = TagDispatcher.get(gaActivity, this);
        mTagDispatcher.enableExclusiveNfc();

        mSubaccount = getGAService().getCurrentSubAccount();

        if (savedInstanceState != null)
            mIsExchanger = savedInstanceState.getBoolean("isExchanger", false);

        if (mIsExchanger)
            mView = inflater.inflate(R.layout.fragment_buy, container, false);
        else
            mView = inflater.inflate(R.layout.fragment_receive, container, false);

        mAddressText = UI.find(mView, R.id.receiveAddressText);
        mAddressImage = UI.find(mView, R.id.receiveQrImageView);
        mCopyIcon = UI.find(mView, R.id.receiveCopyIcon);
        mAmountEdit = UI.find(mView, R.id.sendAmountEditText);
        mAmountFiatEdit = UI.find(mView, R.id.sendAmountFiatEditText);

        final View amountFields = UI.find(mView, R.id.amountFields);

        mAmountFields = new AmountFields(getGAService(), getContext(), mView, this);
        if (savedInstanceState != null) {
            final Boolean pausing = savedInstanceState.getBoolean("pausing", false);
            mAmountFields.setIsPausing(pausing);
        }

        mReceiveAddressLayout = UI.find(mView, R.id.receiveAddressLayout);
        UI.disable(mCopyIcon);
        mCopyIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onCopyClicked();
            }
        });

        if (!mIsExchanger) {
            mShareIcon = UI.find(mView, R.id.receiveShareIcon);
            UI.disable(mShareIcon);
            mShareIcon.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    if (mQrCodeBitmap != null && !mQrCodeBitmap.getData().isEmpty()) {
                        // SHARE intent
                        UI.shareQrcodeAddress(getGaActivity(), mQrCodeBitmap.getQRCode(), getAddressUri());
                    }
                }
            });
        }

        mNewAddressCallback = new FutureCallback<QrBitmap>() {
            @Override
            public void onSuccess(final QrBitmap result) {
                if (getActivity() != null)
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            onNewAddressGenerated(result);
                        }
                    });
            }

            @Override
            public void onFailure(final Throwable t) {
                t.printStackTrace();
                if (getActivity() != null)
                    getActivity().runOnUiThread(new Runnable() {
                        public void run() {
                            hideWaitDialog();
                            UI.enable(mCopyIcon);
                            UI.enable(mShareIcon);
                        }
                    });
            }
        };

        final TextView newAddressIcon = UI.find(mView, R.id.receiveNewAddressIcon);
        newAddressIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                generateNewAddress();
            }
        });

        if (mIsExchanger) {
            setPageSelected(true);
            mAmountFiatWithCommission = UI.find(mView, R.id.amountFiatWithCommission);
            mExchanger = new Exchanger(getContext(), getGAService(), mView, true, this);
            mShowQrCode = UI.find(mView, R.id.showQrCode);
            mShowQrCode.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    final String amountStr = UI.getText(mAmountFiatWithCommission);
                    final double amount = Double.valueOf(amountStr);
                    if (amount > mExchanger.getFiatInBill()) {
                        UI.toast(getGaActivity(), R.string.noEnoughMoneyInPocket, Toast.LENGTH_LONG);
                        return;
                    }
                    final String amountBtc = UI.getText(mAmountEdit);
                    if (amountBtc.isEmpty() || Double.valueOf(amountBtc) <= 0.0) {
                        UI.toast(getGaActivity(), R.string.invalidAmount, Toast.LENGTH_LONG);
                        return;
                    }
                    generateNewAddress(false, new FutureCallback<Void>() {
                        @Override
                        public void onSuccess(final Void aVoid) {
                            String exchangerAddress = mCurrentAddress;
                            if (GaService.IS_ELEMENTS) {
                                final String currentBtcAddress = mCurrentAddress.replace("bitcoin:", "").split("\\?")[0];
                                exchangerAddress = ConfidentialAddress.fromBase58(Network.NETWORK, currentBtcAddress).getBitcoinAddress().toString();
                            }
                            getGAService().cfg().edit().putBoolean("exchanger_address_" + exchangerAddress, true).apply();
                            final BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), mQrCodeBitmap.getQRCode());
                            bitmapDrawable.setFilterBitmap(false);
                            mAddressImage.setImageDrawable(bitmapDrawable);
                            onAddressImageClicked(bitmapDrawable);
                        }

                        @Override
                        public void onFailure(final Throwable t) {
                            t.printStackTrace();
                        }
                    });
                }
            });
        }

        registerReceiver();
        return mView;
    }

    @Override
    public void conversionFinish() {
        if (mIsExchanger && mExchanger != null) {
            mExchanger.conversionFinish();
        } else {
            if (mBitmapWorkerTask != null)
                mBitmapWorkerTask.cancel(true);
            mBitmapWorkerTask = new BitmapWorkerTask();
            mBitmapWorkerTask.execute();
        }
    }

    @Override
    public void calculateCommissionFinish() {
        if (mBitmapWorkerTask != null)
            mBitmapWorkerTask.cancel(true);
        mBitmapWorkerTask = new BitmapWorkerTask();
        mBitmapWorkerTask.execute();
    }

    class BitmapWorkerTask extends AsyncTask<Object, Object, Bitmap> {

        @Override
        protected Bitmap doInBackground(final Object... integers) {
            if (mAmountEdit == null)
                return null;
            final String amount = UI.getText(mAmountEdit);
            mCurrentAmount = null;
            if (amount.isEmpty())
                return mQrCodeBitmap == null ? null : resetBitmap(mCurrentAddress);

            try {
                mCurrentAmount = UI.parseCoinValue(getGAService(), amount);

                final Address address = Address.fromBase58(Network.NETWORK, mCurrentAddress);
                final String qrCodeText = BitcoinURI.convertToBitcoinURI(address, mCurrentAmount, null, null);
                return resetBitmap(qrCodeText);
            } catch (final ArithmeticException | IllegalArgumentException e) {
                return resetBitmap(mCurrentAddress);
            }
        }

        @Override
        protected void onPostExecute(final Bitmap bitmap) {
            if (bitmap == null)
                return;
            if (getActivity() == null)
                return;
            final BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(), bitmap);
            bitmapDrawable.setFilterBitmap(false);
            mAddressImage.setImageDrawable(bitmapDrawable);
            mAddressImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    onAddressImageClicked(bitmapDrawable);
                }
            });
        }

        private Bitmap resetBitmap(final String address) {
            mQrCodeBitmap = new QrBitmap(address, Color.WHITE, getContext());
            return mQrCodeBitmap.getQRCode();
        }
    }

    private void generateNewAddress() {
        generateNewAddress(true, null);
    }

    private void generateNewAddress(final boolean clear, final FutureCallback<Void> onDone) {
        Log.d(TAG, "Generating new address for subaccount " + mSubaccount);
        if (isZombie())
            return;

        Long amount = null;
        if (clear)
            UI.clear(mAmountEdit, mAmountFiatEdit);
        if (mIsExchanger && GaService.IS_ELEMENTS) {
            // TODO: non-fiat / non-assets values
            final String amountText = UI.getText(mAmountEdit);
            if (amountText.isEmpty())
                return;
            amount = (long) (Double.valueOf(amountText) * 100);
        }
        mCurrentAddress = "";
        UI.disable(mCopyIcon);
        if (!mIsExchanger)
            UI.disable(mShareIcon);
        destroyCurrentAddress(clear);
        mNewAddressFinished = onDone;
        final Callable waitFn = new Callable<Void>() {
            @Override
            public Void call() {
                popupWaitDialog(R.string.generating_address);
                return null;
            }
        };
        Futures.addCallback(getGAService().getNewAddressBitmap(mSubaccount, waitFn, amount),
                            mNewAddressCallback, getGAService().getExecutor());
    }

    private void destroyCurrentAddress(final boolean clear) {
        Log.d(TAG, "Destroying address for subaccount " + mSubaccount);
        if (isZombie())
            return;
        mCurrentAddress = "";
        if (clear)
            UI.clear(mAmountEdit, mAmountFiatEdit, mAddressText);
        mAddressImage.setImageBitmap(null);
        UI.hide(mReceiveAddressLayout);
    }

    private void onCopyClicked() {
        // Gets a handle to the clipboard service.
        final GaActivity gaActivity = getGaActivity();
        final ClipboardManager cm;
        cm = (ClipboardManager) gaActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData data = ClipData.newPlainText("data", mQrCodeBitmap.getData());
        cm.setPrimaryClip(data);
        final String text = gaActivity.getString(R.string.toastOnCopyAddress) +
                ' ' + gaActivity.getString(R.string.warnOnPaste);
        gaActivity.toast(text);
    }

    private void onAddressImageClicked(final BitmapDrawable bd) {
        mQrCodeDialog = UI.dismiss(getActivity(), mQrCodeDialog);

        final View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_qrcode, null, false);
        if (mIsExchanger) {
            final Button cancelButton = UI.find(v, R.id.qrInDialogCancel);
            UI.show(cancelButton, UI.find(v, R.id.qrInDialogWaiting));
            cancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View view) {
                    mQrCodeDialog = UI.dismiss(getActivity(), mQrCodeDialog);
                }
            });
        }

        final ImageView qrCode = UI.find(v, R.id.qrInDialogImageView);
        qrCode.setLayoutParams(UI.getScreenLayout(getActivity(), 0.8));

        final Dialog dialog = new Dialog(getActivity());
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(v);
        UI.setDialogCloseHandler(dialog, mDialogCB);

        qrCode.setImageDrawable(bd);

        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                Activity activity = getGaActivity();
                if (activity != null)
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        });
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                Activity activity = getGaActivity();
                if (activity != null)
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

            }
        });
        mQrCodeDialog = dialog;
        mQrCodeDialog.show();

        int currentScreenOrientation = UI.getCurrentScreenOrientation(getActivity());
        //noinspection WrongConstant
        getActivity().setRequestedOrientation(currentScreenOrientation);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);

    // Checks the orientation of the screen
    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Toast.makeText(getContext(), "landscape", Toast.LENGTH_SHORT).show();
    } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
        Toast.makeText(getContext(), "portrait", Toast.LENGTH_SHORT).show();
    }

    }

    private void onNewAddressGenerated(final QrBitmap result) {
        if (getActivity() == null)
            return;

        if (mBitmapWorkerTask != null) {
            mBitmapWorkerTask.cancel(true);
            mBitmapWorkerTask = null;
        }

        mQrCodeBitmap = result;
        final BitmapDrawable bd = new BitmapDrawable(getResources(), result.getQRCode());
        bd.setFilterBitmap(false);
        mAddressImage.setImageDrawable(bd);

        final String qrData = result.getData();
        if (GaService.IS_ELEMENTS) {
            mAddressText.setText(String.format("%s\n" +
                            "%s\n%s\n" +
                            "%s\n%s\n" +
                            "%s\n%s",
                    qrData.substring(0, 12),
                    qrData.substring(12, 24),
                    qrData.substring(24, 36),
                    qrData.substring(36, 48),
                    qrData.substring(48, 60),
                    qrData.substring(60, 72),
                    qrData.substring(72)
            ));
            mAddressText.setLines(7);
            mAddressText.setMaxLines(7);
        } else {
            mAddressText.setText(String.format("%s\n%s", qrData.substring(0, 18),
                                 qrData.substring(18)));
        }
        mCurrentAddress = result.getData();

        mAddressImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onAddressImageClicked(bd);
            }
        });

        hideWaitDialog();
        UI.enable(mCopyIcon);
        if (!mIsExchanger)
            UI.enable(mShareIcon);
        UI.show(mReceiveAddressLayout);

        if (mNewAddressFinished != null)
            mNewAddressFinished.onSuccess(null);
    }

    @Override
    public void tagDiscovered(final Tag t) {
        Log.d(TAG, "Tag discovered " + t);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate -> " + TAG);
    }

    @Override
    protected void onSubaccountChanged(final int newSubAccount) {
        mSubaccount = newSubAccount;
        if (IsPageSelected())
            generateNewAddress();
        else
            destroyCurrentAddress(true);
    }

    private String getAddressUri() {
        final String addr;
        if (GaService.IS_ELEMENTS)
            addr = ConfidentialAddress.fromBase58(Network.NETWORK, mCurrentAddress).toString();
        else
            addr = Address.fromBase58(Network.NETWORK, mCurrentAddress).toString();
        return BitcoinURI.convertToBitcoinURI(Network.NETWORK, addr, mCurrentAmount, null, null);
    }

    @Override
    public void onShareClicked() {
        if (mQrCodeBitmap == null || mQrCodeBitmap.getData().isEmpty())
            return;

        UI.shareQrcodeAddress(getGaActivity(), mQrCodeBitmap.getQRCode(), getAddressUri());
    }

    public void setPageSelected(final boolean isSelected) {
        final boolean needToRegenerate = isSelected && !IsPageSelected();
        super.setPageSelected(isSelected);
        if (!isZombie() && isSelected)
            getGaActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        if (needToRegenerate)
            generateNewAddress();
        else if (!isSelected)
            destroyCurrentAddress(true);
    }

    @Override
    public void onViewStateRestored(final Bundle savedInstanceState) {
        Log.d(TAG, "onViewStateRestored -> " + TAG);
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            // restore page selected and se page selected or not
            final Boolean pageSelected = savedInstanceState.getBoolean("pageSelected", false);
            super.setPageSelected(pageSelected);

            // restore address and reset qr code bitmap
            mCurrentAddress = savedInstanceState.getString("currentAddress");
            mQrCodeBitmap = new QrBitmap(mCurrentAddress, Color.WHITE, getContext());
            conversionFinish();
        }
        if (mAmountFields != null)
            mAmountFields.setIsPausing(false);
        if (mIsExchanger)
            mExchanger.conversionFinish();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        // save page selected state and current address useful after rotate
        outState.putBoolean("pageSelected", IsPageSelected());
        outState.putString("currentAddress", mCurrentAddress);
        if (mAmountFields != null)
            outState.putBoolean("pausing", mAmountFields.isPausing());
        outState.putBoolean("isExchanger", mIsExchanger);
    }

    public void setIsExchanger(final boolean isExchanger) {
        mIsExchanger = isExchanger;
    }

    @Override
    public void attachObservers() {
        if (mNewTxObserver == null) {
            mNewTxObserver = makeUiObserver(new Runnable() { public void run() { onNewTx(); } });
            getGAService().addNewTxObserver(mNewTxObserver);
        }
        super.attachObservers();
    }

    @Override
    public void detachObservers() {
        super.detachObservers();
        if (mNewTxObserver!= null) {
            getGAService().deleteNewTxObserver(mNewTxObserver);
            mNewTxObserver = null;
        }
    }

    private void onNewTx() {
        final GaService service = getGAService();
        Futures.addCallback(service.getMyTransactions(mSubaccount),
                new FutureCallback<Map<String, Object>>() {
                    @Override
                    public void onSuccess(final Map<String, Object> result) {
                        final List txList = (List) result.get("list");
                        final int currentBlock = ((Integer) result.get("cur_block"));
                        for (final Object tx : txList) {
                            try {
                                final JSONMap txJSON = (JSONMap) tx;
                                final ArrayList<String> replacedList = txJSON.get("replaced_by");

                                if (replacedList == null) {
                                    final TransactionItem txItem = new TransactionItem(service, txJSON, currentBlock);
                                    final boolean matches;
                                    if (!GaService.IS_ELEMENTS)
                                        matches = txItem.receivedOn != null && txItem.receivedOn.equals(mCurrentAddress);
                                    else {
                                        final int subaccount = txItem.receivedOnEp.getInt("subaccount", 0);
                                        final int pointer = txItem.receivedOnEp.getInt("pubkey_pointer");
                                        final String receivedOn = ConfidentialAddress.fromP2SHHash(
                                            Network.NETWORK,
                                            Wally.hash160(service.createOutScript(subaccount, pointer)),
                                            service.getBlindingPubKey(subaccount, pointer)
                                        ).toString();
                                        final String currentBtcAddress = mCurrentAddress.replace("bitcoin:", "").split("\\?")[0];
                                        matches = receivedOn.equals(currentBtcAddress);
                                    }
                                    if (matches) {
                                        mExchanger.buyBtc(mExchanger.getAmountWithCommission());
                                        getGaActivity().toast(R.string.transactionCompleted);
                                        getGaActivity().finish();
                                    }
                                }
                            } catch (final ParseException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onFailure(final Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }
}
