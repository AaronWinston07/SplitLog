package utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Turns a timestamp (milliseconds) into readable text like "13 Nov 2026, 03:45 PM". */
public class DateUtils {

    public static String format(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
}