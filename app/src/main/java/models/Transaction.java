package models;


/**
 * One row of the TRANSACTIONS table (plus the person's name, joined in).
 * All money values are in PAISE.
 */
public class Transaction {

    // The three kinds of history entries
    public static final String TYPE_PLUS = "PLUS";
    public static final String TYPE_MINUS = "MINUS";
    public static final String TYPE_RESET = "RESET";

    private final long id;
    private final long personId;
    private final String personName;
    private final String type;
    private final long amount;
    private final long previousBalance;
    private final long updatedBalance;
    private final String description;
    private final long date;

    public Transaction(long id, long personId, String personName, String type,
                       long amount, long previousBalance, long updatedBalance,
                       String description, long date) {
        this.id = id;
        this.personId = personId;
        this.personName = personName;
        this.type = type;
        this.amount = amount;
        this.previousBalance = previousBalance;
        this.updatedBalance = updatedBalance;
        this.description = description;
        this.date = date;
    }

    public long getId() { return id; }
    public long getPersonId() { return personId; }
    public String getPersonName() { return personName; }
    public String getType() { return type; }
    public long getAmount() { return amount; }
    public long getPreviousBalance() { return previousBalance; }
    public long getUpdatedBalance() { return updatedBalance; }
    public String getDescription() { return description; }
    public long getDate() { return date; }
}
