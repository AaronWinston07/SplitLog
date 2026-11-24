package utils;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hisab.ExpensesActivity;
import com.hisab.R;

import database.DatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Sends the 75% / 90% / 100% budget alerts, each at most once per month. */
public class BudgetAlertHelper {

    public static final String CHANNEL_ID = "budget_alerts";
    private static final int NOTIFICATION_ID = 2001;
    private static final String PREFS = "splitlog_prefs";

    /** Call after an expense is saved. expenseDateMillis is that expense's date. */
    public static void check(Context context, long expenseDateMillis) {
        SimpleDateFormat keyFormat = new SimpleDateFormat("yyyy-MM", Locale.US);
        String currentKey = keyFormat.format(new Date());

        // Only expenses in the current month can trigger this month's alerts
        if (!currentKey.equals(keyFormat.format(new Date(expenseDateMillis)))) return;

        DatabaseHelper db = DatabaseHelper.getInstance(context);
        long budget = db.getBudgetForMonth(currentKey);
        if (budget <= 0) return;

        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        c.add(Calendar.MONTH, 1);
        long end = c.getTimeInMillis();

        long spent = db.sumExpenses(start, end);
        int level = levelFor(spent, budget);
        if (level == 0) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String prefKey = "budget_alert_" + currentKey;
        if (level <= prefs.getInt(prefKey, 0)) return; // already notified at this level or higher

        if (!ReminderHelper.hasPermission(context)) return; // try again next time

        if (show(context, level, spent, budget)) {
            prefs.edit().putInt(prefKey, level).apply();
        }
    }

    private static int levelFor(long spent, long budget) {
        long percent = spent * 100 / budget;
        if (percent >= 100) return 100;
        if (percent >= 90) return 90;
        if (percent >= 75) return 75;
        return 0;
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.budget_alert_channel),
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("MissingPermission")
    private static boolean show(Context context, int level, long spent, long budget) {
        createChannel(context);

        String message;
        if (level == 75) message = context.getString(R.string.budget_alert_75, 75);
        else if (level == 90) message = context.getString(R.string.budget_alert_90);
        else message = context.getString(R.string.budget_alert_100);

        String amounts = context.getString(R.string.budget_alert_amount,
                CurrencyUtils.format(spent), CurrencyUtils.format(budget));

        Intent open = new Intent(context, ExpensesActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.budget_alert_title))
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message + "\n" + amounts))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        return true;
    }
}