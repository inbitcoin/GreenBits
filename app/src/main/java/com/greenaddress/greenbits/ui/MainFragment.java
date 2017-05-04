package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.greenaddress.greenbits.FormatMemo;
import com.greenaddress.greenbits.GaService;

import org.bitcoin.protocols.payments.Protos;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.protocols.payments.PaymentSession;
import org.bitcoinj.utils.MonetaryFormat;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observer;

public class MainFragment extends SubaccountFragment {
    private static final String TAG = MainFragment.class.getSimpleName();

    private MaterialDialog mUnconfirmedDialog = null;
    private List<TransactionItem> mTxItems;
    private Map<Sha256Hash, List<Sha256Hash> > replacedTxs;
    private int mSubaccount;
    private Observer mVerifiedTxObserver;
    private Observer mNewTxObserver;
    private final Runnable mDialogCB = new Runnable() { public void run() { mUnconfirmedDialog = null; } };
    private CheckTxsMemo mCheckTxsMemo = null;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private void updateBalance() {
        Log.d(TAG, "Updating balance");
        if (isZombie())
            return;

        final GaService service = getGAService();
        final Monetary monetary = service.getCoinBalance(mSubaccount);
        if (service.getLoginData() == null || monetary == null)
            return;

        final String btcUnit = (String) service.getUserConfig("unit");
        final MonetaryFormat bitcoinFormat = CurrencyMapper.mapBtcUnitToFormat(btcUnit).noCode();
        final TextView balanceBitcoinIcon = UI.find(mView, R.id.mainBalanceBitcoinIcon);
        final TextView bitcoinScale = UI.find(mView, R.id.mainBitcoinScaleText);
        bitcoinScale.setText(Html.fromHtml(CurrencyMapper.mapBtcUnitToPrefix(btcUnit)));
        if (btcUnit == null || btcUnit.equals("bits")) {
            balanceBitcoinIcon.setText("");
            bitcoinScale.setText("bits ");
        } else {
            balanceBitcoinIcon.setText(R.string.fa_btc_space);
        }

        final String btcBalance = bitcoinFormat.format(monetary).toString();
        final String btcVerifiedBalance;
        final Coin verifiedBalance = service.getSPVVerifiedBalance(mSubaccount);
        if (verifiedBalance != null)
            btcVerifiedBalance = bitcoinFormat.format(verifiedBalance).toString();
        else
            btcVerifiedBalance = bitcoinFormat.format(Coin.valueOf(0)).toString();

        final String fiatBalance =
                MonetaryFormat.FIAT.minDecimals(2).noCode().format(
                        service.getFiatBalance(mSubaccount))
                        .toString();
        final String fiatCurrency = service.getFiatCurrency();
        final String converted = CurrencyMapper.map(fiatCurrency);

        // Hide balance question mark if we know our balance is verified
        // (or we are in watch only mode and so have no SPV to verify it with)
        final TextView balanceQuestionMark = UI.find(mView, R.id.mainBalanceQuestionMark);
        final boolean verified = btcBalance.equals(btcVerifiedBalance) ||
                                 !service.isSPVEnabled();
        UI.hideIf(verified, balanceQuestionMark);

        final TextView balanceText = UI.find(mView, R.id.mainBalanceText);
        final TextView balanceFiatText = UI.find(mView, R.id.mainLocalBalanceText);
        final FontAwesomeTextView balanceFiatIcon = UI.find(mView, R.id.mainLocalBalanceIcon);
        UI.setAmountText(balanceText, btcBalance);

        final int nChars = balanceText.getText().length() + balanceQuestionMark.getText().length() + bitcoinScale.getText().length() + balanceBitcoinIcon.getText().length();
        final int size = Math.min(50 - nChars, 34);
        balanceText.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        bitcoinScale.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        balanceBitcoinIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);

        UI.setAmountText(balanceFiatText, fiatBalance);

        if (converted != null) {
            balanceFiatIcon.setText(Html.fromHtml(converted + " "));
            balanceFiatIcon.setAwesomeTypeface();
            balanceFiatIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        } else {
            balanceFiatIcon.setText(fiatCurrency);
            balanceFiatIcon.setDefaultTypeface();
            balanceFiatIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        }
        if (service.showBalanceInTitle())
            UI.hide(bitcoinScale, balanceText, balanceBitcoinIcon);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {

        Log.d(TAG, "onCreateView -> " + TAG);
        if (isZombieNoView())
            return null;

        final GaService service = getGAService();
        // commented follow line because I've a suspect that cause infinite show dialog
        //popupWaitDialog(R.string.loading_transactions);

        mView = inflater.inflate(R.layout.fragment_main, container, false);
        final RecyclerView txView = UI.find(mView, R.id.mainTransactionList);
        txView.setHasFixedSize(true);

        final LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        final DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(txView.getContext(),
        layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.line_divider));
        txView.addItemDecoration(dividerItemDecoration);

        txView.setLayoutManager(layoutManager);

        mSubaccount = service.getCurrentSubAccount();

        final TextView firstP = UI.find(mView, R.id.mainFirstParagraphText);
        final TextView secondP = UI.find(mView, R.id.mainSecondParagraphText);
        final TextView thirdP = UI.find(mView, R.id.mainThirdParagraphText);

        firstP.setMovementMethod(LinkMovementMethod.getInstance());
        secondP.setMovementMethod(LinkMovementMethod.getInstance());
        thirdP.setMovementMethod(LinkMovementMethod.getInstance());

        final TextView balanceText = UI.find(mView, R.id.mainBalanceText);
        final TextView balanceQuestionMark = UI.find(mView, R.id.mainBalanceQuestionMark);
        final View.OnClickListener unconfirmedClickListener = new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (mUnconfirmedDialog == null && balanceQuestionMark.getVisibility() == View.VISIBLE) {
                    // Question mark is visible and dialog not shown, so show it
                    mUnconfirmedDialog = UI.popup(getActivity(), R.string.unconfirmedBalanceTitle, 0)
                                           .content(R.string.unconfirmedBalanceText).build();
                    UI.setDialogCloseHandler(mUnconfirmedDialog, mDialogCB);
                    mUnconfirmedDialog.show();
                }
            }
        };
        balanceText.setOnClickListener(unconfirmedClickListener);
        balanceQuestionMark.setOnClickListener(unconfirmedClickListener);

        makeBalanceObserver(mSubaccount);
        if (IsPageSelected() && service.getCoinBalance(mSubaccount) != null) {
            updateBalance();
            reloadTransactions(false, true);
        }

        mSwipeRefreshLayout = UI.find(mView, R.id.mainTransactionListSwipe);
        mSwipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(getContext(), R.color.accent));
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.d(TAG, "onRefresh -> " + TAG);
                // user action to force reload balance and tx list
                onBalanceUpdated();
            }
        });

        registerReceiver();
        return mView;
    }

    @Override
    protected void onBalanceUpdated() {
        Log.d(TAG, "onBalanceUpdated -> " + TAG);
        updateBalance();
        reloadTransactions(false, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause -> " + TAG);
        detachObservers();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume -> " + TAG);
        if (getGAService() != null)
            attachObservers();
        setIsDirty(true);
    }

    @Override
    public void attachObservers() {
        if (mVerifiedTxObserver == null) {
            mNewTxObserver = makeUiObserver(new Runnable() { public void run() { onNewTx(); } });
            getGAService().addNewTxObserver(mNewTxObserver);
        }
        if (mVerifiedTxObserver == null) {
            mVerifiedTxObserver = makeUiObserver(new Runnable() { public void run() { onVerifiedTx(); } });
            getGAService().addVerifiedTxObserver(mVerifiedTxObserver);
        }
        super.attachObservers();
    }

    @Override
    public void detachObservers() {
        super.detachObservers();
        if (mVerifiedTxObserver != null) {
            getGAService().deleteNewTxObserver(mNewTxObserver);
            mNewTxObserver = null;
        }
        if (mVerifiedTxObserver != null) {
            getGAService().deleteVerifiedTxObserver(mVerifiedTxObserver);
            mVerifiedTxObserver = null;
        }
    }

    // Called when a new transaction is seen
    private void onNewTx() {
        if (!IsPageSelected()) {
            Log.d(TAG, "New transaction while page hidden");
            setIsDirty(true);
            return;
        }
        reloadTransactions(false, false);
    }

    // Called when a new verified transaction is seen
    private void onVerifiedTx() {
        if (mTxItems == null)
          return;

        final GaService service = getGAService();
        for (final TransactionItem txItem : mTxItems)
            txItem.spvVerified = service.isSPVVerified(txItem.txHash);

        final RecyclerView txView = UI.find(mView, R.id.mainTransactionList);
        txView.getAdapter().notifyDataSetChanged();
    }

    private void showTxView(boolean doShow) {
        UI.showIf(doShow, (View) UI.find(mView, R.id.mainTransactionListSwipe));
        UI.hideIf(doShow, (View) UI.find(mView, R.id.mainEmptyTransText));
    }

    private void reloadTransactions(final boolean newAdapter, final boolean showWaitDialog) {
        final Activity activity = getActivity();
        final GaService service = getGAService();
        final RecyclerView txView;

        if (isZombie())
            return;

        // Mark ourselves as clean before fetching. This means that while the callback
        // is running, we may be marked dirty again if a new block arrives, which
        // is required to avoid missing updates while the RPC is in flight.
        setIsDirty(false);

        txView = UI.find(mView, R.id.mainTransactionList);

        if (mTxItems == null || mTxItems.isEmpty() || showWaitDialog) {
            // Show a wait dialog only when initially loading transactions
            popupWaitDialog(R.string.loading_transactions);
        }

        if (mTxItems == null || newAdapter) {
            mTxItems = new ArrayList<>();
            txView.setAdapter(new ListTransactionsAdapter(activity, service, mTxItems));
            // FIXME, more efficient to use swap
            // txView.swapAdapter(lta, false);
        }

        if (replacedTxs == null || newAdapter)
            replacedTxs = new HashMap<>();

        Futures.addCallback(service.getMyTransactions(mSubaccount),
            new FutureCallback<Map<?, ?>>() {
            @Override
            public void onSuccess(final Map<?, ?> result) {
                final List txList = (List) result.get("list");
                final int currentBlock = ((Integer) result.get("cur_block"));

                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        hideWaitDialog();
                        if (mSwipeRefreshLayout != null)
                            mSwipeRefreshLayout.setRefreshing(false);

                        if (!IsPageSelected()) {
                            Log.d(TAG, "Callback after hiding, ignoring");
                            // Mark ourselves as dirty so we reload when next shown
                            setIsDirty(true);
                            return;
                        }

                        if (mCheckTxsMemo != null)
                            mCheckTxsMemo.cancel(true);
                        mCheckTxsMemo = new CheckTxsMemo();

                        showTxView(txList.size() > 0);

                        final Sha256Hash oldTop = mTxItems.size() > 0 ? mTxItems.get(0).txHash : null;
                        mTxItems.clear();
                        replacedTxs.clear();

                        for (Object tx : txList) {
                            try {
                                Map<String, Object> txJSON = (Map) tx;
                                ArrayList<String> replacedList = (ArrayList) txJSON.get("replaced_by");

                                if (replacedList == null) {
                                    mTxItems.add(new TransactionItem(service, txJSON, currentBlock));
                                    continue;
                                }

                                for (String replacedBy : replacedList) {
                                    final Sha256Hash replacedHash = Sha256Hash.wrap(replacedBy);
                                    if (!replacedTxs.containsKey(replacedHash))
                                        replacedTxs.put(replacedHash, new ArrayList<Sha256Hash>());
                                    final Sha256Hash newTxHash = Sha256Hash.wrap((String) txJSON.get("txhash"));
                                    replacedTxs.get(replacedHash).add(newTxHash);
                                }
                            } catch (final ParseException e) {
                                e.printStackTrace();
                            }
                        }

                        for (TransactionItem txItem : mTxItems) {
                            if (replacedTxs.containsKey(txItem.txHash))
                                for (Sha256Hash replaced : replacedTxs.get(txItem.txHash))
                                    txItem.replacedHashes.add(replaced);
                        }

                        txView.getAdapter().notifyDataSetChanged();

                        final Sha256Hash newTop = mTxItems.size() > 0 ? mTxItems.get(0).txHash : null;
                        if (oldTop != null && newTop != null && !oldTop.equals(newTop)) {
                            // A new tx has arrived; scroll to the top to show it
                            txView.smoothScrollToPosition(0);
                        }

                        if (mTxItems != null) {
                            mCheckTxsMemo.execute(mTxItems);
                        }
                    }
                });

            }

            @Override
            public void onFailure(final Throwable t) {
                t.printStackTrace();
                activity.runOnUiThread(new Runnable() {
                    public void run() {
                        hideWaitDialog();
                        if (mSwipeRefreshLayout != null)
                            mSwipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        }, service.getExecutor());
    }

    @Override
    protected void onSubaccountChanged(final int newSubAccount) {
        mSubaccount = newSubAccount;
        makeBalanceObserver(mSubaccount);
        if (!IsPageSelected()) {
            Log.d(TAG, "Subaccount changed while page hidden");
            setIsDirty(true);
            return;
        }
        reloadTransactions(false, true);
        updateBalance();
    }

    @Override
    public void setUserVisibleHint(final boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            hideKeyboard();
        }
    }

    public void setPageSelected(final boolean isSelected) {
        final boolean needReload = isSelected && !IsPageSelected() && isDirty();
        super.setPageSelected(isSelected);
        if (needReload) {
            Log.d(TAG, "Dirty, reloading");
            reloadTransactions(false, true);
            updateBalance();
            if (!isZombie())
                setIsDirty(false);
        }
    }

    class CheckTxsMemo extends AsyncTask<Object, Object, Boolean> {

        @Override
        protected Boolean doInBackground(Object... params) {
            boolean toReload = false;
            GaService service = getGAService();
            final List <TransactionItem> txItems = (List<TransactionItem>) params[0];
            for (final TransactionItem txItem : txItems) {
                try {
                    final String txHash = txItem.txHash.toString();
                    final Boolean merchantChecked = service.memoTxAlreadyChecked(txHash);
                    if (merchantChecked)
                        continue;
                    final String paymentRequest;
                    try {
                        service.setTxChecked(txHash);
                        paymentRequest = service.getPaymentRequest(txHash);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (!paymentRequest.isEmpty()) {
                        // decode and read data from protocol buffer
                        byte[] data = Base64.decode(paymentRequest, Base64.DEFAULT);
                        Protos.PaymentRequest paymentRequest1 = Protos.PaymentRequest.parseFrom(data);

                        PaymentSession paymentSession = new PaymentSession(paymentRequest1);
                        final String [] memoInfo = FormatMemo.sanitizeMemo(paymentSession.getMemo());
                        if (memoInfo == null || memoInfo[0].isEmpty())
                            continue;

                        if (memoInfo.length == 2) {
                            service.saveMerchantInvoiceData(txHash, null, memoInfo[0], memoInfo[1]);
                        } else if (memoInfo.length == 3) {
                            service.saveMerchantInvoiceData(txHash, memoInfo[2], memoInfo[0], memoInfo[1]);
                        }
                        toReload = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return toReload;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                final RecyclerView txView = UI.find(mView, R.id.mainTransactionList);
                txView.getAdapter().notifyDataSetChanged();
            }
        }
    }
}
