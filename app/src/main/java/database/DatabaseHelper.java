package database;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import models.Person;
import models.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.io.File;

import java.util.HashMap;
import java.util.Map;
import android.database.sqlite.SQLiteException;

import models.Expense;

import models.MonthSummary;


/**
 * All database work for HISAB lives here.
 * Every money value is in PAISE.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "hisab.db";
    public static final int DB_VERSION = 4;

    // PEOPLE table
    private static final String TABLE_PEOPLE = "people";
    private static final String COL_PERSON_ID = "person_id";
    private static final String COL_PERSON_NAME = "person_name";
    private static final String COL_CURRENT_BALANCE = "current_balance";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_IS_ARCHIVED = "is_archived";

    // TRANSACTIONS table
    private static final String TABLE_TRANSACTIONS = "transactions";
    private static final String COL_TRANSACTION_ID = "transaction_id";
    private static final String COL_TRANSACTION_TYPE = "transaction_type";
    private static final String COL_AMOUNT = "amount";
    private static final String COL_PREVIOUS_BALANCE = "previous_balance";
    private static final String COL_UPDATED_BALANCE = "updated_balance";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_TRANSACTION_DATE = "transaction_date";

    private static DatabaseHelper instance;

    /** Use this everywhere instead of "new DatabaseHelper(...)": one shared helper for the whole app. */
    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true); // make FOREIGN KEY actually enforced
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PEOPLE + " ("
                + COL_PERSON_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PERSON_NAME + " TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                + COL_CURRENT_BALANCE + " INTEGER NOT NULL DEFAULT 0, "
                + COL_CREATED_AT + " INTEGER NOT NULL, "
                + COL_IS_ARCHIVED + " INTEGER NOT NULL DEFAULT 0)");

        db.execSQL("CREATE TABLE " + TABLE_TRANSACTIONS + " ("
                + COL_TRANSACTION_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_PERSON_ID + " INTEGER NOT NULL, "
                + COL_TRANSACTION_TYPE + " TEXT NOT NULL, "
                + COL_AMOUNT + " INTEGER NOT NULL, "
                + COL_PREVIOUS_BALANCE + " INTEGER NOT NULL, "
                + COL_UPDATED_BALANCE + " INTEGER NOT NULL, "
                + COL_DESCRIPTION + " TEXT, "
                + COL_TRANSACTION_DATE + " INTEGER NOT NULL, "
                + "FOREIGN KEY(" + COL_PERSON_ID + ") REFERENCES "
                + TABLE_PEOPLE + "(" + COL_PERSON_ID + "))");
        createExpensesTable(db);
        createBudgetsTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // NEVER drop tables here: that would delete the user's records.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_PEOPLE + " ADD COLUMN "
                    + COL_IS_ARCHIVED + " INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            createExpensesTable(db);
        }
        if (oldVersion < 4) {
            createBudgetsTable(db);
        }
    }

    // =====================================================================
    //  PEOPLE
    // =====================================================================

    /** Returns true if a person with this name already exists (not case-sensitive). */
    public boolean personExists(String name) {
        if (name == null) return false;
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PEOPLE, new String[]{COL_PERSON_ID},
                COL_PERSON_NAME + " = ? COLLATE NOCASE",
                new String[]{name.trim()}, null, null, null)) {
            return c.moveToFirst();
        }
    }

    /**
     * Adds a new person with balance 0.
     * Returns the new person's id, or -1 if the name is empty or already exists.
     */
    public long addPerson(String name) {
        if (name == null) return -1;
        String clean = name.trim();
        if (clean.isEmpty() || personExists(clean)) return -1;

        ContentValues values = new ContentValues();
        values.put(COL_PERSON_NAME, clean);
        values.put(COL_CURRENT_BALANCE, 0);
        values.put(COL_CREATED_AT, System.currentTimeMillis());
        return getWritableDatabase().insert(TABLE_PEOPLE, null, values);
    }

    /** All people, sorted by name. */
    public List<Person> getAllPeople() {
        List<Person> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PEOPLE, null, COL_IS_ARCHIVED + " = 0", null, null, null,
                COL_PERSON_NAME + " COLLATE NOCASE ASC")) {
            while (c.moveToNext()) {
                list.add(personFromCursor(c));
            }
        }
        return list;
    }

    /** One person by id, or null if not found. */
    public Person getPerson(long personId) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PEOPLE, null, COL_PERSON_ID + " = ?",
                new String[]{String.valueOf(personId)}, null, null, null)) {
            if (c.moveToFirst()) {
                return personFromCursor(c);
            }
        }
        return null;
    }

    private Person personFromCursor(Cursor c) {
        return new Person(
                c.getLong(c.getColumnIndexOrThrow(COL_PERSON_ID)),
                c.getString(c.getColumnIndexOrThrow(COL_PERSON_NAME)),
                c.getLong(c.getColumnIndexOrThrow(COL_CURRENT_BALANCE)),
                c.getLong(c.getColumnIndexOrThrow(COL_CREATED_AT)));
    }

    // =====================================================================
    //  DASHBOARD TOTALS
    // =====================================================================

    /** Total of all positive balances (money people owe me), in paise. Always >= 0. */
    public long getTotalToReceive() {
        return sumBalances(COL_CURRENT_BALANCE + " > 0");
    }

    /** Total of all negative balances (money I owe), as a POSITIVE number of paise. */
    public long getTotalToPay() {
        return Math.abs(sumBalances(COL_CURRENT_BALANCE + " < 0"));
    }

    /** Net balance = to receive - to pay. Positive means overall people owe me. */
    public long getNetBalance() {
        return getTotalToReceive() - getTotalToPay();
    }

    private long sumBalances(String where) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT COALESCE(SUM(" + COL_CURRENT_BALANCE + "), 0) FROM "
                + TABLE_PEOPLE + " WHERE " + where, null)) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    // =====================================================================
    //  TRANSACTIONS (PLUS / MINUS)
    // =====================================================================

    /**
     * Adds a PLUS or MINUS adjustment to one person's balance.
     *   PLUS  : new balance = previous + amount  (they owe me more)
     *   MINUS : new balance = previous - amount  (I owe them more)
     *
     * Steps 1-4 happen inside ONE SQLite transaction: either everything is saved,
     * or nothing is.
     *
     * Returns true on success, false if input is invalid or something failed.
     */
    public boolean addTransaction(long personId, String type, long amountPaise, String description) {
        return addTransaction(personId, type, amountPaise, description, System.currentTimeMillis());
    }

    /** Same as above, but the transaction is stamped with the chosen date/time (never in the future). */
    public boolean addTransaction(long personId, String type, long amountPaise,
                                  String description, long dateMillis) {
        if (dateMillis > System.currentTimeMillis() + 60_000L) return false; // no future dates
        if (amountPaise <= 0) return false;
        if (!Transaction.TYPE_PLUS.equals(type) && !Transaction.TYPE_MINUS.equals(type)) return false;

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // 1. Read the current balance
            Long previous = readBalance(db, personId);
            if (previous == null) return false; // person not found

            // 2. Calculate the new balance
            long updated = Transaction.TYPE_PLUS.equals(type)
                    ? previous + amountPaise
                    : previous - amountPaise;

            // 3. Insert the history row
            long rowId = insertHistoryRow(db, personId, type, amountPaise,
                    previous, updated, description,  dateMillis);
            if (rowId == -1) return false;

            // 4. Update the person's balance
            if (!updateBalance(db, personId, updated)) return false;

            db.setTransactionSuccessful(); // commit (only reached if everything above worked)
            return true;
        } finally {
            db.endTransaction(); // rolls back automatically if setTransactionSuccessful() was not called
        }
    }

    // =====================================================================
    //  RESET (history is kept!)
    // =====================================================================

    /**
     * Resets one person's balance to 0 and records a RESET entry in the history.
     * Old transactions are NOT deleted.
     * 'reason' is optional ("why"); pass null or "" for no reason.
     *
     * Returns false if the balance was already 0 (nothing to reset) or on failure.
     */
    public boolean resetBalance(long personId, String reason) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            boolean done = resetInternal(db, personId, buildResetNote(reason),
                    System.currentTimeMillis());
            if (done) db.setTransactionSuccessful();
            return done;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Resets EVERY person whose balance is not 0, each with its own RESET history entry.
     * All-or-nothing. Returns how many people were reset.
     */
    public int resetAllBalances() {
        SQLiteDatabase db = getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try {
            List<Long> ids = new ArrayList<>();
            try (Cursor c = db.query(TABLE_PEOPLE, new String[]{COL_PERSON_ID},
                    COL_CURRENT_BALANCE + " != 0", null, null, null, null)) {
                while (c.moveToNext()) {
                    ids.add(c.getLong(0));
                }
            }
            long now = System.currentTimeMillis();
            for (long id : ids) {
                if (resetInternal(db, id, "Reset all balances", now)) {
                    count++;
                } else {
                    return 0; // something failed -> whole thing is rolled back
                }
            }
            db.setTransactionSuccessful();
            return count;
        } finally {
            db.endTransaction();
        }
    }

    private String buildResetNote(String reason) {
        if (reason == null || reason.trim().isEmpty()) return "Balance reset";
        return "Balance reset: " + reason.trim();
    }

    /** Must be called inside an open transaction. */
    private boolean resetInternal(SQLiteDatabase db, long personId, String note, long time) {
        Long previous = readBalance(db, personId);
        if (previous == null || previous == 0) return false;

        long rowId = insertHistoryRow(db, personId, Transaction.TYPE_RESET,
                Math.abs(previous), previous, 0, note, time);
        if (rowId == -1) return false;

        return updateBalance(db, personId, 0);
    }

    // =====================================================================
    //  HISTORY QUERIES
    // =====================================================================

    /** History of one person, newest first. */
    public List<Transaction> getTransactionsForPerson(long personId) {
        return queryTransactions("t." + COL_PERSON_ID + " = ?",
                new String[]{String.valueOf(personId)});
    }

    /** History of everyone, newest first. */
    public List<Transaction> getAllTransactions() {
        return queryTransactions(null, null);
    }

    private List<Transaction> queryTransactions(String where, String[] args) {
        List<Transaction> list = new ArrayList<>();
        String sql = "SELECT t.*, p." + COL_PERSON_NAME + " AS person_name_joined "
                + "FROM " + TABLE_TRANSACTIONS + " t "
                + "INNER JOIN " + TABLE_PEOPLE + " p ON t." + COL_PERSON_ID
                + " = p." + COL_PERSON_ID
                + (where != null ? " WHERE " + where : "")
                + " ORDER BY t." + COL_TRANSACTION_DATE + " DESC, t." + COL_TRANSACTION_ID + " DESC";

        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery(sql, args)) {
            while (c.moveToNext()) {
                list.add(new Transaction(
                        c.getLong(c.getColumnIndexOrThrow(COL_TRANSACTION_ID)),
                        c.getLong(c.getColumnIndexOrThrow(COL_PERSON_ID)),
                        c.getString(c.getColumnIndexOrThrow("person_name_joined")),
                        c.getString(c.getColumnIndexOrThrow(COL_TRANSACTION_TYPE)),
                        c.getLong(c.getColumnIndexOrThrow(COL_AMOUNT)),
                        c.getLong(c.getColumnIndexOrThrow(COL_PREVIOUS_BALANCE)),
                        c.getLong(c.getColumnIndexOrThrow(COL_UPDATED_BALANCE)),
                        c.getString(c.getColumnIndexOrThrow(COL_DESCRIPTION)),
                        c.getLong(c.getColumnIndexOrThrow(COL_TRANSACTION_DATE))));
            }
        }
        return list;
    }

    // =====================================================================
    //  SMALL PRIVATE HELPERS (always used inside a transaction)
    // =====================================================================

    /** Returns the person's balance, or null if the person does not exist. */
    private Long readBalance(SQLiteDatabase db, long personId) {
        try (Cursor c = db.query(TABLE_PEOPLE, new String[]{COL_CURRENT_BALANCE},
                COL_PERSON_ID + " = ?", new String[]{String.valueOf(personId)},
                null, null, null)) {
            return c.moveToFirst() ? c.getLong(0) : null;
        }
    }

    private long insertHistoryRow(SQLiteDatabase db, long personId, String type, long amount,
                                  long previous, long updated, String description, long time) {
        ContentValues values = new ContentValues();
        values.put(COL_PERSON_ID, personId);
        values.put(COL_TRANSACTION_TYPE, type);
        values.put(COL_AMOUNT, amount);
        values.put(COL_PREVIOUS_BALANCE, previous);
        values.put(COL_UPDATED_BALANCE, updated);
        values.put(COL_DESCRIPTION, description == null ? "" : description.trim());
        values.put(COL_TRANSACTION_DATE, time);
        return db.insert(TABLE_TRANSACTIONS, null, values);
    }

    private boolean updateBalance(SQLiteDatabase db, long personId, long newBalance) {
        ContentValues values = new ContentValues();
        values.put(COL_CURRENT_BALANCE, newBalance);
        int rows = db.update(TABLE_PEOPLE, values, COL_PERSON_ID + " = ?",
                new String[]{String.valueOf(personId)});
        return rows == 1;
    }
    // =====================================================================
    //  BACKUP SUPPORT
    // =====================================================================

    /** Where Android keeps the database file on this phone. */
    public static File getDatabaseFile(Context context) {
        return context.getDatabasePath(DB_NAME);
    }

    /** Pushes all pending changes into the main .db file, so a copy of the file is complete. */
    public void checkpoint() {
        try {
            SQLiteDatabase db = getWritableDatabase();
            try (Cursor c = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                c.moveToFirst();
            }
        } catch (Exception ignored) {
            // If the phone does not use WAL mode, there is nothing to flush.
        }
    }

    /** Closes the shared helper so the database file can be replaced (used by Restore). */
    public static synchronized void closeAndReset() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
    /**
     * For each person id, the time of their most recent transaction.
     * People with no transactions are not in the map.
     */
    public Map<Long, Long> getLastActivityTimes() {
        Map<Long, Long> map = new HashMap<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT " + COL_PERSON_ID + ", MAX(" + COL_TRANSACTION_DATE
                + ") FROM " + TABLE_TRANSACTIONS + " GROUP BY " + COL_PERSON_ID, null)) {
            while (c.moveToNext()) {
                map.put(c.getLong(0), c.getLong(1));
            }
        }
        return map;
    }
    // =====================================================================
    //  EDIT / ARCHIVE / DELETE PERSON
    // =====================================================================

    public static final int RENAME_OK = 0;
    public static final int RENAME_EMPTY = 1;
    public static final int RENAME_DUPLICATE = 2;
    public static final int RENAME_FAILED = 3;

    /** Changes a person's name. Old history rows show the new name automatically. */
    public int renamePerson(long personId, String newName) {
        if (newName == null) return RENAME_EMPTY;
        String clean = newName.trim().replaceAll("\\s+", " ");
        if (clean.isEmpty()) return RENAME_EMPTY;

        SQLiteDatabase db = getWritableDatabase();

        // Another person (not this one) already has this name?
        try (Cursor c = db.query(TABLE_PEOPLE, new String[]{COL_PERSON_ID},
                COL_PERSON_NAME + " = ? COLLATE NOCASE AND " + COL_PERSON_ID + " != ?",
                new String[]{clean, String.valueOf(personId)}, null, null, null)) {
            if (c.moveToFirst()) return RENAME_DUPLICATE;
        }

        ContentValues values = new ContentValues();
        values.put(COL_PERSON_NAME, clean);
        try {
            int rows = db.update(TABLE_PEOPLE, values, COL_PERSON_ID + " = ?",
                    new String[]{String.valueOf(personId)});
            return rows == 1 ? RENAME_OK : RENAME_FAILED;
        } catch (SQLiteException e) {
            return RENAME_FAILED;
        }
    }

    /**
     * Archives (hides) or restores a person. History is never touched.
     * Archiving is only allowed when the balance is 0.
     */
    public boolean setArchived(long personId, boolean archived) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Long balance = readBalance(db, personId);
            if (balance == null) return false;
            if (archived && balance != 0) return false;

            ContentValues values = new ContentValues();
            values.put(COL_IS_ARCHIVED, archived ? 1 : 0);
            int rows = db.update(TABLE_PEOPLE, values, COL_PERSON_ID + " = ?",
                    new String[]{String.valueOf(personId)});
            if (rows != 1) return false;

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** Hidden (archived) people, sorted by name. */
    public List<Person> getArchivedPeople() {
        List<Person> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PEOPLE, null, COL_IS_ARCHIVED + " = 1", null, null, null,
                COL_PERSON_NAME + " COLLATE NOCASE ASC")) {
            while (c.moveToNext()) {
                list.add(personFromCursor(c));
            }
        }
        return list;
    }

    /** PERMANENTLY deletes the person and all their transactions, all-or-nothing. */
    public boolean deletePerson(long personId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = {String.valueOf(personId)};
            db.delete(TABLE_TRANSACTIONS, COL_PERSON_ID + " = ?", args); // child rows first
            int rows = db.delete(TABLE_PEOPLE, COL_PERSON_ID + " = ?", args);
            if (rows != 1) return false;

            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }
    /** People I owe (balance below 0), biggest debt first. */
    public List<Person> getPeopleIOwe() {
        List<Person> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE_PEOPLE, null, COL_CURRENT_BALANCE + " < 0",
                null, null, null, COL_CURRENT_BALANCE + " ASC")) {
            while (c.moveToNext()) {
                list.add(personFromCursor(c));
            }
        }
        return list;
    }

    // =====================================================================
    //  MY EXPENSES (separate from people and transactions)
    // =====================================================================

    private static final String TABLE_EXPENSES = "expenses";

    private void createExpensesTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EXPENSES + " ("
                + "expense_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "title TEXT NOT NULL, "
                + "amount INTEGER NOT NULL, "
                + "expense_date INTEGER NOT NULL, "
                + "description TEXT, "
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_expense_date ON "
                + TABLE_EXPENSES + "(expense_date)");
    }

    /** Returns the new id, or -1 if the input is invalid (empty title, amount <= 0, future date). */
    public long addExpense(String title, long amountPaise, long dateMillis, String description) {
        if (title == null || title.trim().isEmpty() || amountPaise <= 0) return -1;
        if (dateMillis > System.currentTimeMillis() + 60_000L) return -1;

        ContentValues values = new ContentValues();
        values.put("title", title.trim());
        values.put("amount", amountPaise);
        values.put("expense_date", dateMillis);
        values.put("description", description == null ? "" : description.trim());
        values.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert(TABLE_EXPENSES, null, values);
    }

    public boolean updateExpense(long id, String title, long amountPaise,
                                 long dateMillis, String description) {
        if (title == null || title.trim().isEmpty() || amountPaise <= 0) return false;
        if (dateMillis > System.currentTimeMillis() + 60_000L) return false;

        ContentValues values = new ContentValues();
        values.put("title", title.trim());
        values.put("amount", amountPaise);
        values.put("expense_date", dateMillis);
        values.put("description", description == null ? "" : description.trim());
        return getWritableDatabase().update(TABLE_EXPENSES, values, "expense_id = ?",
                new String[]{String.valueOf(id)}) == 1;
    }

    /** Deletes ONE expense. */
    public boolean deleteExpense(long id) {
        return getWritableDatabase().delete(TABLE_EXPENSES, "expense_id = ?",
                new String[]{String.valueOf(id)}) == 1;
    }

    public Expense getExpense(long id) {
        try (Cursor c = getReadableDatabase().query(TABLE_EXPENSES, null, "expense_id = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c.moveToFirst() ? expenseFromCursor(c) : null;
        }
    }

    /** Expenses with fromMillis <= date < toMillis, newest first. */
    public List<Expense> getExpenses(long fromMillis, long toMillis) {
        List<Expense> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_EXPENSES, null,
                "expense_date >= ? AND expense_date < ?",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis)},
                null, null, "expense_date DESC, expense_id DESC")) {
            while (c.moveToNext()) {
                list.add(expenseFromCursor(c));
            }
        }
        return list;
    }

    /** Total (in paise) of expenses with fromMillis <= date < toMillis. */
    public long sumExpenses(long fromMillis, long toMillis) {
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount), 0) FROM " + TABLE_EXPENSES
                        + " WHERE expense_date >= ? AND expense_date < ?",
                new String[]{String.valueOf(fromMillis), String.valueOf(toMillis)})) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    private Expense expenseFromCursor(Cursor c) {
        return new Expense(
                c.getLong(c.getColumnIndexOrThrow("expense_id")),
                c.getString(c.getColumnIndexOrThrow("title")),
                c.getLong(c.getColumnIndexOrThrow("amount")),
                c.getLong(c.getColumnIndexOrThrow("expense_date")),
                c.getString(c.getColumnIndexOrThrow("description")));
    }

    /** One row per month (newest first) that has expenses dated before beforeMillis. */
    public List<MonthSummary> getMonthSummariesBefore(long beforeMillis) {
        List<MonthSummary> list = new ArrayList<>();
        String ym = "strftime('%Y-%m', expense_date / 1000, 'unixepoch', 'localtime')";
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT " + ym + " AS ym, SUM(amount), COUNT(*) FROM " + TABLE_EXPENSES
                        + " WHERE expense_date < ? GROUP BY ym ORDER BY ym DESC",
                new String[]{String.valueOf(beforeMillis)})) {
            while (c.moveToNext()) {
                String[] parts = c.getString(0).split("-");
                list.add(new MonthSummary(Integer.parseInt(parts[0]),
                        Integer.parseInt(parts[1]), c.getLong(1), c.getInt(2)));
            }
        }
        return list;
    }
    // =====================================================================
    //  MONTHLY BUDGET (one row per month; later months inherit the latest earlier one)
    // =====================================================================

    private static final String TABLE_BUDGETS = "expense_budgets";

    private void createBudgetsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BUDGETS + " ("
                + "month_key TEXT PRIMARY KEY, "
                + "amount INTEGER NOT NULL)");
    }

    /** monthKey looks like "2026-11". Returns paise, or 0 if no budget was ever set. */
    public long getBudgetForMonth(String monthKey) {
        try (Cursor c = getReadableDatabase().query(TABLE_BUDGETS, new String[]{"amount"},
                "month_key <= ?", new String[]{monthKey}, null, null, "month_key DESC", "1")) {
            return c.moveToFirst() ? c.getLong(0) : 0;
        }
    }

    /** Sets the budget for this month (and the months after it, until changed again). */
    public boolean setBudget(String monthKey, long amountPaise) {
        if (amountPaise <= 0) return false;
        ContentValues values = new ContentValues();
        values.put("month_key", monthKey);
        values.put("amount", amountPaise);
        return getWritableDatabase().insertWithOnConflict(TABLE_BUDGETS, null, values,
                SQLiteDatabase.CONFLICT_REPLACE) != -1;
    }
}
