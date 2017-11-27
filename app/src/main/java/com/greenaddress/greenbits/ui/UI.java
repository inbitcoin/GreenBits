package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.content.res.Resources;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.text.Editable;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.dd.CircularProgressButton;
import com.google.common.collect.Lists;
import com.greenaddress.greenbits.GaService;

import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.uri.BitcoinURIParseException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.MonetaryFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class UI {
    public static final int INVALID_RESOURCE_ID = 0;
    public static final ArrayList<String> UNITS = Lists.newArrayList("BTC", "mBTC", "\u00B5BTC", "bits");
    public enum FEE_TARGET {
        HIGH(3),
        NORMAL(6),
        LOW(12),
        ECONOMY(24),
        CUSTOM(-1),
        INSTANT(-2);
        private final int mBlock;
        FEE_TARGET(int block) { mBlock = block; }
        public int getBlock() { return mBlock; }
    }
    public static final FEE_TARGET[] FEE_TARGET_VALUES = FEE_TARGET.values();

    private static final String MICRO_BTC = "\u00B5BTC";
    private static final MonetaryFormat BTC = new MonetaryFormat().shift(0).minDecimals(8).noCode();
    private static final MonetaryFormat MBTC = new MonetaryFormat().shift(3).minDecimals(5).noCode();
    private static final MonetaryFormat UBTC = new MonetaryFormat().shift(6).minDecimals(2).noCode();
    private static final DecimalFormat mDecimalFmt = new DecimalFormat("#,###.########", DecimalFormatSymbols.getInstance(Locale.US));

    // Class to unify cancel and dismiss handling */
    private static class DialogCloseHandler implements DialogInterface.OnCancelListener,
                                                       DialogInterface.OnDismissListener {
        private final Runnable mCallback;
        private final boolean mCancelOnly;

        public DialogCloseHandler(final Runnable callback, final boolean cancelOnly) {
            mCallback = callback;
            mCancelOnly = cancelOnly;
        }
        @Override
        public void onCancel(final DialogInterface d) { mCallback.run(); }
        @Override
        public void onDismiss(final DialogInterface d) { if (!mCancelOnly) mCallback.run(); }
    }

    public static void setDialogCloseHandler(final Dialog d, final Runnable callback, final boolean cancelOnly) {
        final DialogCloseHandler handler = new DialogCloseHandler(callback, cancelOnly);
        d.setOnCancelListener(handler);
        d.setOnDismissListener(handler);
    }

    public static void setDialogCloseHandler(final Dialog d, final Runnable callback) {
        setDialogCloseHandler(d, callback, false);
    }

    public static MaterialDialog dismiss(final Activity a, final Dialog d) {
        if (d != null)
            if (a == null)
                d.dismiss();
            else
                a.runOnUiThread(new Runnable() { public void run() { d.dismiss(); } });
        return null;
    }

    private static boolean isEnterKeyDown(final KeyEvent e) {
        return e != null && e.getAction() == KeyEvent.ACTION_DOWN &&
               e.getKeyCode() == KeyEvent.KEYCODE_ENTER;
    }

    public static TextView.OnEditorActionListener getListenerRunOnEnter(final Runnable r) {
        return new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    isEnterKeyDown(event)) {
                    if (event == null || !event.isShiftPressed()) {
                        r.run(); // The user is done typing.
                        return true; // Consume.
                    }
                }
                return false; // Pass on to other listeners.
            }
        };
    }

    public static MaterialDialog.Builder popup(final Activity a, final String title, final int pos, final int neg) {
        final MaterialDialog.Builder b;
        b = new MaterialDialog.Builder(a)
                .title(title)
                .theme(Theme.LIGHT)
                .positiveColorRes(R.color.accent)
                .negativeColorRes(R.color.accent);
        if (pos != INVALID_RESOURCE_ID)
            b.positiveText(pos);
        if (neg != INVALID_RESOURCE_ID)
            return b.negativeText(neg);
        return b;
    }

    public static MaterialDialog.Builder popup(final Activity a, final int title, final int pos, final int neg) {
        return popup(a, a.getString(title), pos, neg);
    }

    public static MaterialDialog.Builder popup(final Activity a, final int title, final int pos) {
        return popup(a, title, pos, INVALID_RESOURCE_ID);
    }

    public static MaterialDialog.Builder popup(final Activity a, final String title) {
        return popup(a, title, android.R.string.ok, android.R.string.cancel);
    }

    public static MaterialDialog.Builder popup(final Activity a, final int title) {
        return popup(a, title, android.R.string.ok, android.R.string.cancel);
    }

    public static Map<String, String> getTwoFactorLookup(final Resources res) {
        final List<String> localized = Arrays.asList(res.getStringArray(R.array.twoFactorChoices));
        final List<String> methods = Arrays.asList(res.getStringArray(R.array.twoFactorMethods));
        final Map<String, String> map = new HashMap<>();
        for (int i = 0; i < localized.size(); i++)
            map.put(methods.get(i), localized.get(i));
        return map;
    }

    public static MaterialDialog popupTwoFactorChoice(final Activity a, final GaService service,
                                                      final boolean skip, final CB.Runnable1T<String> callback) {
        final List<String> methods = skip ? null : service.getEnabledTwoFactorMethods();

        if (skip || methods.size() <= 1) {
            // Caller elected to skip, or no choices are available: don't prompt
            a.runOnUiThread(new Runnable() {
                public void run() {
                    callback.run((skip || methods.isEmpty()) ? null : methods.get(0));
                }
            });
            return null;
        }

        // Return a pop up dialog to let the user choose.
        final Map<String, String> localizedMap = getTwoFactorLookup(a.getResources());
        final String[] localizedMethods = new String[methods.size()];
        for (int i = 0; i < methods.size(); i++)
            localizedMethods[i] = localizedMap.get(methods.get(i));

        return popup(a, R.string.twoFactorChoicesTitle, R.string.choose, R.string.cancel)
                .items(localizedMethods)
                .itemsCallbackSingleChoice(0, new MaterialDialog.ListCallbackSingleChoice() {
                    @Override
                    public boolean onSelection(final MaterialDialog dialog, final View v, final int which, final CharSequence text) {
                        callback.run(methods.get(which));
                        return true;
                    }
                }).build();
    }

    public static MaterialDialog popupWait(final Activity a, final int title) {
        final int id = INVALID_RESOURCE_ID;
        final MaterialDialog dialog = popup(a, title, id).progress(true, 0).build();
        dialog.show();
        return dialog;
    }

    public static MaterialDialog.Builder popupWaitCustom(final Activity a, final int title) {
        final int id = INVALID_RESOURCE_ID;
        return popup(a, title, id).progress(true, 0);
    }

    public static void toast(final Activity activity, final int id, final int len) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(activity, id, len).show();
            }
        });
    }

    public static void toast(final Activity activity, final String s, final int len) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(activity, s, len).show();
            }
        });
    }

    public static void toast(final Activity activity, final Throwable t, final Button reenable) {
        t.printStackTrace();
        toast(activity, t.getMessage(), reenable);
    }

    public static void toast(final Activity activity, final Throwable t, final CircularProgressButton reenable) {
        t.printStackTrace();
        toast(activity, t.getMessage(), reenable);
    }

    public static void toast(final Activity activity, final String msg, final CircularProgressButton reenable) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                if (reenable != null)
                    reenable.setProgress(0);
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void toast(final Activity activity, final String msg, final Button reenable) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                if (reenable != null)
                    reenable.setEnabled(true);
                Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    // Dummy TextWatcher for simple overrides
    public static class TextWatcher implements android.text.TextWatcher {
        public TextWatcher() { super(); }
        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) { }
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) { }
        @Override
        public void afterTextChanged(final Editable s) { }
    }

    public static View mapClick(final Activity activity, final int id, final View.OnClickListener fn) {
        final View v = find(activity, id);
        v.setOnClickListener(fn);
        return v;
    }

    public static View mapClick(final Activity activity, final int id, final Intent activityIntent) {
        return mapClick(activity, id, new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                activity.startActivity(activityIntent);
            }
        });
    }

    public static void unmapClick(final View v) {
        if (v != null)
            v.setOnClickListener(null);
    }

    public static void mapEnterToPositive(final Dialog dialog, final int editId) {
        final TextView edit = UI.find(dialog, editId);
        edit.setOnEditorActionListener(getListenerRunOnEnter(new Runnable() {
            public void run() {
                final MaterialDialog md = (MaterialDialog) dialog;
                md.onClick(md.getActionButton(DialogAction.POSITIVE));
            }
        }));
    }

    // Show/Hide controls
    public static void showIf(final boolean condition, final View... views) {
        for (final View v: views)
            if (v != null)
                v.setVisibility(condition ? View.VISIBLE : View.GONE);
    }

    public static void show(final View... views) { showIf(true, views); }

    public static void hideIf(final boolean condition, final View... views) {
        showIf(!condition, views);
    }

    public static void hide(final View... views) { showIf(false, views); }

    // Enable/Disable controls
    public static void enableIf(final boolean condition, final View... views) {
        for (final View v: views)
            v.setEnabled(condition);
    }

    public static void enable(final View... views) { enableIf(true, views); }

    public static void disableIf(final boolean condition, final View... views) {
        enableIf(!condition, views);
    }

    public static void disable(final View... views) { enableIf(false, views); }

    public static String getText(final TextView text) {
        return text.getText().toString();
    }

    public static void clear(final TextView... views) {
        for (final TextView v: views)
            v.setText(R.string.empty);
    }

    public static <T extends View> T find(final Activity activity, final int id) {
        return (T) activity.findViewById(id);
    }

    public static <T extends View> T find(final View v, final int id) {
        return (T) v.findViewById(id);
    }

    public static <T extends View> T find(final Dialog dialog, final int id) {
        return (T) dialog.findViewById(id);
    }

    public static LinearLayout.LayoutParams getScreenLayout(final Activity activity,
                                                            final double scale) {
        final DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        final int min = (int) (Math.min(dm.heightPixels, dm.widthPixels) * scale);
        return new LinearLayout.LayoutParams(min, min);
    }

    public static void showDialog(final Dialog dialog) {
        showDialog(dialog, false);
    }

    /**
     * Show dialog
     * @param dialog the dialog to show
     * @param forceFocus force request focus
     */
    public static void showDialog(final Dialog dialog, Boolean forceFocus) {
        if (forceFocus) {
            dialog.getWindow().clearFlags(LayoutParams.FLAG_NOT_FOCUSABLE |
                    LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            dialog.getWindow().setSoftInputMode(LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    private static int getUnitSymbol(final GaService service) {
        final String unit = service.getBitcoinUnit();
        if (MonetaryFormat.CODE_BTC.equals(unit))
            return R.string.fa_btc_space;
        if (MonetaryFormat.CODE_MBTC.equals(unit))
            return R.string.fa_mbtc_space;
        if (MICRO_BTC.equals(unit))
            return R.string.fa_ubtc_space;
        return R.string.fa_bits_space;
    }

    private static MonetaryFormat getUnitFormat(final GaService service) {
        final String unit = service.getBitcoinUnit();
        if (MonetaryFormat.CODE_BTC.equals(unit))
            return BTC;
        if (MonetaryFormat.CODE_MBTC.equals(unit))
            return MBTC;
        return UBTC;
    }

    public static String formatCoinValue(final GaService service, final Coin value) {
        return getUnitFormat(service).format(value).toString();
    }

    public static Coin parseCoinValue(final GaService service, final String value) {
        return getUnitFormat(service).parse(value);
    }

    public static String setCoinText(final GaService service, final FontAwesomeTextView symbol,
                                     final TextView amount, final Coin value) {
        if (symbol != null) {
            symbol.setAwesomeTypeface();
            if (GaService.IS_ELEMENTS)
                symbol.setText(service.getAssetSymbol());
            else
                symbol.setText(getUnitSymbol(service));
        }
        if (value == null)
            return null;
        final String formatted = formatCoinValue(service, value);
        if (amount != null)
            amount.setText(formatted);
        return formatted;
    }

    public static String setCoinText(final GaService service, final View v,
                                     final int symbolId, final int amountId,
                                     final Coin value) {
        return setCoinText(service, (FontAwesomeTextView) find(v, symbolId),
                           (TextView) find(v, amountId), value);
    }

    public static String setCoinText(final GaActivity activity,
                                     final int symbolId, final int amountId,
                                     final Coin value) {
        return setCoinText(activity.mService, (FontAwesomeTextView) find(activity, symbolId),
                           (TextView) find(activity, amountId), value);
    }

    public static void setAmountText(final TextView v, final String value) {
        try {
            v.setText(mDecimalFmt.format(Double.valueOf(value)));
        } catch (final NumberFormatException e) {
            v.setText(value);
        }
    }

    public static void shareImageWithText(Activity activity, Bitmap image, String text) {
        final File file = new File(activity.getCacheDir(), "imagetoshare.png");
        final FileOutputStream fOut;
        final String textToShare = String.format("%s \n\n%s %s", text,
                activity.getResources().getString(R.string.sharedVia),
                activity.getResources().getString(R.string.app_name));
        try {
            fOut = new FileOutputStream(file);
            image.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();
            file.setReadable(true, false);

            Uri fileUri = FileProvider.getUriForFile(activity,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file);

            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.putExtra(Intent.EXTRA_TEXT, textToShare);
            intent.setType("image/*");
            activity.startActivity(intent);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void shareVcard(Activity activity, String text) {
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);

        final File file = new File(activity.getCacheDir(), "contact");
        final FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(file);
            fOut.write(text.getBytes());
            fOut.flush();
            fOut.close();
            file.setReadable(true, false);

            Uri fileUri = FileProvider.getUriForFile(activity,
                    BuildConfig.APPLICATION_ID + ".provider",
                    file);
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(fileUri, "text/x-vcard");
            activity.startActivity(intent);
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    public static void shareQrcodeAddress(Activity activity, Bitmap bitmap, String text) {
        final String sharedViaShare = String.format("%s %s",
                activity.getResources().getString(R.string.sharedVia),
                activity.getResources().getString(R.string.app_name));

        String address = "", amount = "0", label = sharedViaShare;

        if (text.startsWith("bitcoin:")) {
            try {
                BitcoinURI bitcoinURI = new BitcoinURI(text);
                if (bitcoinURI.getAddress() != null)
                    address = bitcoinURI.getAddress().toString();
                if (bitcoinURI.getAmount() != null)
                    amount = bitcoinURI.getAmount().toPlainString();
                if (bitcoinURI.getLabel() != null)
                    label = bitcoinURI.getLabel();
            } catch (BitcoinURIParseException e) {
                e.printStackTrace();
            }
            if (address.isEmpty()) {
                Toast.makeText(activity, R.string.invalidAddress, Toast.LENGTH_LONG).show();
                return;
            }
            if (amount.isEmpty())
                amount = "0";
            if (label.isEmpty())
                label = sharedViaShare;
        } else {
            address = text;
        }

        try {
            label = URLEncoder.encode(label, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        final String url = String.format("http://inbitcoin.it/altana/%s/%s/%s", address, amount, label);
        shareImageWithText(activity, bitmap, url);
    }

    /**
     * Get current screen orientation
     * @param activity the activity to use to get information
     * @return int with orientation value
     */
    public static int getCurrentScreenOrientation(Activity activity) {
        final int rotation = ((WindowManager) activity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();

        final int orientation = activity.getResources().getConfiguration().orientation;
        switch (orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_270)
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                else
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case Configuration.ORIENTATION_LANDSCAPE:
                if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_90)
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                else
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            default:
                return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        }
    }
}
