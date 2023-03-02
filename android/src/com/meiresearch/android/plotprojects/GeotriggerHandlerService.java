/**
 * Copyright 2016 Floating Market B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.meiresearch.android.plotprojects;

import mei.ble.Encounter;
import com.plotprojects.retail.android.GeotriggerHandlerUtil;
import com.plotprojects.retail.android.Geotrigger;

import meipp.EmaLog;
import mei.ble.EncountersApi;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.app.PendingIntent;
import android.net.Uri;
import android.content.Intent;
import android.content.Context;

import android.os.Build;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.app.AlarmManager;

import android.R;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

import org.appcelerator.titanium.util.TiRHelper;
import org.json.*;

@RequiresApi(api = Build.VERSION_CODES.O)
public class GeotriggerHandlerService extends BroadcastReceiver {

    private static final String TAG = "GeotriggerHandlerSvc";

    // Ti App Properties
    public static final String REDUCE_BLETRIGGER_FREQUENCY = "plot.reduceBLETriggerFrequency";
    public static final String SURVEY_TRIGGERED = "plot.surveyTriggered";
    public static final String DWELL_MINUTES = "plot.projects_dwell_minutes";

    /**
     * Start listing after reboot
     * @param context
     */
    public static void onBoot(Context context) {
        EmaLog.info(TAG, "onBoot starting");
        Intent intent = new Intent(context, GeotriggerHandlerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        // TODO: initialize PP again?
    }

    public void onReceive(Context context, Intent intent) {
        Instant eventTime = Instant.now();
        Log.d(TAG, "Geofence triggered at " + eventTime.toString());
        Log.w(TAG, "MGM>>>> Geofence triggered at " + eventTime.toString());

        // If EXIT geotriggers are not reliable, this will need be called on a schedule
        // if an EXIT trigger needs to fire.  Currently not the case.
        Encounter.updateEncounters(eventTime, null);

        if (!GeotriggerHandlerUtil.isGeotriggerHandlerBroadcastReceiverIntent(context, intent))
            return;

        GeotriggerHandlerUtil.Batch batch = GeotriggerHandlerUtil.getBatch(intent, context);
        if (batch == null)
            return;

        List<Geotrigger> triggers = batch.getGeotriggers();
        if (ComMeiresearchAndroidPlotprojectsModule.isEnabled) {
            for (Geotrigger geotrigger: triggers) {

                logGeotrigger(geotrigger);
                // logGeotriggerToEma(geotrigger);

                if (Encounter.updateEncounters(eventTime, geotrigger)) {
                    continue;
                }
                // TODO: we can eventually allow both kinds of campaigns
                // handleGeotrigger(geotrigger);
            }
        }
        batch.markGeotriggersHandled(triggers);
    }

    private void handleGeotrigger(Geotrigger geotrigger) {
        // limit how often these can fire. which improves preformance of EMA due to less data to process.
        Boolean enteredFrequencyAllowed = reduceBLETriggerFrequency();

        // enabling Dwell again. uncomment to disable Dwell feature.
        // if("exit".equals(t.getTrigger())){
        //     // disabling exit triggers for recent testing.
        //     Log.d(TAG, " exit trigger, not attempting to notify - exits disabled");
        //     continue;
        // }

        // if an exit trigger is being checked, we should allow the processing of that.
        // if we're finally processing after a reasonable delay an entry trigger, allow that.
        // disallow too frequent of processing of enter triggers.
        if(enteredFrequencyAllowed == true || "exit".equals(geotrigger.getTrigger())){
            Long tsLong = System.currentTimeMillis()/1000l;
            String ts = tsLong.toString();
            String geofenceName = geotrigger.getName();

            if(EMAFilterRegion.regionAllowed(geofenceName)){
                // for healthkick, we have to test regions and ensure consistent 'generic' naming of a region.
                if(geofenceName.indexOf("generic,") == 0){
                    geofenceName = "generic";
                }
                EmaLog.info(TAG, "geoFENCE trigger");
                sendEventToEMA(ts, geofenceName, geotrigger.getId(), geotrigger.getTrigger(), geotrigger.getGeofenceLatitude(), geotrigger.getGeofenceLongitude());
                sendNotification(geotrigger.getTrigger());
            }
        }
    }

    private void logGeotrigger(Geotrigger geotrigger) {
        // All of the properties of the geotrigger (aka Enter Event)
        EmaLog.info(TAG, "got geotrigger",
                "trigger", geotrigger.getTrigger(),
                "name", geotrigger.getName(),
                "matchPayload", geotrigger.getMatchPayload());
        Log.d(TAG,
              "handle geotrigger:" +
              " Id="+               geotrigger.getId() +
              ", Name="+             geotrigger.getName() +
              ", Data="+             geotrigger.getData() +
              ", DwellingMinutes="+  geotrigger.getDwellingMinutes() +
              ", GeofenceLatitude="+ geotrigger.getGeofenceLatitude() +
              ", GeofenceLongitude="+ geotrigger.getGeofenceLongitude() +
              ", InternalId="+       geotrigger.getInternalId() +
              ", MatchId="+          geotrigger.getMatchId() +
              ", MatchRange="+       geotrigger.getMatchRange() +
              ", matchPayload=" + geotrigger.getMatchPayload() +
              ", deliver delay (ms)=" + (geotrigger.getMatchPayload()) +
                      // TODO: add deliver delay (now - matchPayload.triggerTimeInMillis) in millis
              ", RegionId="+         geotrigger.getRegionId() +
              ", RegionType="+       geotrigger.getRegionType() +
              ", ShortId="+          geotrigger.getShortId() +
              ", Trigger="+          geotrigger.getTrigger() +
              ", TriggerProperties="+ geotrigger.getTriggerProperties());
    }

    private void logGeotriggerToEma(Geotrigger geotrigger) {
        HashMap<String, Object> more_data = new HashMap<String, Object>();
        more_data.put("id", geotrigger.getId());
        more_data.put("name", geotrigger.getName());
        more_data.put("data", geotrigger.getData());
        more_data.put("dwellingMinutes", geotrigger.getDwellingMinutes());
        more_data.put("geofenceLatitude", geotrigger.getGeofenceLatitude());
        more_data.put("geofenceLongitude", geotrigger.getGeofenceLongitude());
        more_data.put("internalId", geotrigger.getInternalId());
        more_data.put("matchId", geotrigger.getMatchId());
        more_data.put("matchRange", geotrigger.getMatchRange());
        more_data.put("regionId", geotrigger.getRegionId());
        more_data.put("regionType", geotrigger.getRegionType());
        more_data.put("shortId", geotrigger.getShortId());
        more_data.put("trigger", geotrigger.getTrigger());
        more_data.put("triggerProperties", geotrigger.getTriggerProperties());
        more_data.put("geotrig.toString", geotrigger.toString());
        EncountersApi.msgQueue.logToEma("DEBUG> geotrigger details", more_data);
//        HashMap<String, Object> msg = new HashMap<>();
//        msg.put("event_type", "message");
//        msg.put("timestamp", EncountersApi.instance.encodeTimestamp(Instant.now()));
//        msg.put("message", message);
//        if (more_data != null) msg.put("more_data", more_data);
//        EncountersApi.instance.sendEmaEvent(msg);
    }

    // return true if this batch of triggers should be iterated over and worked on, based on time filtering.
    // otherwise return false if the last time this function was called was too soon.
    private static Boolean reduceBLETriggerFrequency(){
        Long thresholdMillis = 2 * 60 * 1000l;
        Long currentTimeMillis = System.currentTimeMillis();

        try{
            Long lastTimeMillis = Long.valueOf(EMADataAccess.getStringProperty(REDUCE_BLETRIGGER_FREQUENCY));

            if(lastTimeMillis != null && (currentTimeMillis - lastTimeMillis) > thresholdMillis){
                EMADataAccess.saveStringProperty(REDUCE_BLETRIGGER_FREQUENCY, String.valueOf(currentTimeMillis));

                // Log.w(TAG, "vance not null and over threshold");
                return true;
            }

            // Log.w(TAG, "vance null or under threshold");
            return false;

        }catch (NumberFormatException e){
            // Log.w(TAG, "vance exception thrown");
            // no property set. set it up! this means it's a first time event and as such we should return true.
            EMADataAccess.saveStringProperty(REDUCE_BLETRIGGER_FREQUENCY, String.valueOf(currentTimeMillis));
            return true;
        }
    }

    /**
     * Queue an event for the javascript engine to handle.
     * Uses a Titanium App property for persistence.  Javascript will poll the property
     *
     * @param timestamp
     * @param name
     * @param id
     * @param direction
     * @param latitude
     * @param longitude
     */
     public static void sendEventToEMA(String timestamp, String name, String id, String direction, Double latitude, Double longitude){
        TiProperties props = TiApplication.getInstance().getAppProperties();

        try{
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("detection_timestamp", timestamp);
            jsonObj.put("geotrigger_id", id);
            jsonObj.put("geotrigger_name", name);
            jsonObj.put("geotrigger_direction", direction);
            jsonObj.put("latitude", latitude);
            jsonObj.put("longitude", longitude);

            EMADataAccess.appendToJsonArray(SURVEY_TRIGGERED, jsonObj);
        } catch(JSONException e){
            Log.e(TAG, "error getting notification details");
            e.printStackTrace();
        }
    }

    // TODO: should use ema.service as a library to do this
     public static void sendNotification(String direction){
        Log.i(TAG, "sendNotification: " + direction);
        String notificationTitle = EMADataAccess.getStringProperty("plot.notificationTitle." + direction);
        // TODO: don't need dwell for BLE
        int dwell_time_minutes = 0; //Integer.parseInt(EMADataAccess.getStringProperty(DWELL_MINUTES));

        if("".equals(notificationTitle)){
            Log.e(TAG, "plot.notificationTitle." + direction + " is empty! not sending an empty notification");
            return;
        }

        String notificationText = EMADataAccess.getStringProperty("plot.notificationText." + direction);

        // Entry notifications are id 201, exit are 202
        int notificationId = 200001 + (direction == "exit" ? 1 : 0);

        // for latest testing this might be a bad thing to have - removing for now.
        // cancel the id 201 if on entry otherwise, on exit cancel 202
        //cancelNotification(notificationId - (direction != "exit" ? 1 : 0));

        String notifyChannelName = "EMA Plot Location";
        String notifyChannelDesc = "Location based notifications";
        String groupName = "ema_plot_loc";
        String channel_ID = EMANotificationBroadcastReceiver.PRIMARY_CHANNEL_ID;

        // for delaying the notification, set the time in the future - disabled currently
        long scheduleTime = System.currentTimeMillis() + dwell_time_minutes * 60 * 1000;
        Context context =  TiApplication.getInstance().getApplicationContext();

        //create notification channel
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(channel_ID, notifyChannelName, importance);
        channel.setDescription(notifyChannelDesc);
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        int notiIcon = android.R.drawable.alert_light_frame;
        int notiTranIcon = android.R.drawable.alert_light_frame;
        try {
            notiIcon = TiRHelper.getApplicationResource("drawable.ic_launcherblue");
            notiTranIcon = TiRHelper.getApplicationResource("drawable.ic_launcher");
        }
        catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder
                (context, channel_ID)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setGroupSummary(true)
                .setGroup(groupName)
                .setStyle( new NotificationCompat.InboxStyle())
                .setSmallIcon(notiIcon)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setShowWhen(true)
                .setWhen(scheduleTime);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.setSmallIcon(notiTranIcon);
            builder.setColor(Color.parseColor("#3F51B5"));
        }

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        builder.setSound(alarmSound);

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        launchIntent.setPackage(null);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        builder.setContentIntent(PendingIntent.getActivity(context, 0, launchIntent,
                PendingIntent.FLAG_IMMUTABLE));

        // send the notification immediately? only if Dwell isn't needed.
         Log.d(TAG, "Notify!!!!!");
         try {
            if(dwell_time_minutes == 0){
                notificationManager.notify(notificationId, builder.build());
            } else {

            // Dwell:
            // to delay the notification, this sets up the alarm manager to deliver the notification on a specific time.
                //Creates the notification intent with extras
                Intent notificationIntent = new Intent(context, EMANotificationBroadcastReceiver.class);
                notificationIntent.putExtra(EMANotificationBroadcastReceiver.NOTIFICATION_ID, notificationId);
                notificationIntent.putExtra(EMANotificationBroadcastReceiver.NOTIFICATION, builder.build());

                //Creates the pending intent which includes the notificationIntent
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notificationId, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE);
                Log.d(TAG, context.toString());

                AlarmManager am = (AlarmManager)context.getSystemService(TiApplication.ALARM_SERVICE);
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, scheduleTime, pendingIntent);

            }
         } catch (Exception e) {
             Log.e(TAG, "setting alarm manager for dwell notification for a geotrigger failed.");
             Log.e(TAG, e.toString());
        }
    }

    private static void cancelNotification(int notificationId) {
        Log.i(TAG, "cancelNotification id: " + notificationId);

        Context context = TiApplication.getInstance().getApplicationContext();
        AlarmManager alarmManagerCanceller = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        //Creates a dummy notification intent with extras
        Intent dummyNotificationIntent = new Intent(context, EMANotificationBroadcastReceiver.class);
        dummyNotificationIntent.putExtra(EMANotificationBroadcastReceiver.NOTIFICATION_ID, notificationId);

        //Creates the pending intent which includes the notificationIntent
        PendingIntent pendingIntentToCancel = PendingIntent.getBroadcast(context, notificationId, dummyNotificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        //PendingIntent expireIntentToCancel  = PendingIntent.getBroadcast(context, notificationId + 100, dummyNotificationIntent, 0);

        alarmManagerCanceller.cancel(pendingIntentToCancel);
        //alarmManagerCanceller.cancel(expireIntentToCancel);
    }

    //This is code that can be adapted so that this broadcastreceiver can be managed from titanium.
    //Previously this was a service and not a broadcast receiver, so onStartCommand is irrelevant for instance.
    //I left in here if you desire to extend TiBroadcastReceiver and place the .js code in the platform/android/assets folder instead
//    public GeotriggerHandlerService() {
//        super("plotgeotriggerhandler.js");
//        Log.d(TAG, "GeotriggerHandlerService()");
//    }
//
//    @Override
//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.d(TAG, "onStartCommand()");
//        if (GeotriggerHandlerUtil.isGeotriggerHandlerBroadcastReceiverIntent(this,intent)) {
//            GeotriggerHandlerUtil.Batch batch = GeotriggerHandlerUtil.getBatch(intent, this);
//            if (batch != null) {
//                if (SettingsUtil.isGeotriggerHandlerEnabled()) {
//                    GeotriggerBatches.addBatch(batch, this, startId);
//                    return super.onStartCommand(intent, flags, startId);
//                } else {
//                    batch.markGeotriggersHandled(batch.getGeotriggers());
//                }
//            } else {
//                Log.w(TAG, "Unable to obtain batch with geotriggers from intent");
//            }
//        } else {
//            Log.w(TAG, String.format("Received unexpected intent with action: %s", intent.getAction()));
//        }
//        stopSelf(startId);
//        return START_NOT_STICKY;
//    }
//
//    @Override
//    public void onTaskRemoved(Intent rootIntent) {
//
//    }

}
