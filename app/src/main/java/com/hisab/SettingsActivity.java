package com.hisab;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import database.DatabaseHelper;
import models.Person;
import utils.AppLockManager;
import utils.BackupManager;
import utils.ReminderHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Settings: history, archived, backup, restore, CSV, app lock, reminders, reset all, about, version. */
public class SettingsActivity extends AppCompatActivity {

    private boolean working = false;
    private boolean updatingUi = false; // stops switch listeners firing while we set them in code

    private SwitchCompat switchLock, switchBiometric;
    private View lockOptions;

    // ---- File pickers ----
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

    // ---- App lock screens ----
    private final ActivityResultLauncher<Intent> setPinLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, R.string.lock_turned_on, Toast.LENGTH_SHORT).show();
                }
                refreshLockUi();
            });

    private final ActivityResultLauncher<Intent> disableLockLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    AppLockManager.disable(this);
                    Toast.makeText(this, R.string.lock_turned_off, Toast.LENGTH_SHORT).show();
                }
                refreshLockUi();
            });

    private final ActivityResultLauncher<Intent> changePinLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    launchLock(setPinLauncher, LockActivity.MODE_SET); // current PIN was right
                }
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

        // ---- App lock ----
        switchLock = findViewById(R.id.switchLock);
        switchBiometric = findViewById(R.id.switchBiometric);
        lockOptions = findViewById(R.id.lockOptions);

        switchLock.setOnCheckedChangeListener((button, checked) -> {
            if (updatingUi) return;
            if (checked) launchLock(setPinLauncher, LockActivity.MODE_SET);
            else launchLock(disableLockLauncher, LockActivity.MODE_VERIFY);
        });

        switchBiometric.setOnCheckedChangeListener((button, checked) -> {
            if (updatingUi) return;
            if (checked && !AppLockManager.isBiometricAvailable(this)) {
                Toast.makeText(this, R.string.biometric_unavailable, Toast.LENGTH_LONG).show();
                refreshLockUi();
                return;
            }
            AppLockManager.setBiometricEnabled(this, checked);
        });

        findViewById(R.id.btnChangePin).setOnClickListener(v ->
                launchLock(changePinLauncher, LockActivity.MODE_VERIFY));

        refreshLockUi();

        // ---- Payment reminders ----
        SwitchCompat switchReminder = findViewById(R.id.switchReminder);
        switchReminder.setChecked(ReminderHelper.isEnabled(this));
        switchReminder.setOnCheckedChangeListener((button, checked) ->
                ReminderHelper.setEnabled(this, checked));

        findViewById(R.id.btnTestNotification).setOnClickListener(v -> {
            if (!ReminderHelper.hasPermission(this)) {
                Toast.makeText(this, R.string.test_no_permission, Toast.LENGTH_LONG).show();
            } else if (!ReminderHelper.showOweNotification(this)) {
                Toast.makeText(this, R.string.test_nothing_owed, Toast.LENGTH_LONG).show();
            }
        });
    }

    // =====================================================================
    //  APP LOCK
    // =====================================================================
    private void launchLock(ActivityResultLauncher<Intent> launcher, int mode) {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.EXTRA_MODE, mode);
        launcher.launch(intent);
    }

    private void refreshLockUi() {
        updatingUi = true;
        boolean enabled = AppLockManager.isEnabled(this);
        switchLock.setChecked(enabled);
        lockOptions.setVisibility(enabled ? View.VISIBLE : View.GONE);
        switchBiometric.setChecked(enabled
                && AppLockManager.isBiometricEnabled(this)
                && AppLockManager.isBiometricAvailable(this));
        updatingUi = false;
    }

    // =====================================================================
    //  BACKUP / RESTORE / CSV
    // =====================================================================
    private Uri pickedUri(int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) return data.getData();
        return null;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date());
    }

    private void launchCreate(ActivityResultLauncher<Intent> launcher, String mime, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        launcher.launch(intent);
    }

    private void onBackupClicked() {
        launchCreate(backupLauncher, "application/octet-stream",
                "hisab_backup_" + timestamp() + ".db");
    }

    private void doBackup(Uri uri) {
        boolean ok = BackupManager.backupDatabase(this, uri);
        Toast.makeText(this, ok ? R.string.backup_done : R.string.backup_failed,
                Toast.LENGTH_LONG).show();
    }

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
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if (result == BackupManager.RESTORE_INVALID) {
            Toast.makeText(this, R.string.restore_invalid, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_LONG).show();
        }
    }

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

    // =====================================================================
    //  VERSION
    // =====================================================================
    private String getVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (info.versionName != null) return info.versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return getString(R.string.version_unknown);
    }

    // =====================================================================
    //  RESET ALL
    // =====================================================================
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

    // =====================================================================
    //  ARCHIVED PEOPLE
    // =====================================================================
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