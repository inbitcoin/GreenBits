package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.Intent;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;


public class GaIntentIntegrator extends IntentIntegrator {

    public GaIntentIntegrator(Activity activity) {
        super(activity);
    }

    //
    public final void initiateScan(final int requestCode) {
        startActivityForResult(createScanIntent(), requestCode);
    }

    /**
     * <p>Call this from your {@link Activity}'s
     * {@link Activity#onActivityResult(int, int, Intent)} method.</p>
     *
     * @param resultCode  result code from {@code onActivityResult()}
     * @param intent      {@link Intent} from {@code onActivityResult()}
     * @return null if the event handled here was not related to this class, or
     * else an {@link IntentResult} containing the result of the scan. If the user cancelled scanning,
     * the fields will be null.
     */
    public static IntentResult parseActivityResult(int resultCode, Intent intent) {
        return IntentIntegrator.parseActivityResult(REQUEST_CODE, resultCode, intent);
    }

    public static void scanQRCode(Activity activity, final int requestCode) {
        final GaIntentIntegrator integrator = (GaIntentIntegrator)
                new GaIntentIntegrator(activity)
                        .setOrientationLocked(true)
                        .setBeepEnabled(false)
                        .setPrompt(activity.getString(R.string.qr_core_reader_prompt));
        integrator.initiateScan(requestCode);
    }
}
