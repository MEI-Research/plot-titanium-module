package mei.ble;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.appcelerator.titanium.TiApplication;

import java.time.Instant;

import mei.EmaLog;

/**
 * Schedules updating Encounters
 *
 * @see Encounter#updateAllForPassedTime(Instant)
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class EncounterUpdateReceiver extends BroadcastReceiver  {
    private static final String TAG = EncounterUpdateReceiver.class.getName();

    private static final Object LOCK = new Object();
    private static Instant nextUpdateAt = null;

    public static void scheduleNextUpdateBefore(Instant scheduleTime) {
        synchronized (LOCK) {
            Log.d(TAG, "scheduleNextUpdateBefore:" +
                    "scheduleTime=" + scheduleTime +
                    ", nextUpdateAt" + nextUpdateAt);
            if (nextUpdateAt == null || scheduleTime.isBefore(nextUpdateAt)) {
                nextUpdateAt = scheduleTime;
                Context context =  TiApplication.getInstance().getApplicationContext();
                Intent updateIntent = new Intent(context, EncounterUpdateReceiver.class);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        context, 0, updateIntent, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager am = (AlarmManager)context.getSystemService(TiApplication.ALARM_SERVICE);
                Log.d(TAG, "DEBUG>>>>> scheduliing for " + nextUpdateAt);
                am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, nextUpdateAt.getEpochSecond(), pendingIntent);

            }
        }
    }


    @Override
    public void onReceive(Context context, Intent intent) {
        final Instant now = Instant.now();
        Instant oldUpdateAt;
        synchronized(LOCK) {
            oldUpdateAt = nextUpdateAt;
            nextUpdateAt = null;
        }
        Log.d(TAG, "onReceive at " + now);
        if (oldUpdateAt == null) {
            Log.e(TAG, "Did not expect to be called!");
            return;
        }
        if (oldUpdateAt.isAfter(now)) {
            Log.w(TAG, "onReceive called to soon! expected at=" + oldUpdateAt);
            scheduleNextUpdateBefore(oldUpdateAt);
            return;
        }
        EmaLog.info(TAG, "onReceive: wakeup", "updateAt", oldUpdateAt);
        Encounter.updateAllForPassedTime(now);
    }
}
