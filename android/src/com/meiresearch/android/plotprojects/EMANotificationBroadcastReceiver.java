package com.meiresearch.android.plotprojects;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.Log;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;

import java.util.Calendar;
import java.util.Date;

public class EMANotificationBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "Plot.EMANotificationBR";

    NotificationManager mNotifyManager;

    public static String NOTIFICATION_ID = "notification-id";
    public static String NOTIFICATION = "notification";
    public static final String PRIMARY_CHANNEL_ID =
            "12";

        @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive-Start");
        // Define notification manager object.
        mNotifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Notification channels are only available in OREO and higher.
        // So, add a check on SDK version.
        if (android.os.Build.VERSION.SDK_INT >=
                android.os.Build.VERSION_CODES.O) {

            // Create the NotificationChannel with all the parameters.
            NotificationChannel notificationChannel = new NotificationChannel
                    (PRIMARY_CHANNEL_ID,
                            "Survey Notifications",
                            NotificationManager.IMPORTANCE_HIGH);

            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.enableVibration(true);
            notificationChannel.setDescription
                    ("Notifications from Job Service");

            mNotifyManager.createNotificationChannel(notificationChannel);
        }

        Notification notification = intent.getParcelableExtra(NOTIFICATION);
        int id = intent.getIntExtra(NOTIFICATION_ID, 0);

        // store info to be uploaded to the app_log by Titanium js code.
        // app_log additions: indicate when a notification was shown to the User (actually delivered)
        //EMADataAccess.putNotificationConfirmation(id, Calendar.getInstance().getTimeInMillis());

        mNotifyManager.notify(id, notification);

        Log.i(TAG, "onReceive-End");
    }
}
