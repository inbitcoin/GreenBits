package com.greenaddress.greenbits;

import android.os.Binder;

class GaBinder extends Binder {

    public final GaService gaService;

    public GaBinder(final GaService gaService) {
        this.gaService = gaService;
    }
}
