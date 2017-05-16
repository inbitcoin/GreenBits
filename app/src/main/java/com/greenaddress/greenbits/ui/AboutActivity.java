package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import mehdi.sakout.aboutpage.AboutPage;
import mehdi.sakout.aboutpage.Element;

public class AboutActivity extends AppCompatActivity {

    final static private int TAP_TO_DEV = 10;
    private int mTapToDevCounter = 0;
    private boolean mIsTimerRunning = false;

    private Element getGitHubElement() {
        final Element gitHubElement = new Element();
        gitHubElement.setTitle(getString(mehdi.sakout.aboutpage.R.string.about_github));
        gitHubElement.setIconDrawable(mehdi.sakout.aboutpage.R.drawable.about_icon_github);
        gitHubElement.setIconTint(mehdi.sakout.aboutpage.R.color.about_github_color);
        final String release = "https://github.com/greenaddress/GreenBits/releases/tag/r%s";
        final Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse(String.format(release, BuildConfig.VERSION_NAME)));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        gitHubElement.setIntent(intent);
        return gitHubElement;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final AboutPage aboutPage = new AboutPage(this)
                .setDescription("powered by GreenAddress")
                .setImage(R.drawable.logo_big)
                .addEmail("info@inbitcoin.it")
                .addFacebook("inbitcoin")
                .addTwitter("inbitcoin")
                //.addYoutube("UCcTlQ46wcp-pmwAg_Rj1DHQ")
                .addWebsite("https://inbitcoin.it");
        final String pkgName = getPackageName();
        final String installer = getPackageManager().getInstallerPackageName(pkgName);
        if ("com.android.vending".equals(installer)) {
            aboutPage.addPlayStore(pkgName);
        }
        setContentView(aboutPage.create());
        setTitle();

        // manage developer flag
        final CountDownTimer countDownTimer = new CountDownTimer(500, 500) {
            @Override
            public void onTick(long l) {
            }

            @Override
            public void onFinish() {
                mIsTimerRunning = false;
            }
        };

        final Activity activity = this;
        final View image = findViewById (R.id.image);
        image.setClickable(true);
        image.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!mIsTimerRunning) {
                    mTapToDevCounter = 1;
                    mIsTimerRunning = true;
                    countDownTimer.start();
                } else {
                    mTapToDevCounter++;
                    if (mTapToDevCounter == TAP_TO_DEV) {
                        if (isDev()) {
                            setDev(false);
                            setTitle();
                            UI.toast(activity, "non sei più DEV", Toast.LENGTH_LONG);
                        } else {
                            setDev(true);
                            setTitle();
                            UI.toast(activity, "SEI DEV", Toast.LENGTH_LONG);
                        }
                        setTitle();
                    } else {
                        mIsTimerRunning = true;
                        countDownTimer.cancel();
                        countDownTimer.start();
                    }
                }
            }
        });
    }

    private void setDev(boolean status) {
        getSharedPreferences("dev_mode", MODE_PRIVATE).edit().putBoolean("enabled", status).apply();
    }

    private boolean isDev() {
        return getSharedPreferences("dev_mode", MODE_PRIVATE).getBoolean("enabled", false);
    }

    private void setTitle() {
        setTitle(String.format("%s%s %s",
                isDev() ? "[DEV]" : "",
                getString(R.string.app_name),
                getString(R.string.app_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.BUILD_TYPE)));
    }
}
