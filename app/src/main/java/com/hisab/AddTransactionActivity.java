package com.hisab;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import database.DatabaseHelper;
import models.Person;
import models.Transaction;
import utils.CurrencyUtils;
import utils.DateUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Add Transaction: amount, date/time (never in the future), note, confirm, save. */
public class AddTransactionActivity extends AppCompatActivity {

    public static final String EXTRA_PERSON_ID = "person_id";
    public static final String EXTRA_TYPE = "type";

    private DatabaseHelper db;
    private Person person;
    private String type;

    private EditText etAmount, etDescription;
    private TextView tvError, btnSave, tvDate, tvTime, tvUseNow;

    private final Calendar selected = Calendar.getInstance(); // the chosen date and time
    private boolean customDate = false; // false = use the exact moment of saving

    private boolean saving = false;
    private boolean confirmShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);

        db = DatabaseHelper.getInstance(this);

        long personId = getIntent().getLongExtra(EXTRA_PERSON_ID, -1);
        type = getIntent().getStringExtra(EXTRA_TYPE);
        person = db.getPerson(personId);

        boolean validType = Transaction.TYPE_PLUS.equals(type) || Transaction.TYPE_MINUS.equals(type);
        if (person == null || !validType) {
            Toast.makeText(this, R.string.person_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView tvPersonName = findViewById(R.id.tvPersonName);
        TextView tvType = findViewById(R.id.tvType);
        etAmount = findViewById(R.id.etAmount);
        etDescription = findViewById(R.id.etDescription);
        tvError = findViewById(R.id.tvError);
        btnSave = findViewById(R.id.btnSave);
        tvDate = findViewById(R.id.tvDate);
        tvTime = findViewById(R.id.tvTime);
        tvUseNow = findViewById(R.id.tvUseNow);

        tvPersonName.setText(person.getName());

        if (Transaction.TYPE_PLUS.equals(type)) {
            tvType.setText(R.string.add_plus_title);
            tvType.setTextColor(ContextCompat.getColor(this, R.color.positive));
            btnSave.setBackgroundResource(R.drawable.bg_button_plus);
        } else {
            tvType.setText(R.string.add_minus_title);
            tvType.setTextColor(ContextCompat.getColor(this, R.color.negative));
            btnSave.setBackgroundResource(R.drawable.bg_button_minus);
        }

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> onSaveClicked());
        tvDate.setOnClickListener(v -> pickDate());
        tvTime.setOnClickListener(v -> pickTime());
        tvUseNow.setOnClickListener(v -> resetToNow());

        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                tvError.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        updateDateTimeViews();
    }

    // =====================================================================
    //  DATE AND TIME
    // =====================================================================
    private void updateDateTimeViews() {
        tvDate.setText(new SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                .format(selected.getTime()));
        tvTime.setText(new SimpleDateFormat("hh:mm a", Locale.getDefault())
                .format(selected.getTime()));
        tvUseNow.setVisibility(customDate ? View.VISIBLE : View.GONE);
    }

    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            selected.set(Calendar.YEAR, year);
            selected.set(Calendar.MONTH, month);
            selected.set(Calendar.DAY_OF_MONTH, day);
            customDate = true;

            // e.g. 11:00 PM yesterday, then switching the date to today
            if (selected.getTimeInMillis() > System.currentTimeMillis()) {
                selected.setTimeInMillis(System.currentTimeMillis());
                Toast.makeText(this, R.string.error_future_time, Toast.LENGTH_SHORT).show();
            }
            updateDateTimeViews();
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH),
                selected.get(Calendar.DAY_OF_MONTH));

        dialog.getDatePicker().setMaxDate(System.currentTimeMillis()); // blocks future dates
        dialog.show();
    }

    private void pickTime() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            Calendar candidate = (Calendar) selected.clone();
            candidate.set(Calendar.HOUR_OF_DAY, hour);
            candidate.set(Calendar.MINUTE, minute);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);

            if (candidate.getTimeInMillis() > System.currentTimeMillis()) {
                Toast.makeText(this, R.string.error_future_time, Toast.LENGTH_SHORT).show();
                return; // keep the old time
            }
            selected.setTimeInMillis(candidate.getTimeInMillis());
            customDate = true;
            updateDateTimeViews();
        }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), false).show();
    }

    private void resetToNow() {
        selected.setTimeInMillis(System.currentTimeMillis());
        customDate = false;
        updateDateTimeViews();
    }

    /** The time that will be stored: the exact moment of saving, unless the user chose one. */
    private long chosenMillis() {
        long now = System.currentTimeMillis();
        if (!customDate) return now;
        return Math.min(selected.getTimeInMillis(), now);
    }

    // =====================================================================
    //  SAVE
    // =====================================================================
    private void onSaveClicked() {
        if (saving || confirmShowing) return;

        long amountPaise = CurrencyUtils.parsePaise(etAmount.getText().toString());
        if (amountPaise <= 0) {
            tvError.setText(R.string.error_amount_invalid);
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        String amountText = CurrencyUtils.format(amountPaise);
        String message = Transaction.TYPE_PLUS.equals(type)
                ? getString(R.string.confirm_plus_message, amountText, person.getName())
                : getString(R.string.confirm_minus_message, amountText, person.getName());
        message += "\n\n" + getString(R.string.confirm_date_line,
                DateUtils.format(chosenMillis()));

        confirmShowing = true;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_transaction_title)
                .setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> saveTransaction(amountPaise))
                .setOnDismissListener(d -> confirmShowing = false)
                .show();
    }

    private void saveTransaction(long amountPaise) {
        if (saving) return;
        saving = true;
        btnSave.setEnabled(false);

        String description = etDescription.getText().toString().trim();
        boolean ok = db.addTransaction(person.getId(), type, amountPaise, description,
                chosenMillis());

        if (ok) {
            Toast.makeText(this, R.string.transaction_saved, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            saving = false;
            btnSave.setEnabled(true);
            tvError.setText(R.string.error_save_failed);
            tvError.setVisibility(View.VISIBLE);
        }
    }
}