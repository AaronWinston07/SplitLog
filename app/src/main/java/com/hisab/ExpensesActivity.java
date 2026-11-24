package com.hisab;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import adapters.ExpenseAdapter;
import database.DatabaseHelper;
import models.Expense;
import models.MonthSummary;
import utils.CurrencyUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** My Expenses: totals, budget bar, previous months and this month's list. */
public class ExpensesActivity extends AppCompatActivity {

    private static final int PREVIOUS_SHOWN = 3;

    private DatabaseHelper db;
    private ExpenseAdapter adapter;
    private TextView tvMonthName, tvMonthTotal, tvMonthSmall, tvToday, tvWeek;
    private TextView tvBudgetInfo, tvBudgetAmounts, tvViewAll;
    private ProgressBar progressBudget;
    private RecyclerView rvExpenses;
    private View emptyView, previousSection;
    private LinearLayout previousList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_expenses);

        db = DatabaseHelper.getInstance(this);

        tvMonthName = findViewById(R.id.tvMonthName);
        tvMonthTotal = findViewById(R.id.tvMonthTotal);
        tvMonthSmall = findViewById(R.id.tvMonthSmall);
        tvToday = findViewById(R.id.tvToday);
        tvWeek = findViewById(R.id.tvWeek);
        tvBudgetInfo = findViewById(R.id.tvBudgetInfo);
        tvBudgetAmounts = findViewById(R.id.tvBudgetAmounts);
        progressBudget = findViewById(R.id.progressBudget);
        rvExpenses = findViewById(R.id.rvExpenses);
        emptyView = findViewById(R.id.emptyView);
        previousSection = findViewById(R.id.previousSection);
        previousList = findViewById(R.id.previousList);
        tvViewAll = findViewById(R.id.tvViewAll);

        adapter = new ExpenseAdapter(this::showOptions);
        rvExpenses.setLayoutManager(new LinearLayoutManager(this));
        rvExpenses.setAdapter(adapter);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddExpense).setOnClickListener(v ->
                startActivity(new Intent(this, AddExpenseActivity.class)));
        findViewById(R.id.cardBudget).setOnClickListener(v -> showBudgetDialog());
        tvViewAll.setOnClickListener(v ->
                startActivity(new Intent(this, PreviousMonthsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private static void startOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private String currentMonthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private void refresh() {
        // Today
        Calendar day = Calendar.getInstance();
        startOfDay(day);
        long todayStart = day.getTimeInMillis();
        day.add(Calendar.DAY_OF_MONTH, 1);
        long todayEnd = day.getTimeInMillis();

        // This week (Monday to Sunday)
        Calendar week = Calendar.getInstance();
        startOfDay(week);
        int daysSinceMonday = (week.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        week.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);
        long weekStart = week.getTimeInMillis();
        week.add(Calendar.DAY_OF_MONTH, 7);
        long weekEnd = week.getTimeInMillis();

        // This month
        Calendar month = Calendar.getInstance();
        startOfDay(month);
        month.set(Calendar.DAY_OF_MONTH, 1);
        long monthStart = month.getTimeInMillis();
        tvMonthName.setText(new SimpleDateFormat("MMMM yyyy", Locale.getDefault())
                .format(month.getTime()));
        month.add(Calendar.MONTH, 1);
        long monthEnd = month.getTimeInMillis();

        long monthSpent = db.sumExpenses(monthStart, monthEnd);
        String monthText = CurrencyUtils.format(monthSpent);
        tvMonthTotal.setText(monthText);
        tvMonthSmall.setText(monthText);
        tvToday.setText(CurrencyUtils.format(db.sumExpenses(todayStart, todayEnd)));
        tvWeek.setText(CurrencyUtils.format(db.sumExpenses(weekStart, weekEnd)));

        updateBudgetCard(monthSpent);
        updatePreviousMonths(monthStart);

        List<Expense> list = db.getExpenses(monthStart, monthEnd);
        adapter.setExpenses(list);
        boolean empty = list.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvExpenses.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // =====================================================================
    //  PREVIOUS MONTHS (latest few, as simple rows)
    // =====================================================================
    private void updatePreviousMonths(long currentMonthStart) {
        List<MonthSummary> months = db.getMonthSummariesBefore(currentMonthStart);
        previousList.removeAllViews();

        if (months.isEmpty()) {
            previousSection.setVisibility(View.GONE);
            return;
        }
        previousSection.setVisibility(View.VISIBLE);
        tvViewAll.setVisibility(months.size() > PREVIOUS_SHOWN ? View.VISIBLE : View.GONE);

        SimpleDateFormat nameFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        LayoutInflater inflater = LayoutInflater.from(this);
        int shown = Math.min(PREVIOUS_SHOWN, months.size());

        for (int i = 0; i < shown; i++) {
            MonthSummary m = months.get(i);
            View row = inflater.inflate(R.layout.item_month_row, previousList, false);
            ((TextView) row.findViewById(R.id.tvMonth))
                    .setText(nameFormat.format(new Date(m.startMillis())));
            ((TextView) row.findViewById(R.id.tvTotal))
                    .setText(CurrencyUtils.format(m.getTotal()));
            row.setOnClickListener(v -> {
                Intent intent = new Intent(this, MonthExpensesActivity.class);
                intent.putExtra(MonthExpensesActivity.EXTRA_MONTH_START, m.startMillis());
                startActivity(intent);
            });
            previousList.addView(row);
        }
    }

    // =====================================================================
    //  BUDGET
    // =====================================================================
    private void updateBudgetCard(long spent) {
        long budget = db.getBudgetForMonth(currentMonthKey());

        if (budget <= 0) {
            progressBudget.setVisibility(View.GONE);
            tvBudgetAmounts.setText("");
            tvBudgetInfo.setText(R.string.budget_tap_set);
            tvBudgetInfo.setTextColor(0xFFCFC2F5);
            return;
        }

        boolean over = spent >= budget;
        int barPercent = (int) Math.min(100L, spent * 100 / budget);

        progressBudget.setVisibility(View.VISIBLE);
        progressBudget.setProgressDrawable(ContextCompat.getDrawable(this,
                over ? R.drawable.progress_budget_over : R.drawable.progress_budget));
        progressBudget.setProgress(barPercent);

        tvBudgetAmounts.setText(CurrencyUtils.format(spent) + " / " + CurrencyUtils.format(budget));

        String percentText = String.format(Locale.getDefault(), "%.1f", spent * 100.0 / budget);
        String info = getString(R.string.budget_percent_used, percentText);
        if (spent > budget) {
            info += "\n" + getString(R.string.budget_over, CurrencyUtils.format(spent - budget));
        }
        tvBudgetInfo.setText(info);
        tvBudgetInfo.setTextColor(over ? 0xFFFF6B6B : 0xFFCFC2F5);
    }

    private void showBudgetDialog() {
        long current = db.getBudgetForMonth(currentMonthKey());

        EditText input = new EditText(this);
        input.setHint(R.string.budget_hint);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setTextColor(ContextCompat.getColor(this, R.color.white));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        if (current > 0) {
            input.setText(BigDecimal.valueOf(current, 2).stripTrailingZeros().toPlainString());
            input.setSelection(input.getText().length());
        }

        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, pad / 2, pad, 0);
        input.setLayoutParams(lp);
        container.addView(input);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.budget_dialog_title)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long paise = CurrencyUtils.parsePaise(input.getText().toString());
            if (paise <= 0) {
                input.setError(getString(R.string.error_amount_invalid));
                return;
            }
            if (db.setBudget(currentMonthKey(), paise)) {
                dialog.dismiss();
                Toast.makeText(this, R.string.budget_saved, Toast.LENGTH_SHORT).show();
                refresh();
            } else {
                input.setError(getString(R.string.error_save_failed));
            }
        });
    }

    // =====================================================================
    //  EXPENSE OPTIONS
    // =====================================================================
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
                        confirmDelete(expense);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(Expense expense) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exp_delete_title)
                .setMessage(getString(R.string.exp_delete_message, expense.getTitle(),
                        CurrencyUtils.format(expense.getAmount())))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.exp_delete, (dialog, which) -> {
                    boolean ok = db.deleteExpense(expense.getId());
                    Toast.makeText(this, ok ? R.string.exp_deleted : R.string.error_save_failed,
                            Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }
}