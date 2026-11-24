package com.hisab;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import database.DatabaseHelper;
import models.Expense;
import utils.CurrencyUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import utils.BudgetAlertHelper;
/** Add a new expense, or edit one when EXTRA_EXPENSE_ID is given. */
public class AddExpenseActivity extends AppCompatActivity {

    public static final String EXTRA_EXPENSE_ID = "expense_id";

    private DatabaseHelper db;
    private long expenseId = -1; // -1 = adding a new one
    private final Calendar selected = Calendar.getInstance();

    private EditText etTitle, etAmount, etDescription;
    private TextView tvDate, tvError, btnSave;
    private boolean saving = false;
    private boolean confirmShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_expense);

        db = DatabaseHelper.getInstance(this);

        etTitle = findViewById(R.id.etTitle);
        etAmount = findViewById(R.id.etAmount);
        etDescription = findViewById(R.id.etDescription);
        tvDate = findViewById(R.id.tvDate);
        tvError = findViewById(R.id.tvError);
        btnSave = findViewById(R.id.btnSave);

        expenseId = getIntent().getLongExtra(EXTRA_EXPENSE_ID, -1);
        if (expenseId != -1) {
            Expense e = db.getExpense(expenseId);
            if (e == null) {
                finish();
                return;
            }
            ((TextView) findViewById(R.id.tvHeading)).setText(R.string.exp_edit_title);
            etTitle.setText(e.getTitle());
            etAmount.setText(java.math.BigDecimal.valueOf(e.getAmount(), 2)
                    .stripTrailingZeros().toPlainString());
            etDescription.setText(e.getDescription());
            selected.setTimeInMillis(e.getDate());
        }

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> onSaveClicked());
        tvDate.setOnClickListener(v -> pickDate());

        TextWatcher hideError = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                tvError.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        etTitle.addTextChangedListener(hideError);
        etAmount.addTextChangedListener(hideError);

        updateDateView();
    }

    private void updateDateView() {
        tvDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(selected.getTime()));
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, day);
            updateDateView();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis()); // no future dates
        dialog.show();
    }

    /** Never later than "now". */
    private long chosenMillis() {
        return Math.min(selected.getTimeInMillis(), System.currentTimeMillis());
    }

    private void showError(int messageRes) {
        tvError.setText(messageRes);
        tvError.setVisibility(View.VISIBLE);
    }

    private void onSaveClicked() {
        if (saving || confirmShowing) return;

        String title = etTitle.getText().toString().trim();
        if (title.isEmpty()) {
            showError(R.string.exp_error_title);
            return;
        }
        long amountPaise = CurrencyUtils.parsePaise(etAmount.getText().toString());
        if (amountPaise <= 0) {
            showError(R.string.error_amount_invalid);
            return;
        }

        String dateText = tvDate.getText().toString();
        confirmShowing = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.exp_confirm_title)
                .setMessage(getString(R.string.exp_confirm_message, title,
                        CurrencyUtils.format(amountPaise), dateText))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (d, w) -> save(title, amountPaise))
                .setOnDismissListener(d -> confirmShowing = false)
                .show();
    }

    private void save(String title, long amountPaise) {
        if (saving) return;
        saving = true;
        btnSave.setEnabled(false);

        String description = etDescription.getText().toString().trim();
        boolean ok;
        if (expenseId == -1) {
            ok = db.addExpense(title, amountPaise, chosenMillis(), description) != -1;
        } else {
            ok = db.updateExpense(expenseId, title, amountPaise, chosenMillis(), description);
        }

        if (ok) {
            Toast.makeText(this, expenseId == -1 ? R.string.exp_saved : R.string.exp_updated,
                    Toast.LENGTH_SHORT).show();
            BudgetAlertHelper.check(this, chosenMillis()); // budget alerts
            finish();
        } else {
            saving = false;
            btnSave.setEnabled(true);
            showError(R.string.error_save_failed);
        }
    }
}