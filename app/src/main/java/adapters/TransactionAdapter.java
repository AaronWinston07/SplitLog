package adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.hisab.R;

import models.Transaction;
import utils.CurrencyUtils;
import utils.DateUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows history rows (item_transaction.xml).
 * showPersonName = false on one person's page, true on the "All Transactions" screen.
 */
public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private final List<Transaction> items = new ArrayList<>();
    private final boolean showPersonName;

    public TransactionAdapter(boolean showPersonName) {
        this.showPersonName = showPersonName;
    }

    public void setTransactions(List<Transaction> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Transaction t = items.get(position);
        Context context = h.itemView.getContext();

        int colorRes;
        int typeRes;
        String amountText;
        if (Transaction.TYPE_PLUS.equals(t.getType())) {
            colorRes = R.color.positive;
            typeRes = R.string.type_plus;
            amountText = "+" + CurrencyUtils.format(t.getAmount());
        } else if (Transaction.TYPE_MINUS.equals(t.getType())) {
            colorRes = R.color.negative;
            typeRes = R.string.type_minus;
            amountText = "-" + CurrencyUtils.format(t.getAmount());
        } else {
            colorRes = R.color.primary_light;
            typeRes = R.string.type_reset;
            amountText = CurrencyUtils.format(t.getAmount());
        }
        int color = ContextCompat.getColor(context, colorRes);

        h.tvType.setText(typeRes);
        h.tvType.setTextColor(color);
        h.tvAmount.setText(amountText);
        h.tvAmount.setTextColor(color);

        h.tvChange.setText(context.getString(R.string.balance_change,
                CurrencyUtils.formatSigned(t.getPreviousBalance()),
                CurrencyUtils.formatSigned(t.getUpdatedBalance())));

        String desc = t.getDescription();
        if (desc == null || desc.trim().isEmpty()) {
            h.tvDescription.setVisibility(View.GONE);
        } else {
            h.tvDescription.setVisibility(View.VISIBLE);
            h.tvDescription.setText(desc);
        }

        h.tvDate.setText(DateUtils.format(t.getDate()));

        if (showPersonName) {
            h.tvPerson.setVisibility(View.VISIBLE);
            h.tvPerson.setText(t.getPersonName());
            h.spacer.setVisibility(View.GONE);
        } else {
            h.tvPerson.setVisibility(View.GONE);
            h.spacer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvType, tvPerson, tvAmount, tvChange, tvDescription, tvDate;
        final View spacer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvType = itemView.findViewById(R.id.tvType);
            tvPerson = itemView.findViewById(R.id.tvPerson);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvChange = itemView.findViewById(R.id.tvChange);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvDate = itemView.findViewById(R.id.tvDate);
            spacer = itemView.findViewById(R.id.spacer);
        }
    }
}