package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import static com.greenaddress.greenbits.ui.TabbedMainActivity.REQUEST_BITCOIN_URL_LOGIN;

/**
 * Created by Antonio Parrella on 12/19/16.
 * by inbitcoin
 */

public class VisiuWebview extends GaActivity {
    private static final String TAG = VisiuWebview.class.getSimpleName();
    private View progressbarLayout;
    private Activity mActivity;

    @Override
    protected void onCreateWithService(Bundle savedInstanceState) {
        if (!mService.isLoggedOrLoggingIn()) {
            // Not logged in, force the user to login
            mService.disconnect(false);
            final Intent login = new Intent(this, RequestLoginActivity.class);
            startActivityForResult(login, REQUEST_BITCOIN_URL_LOGIN);
            return;
        }
        setContentView(R.layout.activity_visiu_webview);

        mActivity = this;

        final Toolbar toolbar = UI.find(this, R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final Intent intent = getIntent();
        String publicAddress = intent.getStringExtra("public_address");
        if (publicAddress.isEmpty()) {
            finish();
            overridePendingTransition(R.anim.fade_in, R.anim.slide_to_right);
        }

        progressbarLayout = UI.find(this, R.id.progressbarLayout);

        final WebView visiuWebview = UI.find(this, R.id.visiu_webview);

        // enable remote debug on android >= 4.4
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE))
                WebView.setWebContentsDebuggingEnabled(true);
        }

        // log console errors
        visiuWebview.setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage cm) {
                if (0 != (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE)) {
                    Log.d(TAG, "[JS:" + cm.messageLevel().toString() + "] "
                            + cm.message() + " -- From line "
                            + cm.lineNumber() + " of "
                            + cm.sourceId());
                }
                if (cm.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    showErrorLayout();
                }
                return true;
            }
        });

        final String url = "https://inbitcoin.it/static/visiu/index.html#" + publicAddress;
        visiuWebview.getSettings().setJavaScriptEnabled(true);
        visiuWebview.setWebViewClient(new CustomWebViewClient());
        visiuWebview.loadUrl(url);
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
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_to_right);
    }

    private class CustomWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            UI.hide(progressbarLayout);
            UI.show(view);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("bitcoin:")) {
                final Intent result = new Intent();
                result.setData(Uri.parse(url));
                setResult(RESULT_OK, result);
                finish();
                overridePendingTransition(R.anim.fade_in, R.anim.slide_to_right);
            }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error)  {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                Log.d(TAG, "onReceivedError" + error.getErrorCode() + ": " + error.getDescription());
            else
                Log.d(TAG, "onReceivedError");
            showErrorLayout();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            Log.d(TAG, "onReceivedSslError - " + error.getUrl());
            showErrorLayout();
        }

        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                        WebResourceResponse errorResponse) {
            Log.d(TAG, "onReceivedHttpError - " + errorResponse.getStatusCode() + " url: " + request.getUrl());
            showErrorLayout();
        }
    }

    /**
     * hide all and show error layout
     */
    public void showErrorLayout() {
        Log.d(TAG, "show error layout");
        UI.hide(progressbarLayout);
        UI.hide((View) UI.find(mActivity, R.id.visiu_webview));
        UI.show((View) UI.find(mActivity, R.id.errorLayout));

    }
}
