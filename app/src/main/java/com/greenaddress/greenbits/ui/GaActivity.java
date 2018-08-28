package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.common.util.concurrent.Futures;
import com.google.zxing.integration.android.IntentResult;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.GreenAddressApplication;

import java.util.regex.Pattern;

import de.schildbach.wallet.ui.ScanActivity;

/**
 * Base class for activities within the application.
 *
 * Provides access to the main Application and Service objects along with
 * support code to handle service initialization, error handling etc.
 */
public abstract class GaActivity extends AppCompatActivity {

    private static final String TAG = GaActivity.class.getSimpleName();
    protected static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";

    // Both of these variables are only assigned in the UI thread.
    // mService is available to all derived classes as soon as
    // onCreateWithService() is called. Once assigned it does not
    // change so may be read from background threads.
    private boolean mResumed;
    protected GaService mService;

    private GreenAddressApplication getGAApp() {
        return (GreenAddressApplication) getApplication();
    }

    @Override
    protected final void onCreate(final Bundle savedInstanceState) {
        Log.d(TAG, "onCreate -> " + this.getClass().getSimpleName());
        super.onCreate(savedInstanceState);

        final int viewId = getMainViewId();
        if (viewId != UI.INVALID_RESOURCE_ID)
            setContentView(viewId);

        // Call onCreateWithService() on the GUI thread once our service
        // becomes available. In most cases this will execute immediately.
        Futures.addCallback(getGAApp().onServiceAttached, new CB.Op<Void>() {
            @Override
            public void onSuccess(final Void result) {
                GaActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        final GaActivity self = GaActivity.this;
                        Log.d(TAG, "onCreateWithService -> " + self.getClass().getSimpleName());
                        self.mService = getGAApp().mService;
                        self.onCreateWithService(savedInstanceState);
                        if (self.mResumed) {
                            // We resumed before the service became available, and so
                            // did not call onResumeWithService() then - call it now.
                            Log.d(TAG, "(delayed)onResumeWithService -> " + self.getClass().getSimpleName());
                            self.mService.incRef();
                            onResumeWithService();
                        }
                    }
                });
            }
        });

        // use fixed orientation based on device type (smartphone or tablet)
        if (getResources().getBoolean(R.bool.portrait)){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    @Override
    final public void onPause() {
        Log.d(TAG, getLogMessage("onPause"));
        super.onPause();
        mResumed = false;
        if (mService != null) {
            mService.decRef();
            onPauseWithService();
        }
    }

    @Override
    final public void onResume() {
        Log.d(TAG, getLogMessage("onResume"));
        super.onResume();
        mResumed = true;
        if (mService != null) {
            mService.incRef();
            onResumeWithService();
        }
    }

    private String getLogMessage(final String caller) {
        return caller + " -> " + getClass().getSimpleName() +
            (mService == null ? " (no attached service)" : "");
    }

    /** Override to provide the main view id */
    protected int getMainViewId() { return UI.INVALID_RESOURCE_ID; }

    /** Override to provide onCreate/onResume/onPause processing.
      * When called, our service is guaranteed to be available. */
    abstract protected void onCreateWithService(final Bundle savedInstanceState);
    protected void onPauseWithService() { }
    protected void onResumeWithService() { }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_qr_no_login:
            case R.id.action_qr:
                final int requestCode = item.getItemId() == R.id.action_qr_no_login ?
                        TabbedMainActivity.REQUEST_SEND_QR_SCAN_NO_LOGIN :
                        TabbedMainActivity.REQUEST_SEND_QR_SCAN;
                if (Build.VERSION.SDK_INT >= 23) {
                    GaIntentIntegrator.scanQRCode(GaActivity.this, requestCode);
                } else {
                    final Intent qrcodeScanner = new Intent(this, ScanActivity.class);
                    startActivityForResult(qrcodeScanner, requestCode);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Utility methods

    void finishOnUiThread() {
        runOnUiThread(new Runnable() {
            public void run() {
                GaActivity.this.finish();
            }
        });
    }

    protected void setMenuItemVisible(final Menu m, final int id, final boolean visible) {
        if (m == null)
            return;
        runOnUiThread(new Runnable() {
            public void run() {
                final MenuItem item = m.findItem(id);
                if (item != null)
                    item.setVisible(visible);
            }
        });
    }

    protected void hideKeyboardFrom(final View v) {
        final View toHideFrom = v == null ? getCurrentFocus() : v;
        if (toHideFrom != null) {
            final InputMethodManager imm;
            imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(toHideFrom.getWindowToken(), 0);
        }
    }

    public void toast(final Throwable t) { UI.toast(this, t, (Button) null); }
    public void toast(final int id) { UI.toast(this, id, Toast.LENGTH_LONG); }
    public void toast(final int id, final Button reenable) { UI.toast(this, getString(id), reenable); }
    public void toast(final int id, final CircularButton reenable) { UI.toast(this, getString(id), reenable); }
    public void toast(final String s) { UI.toast(this, s, Toast.LENGTH_LONG); }
    public void shortToast(final int id) { UI.toast(this, id, Toast.LENGTH_SHORT); }


    public Boolean isPublicKey(String pubkey) {
        return !(pubkey.length() < 26 || pubkey.length() > 35 || !pubkey.matches("^[0-9a-zA-Z]+$"));
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case TabbedMainActivity.REQUEST_SEND_QR_SCAN_NO_LOGIN:
            case TabbedMainActivity.REQUEST_SEND_QR_SCAN:
                if (data == null)
                    break;

                final String scanned;
                if (Build.VERSION.SDK_INT >= 23) {
                    final IntentResult result = GaIntentIntegrator.parseActivityResult(resultCode, data);
                    scanned = result.getContents();
                } else {
                    scanned = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                }
                if (scanned != null) {
                    if (scanned.length() >= 11
                            && (scanned.toLowerCase().startsWith("http://") || scanned.toLowerCase().startsWith("https://"))) {
                        String url = scanned;
                        // Lowercase the protocol
                        if (Pattern.compile("^HTTPS.*", Pattern.CASE_INSENSITIVE).matcher(scanned).matches()) {
                            url = "https" + scanned.substring(5);
                        } else if (Pattern.compile("^HTTP.*", Pattern.CASE_INSENSITIVE).matcher(scanned).matches()) {
                            url = "http" + scanned.substring(4);
                        }
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        } catch (Exception e) {
                            new QrcodeScanDialog(this, scanned).show();
                        }
                    } else {
                        if (requestCode == TabbedMainActivity.REQUEST_SEND_QR_SCAN_NO_LOGIN) {
                            if (scanned.toLowerCase().startsWith("begin:vcard")) {
                                try {
                                    UI.shareVcard(this, scanned);
                                } catch (Exception e) {
                                    new QrcodeScanDialog(this, scanned).show();
                                }
                            } else {
                                new QrcodeScanDialog(this, scanned).show();
                            }
                        } else {
                            final String intentData;

                            // manage bitid
                            if (scanned.startsWith("bitid://")) {
                                intentData = scanned;
                            } else {
                                String text = scanned;

                                // clean bitcoin address
                                if (scanned.startsWith("bitcoin:")) {
                                    text = scanned.replaceFirst("^bitcoin:", "").replaceFirst("\\?.*", "");
                                }

                                // if is not bitcoin address, show dialog with text to share
                                // if text == "" -> bip72b url bitcoin:?r=.+
                                if (!isPublicKey(text) && !text.equals("")) {
                                    if (text.toLowerCase().startsWith("begin:vcard")) {
                                        UI.shareVcard(this, text);
                                    } else {
                                        new QrcodeScanDialog(this, scanned).show();
                                    }
                                    return;
                                }

                                String pubKey = scanned;
                                if (!(scanned.length() >= 8 && scanned.substring(0, 8).equalsIgnoreCase("bitcoin:"))) {
                                    pubKey = String.format("bitcoin:%s", scanned);
                                }
                                intentData = pubKey;
                            }

                            final Intent browsable = new Intent(this, TabbedMainActivity.class);
                            browsable.setData(Uri.parse(intentData));
                            browsable.addCategory(Intent.CATEGORY_BROWSABLE);
                            browsable.putExtra("internal_qr", true);
                            // start new activity and finish old one
                            startActivity(browsable);
                            this.finish();
                        }
                    }
                }
                break;
        }
    }

    protected boolean isPermissionGranted(final int[] granted, final int msgId) {
        if (granted == null || granted.length == 0 || granted[0] != PackageManager.PERMISSION_GRANTED) {
            shortToast(msgId);
            return false;
        }
        return true;
    }

    protected void exitApp() {
        if (mService != null)
            mService.disconnect(false);

        // FIXME: Should pass flag to activity so it shows it was forced logged out
        Intent intent = new Intent(GaActivity.this, FirstScreenActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finishOnUiThread();
    }
 }
