package com.greenaddress.greenbits.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.common.collect.ImmutableMap;

import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class SetEmailActivity extends GaActivity {

    private static final String TAG = SetEmailActivity.class.getSimpleName();
    private Map<String, String> mLocalizedMap; // 2FA method to localized description
    private String mNewEmailAddress;
    private List<String> mEnabledMethods;
    private int mNumSteps;

    private Button mContinueButton;
    private TextView mPromptText;
    private ProgressBar mProgressBar;
    private EditText mCodeText;

    private void setView(final int id) {
        setContentView(id);
        mContinueButton = UI.find(this, R.id.continueButton);
        mPromptText = UI.find(this, R.id.prompt);
        mProgressBar = UI.find(this, R.id.progressBar);
        mCodeText = UI.find(this, R.id.code);
    }

    private String getTypeString(final String fmt, final String type) {
        return new Formatter().format(fmt, type).toString();
    }

    @Override
    protected void onCreateWithService(final Bundle savedInstanceState) {

        if (mService.getTwoFactorConfig() == null) {
            finish();
            return;
        }

        mLocalizedMap = UI.getTwoFactorLookup(getResources());
        mEnabledMethods = mService.getEnabledTwoFactorMethods();
        switch (mEnabledMethods.size()) {
            case 0:
                mNumSteps = 2;
                break;
            case 1:
                mNumSteps = 3;
                break;
            default:
                mNumSteps = 4;
        }
        Log.d(TAG, "numSteps: " + mNumSteps);
        showProvideEmailAddress();
    }

    /* Check 2FA configuration
     *  no 2fa -> set email
     *  1 -> get 2fa code
     *  >1 -> choose 2fa
     * */
    private void showGetAuthConfig() {

        final int resId = R.string.emailAddress;
        final String type = getResources().getString(resId);
        mPromptText.setText(getTypeString(UI.getText(mPromptText), type));

        Log.d(TAG, "enabledMethods = " + mEnabledMethods);
        Log.d(TAG, "Updated user config = " + mService.getUserConfig("two_factor"));
        switch (mEnabledMethods.size()) {
            case 0:
                Log.d(TAG, "# enabled 2FA methods: 0");
                // no 2FA enabled - go straight to email input
                setEmail(null, null, 2);
                break;
            case 1:
                Log.d(TAG, "# enabled 2FA methods: 1");
                // just one 2FA enabled - go straight to code verification
                showProvideAuthCode(2, mEnabledMethods.get(0));
                break;
            default:
                Log.d(TAG, "# enabled 2FA methods: " + mEnabledMethods.size());
                // Multiple 2FA options enabled - Allow the user to choose
                setView(R.layout.activity_two_factor_1_choose);
                mProgressBar.setProgress(2);
                mProgressBar.setMax(mNumSteps);
                Log.d(TAG, "stepNum: 2 of " + mProgressBar.getMax());

                final RadioGroup group = UI.find(this, R.id.radioGroup);
                group.removeViews(0, group.getChildCount());

                for (int i = 0; i < mEnabledMethods.size(); ++i) {
                    final RadioButton b = new RadioButton(SetEmailActivity.this);
                    b.setText(mLocalizedMap.get(mEnabledMethods.get(i)));
                    b.setId(i);
                    group.addView(b);
                }

                group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(final RadioGroup group, final int checkedId) {
                        mContinueButton.setEnabled(true);
                    }
                });

                mContinueButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(final View v) {
                        final int checked = group.getCheckedRadioButtonId();
                        showProvideAuthCode(3, mEnabledMethods.get(checked));
                    }
                });
        }
    }

    private Map<String, String> make2FAData(final String method, final String code) {
        if (code == null)
            return new HashMap<String, String>();
        return ImmutableMap.of("method", method, "code", code);
    }

    private void showProvideEmailAddress() {
        Log.d(TAG, "Start showProvideEmailAddress");
        setView(R.layout.activity_two_factor_3_provide_details);

        final int resId = R.string.emailAddress;
        final String type = getResources().getString(resId);
        mPromptText.setText(getTypeString(UI.getText(mPromptText), type));
        mProgressBar.setProgress(1);
        mProgressBar.setMax(mNumSteps);
        Log.d(TAG, "stepNum: 1 of " + mProgressBar.getMax());

        final TextView detailsText = UI.find(this, R.id.details);
        mContinueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                mNewEmailAddress = UI.getText(detailsText).trim();
                if (mNewEmailAddress.isEmpty())
                    return;
                UI.disable(mContinueButton);

                showGetAuthConfig();
            }
        });
    }

    // provide 2fa auth code
    private void showProvideAuthCode(final int stepNum, final String method) {
        // method: 2fa used to set email
        Log.d(TAG, "Start showProvideAuthCode");

        final String localizedName = mLocalizedMap.get(method);
        if (!method.equals("gauth")) {
            mService.requestTwoFacCode(method, "set_email",
                    ImmutableMap.of("address", mNewEmailAddress));
        }

        setView(R.layout.activity_two_factor_2_4_provide_code);
        mProgressBar.setProgress(stepNum);
        mProgressBar.setMax(mNumSteps);
        Log.d(TAG, "stepNum: " + stepNum + " of " + mProgressBar.getMax());

        final TextView descriptionText = UI.find(this, R.id.description);
        descriptionText.setText(R.string.twoFacProvideAuthCodeDescription);
        mPromptText.setText(getTypeString(UI.getText(mPromptText), localizedName));

        mContinueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                mContinueButton.setEnabled(false);
                setEmail(method, UI.getText(mCodeText).trim(), stepNum + 1);

            }
        });
    }

    private void setEmail(final String method, final String code, final int stepNum) {

        CB.after(mService.setEmail(mNewEmailAddress, make2FAData(method, code)),
                new CB.Toast<Object>(SetEmailActivity.this, mContinueButton) {
                    @Override
                    public void onSuccess(Object result) {
                        setResult(RESULT_OK);

                        if (!isNlocktimeConfig(true)) {
                            // {"email_incoming":true,"email_outgoing":true}
                            final Map<String, Boolean> inner = ImmutableMap.of(
                                    "email_incoming", true,
                                    "email_outgoing", true);
                            final Map<String, Object> outer = ImmutableMap.of(
                                    "notifications_settings", (Object) inner);
                            mService.setUserConfig(outer, false);
                        }
                        // Confirm email
                        runOnUiThread(new Runnable() {
                            public void run() {
                                showProvideConfirmationCode(stepNum);
                            }
                        });
                    }
                });
    }

    private void showProvideConfirmationCode(final int stepNum) {
        Log.d(TAG, "Start showProvideConfirmationCode");

        setView(R.layout.activity_two_factor_2_4_provide_code);
        mPromptText.setText(getTypeString(UI.getText(mPromptText), mLocalizedMap.get("email")));
        mProgressBar.setProgress(stepNum);
        mProgressBar.setMax(mNumSteps);
        Log.d(TAG, "stepNum: " + stepNum + " of " + mProgressBar.getMax());

        mContinueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final String enteredCode = UI.getText(mCodeText).trim();
                if (enteredCode.length() != 6)
                    return;
                // Print update 2fa config
                Log.d(TAG, "2fa config: " + mService.getTwoFactorConfig());
                mContinueButton.setEnabled(false);
                CB.after(mService.activateEmail(enteredCode),
                        new CB.Toast<Boolean>(SetEmailActivity.this, mContinueButton) {
                            @Override
                            public void onSuccess(Boolean result) {
                                setResult(RESULT_OK);
                                // Update 2FA setting in whole app
                                mService.getAvailableTwoFactorMethods();
                                finishOnUiThread();
                            }
                        });
            }
        });
    }

    final boolean isNlocktimeConfig(final Boolean enabled) {
        Boolean b = false;
        final Map<String, Object> outer;
        outer = (Map) mService.getUserConfig("notifications_settings");
        if (outer != null)
            b = Boolean.TRUE.equals(outer.get("email_incoming")) &&
                    Boolean.TRUE.equals(outer.get("email_outgoing"));
        return b.equals(enabled);
    }
}
