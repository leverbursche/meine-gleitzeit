package de.leverbursche.meinegleitzeitapp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    static final String CHANNEL_ID = "dienstende_kanal";
    static final int NOTIFICATION_ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.alarm_kanal_name),
                    NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        String jobName = intent != null ? intent.getStringExtra("job_name") : null;
        int jobIndex = intent != null ? intent.getIntExtra("job_index", NOTIFICATION_ID) : NOTIFICATION_ID;
        String title = context.getString(R.string.alarm_benachrichtigung_titel);
        if (jobName != null && !jobName.isEmpty()) {
            title = jobName + ": " + title;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_alarm_on)
                .setContentTitle(title)
                .setContentText(context.getString(R.string.alarm_benachrichtigung_text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        nm.notify(jobIndex, builder.build());

        PendingIntent pi = PendingIntent.getBroadcast(context, jobIndex, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) pi.cancel();
    }
}
