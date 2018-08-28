package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.widget.Button;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;


public final class CB {

    public static <T> void after(final ListenableFuture<T> f,
                                 final FutureCallback<? super T> cb) {
        Futures.addCallback(f, cb);
    }

    /** A FutureCallback that does nothing by default */
    public static class Op<T> implements FutureCallback<T> {
        @Override
        public void onSuccess(final T result) { /* No-op */ }

        @Override
        public void onFailure(final Throwable t) {
            t.printStackTrace();
        }
    }


    /** A FutureCallback that shows a toast (and optionally
     *  enables a button) on failure
     */
    public static class Toast<T> extends Op<T> {

       final Activity mActivity;
       final Button mEnabler;
       final CircularButton mCircular;

       Toast(final Activity activity) {
           this(activity, (Button) null);
       }

       Toast(final Activity activity, final Button enabler) {
           super();
           mActivity = activity;
           mEnabler = enabler;
           mCircular = null;
       }

       Toast(final Activity activity, final CircularButton enabler) {
            super();
            mActivity = activity;
            mEnabler = null;
            mCircular = enabler;
        }

       @Override
       public void onFailure(final Throwable t) {
           t.printStackTrace();
           if (mCircular != null)
               UI.toast(mActivity, t, mCircular);
           else
               UI.toast(mActivity, t, mEnabler);
       }
    }

    /** A runnable that takes 1 argument */
    public interface Runnable1T<T> {
        void run(final T arg);
    }
}
