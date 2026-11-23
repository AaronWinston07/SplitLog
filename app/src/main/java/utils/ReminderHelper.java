package utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.hisab.MainActivity;
import com.hisab.R;

import database.DatabaseHelper;
import models.Person;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Schedules and shows the "you owe these people" reminders. */
public class ReminderHelper {

    public static final String CHANNEL_ID = "payment_reminders";
    private static final String WORK_NAME = "payment_reminder_work";
    private static final String PREFS = "splitlog_prefs";
    private static final String KEY_ENABLED = "reminders_enabled";
    private static final int NOTIFICATION_ID = 1001;

    // ---------------- On / off ----------------
    public static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) schedule(context);
        else WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- Scheduling ----------------
    /** Plans the next reminder. Safe to call many times. */
    public static void schedule(Context context) {
        if (!isEnabled(context)) return;
        long now = System.currentTimeMillis();
        long delay = Math.max(0, nextTriggerMillis(now) - now);

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    /** Next of: 1st at 9:30 AM, or (last day - 3) at 9:30 PM. */
    public static long nextTriggerMillis(long nowMillis) {
        long best = Long.MAX_VALUE;
        for (int offset = 0; offset <= 2; offset++) {
            Calendar first = Calendar.getInstance();
            first.setTimeInMillis(nowMillis);
            first.set(Calendar.DAY_OF_MONTH, 1);
            first.add(Calendar.MONTH, offset);
            first.set(Calendar.HOUR_OF_DAY, 9);
            first.set(Calendar.MINUTE, 30);
            first.set(Calendar.SECOND, 0);
            first.set(Calendar.MILLISECOND, 0);

            Calendar beforeEnd = (Calendar) first.clone();
            int lastDay = first.getActualMaximum(Calendar.DAY_OF_MONTH);
            beforeEnd.set(Calendar.DAY_OF_MONTH, lastDay - 3);
            beforeEnd.set(Calendar.HOUR_OF_DAY, 21);
            beforeEnd.set(Calendar.MINUTE, 30);

            long a = first.getTimeInMillis();
            long b = beforeEnd.getTimeInMillis();
            if (a > nowMillis && a < best) best = a;
            if (b > nowMillis && b < best) best = b;
        }
        return best;
    }

    // ---------------- Notification ----------------
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Shows the notification if I owe anyone.
     * Returns false when there is nothing to show or the permission is missing.
     */
    @SuppressLint("MissingPermission")
    public static boolean showOweNotification(Context context) {
        List<Person> owed = DatabaseHelper.getInstance(context).getPeopleIOwe();
        if (owed.isEmpty() || !hasPermission(context)) return false;

        createChannel(context);

        long total = 0;
        StringBuilder lines = new StringBuilder();
        for (Person p : owed) {
            long debt = Math.abs(p.getBalance());
            total += debt;
            if (lines.length() > 0) lines.append("\n");
            lines.append(p.getName()).append(": ").append(CurrencyUtils.format(debt));
        }

        String summary = context.getString(R.string.notif_summary,
                owed.size(), CurrencyUtils.format(total));

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_title))
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary + "\n\n" + lines))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
        return true;
    }
}