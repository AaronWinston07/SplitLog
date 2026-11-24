package adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hisab.R;

import models.MonthSummary;
import utils.CurrencyUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Shows one row per previous month. */
public class MonthAdapter extends RecyclerView.Adapter<MonthAdapter.ViewHolder> {

    public interface OnMonthClickListener {
        void onMonthClick(MonthSummary month);
    }

    private final List<MonthSummary> items = new ArrayList<>();
    private final OnMonthClickListener listener;

    public MonthAdapter(OnMonthClickListener listener) {
        this.listener = listener;
    }

    public void setMonths(List<MonthSummary> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_month, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        MonthSummary m = items.get(position);
        h.tvMonth.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(new Date(m.startMillis())));
        h.tvCount.setText(h.itemView.getContext()
                .getString(R.string.exp_count, m.getCount()));
        h.tvTotal.setText(CurrencyUtils.format(m.getTotal()));
        h.itemView.setOnClickListener(v -> listener.onMonthClick(m));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMonth, tvCount, tvTotal;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMonth = itemView.findViewById(R.id.tvMonth);
            tvCount = itemView.findViewById(R.id.tvCount);
            tvTotal = itemView.findViewById(R.id.tvTotal);
        }
    }
}