package utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** Runs at the scheduled time: shows the reminder, then plans the next one. */
public class ReminderWorker extends Worker {

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (ReminderHelper.isEnabled(context)) {
            ReminderHelper.showOweNotification(context);
            ReminderHelper.schedule(context); // plan the next reminder
        }
        return Result.success();
    }
}