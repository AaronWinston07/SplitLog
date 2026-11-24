package com.hisab;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import adapters.ExpenseAdapter;
import database.DatabaseHelper;
import models.Expense;
import utils.CurrencyUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** All expenses of one chosen month. */
public class MonthExpensesActivity extends AppCompatActivity {

    public static final String EXTRA_MONTH_START = "month_start";

    private DatabaseHelper db;
    private ExpenseAdapter adapter;
    private long monthStart, monthEnd;
    private TextView tvMonthTotal, tvMonthCount;
    private RecyclerView rvExpenses;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_month_expenses);

        db = DatabaseHelper.getInstance(this);

        monthStart = getIntent().getLongExtra(EXTRA_MONTH_START, -1);
        if (monthStart == -1) {
            finish();
            return;
        }
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(monthStart);
        c.add(Calendar.MONTH, 1);
        monthEnd = c.getTimeInMillis();

        ((TextView) findViewById(R.id.tvMonthName)).setText(
                new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(monthStart)));
        tvMonthTotal = findViewById(R.id.tvMonthTotal);
        tvMonthCount = findViewById(R.id.tvMonthCount);
        rvExpenses = findViewById(R.id.rvExpenses);
        emptyView = findViewById(R.id.emptyView);

        adapter = new ExpenseAdapter(this::showOptions);
        rvExpenses.setLayoutManager(new LinearLayoutManager(this));
        rvExpenses.setAdapter(adapter);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (monthStart == -1) return;
        refresh();
    }

    private void refresh() {
        List<Expense> list = db.getExpenses(monthStart, monthEnd);
        adapter.setExpenses(list);
        tvMonthTotal.setText(CurrencyUtils.format(db.sumExpenses(monthStart, monthEnd)));
        tvMonthCount.setText(getString(R.string.exp_count, list.size()));
        boolean empty = list.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvExpenses.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showOptions(Expense expense) {
        String[] options = {getString(R.string.exp_edit), getString(R.string.exp_delete)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(expense.getTitle())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, AddExpenseActivity.class);
                        intent.putExtra(AddExpenseActivity.EXTRA_EXPENSE_ID, expense.getId());
                        startActivity(intent);
                    } else {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.exp_delete_title)
                                .setMessage(getString(R.string.exp_delete_message,
                                        expense.getTitle(), CurrencyUtils.format(expense.getAmount())))
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.exp_delete, (d, w) -> {
                                    boolean ok = db.deleteExpense(expense.getId());
                                    Toast.makeText(this,
                                            ok ? R.string.exp_deleted : R.string.error_save_failed,
                                            Toast.LENGTH_SHORT).show();
                                    refresh();
                                })
                                .show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}