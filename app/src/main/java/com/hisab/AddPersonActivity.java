package com.hisab;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import database.DatabaseHelper;

/** Add Person screen: validates the name, asks for confirmation, then saves. */
public class AddPersonActivity extends AppCompatActivity {

    private DatabaseHelper db;
    private EditText etName;
    private TextView tvError, btnAdd;
    private boolean saving = false; // blocks double taps

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_person);

        db = DatabaseHelper.getInstance(this);

        etName = findViewById(R.id.etName);
        tvError = findViewById(R.id.tvError);
        btnAdd = findViewById(R.id.btnAdd);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        btnAdd.setOnClickListener(v -> onAddClicked());

        // Pressing "Done" on the keyboard does the same as the button
        etName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onAddClicked();
                return true;
            }
            return false;
        });

        // Hide the error as soon as the user edits the name
        etName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                tvError.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void onAddClicked() {
        if (saving) return;

        // Trim and collapse repeated spaces: "  Rahul   Kumar " -> "Rahul Kumar"
        String name = etName.getText().toString().trim().replaceAll("\\s+", " ");

        if (name.isEmpty()) {
            showError(getString(R.string.error_name_empty));
            return;
        }
        if (db.personExists(name)) {
            showError(getString(R.string.error_name_duplicate));
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_add_person_title)
                .setMessage(getString(R.string.confirm_add_person_message, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> savePerson(name))
                .show();
    }

    private void savePerson(String name) {
        if (saving) return;
        saving = true;
        btnAdd.setEnabled(false);

        long id = db.addPerson(name);
        if (id == -1) {
            saving = false;
            btnAdd.setEnabled(true);
            showError(getString(R.string.error_add_failed));
            return;
        }

        Toast.makeText(this, getString(R.string.person_added, name), Toast.LENGTH_SHORT).show();
        finish(); // back to the dashboard, which refreshes itself in onResume()
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}