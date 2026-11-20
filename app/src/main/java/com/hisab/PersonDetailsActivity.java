package com.hisab;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import adapters.TransactionAdapter;
import database.DatabaseHelper;
import models.Person;
import models.Transaction;
import utils.CurrencyUtils;

import java.util.List;

/** Person Details: balance, PLUS / MINUS / Reset / Manage and this person's history. */
public class PersonDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_PERSON_ID = "person_id";

    private DatabaseHelper db;
    private long personId;
    private Person person;

    private TextView tvPersonName, tvBalance, tvStatus, tvEmpty;
    private RecyclerView rvTransactions;
    private TransactionAdapter adapter;
    private boolean resetting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person_details);

        db = DatabaseHelper.getInstance(this);
        personId = getIntent().getLongExtra(EXTRA_PERSON_ID, -1);

        tvPersonName = findViewById(R.id.tvPersonName);
        tvBalance = findViewById(R.id.tvBalance);
        tvStatus = findViewById(R.id.tvStatus);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvTransactions = findViewById(R.id.rvTransactions);

        adapter = new TransactionAdapter(false);
        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        rvTransactions.setAdapter(adapter);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPlus).setOnClickListener(v -> openAddTransaction(Transaction.TYPE_PLUS));
        findViewById(R.id.btnMinus).setOnClickListener(v -> openAddTransaction(Transaction.TYPE_MINUS));
        findViewById(R.id.btnReset).setOnClickListener(v -> onResetClicked());
        findViewById(R.id.btnManage).setOnClickListener(v -> showManageDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        person = db.getPerson(personId);
        if (person == null) {
            Toast.makeText(this, R.string.person_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvPersonName.setText(person.getName());
        long balance = person.getBalance();
        tvBalance.setText(CurrencyUtils.formatSigned(balance));

        if (balance > 0) {
            tvStatus.setText(R.string.they_owe_you);
        } else if (balance < 0) {
            tvStatus.setText(R.string.you_owe_them);
        } else {
            tvStatus.setText(R.string.settled);
        }

        List<Transaction> list = db.getTransactionsForPerson(personId);
        adapter.setTransactions(list);
        boolean empty = list.isEmpty();
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openAddTransaction(String type) {
        Intent intent = new Intent(this, AddTransactionActivity.class);
        intent.putExtra(AddTransactionActivity.EXTRA_PERSON_ID, personId);
        intent.putExtra(AddTransactionActivity.EXTRA_TYPE, type);
        startActivity(intent);
    }

    // =====================================================================
    //  RESET BALANCE
    // =====================================================================
    private void onResetClicked() {
        if (resetting || person == null) return;

        if (person.getBalance() == 0) {
            Toast.makeText(this, R.string.already_settled, Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = createInput(getString(R.string.reset_reason_hint));
        FrameLayout container = wrap(input);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_confirm_title)
                .setMessage(getString(R.string.reset_confirm_message, person.getName()))
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    if (resetting) return;
                    resetting = true;
                    boolean ok = db.resetBalance(personId, input.getText().toString());
                    resetting = false;
                    Toast.makeText(this,
                            ok ? R.string.balance_reset_done : R.string.error_save_failed,
                            Toast.LENGTH_SHORT).show();
                    refresh();
                })
                .show();
    }

    // =====================================================================
    //  MANAGE: EDIT NAME / ARCHIVE / DELETE
    // =====================================================================
    private void showManageDialog() {
        if (person == null) return;
        String[] options = {
                getString(R.string.edit_name),
                getString(R.string.archive_person),
                getString(R.string.delete_person)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.manage_person)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showEditNameDialog();
                    else if (which == 1) onArchiveClicked();
                    else onDeleteClicked();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showEditNameDialog() {
        EditText input = createInput(getString(R.string.person_name));
        input.setText(person.getName());
        input.setSelection(input.getText().length());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});

        // The positive button is set to null first so we can keep the dialog open on errors.
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_name)
                .setView(wrap(input))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int result = db.renamePerson(personId, input.getText().toString());
            if (result == DatabaseHelper.RENAME_OK) {
                dialog.dismiss();
                Toast.makeText(this, R.string.name_updated, Toast.LENGTH_SHORT).show();
                refresh();
            } else if (result == DatabaseHelper.RENAME_EMPTY) {
                input.setError(getString(R.string.error_name_empty));
            } else if (result == DatabaseHelper.RENAME_DUPLICATE) {
                input.setError(getString(R.string.error_name_duplicate));
            } else {
                input.setError(getString(R.string.error_save_failed));
            }
        });
    }

    private void onArchiveClicked() {
        if (person.getBalance() != 0) {
            Toast.makeText(this, R.string.archive_needs_settled, Toast.LENGTH_LONG).show();
            return;
        }
        String name = person.getName();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.archive_confirm_title)
                .setMessage(getString(R.string.archive_confirm_message, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.archive_action, (dialog, which) -> {
                    if (db.setArchived(personId, true)) {
                        Toast.makeText(this, getString(R.string.person_archived, name),
                                Toast.LENGTH_SHORT).show();
                        finish(); // back to the dashboard, which no longer lists them
                    } else {
                        Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void onDeleteClicked() {
        String name = person.getName();
        int count = db.getTransactionsForPerson(personId).size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(getString(R.string.delete_confirm_message, name, count))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_action, (dialog, which) -> {
                    if (db.deletePerson(personId)) {
                        Toast.makeText(this, getString(R.string.person_deleted, name),
                                Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, R.string.error_save_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    // =====================================================================
    //  SMALL HELPERS for text boxes inside dialogs
    // =====================================================================
    private EditText createInput(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setSingleLine(true);
        input.setTextColor(ContextCompat.getColor(this, R.color.white));
        input.setHintTextColor(ContextCompat.getColor(this, R.color.text_hint));
        return input;
    }

    private FrameLayout wrap(EditText input) {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, pad / 2, pad, 0);
        input.setLayoutParams(lp);
        container.addView(input);
        return container;
    }
}