package com.greenaddress.greenbits;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;
import com.greenaddress.greenbits.ui.R;

import org.bitcoinj.core.NetworkParameters;

public class QrBitmap implements Parcelable {
    private static final int SCALE = 4;

    private final String mData;
    private final int mBackgroundColor;
    private Bitmap mQRCode;
    private Bitmap mLogo;
    private static int BORDER = 10;

    public QrBitmap(final String data, final int backgroundColor, Context context) {
        mData = data;
        mBackgroundColor = backgroundColor;
        final Bitmap logo;
        logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.logo_qrcode);
        mLogo = logo;
    }

    public String getData() {
        return mData;
    }

    public Bitmap getQRCode() {
        Bitmap image = getmQRCodeWithoutLogo();
        int height = image.getHeight();
        int width = image.getWidth();

        int SCALE_FACTOR = 5 ;
        int TOP_PADDING = 30 * SCALE_FACTOR;
        int LOGO_PERC_SCALING = 30;

        image = Bitmap.createScaledBitmap(image, width * SCALE_FACTOR, height * SCALE_FACTOR, false);
        Bitmap combined = Bitmap.createBitmap(image.getWidth(), image.getHeight(), image.getConfig());

        float widthLogo, heightLogo = 0;
        if (mLogo.getWidth() > mLogo.getHeight()) {
            widthLogo = (combined.getWidth() / 100) * LOGO_PERC_SCALING;
            heightLogo = (mLogo.getHeight() * widthLogo) / mLogo.getWidth();
        } else {
            heightLogo = (combined.getHeight() / 100) * LOGO_PERC_SCALING;
            widthLogo = (mLogo.getWidth() * heightLogo) / mLogo.getHeight();
        }

        Bitmap mLogo2 = Bitmap.createScaledBitmap(mLogo, (int)widthLogo, (int)heightLogo, false);

        int centreX = (image.getWidth() - mLogo2.getWidth());
        for (int y = 0; y < mLogo2.getHeight(); y++) {
            boolean notTransparentPxFound = false;
            for (int x = 0; x < mLogo2.getWidth(); x++) {
                int pixelContent = mLogo2.getPixel(x, y);
                if (notTransparentPxFound || pixelContent != Color.TRANSPARENT) {
                    notTransparentPxFound = true;
                    image.setPixel(x + centreX, y + TOP_PADDING, mBackgroundColor);
                }
            }
        }

        final Bitmap qrcode = Bitmap.createBitmap(image.getWidth() + BORDER*2,
                image.getHeight() + BORDER*2, image.getConfig());
        qrcode.eraseColor(mBackgroundColor);

        Canvas canvas = new Canvas(qrcode);
        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();

        canvas.drawBitmap(image, BORDER, BORDER, null);

        int centreY = (canvasHeight - mLogo2.getHeight());

        canvas.drawBitmap(mLogo2, centreX + BORDER, TOP_PADDING + BORDER, null);

        return qrcode;
    }

    public Bitmap getmQRCodeWithoutLogo() {
        if (mQRCode == null) {
            final ByteMatrix matrix;
            try {
                matrix = Encoder.encode(mData, ErrorCorrectionLevel.M).getMatrix();
            } catch (final WriterException e) {
                throw new RuntimeException(e);
            }
            final int height = matrix.getHeight() * SCALE;
            final int width = matrix.getWidth() * SCALE;
            mQRCode = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < width; ++x)
                for (int y = 0; y < height; ++y)
                    mQRCode.setPixel(x, y, matrix.get(x / SCALE, y / SCALE) == 1 ? Color.BLACK : mBackgroundColor);
        }
        return mQRCode;
    }

    // Parcelable support

    private QrBitmap(final Parcel in) {
        mData = in.readString();
        mBackgroundColor = in.readInt();
    }

    public static final Parcelable.Creator<QrBitmap> CREATOR
            = new Parcelable.Creator<QrBitmap>() {
        public QrBitmap createFromParcel(final Parcel in) {
            return new QrBitmap(in);
        }

        public QrBitmap[] newArray(final int size) {
            return new QrBitmap[size];
        }
    };

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeString(mData);
        dest.writeInt(mBackgroundColor);
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
