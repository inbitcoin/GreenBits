package com.greenaddress.greenbits;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by Antonio Parrella on 1/9/17.
 * by inbitcoin
 */

public class ApplicationService extends Service {
    private ServiceConnection mConnection;
    private GaService mService;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName className, final IBinder service) {
                mService = ((GaService.GaBinder)service).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {

            }
        };

        final Intent intent = new Intent(this, GaService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (mService != null) {
            mService.mSPV.stopSync();
        }
        super.onTaskRemoved(rootIntent);
    }
}
