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

import mei.ble.GeotriggerAdapter;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;

import androidx.core.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.media.RingtoneManager;
import android.app.PendingIntent;
import android.net.Uri;
import android.content.Intent;
import android.content.Context;

import android.util.Log;
import ti.modules.titanium.android.TiBroadcastReceiver;
import android.content.BroadcastReceiver;
import android.app.AlarmManager;
import android.location.*;

import android.R;

import java.time.Instant;
import java.util.List;

import org.json.*;


public class GeotriggerHandlerService extends BroadcastReceiver {

    private static final String TAG = "GeotriggerHandlerService";

    // Ti App Properties
    public static final String REDUCE_BLETRIGGER_FREQUENCY = "plot.reduceBLETriggerFrequency";
    public static final String SURVEY_TRIGGERED = "plot.surveyTriggered";
    public static final String DWELL_MINUTES = "plot.projects_dwell_minutes";

    public void onReceive(Context context, Intent intent) {
        Instant eventTime = Instant.now();
        Log.d(TAG, "Geofence triggered at " + eventTime.toString());

        Encounter.updateAllForPassedTime(eventTime);

        if (!GeotriggerHandlerUtil.isGeotriggerHandlerBroadcastReceiverIntent(context, intent))
            return;

        GeotriggerHandlerUtil.Batch batch = GeotriggerHandlerUtil.getBatch(intent, context);
        if (batch == null)
            return;

        List<Geotrigger> triggers = batch.getGeotriggers();
        for (Geotrigger geotrigger: triggers) {
            Log.d(TAG,
                    "DEBUG### handle geotrigger:" +
                            " Id="+                geotrigger.getId() +
                            ", Name="+             geotrigger.getName() +
//                            ", Data="+             geotrigger.getData() +
//                            ", DwellingMinutes="+  geotrigger.getDwellingMinutes() +
//                            ", GeofenceLatitude="+ geotrigger.getGeofenceLatitude() +
                            ", GeofenceLongitude="+geotrigger.getGeofenceLongitude() +
                            ", InternalId="+       geotrigger.getInternalId() +
                            ", MatchId="+          geotrigger.getMatchId() +
                            ", MatchRange="+       geotrigger.getMatchRange() +
                            ", RegionId="+         geotrigger.getRegionId() +
                            ", RegionType="+       geotrigger.getRegionType() +
                            ", ShortId="+          geotrigger.getShortId() +
                            ", Trigger="+          geotrigger.getTrigger() +
                            ", TriggerProperties="+geotrigger.getTriggerProperties());
            GeotriggerAdapter geotrigAdapt = new GeotriggerAdapter(geotrigger);
            if (geotrigAdapt.isBeaconEvent()) {
                Encounter.handleGeotrigger(geotrigAdapt, eventTime);
            }
            // TODO: handle geofence triggers
        }

        batch.markGeotriggersHandled(triggers);
        Log.d(TAG, "DEBUG######### markGeotriggersHandled happened:");

        // Skip geofence stuff
        return;
    /*

        // limit how often these can fire. which improves preformance of EMA due to less data to process.
            Boolean enteredFrequencyAllowed = reduceBLETriggerFrequency();

        // We do not need to get location anymore
//        Location location;
//        try {
//            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
//            location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
//            if (location != null) {
//                Log.d(TAG, "Location lat:" + location.getLatitude());
//                Log.d(TAG, "Location lon:" + location.getLongitude());
//                Log.d(TAG, "-- end  -- ");
//            } else {
//                Log.d(TAG, "Location Null");
//            }
//
//        } catch (SecurityException e) {
//            Log.e(TAG, "security exception, is the LOCATION permission allowed?");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        for(int i = 0; i < triggers.size(); i++){
            Geotrigger t = triggers.get(i);

            Log.d("Handled Geotrigger", t.toString());


            // All of the properties of the geotrigger (aka Enter Event)
            Log.d(TAG,
                  "handle geotrigger:" +
                  " Id="+               t.getId() +
                  ", Name="+             t.getName() +
                  ", Data="+             t.getData() +
                  ", DwellingMinutes="+  t.getDwellingMinutes() +
                  ", GeofenceLatitude="+ t.getGeofenceLatitude() +
                  ", GeofenceLongitude="+t.getGeofenceLongitude() +
                  ", InternalId="+       t.getInternalId() +
                  ", MatchId="+          t.getMatchId() +
                  ", MatchRange="+       t.getMatchRange() +
                  ", RegionId="+         t.getRegionId() +
                  ", RegionType="+       t.getRegionType() +
                  ", ShortId="+          t.getShortId() +
                  ", Trigger="+          t.getTrigger() +
                  ", TriggerProperties="+t.getTriggerProperties());


            // enabling Dwell again. uncomment to disable Dwell feature.
            // if("exit".equals(t.getTrigger())){
            //     // disabling exit triggers for recent testing.
            //     Log.d(TAG, " exit trigger, not attempting to notify - exits disabled");
            //     continue;
            // }

            // if an exit trigger is being checked, we should allow the processing of that.
            // if we're finally processing after a reasonable delay an entry trigger, allow that.
            // disallow too frequent of processing of enter triggers.
            if(enteredFrequencyAllowed == true || "exit".equals(t.getTrigger())){
                Long tsLong = System.currentTimeMillis()/1000l;
                String ts = tsLong.toString();
                String geofenceName = t.getName();

                if(EMAFilterRegion.regionAllowed(geofenceName)){
                    // for healthkick, we have to test regions and ensure consistent 'generic' naming of a region.
                    if(geofenceName.indexOf("generic,") == 0){
                        geofenceName = "generic";
                    }

                    sendEventToEMA(ts, geofenceName, t.getId(), t.getTrigger(), t.getGeofenceLatitude(), t.getGeofenceLongitude());
                    sendNotification(t.getTrigger());
                }
            }
        }


        batch.markGeotriggersHandled(triggers);
      */
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder
                (context, channel_ID)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setGroupSummary(true)
                .setGroup(groupName)
                .setStyle( new NotificationCompat.InboxStyle())
                .setSmallIcon(R.drawable.btn_star)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setShowWhen(true)
                .setWhen(scheduleTime);

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        builder.setSound(alarmSound);

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        launchIntent.setPackage(null);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        builder.setContentIntent(PendingIntent.getActivity(context, 0, launchIntent, 0));

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
                PendingIntent pendingIntent = PendingIntent.getBroadcast(context, notificationId, notificationIntent, 0);

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
        PendingIntent pendingIntentToCancel = PendingIntent.getBroadcast(context, notificationId, dummyNotificationIntent, 0);
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
