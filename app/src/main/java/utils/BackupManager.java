package utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import database.DatabaseHelper;
import models.Transaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Backup, restore and CSV export. Works with files chosen through Android's file picker. */
public class BackupManager {

    public static final int RESTORE_OK = 0;
    public static final int RESTORE_INVALID = 1;
    public static final int RESTORE_FAILED = 2;

    // ------------------------------------------------------------------
    //  BACKUP: copy hisab.db to the file the user picked
    // ------------------------------------------------------------------
    public static boolean backupDatabase(Context context, Uri destination) {
        try {
            DatabaseHelper.getInstance(context).checkpoint();
            File dbFile = DatabaseHelper.getDatabaseFile(context);

            try (InputStream in = new FileInputStream(dbFile);
                 OutputStream out = context.getContentResolver().openOutputStream(destination, "wt")) {
                if (out == null) return false;
                copy(in, out);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    //  RESTORE: check the chosen file, then replace the database with it
    // ------------------------------------------------------------------
    public static int restoreDatabase(Context context, Uri source) {
        File temp = new File(context.getCacheDir(), "restore_temp.db");
        try {
            // 1. Copy the chosen file into a temporary file
            try (InputStream in = context.getContentResolver().openInputStream(source);
                 OutputStream out = new FileOutputStream(temp)) {
                if (in == null) return RESTORE_FAILED;
                copy(in, out);
            }

            // 2. Make sure it really is a HISAB backup BEFORE touching current data
            if (!isValidBackup(temp)) return RESTORE_INVALID;

            // 3. Close the database and swap the file
            DatabaseHelper.closeAndReset();
            File dbFile = DatabaseHelper.getDatabaseFile(context);
            new File(dbFile.getPath() + "-wal").delete();
            new File(dbFile.getPath() + "-shm").delete();
            new File(dbFile.getPath() + "-journal").delete();

            if (!temp.renameTo(dbFile)) {
                try (InputStream in = new FileInputStream(temp);
                     OutputStream out = new FileOutputStream(dbFile)) {
                    copy(in, out);
                }
            }
            return RESTORE_OK;
        } catch (Exception e) {
            return RESTORE_FAILED;
        } finally {
            temp.delete();
        }
    }

    private static boolean isValidBackup(File file) {
        // The first 16 bytes of every SQLite file say "SQLite format 3"
        try (InputStream in = new FileInputStream(file)) {
            byte[] header = new byte[16];
            if (in.read(header) != 16) return false;
            String text = new String(header, StandardCharsets.US_ASCII);
            if (!text.equals("SQLite format 3\u0000")) return false;
        } catch (IOException e) {
            return false;
        }

        SQLiteDatabase check = null;
        try {
            check = SQLiteDatabase.openDatabase(file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            if (check.getVersion() > DatabaseHelper.DB_VERSION) return false; // from a newer app
            return tableExists(check, "people") && tableExists(check, "transactions");
        } catch (Exception e) {
            return false;
        } finally {
            if (check != null) check.close();
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String name) {
        try (Cursor c = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{name})) {
            return c.moveToFirst();
        }
    }

    // ------------------------------------------------------------------
    //  CSV EXPORT: returns number of rows written, or -1 on failure
    // ------------------------------------------------------------------
    public static int exportCsv(Context context, Uri destination) {
        List<Transaction> list = DatabaseHelper.getInstance(context).getAllTransactions();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

        try (OutputStream out = context.getContentResolver().openOutputStream(destination, "wt")) {
            if (out == null) return -1;
            Writer w = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));

            w.write("\uFEFF"); // lets Excel read the file as UTF-8
            w.write("Date,Person,Type,Amount (INR),Previous Balance (INR),Updated Balance (INR),Description\r\n");

            // getAllTransactions() is newest-first; spreadsheets read better oldest-first
            for (int i = list.size() - 1; i >= 0; i--) {
                Transaction t = list.get(i);
                w.write(sdf.format(new Date(t.getDate())));
                w.write(",");
                w.write(csvText(t.getPersonName()));
                w.write(",");
                w.write(t.getType());
                w.write(",");
                w.write(rupees(t.getAmount()));
                w.write(",");
                w.write(rupees(t.getPreviousBalance()));
                w.write(",");
                w.write(rupees(t.getUpdatedBalance()));
                w.write(",");
                w.write(csvText(t.getDescription()));
                w.write("\r\n");
            }
            w.flush();
            return list.size();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 5025 -> "50.25", -5000 -> "-50.00" (plain numbers so spreadsheets can add them up). */
    private static String rupees(long paise) {
        return BigDecimal.valueOf(paise, 2).toPlainString();
    }

    /** Wraps text in quotes when needed and stops spreadsheet "formula" tricks. */
    private static String csvText(String s) {
        if (s == null) s = "";
        if (!s.isEmpty()) {
            char first = s.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                s = "'" + s;
            }
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }
}