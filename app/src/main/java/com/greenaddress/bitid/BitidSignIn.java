/**
 * Copyright 2014 Skubit
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.greenaddress.bitid;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import com.greenaddress.greenapi.ISigningWallet;
import com.greenaddress.greenapi.Network;
import com.greenaddress.greenbits.GaService;
import com.greenaddress.greenbits.ui.BuildConfig;
import com.greenaddress.greenbits.ui.R;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class BitidSignIn extends AsyncTask<Object, Object, SignInResponse> {

    private static final String TAG = BitidSignIn.class.getSimpleName();

    private Activity mActivity;
    private BitID mBitID;
    private ISigningWallet mSigningWallet;
    private HttpURLConnection mConnection;
    private OnPostSignInListener mOnPostSignInListener;
    private GaService mService;

    public interface OnPostSignInListener {
        void postExecuteSignIn(SignInResponse response);
    }

    @Override
    protected SignInResponse doInBackground(Object... objects) {
        mActivity = (Activity) objects[0];
        mBitID = (BitID) objects[1];
        mSigningWallet = (ISigningWallet) objects[2];
        mOnPostSignInListener = (OnPostSignInListener) objects[3];
        mService = (GaService) objects[4];
        try {
            return performRequest();
        } catch (IOException | JSONException | URISyntaxException | InvalidKeyException |
                NoSuchAlgorithmException e) {
            Log.d(TAG, "performRequest error: " + e.getMessage());
            return new SignInResponse(ResultCode.GENERIC_ERROR,
                    mActivity.getString(R.string.err_bitid_login_error));
        }
    }

    @Override
    protected void onPostExecute(final SignInResponse response) {
        if (mOnPostSignInListener != null)
            mOnPostSignInListener.postExecuteSignIn(response);
    }

    private static String asString(InputStream inputStream) throws IOException {
        try {
            Scanner scanner = new Scanner(inputStream, "UTF-8").useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        } finally {
            inputStream.close();
        }
    }

    private SignInResponse performRequest()
            throws IOException, URISyntaxException, JSONException, InvalidKeyException,
            NoSuchAlgorithmException {
        openConnection();
        writeRequest(buildRequest());
        return readResponse();
    }

    private void openConnection() throws IOException, URISyntaxException {
        final String userAgent = String.format("%s %s/%s", System.getProperty("http.agent"),
                mActivity.getResources().getString(R.string.app_name), BuildConfig.VERSION_NAME);

        if (mService.isProxyEnabled()) {
            Log.d(TAG, "proxy enabled, use BitID via proxy");
            final InetSocketAddress socketAddress = new InetSocketAddress(mService.getProxyHost(),
                    Integer.parseInt(mService.getProxyPort()));
            final Proxy proxy = new Proxy(Proxy.Type.SOCKS, socketAddress);
            mConnection = (HttpURLConnection) mBitID.toCallbackURI().toURL().openConnection(proxy);
        } else {
            mConnection = (HttpURLConnection) mBitID.toCallbackURI().toURL().openConnection();
        }
        mConnection.setRequestMethod("POST");
        mConnection.setRequestProperty("Content-Type", "application/json");
        mConnection.setRequestProperty("User-Agent", userAgent);
        mConnection.setDoInput(true);
        mConnection.setDoOutput(true);
        mConnection.setUseCaches(false);

        mConnection.connect();
    }

    private void writeRequest(String message) throws IOException {
        final DataOutputStream dos = new DataOutputStream(mConnection.getOutputStream());
        dos.write(message.getBytes());
        dos.close();
    }

    private SignInResponse readResponse() throws IOException {
        final int rc = mConnection.getResponseCode();
        if (rc == -1) {
            return new SignInResponse(ResultCode.NO_CONNECTION, null);
        }
        if (rc < 300 && rc >= 200) {
            return new SignInResponse(ResultCode.OK, asString(mConnection.getInputStream()));
        } else if (rc >= 400) {
            String message = asString(mConnection.getErrorStream());
            return new SignInResponse(rc, message);
        } else {
            return new SignInResponse(ResultCode.UNKNOWN_ERROR, null);
        }
    }

    private String signMessage() {
        final ECKey.ECDSASignature signature = mSigningWallet.signMessage(mBitID.getRawUri());
        return ISigningWallet.ECDSASignatureToBase64(signature, mBitID.getRawUri(), mSigningWallet.getPubKey());
    }

    private String buildBidRequest() throws JSONException, UnsupportedEncodingException {
        final NetworkParameters netParams;
        if (mService.getNetworkParameters().getId().equals(NetworkParameters.ID_MAINNET)) {
            netParams = MainNetParams.get();
        } else if (mService.getNetworkParameters().getId().equals(NetworkParameters.ID_REGTEST)) {
            netParams = RegTestParams.get();
        } else {
            netParams = TestNet3Params.get();
        }
        final String address = URLDecoder.decode(mSigningWallet.getPubKey().toAddress(netParams).toString(), "UTF-8");

        final JSONObject json = new JSONObject();
        json.put("address", address);
        json.put("signature", signMessage());
        json.put("uri", mBitID.getRawUri());
        return json.toString();
    }

    private String buildRequest()
            throws JSONException, UnsupportedEncodingException, NoSuchAlgorithmException,
            InvalidKeyException {
        return buildBidRequest();
    }
}
