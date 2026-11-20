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

import models.Person;
import utils.CurrencyUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Fills the dashboard list with one card (item_person.xml) per person. */
public class PersonAdapter extends RecyclerView.Adapter<PersonAdapter.ViewHolder> {

    /** The dashboard implements this to find out which person was tapped. */
    public interface OnPersonClickListener {
        void onPersonClick(Person person);
    }

    private final List<Person> people = new ArrayList<>();
    private final OnPersonClickListener listener;

    public PersonAdapter(OnPersonClickListener listener) {
        this.listener = listener;
    }

    /** Replaces the list contents and redraws. */
    public void setPeople(List<Person> newPeople) {
        people.clear();
        people.addAll(newPeople);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_person, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Person person = people.get(position);
        Context context = holder.itemView.getContext();

        String name = person.getName();
        holder.tvName.setText(name);
        holder.tvInitial.setText(name.isEmpty() ? "?"
                : name.substring(0, 1).toUpperCase(Locale.getDefault()));

        long balance = person.getBalance();
        holder.tvBalance.setText(CurrencyUtils.formatSigned(balance));

        int colorRes;
        int statusRes;
        if (balance > 0) {
            colorRes = R.color.positive;
            statusRes = R.string.they_owe_you;
        } else if (balance < 0) {
            colorRes = R.color.negative;
            statusRes = R.string.you_owe_them;
        } else {
            colorRes = R.color.zero;
            statusRes = R.string.settled;
        }
        holder.tvBalance.setTextColor(ContextCompat.getColor(context, colorRes));
        holder.tvStatus.setText(statusRes);

        holder.itemView.setOnClickListener(v -> listener.onPersonClick(person));
    }

    @Override
    public int getItemCount() {
        return people.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvInitial, tvName, tvStatus, tvBalance;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial = itemView.findViewById(R.id.tvInitial);
            tvName = itemView.findViewById(R.id.tvName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvBalance = itemView.findViewById(R.id.tvBalance);
        }
    }
}