package com.greenaddress.greenbits.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.greenaddress.greenbits.GaService;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.ExchangeRate;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;

import java.math.BigDecimal;
import java.util.List;

public class ListTransactionsAdapter extends
        RecyclerView.Adapter<ListTransactionsAdapter.ViewHolder> {

    private final static int REQUEST_TX_DETAILS = 4;

    private final List<TransactionItem> mTxItems;
    private final String mBtcUnit;
    private final Activity mActivity;
    private final GaService mService;

    public ListTransactionsAdapter(final Activity activity, final GaService service,
                                   final List<TransactionItem> txItems) {
        mTxItems = txItems;
        mBtcUnit = (String) service.getUserConfig("unit");
        mActivity = activity;
        mService = service;
    }

    @Override
    public ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_element_transaction, parent, false));
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final TransactionItem txItem = mTxItems.get(position);


        final Coin coin = Coin.valueOf(txItem.amount);
        final MonetaryFormat bitcoinFormat = CurrencyMapper.mapBtcUnitToFormat(mBtcUnit);
        holder.bitcoinScale.setText(Html.fromHtml(CurrencyMapper.mapBtcUnitToPrefix(mBtcUnit)));
        if (mBtcUnit == null || mBtcUnit.equals("bits")) {
            holder.bitcoinIcon.setText("");
            holder.bitcoinScale.setText("bits ");
        } else {
            holder.bitcoinIcon.setText(R.string.fa_btc_space);
        }

        final String btcBalance = bitcoinFormat.noCode().format(coin).toString();
        UI.setAmountText(holder.textValue, btcBalance);

        // Show actual fiat value
        float exchangeRate = mService.getFiatRate();
        final Fiat exchangeFiat = Fiat.valueOf("???", new BigDecimal(exchangeRate).movePointRight(Fiat.SMALLEST_UNIT_EXPONENT)
                .toBigInteger().longValue());

        try {
            final ExchangeRate rate = new ExchangeRate(exchangeFiat);
            Fiat fiatValue = rate.coinToFiat(coin);
            // strip extra decimals (over 2 places) because that's what the old JS client does
            fiatValue = fiatValue.subtract(fiatValue.divideAndRemainder((long) Math.pow(10, Fiat.SMALLEST_UNIT_EXPONENT - 2))[1]);
            UI.setAmountText(holder.fiatValue, fiatValue.toPlainString());

            final String currency = mService.getFiatCurrency();
            final String converted = CurrencyMapper.map(currency);
            if (converted != null) {
                holder.fiatIcon.setText(Html.fromHtml(converted + " "));
                holder.fiatIcon.setAwesomeTypeface();
            } else {
                holder.fiatIcon.setText(currency);
                holder.fiatIcon.setDefaultTypeface();
            }
            UI.show(holder.fiatView);
        } catch (final ArithmeticException | IllegalArgumentException e) {
            UI.hide(holder.fiatView);
        }

        // Hide question mark if we know this tx is verified
        // (or we are in watch only mode and so have no SPV to verify it with)
        final boolean verified = txItem.spvVerified || txItem.isSpent ||
                                 txItem.type.equals(TransactionItem.TYPE.OUT) ||
                                 !mService.isSPVEnabled();
        UI.hideIf(verified, holder.textValueQuestionMark);

        final Resources res = mActivity.getResources();

        if (txItem.doubleSpentBy == null) {
            holder.textWhen.setTextColor(res.getColor(R.color.tertiaryTextColor));
            holder.textWhen.setText(TimeAgo.fromNow(txItem.date.getTime(), mActivity));
        } else {
            switch (txItem.doubleSpentBy) {
                case "malleability":
                    holder.textWhen.setTextColor(Color.parseColor("#FF8000"));
                    holder.textWhen.setText(res.getText(R.string.malleated));
                    break;
                case "update":
                    holder.textWhen.setTextColor(Color.parseColor("#FF8000"));
                    holder.textWhen.setText(res.getText(R.string.updated));
                    break;
                default:
                    holder.textWhen.setTextColor(Color.RED);
                    holder.textWhen.setText(res.getText(R.string.doubleSpend));
            }
        }

        UI.showIf(txItem.replaceable, holder.textReplaceable);

        final boolean humanCpty = txItem.type.equals(TransactionItem.TYPE.OUT)
                && txItem.counterparty != null && txItem.counterparty.length() > 0
                && !GaService.isValidAddress(txItem.counterparty);

        final String message;
        if (TextUtils.isEmpty(txItem.memo)) {
            if (humanCpty)
                message = txItem.counterparty;
            else
                message = getTypeString(txItem.type);
        } else {
            if (humanCpty && !txItem.counterparty.contains("inbitcoin"))
                message = String.format("%s %s", txItem.counterparty, txItem.memo);
            else
                message = txItem.memo;
        }
        final String merchant = mService.getTxMerchant(txItem.txHash.toString());
        if (!merchant.isEmpty()) {
            holder.textWho.setText(Html.fromHtml(String.format("<b>%s</b>", merchant)));
        } else {
            holder.textWho.setText(message);
        }

        final int color = txItem.amount > 0 ? R.color.superLightGreen : R.color.superLightPink;
        holder.mainLayout.setBackgroundColor(res.getColor(color));

        if (txItem.hasEnoughConfirmations()) {
            final int glyph = txItem.amount > 0 ? R.string.fa_chevron_circle_down : R.string.fa_chevron_up;
            holder.inOutIcon.setText(glyph);
            UI.hide(holder.listNumberConfirmation);
        } else {
            holder.inOutIcon.setText(R.string.fa_clock_o);
            UI.show(holder.listNumberConfirmation);
            holder.listNumberConfirmation.setText(String.valueOf(txItem.getConfirmations()));
        }

        holder.mainLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final Intent transactionActivity = new Intent(mActivity, TransactionActivity.class);
                transactionActivity.putExtra("TRANSACTION", txItem);
                mActivity.startActivityForResult(transactionActivity, REQUEST_TX_DETAILS);
            }
        });
    }

    private String getTypeString(final TransactionItem.TYPE type) {
        switch (type) {
            case IN:
                return mActivity.getString(R.string.txTypeIn);
            case OUT:
                return mActivity.getString(R.string.txTypeOut);
            case REDEPOSIT:
                return mActivity.getString(R.string.txTypeRedeposit);
            default:
                return "No type";
        }
    }

    @Override
    public int getItemCount() {
        return mTxItems == null ? 0 : mTxItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final TextView listNumberConfirmation;
        public final TextView textValue;
        public final TextView textWhen;
        public final TextView textReplaceable;
        public final TextView bitcoinIcon;
        public final TextView textWho;
        public final TextView paymentProcessorInfo;
        public final TextView inOutIcon;
        public final TextView bitcoinScale;
        public final TextView textValueQuestionMark;
        public final LinearLayout mainLayout;
        public final TextView fiatValue;
        public final View fiatView;
        public final FontAwesomeTextView fiatIcon;

        public ViewHolder(final View v) {

            super(v);

            textValue = UI.find(v, R.id.listValueText);
            textValueQuestionMark = UI.find(v, R.id.listValueQuestionMark);
            textWhen = UI.find(v, R.id.listWhenText);
            textReplaceable = UI.find(v, R.id.listReplaceableText);
            textWho = UI.find(v, R.id.listWhoText);
            paymentProcessorInfo = UI.find(v, R.id.paymentProcessor);
            inOutIcon = UI.find(v, R.id.listInOutIcon);
            mainLayout = UI.find(v, R.id.list_item_layout);
            bitcoinIcon = UI.find(v, R.id.listBitcoinIcon);
            bitcoinScale = UI.find(v, R.id.listBitcoinScaleText);
            listNumberConfirmation = UI.find(v, R.id.listNumberConfirmation);
            fiatValue = UI.find(v, R.id.fiatValue);
            fiatView = UI.find(v, R.id.fiatView);
            fiatIcon = UI.find(v, R.id.fiatIcon);
        }
    }
}
