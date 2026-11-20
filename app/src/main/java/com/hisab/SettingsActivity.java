package com.hisab;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import database.DatabaseHelper;
import utils.BackupManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import models.Person;
import java.util.List;

/** Settings: backup, restore, CSV export, reset all balances, about and version. */
public class SettingsActivity extends AppCompatActivity {

    private boolean working = false; // blocks double taps

    // Each launcher receives the file the user picked in Android's file picker.
    private final ActivityResultLauncher<Intent> backupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri uri = pickedUri(result.getResultCode(), result.getData());
                if (uri != null) doBackup(uri);
            });

    private final ActivityResultLauncher<Intent> restoreLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri uri = pickedUri(result.getResultCode(), result.getData());
                if (uri != null) doRestore(uri);
            });

    private final ActivityResultLauncher<Intent> csvLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri uri = pickedUri(result.getResultCode(), result.getData());
                if (uri != null) doExportCsv(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.tvBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnResetAll).setOnClickListener(v -> onResetAllClicked());
        findViewById(R.id.btnBackup).setOnClickListener(v -> onBackupClicked());
        findViewById(R.id.btnRestore).setOnClickListener(v -> onRestoreClicked());
        findViewById(R.id.btnExportCsv).setOnClickListener(v -> onExportCsvClicked());
        findViewById(R.id.btnHistory).setOnClickListener(v ->
                startActivity(new Intent(this, AllTransactionsActivity.class)));
        findViewById(R.id.btnArchived).setOnClickListener(v -> showArchivedDialog());
        TextView tvVersion = findViewById(R.id.tvVersion);
        tvVersion.setText(getVersionName());
    }

    private Uri pickedUri(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) return data.getData();
        return null;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
    }

    /** Opens Android's "Save as..." screen. */
    private void launchCreate(ActivityResultLauncher<Intent> launcher, String mime, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        launcher.launch(intent);
    }

    // ---------------- Backup ----------------
    private void onBackupClicked() {
        launchCreate(backupLauncher, "application/octet-stream",
                "hisab_backup_" + timestamp() + ".db");
    }

    private void doBackup(Uri uri) {
        boolean ok = BackupManager.backupDatabase(this, uri);
        Toast.makeText(this, ok ? R.string.backup_done : R.string.backup_failed,
                Toast.LENGTH_LONG).show();
    }

    // ---------------- Restore ----------------
    private void onRestoreClicked() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.restore_confirm_title)
                .setMessage(R.string.restore_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.restore_choose_file, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    restoreLauncher.launch(intent);
                })
                .show();
    }

    private void doRestore(Uri uri) {
        if (working) return;
        working = true;

        int result = BackupManager.restoreDatabase(this, uri);
        working = false;

        if (result == BackupManager.RESTORE_OK) {
            Toast.makeText(this, R.string.restore_done, Toast.LENGTH_LONG).show();
            // Restart from the dashboard so every screen loads the restored data
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if (result == BackupManager.RESTORE_INVALID) {
            Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- CSV export ----------------
    private void onExportCsvClicked() {
        if (DatabaseHelper.getInstance(this).getAllTransactions().isEmpty()) {
            Toast.makeText(this, R.string.csv_nothing, Toast.LENGTH_SHORT).show();
            return;
        }
        launchCreate(csvLauncher, "text/csv", "hisab_transactions_" + timestamp() + ".csv");
    }

    private void doExportCsv(Uri uri) {
        int count = BackupManager.exportCsv(this, uri);
        if (count >= 0) {
            Toast.makeText(this, getString(R.string.csv_done, count), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.csv_failed, Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- Version ----------------
    private String getVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null) return info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return getString(R.string.version_unknown);
    }

    // ---------------- Reset all balances ----------------
    private void onResetAllClicked() {
        if (working) return;

        DatabaseHelper db = DatabaseHelper.getInstance(this);

        if (db.getTotalToReceive() == 0 && db.getTotalToPay() == 0) {
            Toast.makeText(this, R.string.nothing_to_reset, Toast.LENGTH_SHORT).show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_all_title)
                .setMessage(R.string.reset_all_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    if (working) return;
                    working = true;
                    int count = DatabaseHelper.getInstance(this).resetAllBalances();
                    working = false;
                    if (count > 0) {
                        Toast.makeText(this, getString(R.string.reset_all_done, count),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.error_save_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }
    // ---------------- Archived people ----------------
    private void showArchivedDialog() {
        DatabaseHelper db = DatabaseHelper.getInstance(this);
        List<Person> archived = db.getArchivedPeople();

        if (archived.isEmpty()) {
            Toast.makeText(this, R.string.no_archived, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names = new String[archived.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = archived.get(i).getName();
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.archived_people)
                .setItems(names, (dialog, which) -> confirmRestore(archived.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmRestore(Person person) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.unarchive_title)
                .setMessage(getString(R.string.unarchive_message, person.getName()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.unarchive_action, (dialog, which) -> {
                    boolean ok = DatabaseHelper.getInstance(this).setArchived(person.getId(), false);
                    Toast.makeText(this,
                            ok ? getString(R.string.person_restored, person.getName())
                                    : getString(R.string.error_save_failed),
                            Toast.LENGTH_SHORT).show();
                })
                .show();
    }
}