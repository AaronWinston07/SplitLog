package models;

/** One personal expense. amount is in PAISE. */
public class Expense {

    private final long id;
    private final String title;
    private final long amount;
    private final long date;
    private final String description;

    public Expense(long id, String title, long amount, long date, String description) {
        this.id = id;
        this.title = title;
        this.amount = amount;
        this.date = date;
        this.description = description;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public long getAmount() { return amount; }
    public long getDate() { return date; }
    public String getDescription() { return description; }
}