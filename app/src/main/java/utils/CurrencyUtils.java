package utils;



import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Converts between what the user types (rupees, like "50.25")
 * and what we store (paise, like 5025).
 */
public class CurrencyUtils {

    public static final String SYMBOL = "\u20B9"; // the rupee sign

    // Largest amount allowed in one transaction: 99,99,99,999 rupees
    private static final long MAX_PAISE = 999_999_999_00L;

    /**
     * Turns user input into paise.
     * Returns -1 if the text is empty, not a number, zero, negative, or too large.
     */
    public static long parsePaise(String text) {
        if (text == null) return -1;
        String clean = text.trim().replace(",", "");
        if (clean.isEmpty()) return -1;
        try {
            BigDecimal rupees = new BigDecimal(clean);
            BigDecimal paise = rupees.setScale(2, RoundingMode.HALF_UP).movePointRight(2);
            if (paise.signum() <= 0) return -1;
            if (paise.compareTo(BigDecimal.valueOf(MAX_PAISE)) > 0) return -1;
            return paise.longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    /** Formats paise as rupees WITHOUT a sign: 5025 -> "₹50.25", 5000 -> "₹50". */
    public static String format(long paise) {
        long abs = Math.abs(paise);
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        nf.setMinimumFractionDigits(abs % 100 == 0 ? 0 : 2);
        nf.setMaximumFractionDigits(2);
        return SYMBOL + nf.format(BigDecimal.valueOf(abs, 2));
    }

    /** Formats with a sign: +₹50, -₹50, or ₹0. */
    public static String formatSigned(long paise) {
        if (paise > 0) return "+" + format(paise);
        if (paise < 0) return "-" + format(paise);
        return format(0);
    }
}
