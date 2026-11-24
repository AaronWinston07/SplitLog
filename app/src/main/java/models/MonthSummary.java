package models;

import java.util.Calendar;

/** Total and count of expenses for one calendar month. total is in PAISE. */
public class MonthSummary {

    private final int year;
    private final int month; // 1 to 12
    private final long total;
    private final int count;

    public MonthSummary(int year, int month, long total, int count) {
        this.year = year;
        this.month = month;
        this.total = total;
        this.count = count;
    }

    public int getYear() { return year; }
    public int getMonth() { return month; }
    public long getTotal() { return total; }
    public int getCount() { return count; }

    /** Midnight on the 1st of this month. */
    public long startMillis() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, month - 1, 1, 0, 0, 0);
        return c.getTimeInMillis();
    }
}