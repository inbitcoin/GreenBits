package com.greenaddress.greenbits.ui;

import android.annotation.TargetApi;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.security.keystore.KeyProperties;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.dd.CircularProgressButton;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.greenaddress.greenapi.GAException;
import com.greenaddress.greenapi.LoginData;
import com.greenaddress.greenapi.LoginFailed;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.KeyStoreAES;
import com.greenaddress.greenbits.ui.preferences.NetworkSettingsActivity;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Observable;
import java.util.Observer;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;


public class PinActivity extends LoginActivity implements Observer {

    private Menu mMenu;
    private static final String KEYSTORE_KEY = "NativeAndroidAuth";
    private static final int ACTIVITY_REQUEST_CODE = 1;
    private CircularProgressButton mPinLoginButton;
    private EditText mPinText;
    private TextView mPinError;
    private TextView mPinCountdown;
    private TextView mOneAttempLeft;
    private String nativePIN;
    private final static int WAIT_TIME_SEC_1 = 5;
    private final static int WAIT_TIME_SEC_2 = 20;
    private final static int MAX_ATTEMPTS = 3;
    private Boolean mErrorAuthScreen = false;
    private int AUTH_SCREEN_ATTEMPT = 0;

    private void login() {

        if (mPinLoginButton.getProgress() != 0)
            return;

        if (mPinText.length() < 4) {
            shortToast(R.string.pinErrorLength);
            return;
        }

        if (!mService.isConnected()) {
            toast(R.string.err_send_not_connected_will_resume);
            return;
        }

        final Long timestamp = System.currentTimeMillis()/1000;
        final Long lastFailTimestamp = mService.cfg("pin").getLong("last_fail_timestamp", 0L);
        if (lastFailTimestamp > 0 && (timestamp - lastFailTimestamp) < getWaitTimeSec()) {
            PinActivity.this.toast(getString(R.string.waitBeforeRetry));
            return;
        }

        // hide all menu entry during login
        setMenuItemVisible(mMenu, R.id.action_qr, false);
        setMenuItemVisible(mMenu, R.id.network_preferences, false);
        setMenuItemVisible(mMenu, R.id.watchonly_preference, false);

        mPinLoginButton.setProgress(50);
        mPinText.setEnabled(false);

        final InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mPinText.getWindowToken(), 0);

        setUpLogin(UI.getText(mPinText), new Runnable() {
             public void run() {
                 // restore all menu entry on login error
                 setMenuItemVisible(mMenu, R.id.action_qr, true);
                 setMenuItemVisible(mMenu, R.id.network_preferences, true);
                 setMenuItemVisible(mMenu, R.id.watchonly_preference, true);

                 mPinText.setText("");
                 mPinLoginButton.setProgress(0);
                 mPinText.setEnabled(true);
                 UI.show(mPinError);
                 final int counter = mService.cfg("pin").getInt("counter", 1);
                 mPinError.setText(getString(R.string.attemptsLeft, MAX_ATTEMPTS - counter));
                 if (counter == MAX_ATTEMPTS -1)
                     UI.show(mOneAttempLeft);
                 startCountdown(getWaitTimeSec());
              }
         });
     }

    private void setUpLogin(final String pin, final Runnable onFailureFn) {

        final AsyncFunction<Void, LoginData> connectToLogin = new AsyncFunction<Void, LoginData>() {
            @Override
            public ListenableFuture<LoginData> apply(final Void input) throws Exception {
                return mService.pinLogin(pin);
            }
        };

        final ListenableFuture<LoginData> loginFuture;
        loginFuture = Futures.transform(mService.onConnected, connectToLogin, mService.getExecutor());

        Futures.addCallback(loginFuture, new FutureCallback<LoginData>() {
            @Override
            public void onSuccess(final LoginData result) {
                mService.cfgEdit("pin").putInt("counter", 0).apply();
                if (getCallingActivity() == null) {
                    onLoginSuccess();
                    return;
                }
                setResult(RESULT_OK);
                finishOnUiThread();
            }

            @Override
            public void onFailure(final Throwable t) {
                final String message;
                final SharedPreferences prefs = mService.cfg("pin");
                final int counter = prefs.getInt("counter", 0) + 1;

                if (t instanceof GAException ||
                    Throwables.getRootCause(t) instanceof LoginFailed) {
                    final SharedPreferences.Editor editor = prefs.edit();
                    if (counter < MAX_ATTEMPTS) {
                        final Long timestamp = System.currentTimeMillis()/1000;
                        editor.putLong("last_fail_timestamp", timestamp);
                        editor.putInt("counter", counter);
                        message = getString(R.string.attemptsLeftLong, MAX_ATTEMPTS - counter);
                    } else {
                        message = getString(R.string.attemptsFinished);
                        editor.clear();
                    }
                    editor.apply();
                }
                else
                    message = t.getMessage();

                PinActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        PinActivity.this.toast(message);

                        if (counter >= MAX_ATTEMPTS) {
                            startActivity(new Intent(PinActivity.this, FirstScreenActivity.class));
                            finish();
                            return;
                        }
                        if (onFailureFn != null)
                            onFailureFn.run();
                    }
                });
            }
        }, mService.getExecutor());
    }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {

        final SharedPreferences prefs = mService.cfg("pin");
        final String ident = prefs.getString("ident", null);

        if (ident == null) {
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_pin);
        mPinLoginButton = UI.find(this, R.id.pinLoginButton);
        mPinLoginButton.setIndeterminateProgressMode(true);
        mPinLoginButton.setProgress(50);
        mPinText = UI.find(this, R.id.pinText);
        mPinError = UI.find(this, R.id.pinErrorText);
        mPinCountdown = UI.find(this, R.id.pinCountdownText);
        mOneAttempLeft = UI.find(this, R.id.oneAttemptLeft);

        nativePIN = prefs.getString("native", null);

        mPinText.setOnEditorActionListener(
                UI.getListenerRunOnEnter(new Runnable() {
                    public void run() {
                        login();
                    }
                }));

        mPinLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                login();
            }
        });

        if (!TextUtils.isEmpty(nativePIN)) {
            // force hide keyboard
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

            mPinText.setEnabled(false);
            mPinLoginButton.setProgress(50);
            tryDecrypt();
        }

        final Long timestamp = System.currentTimeMillis()/1000;
        final Long lastFailTimestamp = mService.cfg("pin").getLong("last_fail_timestamp", 0L);
        final Long timeLeft = timestamp - lastFailTimestamp;
        if (lastFailTimestamp > 0 && (timestamp - lastFailTimestamp) < getWaitTimeSec())
            startCountdown(getWaitTimeSec() - timeLeft.intValue());

        final int counter = mService.cfg("pin").getInt("counter", 1);
        if (counter > 0) {
            UI.show(mPinError);
            mPinError.setText(getString(R.string.attemptsLeft, MAX_ATTEMPTS - counter));
            if (counter == MAX_ATTEMPTS -1)
                UI.show(mOneAttempLeft);
        }

        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Cipher getAESCipher() throws NoSuchAlgorithmException, NoSuchPaddingException {
        final String name = KeyProperties.KEY_ALGORITHM_AES + "/" +
                            KeyProperties.BLOCK_MODE_CBC + "/" +
                            KeyProperties.ENCRYPTION_PADDING_PKCS7;
        return Cipher.getInstance(name);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void tryDecrypt() {

        if (mService.onConnected == null) {
            toast(R.string.unable_to_connect_to_service);
            finishOnUiThread();
            return;
        }

        final SharedPreferences prefs = mService.cfg("pin");
        final String nativePIN = prefs.getString("native", null);
        final String nativeIV = prefs.getString("nativeiv", null);
        final int nativeVersion = prefs.getInt("nativeVersion", 1);

        try {
            final KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            final SecretKey secretKey = (SecretKey) keyStore.getKey(KEYSTORE_KEY, null);
            final Cipher cipher = getAESCipher();
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(Base64.decode(nativeIV, Base64.NO_WRAP)));
            final byte[] decrypted = cipher.doFinal(Base64.decode(nativePIN, Base64.NO_WRAP));
            final String pin;
            if (nativeVersion < KeyStoreAES.SAVED_PIN_VERSION)
                pin = Base64.encodeToString(decrypted, Base64.NO_WRAP).substring(0, 15);
            else
                pin = new String(decrypted);

            Futures.addCallback(mService.onConnected, new FutureCallback<Void>() {
                @Override
                public void onSuccess(final Void result) {

                    if (mService.isConnected()) {
                        setUpLogin(pin, null);
                        return;
                    }

                    // try again
                    tryDecrypt();
                }

                @Override
                public void onFailure(final Throwable t) {
                    finishOnUiThread();
                }
            });
        } catch (final KeyStoreException | InvalidKeyException e) {
            if (AUTH_SCREEN_ATTEMPT < MAX_ATTEMPTS) {
                showAuthenticationScreen();
            } else {
                UI.toast(this, R.string.error_auth_screen, Toast.LENGTH_LONG);
                errorAuthenticationScreen();
            }
        } catch (final InvalidAlgorithmParameterException | BadPaddingException | IllegalBlockSizeException  |
                 CertificateException | UnrecoverableKeyException | IOException |
                 NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_REQUEST_CODE) {
            // Challenge completed, proceed with using cipher
            if (resultCode == RESULT_OK) {
                tryDecrypt();
            } else {
                errorAuthenticationScreen();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void showAuthenticationScreen() {
        AUTH_SCREEN_ATTEMPT++;
        final KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        final Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
        if (intent != null) {
            startActivityForResult(intent, ACTIVITY_REQUEST_CODE);
        } else {
            errorAuthenticationScreen();
        }
    }

    @Override
    public void onResumeWithService() {
        mService.addConnectionObserver(this);
        // if is already connected, show the login button
        if (TextUtils.isEmpty(nativePIN) && mPinLoginButton != null) {
            if (mService.isConnected())
                mPinLoginButton.setProgress(0);
            else
                mPinLoginButton.setProgress(50);
        }
        if (!mService.isConnected())
            setMenuItemVisible(mMenu, R.id.network_unavailable, true);
        super.onResumeWithService();
    }

    @Override
    public void onPauseWithService() {
        mService.deleteConnectionObserver(this);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.common_menu, menu);
        getMenuInflater().inflate(R.menu.camera_menu, menu);
        getMenuInflater().inflate(R.menu.preauth_menu, menu);
        mMenu = menu;
        final boolean connected = mService != null && mService.isConnected();
        setMenuItemVisible(mMenu, R.id.network_unavailable, !connected);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch(item.getItemId()) {
            case R.id.network_unavailable:
                return true;
            case R.id.network_preferences:
                startActivity(new Intent(this, NetworkSettingsActivity.class));
                return true;
            case R.id.watchonly_preference:
                startActivity(new Intent(PinActivity.this, WatchOnlyLoginActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void update(final Observable observable, final Object data) {
        final GaService.State state = (GaService.State) data;
        setMenuItemVisible(mMenu, R.id.network_unavailable,
                           !state.isConnected() && !state.isLoggedOrLoggingIn());
        if (TextUtils.isEmpty(nativePIN) || mErrorAuthScreen) {
            if (state.isConnected()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPinLoginButton.setProgress(0);
                    }
                });
            } else if (!state.isLoggedOrLoggingIn()){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mPinLoginButton.setProgress(50);
                    }
                });
            }
        }
    }

    private void startCountdown(final int sec) {
        UI.show(mPinCountdown);
        new CountDownTimer(sec * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                mPinCountdown.setText(getString(R.string.tryAgainInSeconds, millisUntilFinished / 1000));
            }
            public void onFinish() {
                mPinCountdown.setVisibility(View.INVISIBLE);
            }
        }.start();
    }

    private int getWaitTimeSec() {
        final int counter = mService.cfg("pin").getInt("counter", 1);
        if (counter == 1)
            return WAIT_TIME_SEC_1;
        else if (counter > 1)
            return WAIT_TIME_SEC_2;
        return 0;
    }

    private void errorAuthenticationScreen() {
        final SharedPreferences prefs = mService.cfg("pin");
        final int nativeVersion = prefs.getInt("nativeVersion", 1);
        if (nativeVersion < KeyStoreAES.SAVED_PIN_VERSION) {
            // The user canceled or didn’t complete the lock screen
            // operation. Go back to the initial login screen to allow
            // them to enter mnemonics.
            mService.setUserCancelledPINEntry(true);
            startActivity(new Intent(this, FirstScreenActivity.class));
            finish();
        } else {
            mErrorAuthScreen = true;
            mPinText.setEnabled(true);
            if (mService.isConnected())
                mPinLoginButton.setProgress(0);
        }
    }
}
