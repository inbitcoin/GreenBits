package com.greenaddress.greenbits.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.WindowManager;

import com.google.zxing.integration.android.IntentResult;

import de.schildbach.wallet.ui.ScanActivity;

import static com.greenaddress.greenbits.ui.TabbedMainActivity.REQUEST_CLEAR_ACTIVITY;

/**
 * Created by Antonio Parrella on 11/24/16.
 * by inbitcoin
 */

public class VendorActivity extends GaActivity {

    private VendorFragmentMain vendorFragmentMain;

    @Override
    protected void onCreateWithService(Bundle savedInstanceState) {
        if (!mService.isLoggedOrLoggingIn()) {
            // Not logged in, force the user to login
            mService.disconnect(false);
            final Intent login = new Intent(this, RequestLoginActivity.class);
            startActivityForResult(login, REQUEST_CLEAR_ACTIVITY);
            return;
        }
        setContentView(R.layout.activity_vendor);

        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // force hide keyboard
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // setup the main fragment
        if (savedInstanceState == null) {
            vendorFragmentMain = new VendorFragmentMain();
            final FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.fragment_container, vendorFragmentMain);
            fragmentTransaction.addToBackStack("main");
            fragmentTransaction.commit();
        }

        // enable vendor activity by default on app start
        mService.cfgEdit("is_vendor_mode").putBoolean("enabled", true).apply();
        mService.cfgEdit("vendor_message").putInt("count", SendFragment.VENDOR_MESSAGE_MAX).apply();
    }


    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case TabbedMainActivity.REQUEST_SEND_QR_SCAN_VENDOR:
                if (data == null)
                    break;

                final String scanned;
                if (Build.VERSION.SDK_INT >= 23) {
                    final IntentResult result = GaIntentIntegrator.parseActivityResult(resultCode, data);
                    scanned = result.getContents();
                } else {
                    scanned = data.getStringExtra(ScanActivity.INTENT_EXTRA_RESULT);
                }
                vendorFragmentMain.scanResult(scanned);
                break;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        // Respond to the action bar's Up/Home button
        case android.R.id.home:
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        int count = getSupportFragmentManager().getBackStackEntryCount();
        if (count == 1) {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.slide_to_right);
            // disable vendor activity by default on app start
            mService.cfgEdit("is_vendor_mode").putBoolean("enabled", false).apply();
        } else {
            getSupportFragmentManager().popBackStack();
        }
    }

    @Override
    public void onResumeWithService() {
        if (mService.isForcedOff()) {
            // FIXME: Should pass flag to activity so it shows it was forced logged out
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
            return;
        }
    }
}
