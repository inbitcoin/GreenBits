package com.greenaddress.greenbits.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * Created by Antonio Parrella on 1/18/17.
 * by inbitcoin
 */

class QrcodeScanDialog {

    private Dialog dialog;

    QrcodeScanDialog(Context context, String text) {
        dialog = new Dialog(context);
        final View v = dialog.getLayoutInflater().inflate(R.layout.dialog_text_share_btn, null, false);

        final TextView textView = UI.find(v, R.id.inDialogText);
        textView.setText(text);

        final String textToShare = String.format("%s \n\n%s %s", text,
                context.getResources().getString(R.string.sharedVia),
                context.getResources().getString(R.string.app_name));

        final ImageButton shareButton = UI.find(v, R.id.inDialogShare);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_TEXT, textToShare);
                intent.setType("text/plain");
                dialog.getContext().startActivity(intent);
            }
        });

        dialog.setContentView(v);
    }

    public void show() {
        if (dialog != null && !dialog.isShowing()) {
            dialog.show();
        }
    }
}
