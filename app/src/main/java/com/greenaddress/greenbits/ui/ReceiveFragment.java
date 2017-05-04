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
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.greenaddress.greenapi.Network;
import com.greenaddress.greenbits.QrBitmap;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.utils.MonetaryFormat;

import nordpol.android.OnDiscoveredTagListener;
import nordpol.android.TagDispatcher;


public class ReceiveFragment extends SubaccountFragment implements OnDiscoveredTagListener, AmountFields.OnConversionFinishListener {
    private static final String TAG = ReceiveFragment.class.getSimpleName();

    private FutureCallback<QrBitmap> mNewAddressCallback = null;
    private QrBitmap mQrCodeBitmap = null;
    private int mSubaccount = 0;
    private Dialog mQrCodeDialog = null;
    private TagDispatcher mTagDispatcher = null;
    private TextView mAddressText = null;
    private ImageView mAddressImage = null;
    private TextView mCopyIcon = null;
    private TextView mShareIcon = null;
    private LinearLayout mReceiveAddressLayout = null;
    private final Runnable mDialogCB = new Runnable() { public void run() { mQrCodeDialog = null; } };

    private EditText mAmountEdit;
    private EditText mAmountFiatEdit;
    private String mCurrentAddress = "";
    private Coin mCurrentAmount = null;
    private BitmapWorkerTask mBitmapWorkerTask;
    private AmountFields mAmountFields = null;

    @Override
    public void onResume() {
        super.onResume();
        if (mAmountFields != null)
            mAmountFields.setIsPausing(false);
        Log.d(TAG, "onResume -> " + TAG);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAmountFields != null)
            mAmountFields.setIsPausing(true);
        Log.d(TAG, "onPause -> " + TAG);
        if (mQrCodeDialog != null) {
            mQrCodeDialog.dismiss();
            mQrCodeDialog = null;
        }
        if (mTagDispatcher != null)
            mTagDispatcher.disableExclusiveNfc();
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView -> " + TAG);

        if (isZombieNoView())
            return null;

        popupWaitDialog(R.string.generating_address);

        final GaActivity gaActivity = getGaActivity();

        mTagDispatcher = TagDispatcher.get(gaActivity, this);
        mTagDispatcher.enableExclusiveNfc();

        mSubaccount = getGAService().getCurrentSubAccount();

        mView = inflater.inflate(R.layout.fragment_receive, container, false);

        mAmountFields = new AmountFields(getGAService(), getContext(), mView, this);
        if (savedInstanceState != null) {
            final Boolean pausing = savedInstanceState.getBoolean("pausing", false);
            mAmountFields.setIsPausing(pausing);
        }

        mReceiveAddressLayout = UI.find(mView, R.id.receiveAddressLayout);
        mAddressText = UI.find(mView, R.id.receiveAddressText);
        mAddressImage = UI.find(mView, R.id.receiveQrImageView);

        mCopyIcon = UI.find(mView, R.id.receiveCopyIcon);
        UI.disable(mCopyIcon);
        mCopyIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onCopyClicked();
            }
        });

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
                getNewAddress();
            }
        });
        mAmountEdit = UI.find(mView, R.id.sendAmountEditText);
        mAmountFiatEdit = UI.find(mView, R.id.sendAmountFiatEditText);

        registerReceiver();
        return mView;
    }

    @Override
    public void conversionFinish() {
        if (mBitmapWorkerTask != null)
            mBitmapWorkerTask.cancel(true);
        mBitmapWorkerTask = new BitmapWorkerTask();
        mBitmapWorkerTask.execute();
    }

    class BitmapWorkerTask extends AsyncTask<Object, Object, Bitmap> {

        @Override
        protected Bitmap doInBackground(Object... integers) {
            final String amount = UI.getText(mAmountEdit);
            mCurrentAmount = null;
            if (amount.isEmpty())
                return mQrCodeBitmap == null ? null : resetBitmap(mCurrentAddress);

            try {
                final String btcUnit = (String) getGAService().getUserConfig("unit");
                final MonetaryFormat bitcoinFormat = CurrencyMapper.mapBtcUnitToFormat(btcUnit);

                final Address address = Address.fromBase58(Network.NETWORK, mCurrentAddress);
                mCurrentAmount = bitcoinFormat.parse(amount);
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

    private void getNewAddress() {
        Log.d(TAG, "Generating new address for subaccount " + mSubaccount);
        if (isZombie())
            return;
        mAmountEdit.setText("");
        mAmountFiatEdit.setText("");
        mCurrentAddress = "";
        popupWaitDialog(R.string.generating_address);
        UI.disable(mCopyIcon);
        UI.disable(mShareIcon);
        destroyCurrentAddress();
        Futures.addCallback(getGAService().getNewAddressBitmap(mSubaccount),
                            mNewAddressCallback, getGAService().getExecutor());
    }

    private void destroyCurrentAddress() {
        Log.d(TAG, "Destroying address for subaccount " + mSubaccount);
        if (isZombie())
            return;
        mAmountEdit.setText("");
        mAmountFiatEdit.setText("");
        mCurrentAddress = "";
        mAddressText.setText("");
        mAddressImage.setImageBitmap(null);
        mReceiveAddressLayout.setVisibility(View.GONE);
    }

    private void onCopyClicked() {
        // Gets a handle to the clipboard service.
        final GaActivity gaActivity = getGaActivity();
        final ClipboardManager cm;
        cm = (ClipboardManager) gaActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData data = ClipData.newPlainText("data", mQrCodeBitmap.getData());
        cm.setPrimaryClip(data);
        final String text = gaActivity.getString(R.string.toastOnCopyAddress) +
                            " " + gaActivity.getString(R.string.warnOnPaste);
        gaActivity.toast(text);
    }

    private void onAddressImageClicked(final BitmapDrawable bd) {
        if (mQrCodeDialog != null)
            mQrCodeDialog.dismiss();

        final View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_qrcode, null, false);

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
        mAddressText.setText(String.format("%s\n%s", qrData.substring(0, 18),
                             qrData.substring(18)));
        mCurrentAddress = result.getData();

        mAddressImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onAddressImageClicked(bd);
            }
        });

        hideWaitDialog();
        UI.enable(mCopyIcon);
        UI.enable(mShareIcon);
        mReceiveAddressLayout.setVisibility(View.VISIBLE);
    }

    @Override
    public void tagDiscovered(final Tag t) {
        Log.d(TAG, "Tag discovered " + t);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate -> " + TAG);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.receive, menu);
    }

    @Override
    protected void onSubaccountChanged(final int newSubAccount) {
        mSubaccount = newSubAccount;
        if (IsPageSelected())
            getNewAddress();
        else
            destroyCurrentAddress();
    }

    private String getAddressUri() {
        final Address address = Address.fromBase58(Network.NETWORK, mCurrentAddress);
        return BitcoinURI.convertToBitcoinURI(address, mCurrentAmount, null, null);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {

        if (item.getItemId() == R.id.action_share) {
            if (mQrCodeBitmap != null && !mQrCodeBitmap.getData().isEmpty()) {
                // SHARE intent
                UI.shareQrcodeAddress(getGaActivity(), mQrCodeBitmap.getQRCode(), getAddressUri());
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void setPageSelected(final boolean isSelected) {
        final boolean needToRegenerate = isSelected && !IsPageSelected();
        super.setPageSelected(isSelected);
        if (needToRegenerate)
            getNewAddress();
        else if (!isSelected)
            destroyCurrentAddress();
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
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        // save page selected state and current address useful after rotate
        outState.putBoolean("pageSelected", IsPageSelected());
        outState.putString("currentAddress", mCurrentAddress);
        if (mAmountFields != null)
            outState.putBoolean("pausing", mAmountFields.isPausing());
    }
}
