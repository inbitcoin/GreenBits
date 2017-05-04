/**
 * Copyright 2014 Skubit
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.greenaddress.bitid;

import android.net.Uri;
import android.text.TextUtils;

import java.net.URI;
import java.net.URISyntaxException;

public class BitID {

    private Uri mUri;
    private boolean mIsSecured;
    private String mNonce;
    private String mRawUri;

    public static BitID parse(String bitID) throws URISyntaxException {
        if (TextUtils.isEmpty(bitID)) {
            throw new IllegalArgumentException("bitID is null");
        }

        BitID bitId = new BitID();
        bitId.mRawUri = bitID;
        bitId.mUri = Uri.parse(bitID);
        checkIfValid(bitId.mUri);

        bitId.mNonce = bitId.mUri.getQueryParameter("x");
        String uValue = bitId.mUri.getQueryParameter("u");
        bitId.mIsSecured = TextUtils.isEmpty(uValue) || uValue.equals("0");

        return bitId;
    }

    private static void checkIfValid(Uri uri) throws URISyntaxException {
        if (!"bitid".equals(uri.getScheme())) {
            throw new URISyntaxException(uri.toString(), "Invalid scheme");
        }
        if (TextUtils.isEmpty(uri.getQueryParameter("x"))) {
            throw new URISyntaxException(uri.toString(), "Missing x parameter");
        }

        String uValue = uri.getQueryParameter("u");
        if (!TextUtils.isEmpty(uValue) && (!uValue.equals("0")) && !uValue.equals("1")) {
            throw new URISyntaxException(uValue, "Illegal value for u param");
        }
    }

    public boolean isSecured() {
        return mIsSecured;
    }

    public String getNonce() {
        return mNonce;
    }

    public Uri getUri() {
        return mUri;
    }

    String getRawUri() {
        return mRawUri;
    }

    public URI toCallbackURI() throws URISyntaxException {
        return new URI(mIsSecured ? "https" : "http", null, mUri.getHost(), mUri.getPort(),
                mUri.getPath(), null, null);
    }

    @Override
    public String toString() {
        String url = "";
        try {
            url = toCallbackURI().toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return "BitID{" +
                "mRawUri='" + mRawUri + '\'' +
                ", callbackUri=" + url +
                '}';
    }
}
