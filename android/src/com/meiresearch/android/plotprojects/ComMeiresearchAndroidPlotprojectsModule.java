/**
 * This file was auto-generated by the Titanium Module SDK helper for Android
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 *
 */
package com.meiresearch.android.plotprojects;

import mei.ble.Encounter;
import mei.ble.EncountersApi;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;

import org.appcelerator.titanium.TiApplication;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.plotprojects.retail.android.NotificationTrigger;
import com.plotprojects.retail.android.FilterableNotification;
import com.plotprojects.retail.android.Geotrigger;
import com.plotprojects.retail.android.OpenUriReceiver;
import com.plotprojects.retail.android.Plot;
import com.plotprojects.retail.android.PlotConfiguration;
import com.plotprojects.retail.android.SentGeotrigger;
import com.plotprojects.retail.android.SentNotification;

import com.plotprojects.retail.android.PlotAddon;
import com.plotprojects.addon.interaction_tracer.InteractionTracerAddon;

import com.meiresearch.android.plotprojects.GeotriggerBatches.GeotriggersAndId;


/**
 * This is the API of the module exposed to javascript.
 *
 * TODO: most of these are not needed. Get rid of them
 */
@RequiresApi(api = Build.VERSION_CODES.O)
@Kroll.module(name="ComMeiresearchAndroidPlotprojects", id="com.meiresearch.android.plotprojects")
public class ComMeiresearchAndroidPlotprojectsModule extends KrollModule {

	// Standard Debugging variables
	private static final String LCAT = "MEIPlotModule";
	private static final boolean DBG = TiConfig.LOGD;

	private static Boolean isEnabled = false;
	private static String version = "0.00.00";
	private static Boolean isGeoTriggerHandlerEnabled = false;

	// You can define constants with @Kroll.constant, for example:
	// @Kroll.constant public static final String EXTERNAL_NAME = value;

	public ComMeiresearchAndroidPlotprojectsModule()
	{
		super();
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		Log.d(LCAT, "onAppCreate");
	}

	// Methods
	@Kroll.method()
	public void start() {
		Encounter.reset();
	}

	@Kroll.method()
	public void stop() {
		// should be called on logout. Don't collect encounters
		// Plot.disable();
		Encounter.reset();
	}

	/**
	 * Called on bye EMA on login and after every sync.
	 */
	@Kroll.method
	public void initPlot() {
		// put module init code that needs to run when the application is created
		TiApplication appContext = TiApplication.getInstance();

		Activity activity = appContext.getCurrentActivity();

		SettingsUtil.setGeotriggerHandlerEnabled(true);
		Plot.init(activity);

		// Interaction
		try {
			PlotAddon.register(InteractionTracerAddon.class, activity);

		}
		catch (Exception ex) {
			Log.e(LCAT, "PlotAddon.register failed, ignoring", ex);
			Encounter.logToEma("TPlotAddon.register failed", null);

		}

		isEnabled = Plot.isEnabled();
		isGeoTriggerHandlerEnabled = SettingsUtil.isGeotriggerHandlerEnabled();
		version = Plot.getVersion();


		Log.d(LCAT, "Plot Version is - " + version);
		Log.d(LCAT, "Is Plot Enabled? - " + isEnabled.toString());
		Log.d(LCAT, "Is GeotriggerHandler Enabled? - " + isGeoTriggerHandlerEnabled.toString());

		//Encounter.logToEma("Test message from initPlot()", null);
	}

	/**
	 * To facilitate client-specific MEI tiModules, all changes should be in a client- or feature-
	 * specific package and vend its API via this TiProxy.
	 * The module maintains a Map<String,String> of custom properties are used by client-specific
	 * extensions to the MEI Plotproject module. They are writeable by EMA, and readable only from
	 * within the module.
	 * This makes applying updates from plotprojects/master branch easier.
	 */
	@Kroll.getProperty
	public EncountersApi getExtensionApi() {
		return EncountersApi.instance;
	}

//	@Kroll.getProperty
//	public EncountersApi getEncounters() {
//		return EncountersApi.instance;
//	}

//	/**
//	 * Retrieves events detected
//	 *
//	 * @return an array of encounter rows (javascript Objects)
//	 */
//	@Kroll.method()
//	//public HashMap<String,Object>[] fetchEvents() {
//	public Object[] fetchEvents() {
//		ArrayList<HashMap<String, Object>> result = new ArrayList<HashMap<String, Object>>();
//		HashMap<String, Object> ev = new HashMap<>();
//		ev.put("event_type", "start_encounter");
//		ev.put("timestamp", new Date());
//		result.add(ev);
//		Log.d(LCAT, "fetchEvents: " + result);
//
//		@SuppressWarnings("unchecked")
//		HashMap<String,Object>[] t =  (HashMap<String,Object>[]) new HashMap<?,?>[0];
//		return result.toArray(t);
//	}

	@Kroll.method
	public void enable() {
		Plot.enable();
	}

	@Kroll.method
	public void disable() {
		Plot.disable();
	}

	@Kroll.method
	public boolean getEnabled() {
		return Plot.isEnabled();
	}


//	@Kroll.getProperty @Kroll.method
//	public String getVersion() {
//		return Plot.getVersion();
//	}

	@Kroll.method
	public void mailDebugLog() {
		Plot.mailDebugLog();
	}

    @Kroll.method
    public void androidCleanupPlotDebugLog(){
        TiApplication appContext = TiApplication.getInstance();
        erasePlotProjectLogFile(appContext.getApplicationInfo().dataDir + "/files");
    }

    private void erasePlotProjectLogFile(String path){

        // TODO: cut the file contents and leave the end of the file + MAX_ALLOWED_PLOT_DEBUG_LOG_SIZE bytes
        int MAX_ALLOWED_PLOT_DEBUG_LOG_SIZE = 1024 * 1024 * 5;

        File f = new File(path);
        File[] files = f.listFiles();
        for (File inFile : files) {
            if (!inFile.isDirectory() && inFile.getName().contains("plot") && inFile.getName().contains(".log")){

                if(inFile.length() > MAX_ALLOWED_PLOT_DEBUG_LOG_SIZE){
                    Log.w(LCAT, "found a large plot debug log, cleaning up!");
                    Log.w(LCAT, inFile.getAbsolutePath());
                    Log.w(LCAT, String.valueOf(inFile.length()));
                    Log.w(LCAT, "end of plot log file");

                    truncateFile(inFile.getName(), MAX_ALLOWED_PLOT_DEBUG_LOG_SIZE+1);
                }
            }
        }
    }

    private void truncateFile(String filename, int trimToSize) {
        // TiApplication appContext = TiApplication.getInstance();

        // try {
        //     OutputStreamWriter outputStreamWriter = new OutputStreamWriter(appContext.openFileOutput(filename, Context.MODE_PRIVATE));
        //     outputStreamWriter.write("");
        //     outputStreamWriter.close();
        // }
        // catch (IOException e) {
        //     Log.e("Exception", "File write failed: " + e.toString());
        // }

        // copy the last n bytes to the beginning of the file, then truncate the rest.
        // this requires less memory than reading the entire file in-memory just to dump it back.
        RandomAccessFile raf = null;
        try{
            raf = new RandomAccessFile(filename, "rw");
        }catch(FileNotFoundException e){
            Log.e(LCAT, "filename not found:", filename);
            return;
        }


        for (int i = 1; i*1024 < trimToSize; i++) {
            try {
                byte[] b = new byte[1024];
                raf.read(b, (int)raf.length() - i * 1024, b.length);
                raf.seek(raf.length());
                raf.write(b, (int)raf.length() - trimToSize + (i-1)* 1024, b.length);
            } catch (IOException e) {
                Log.e(LCAT, "RandomAccessFile read,seek,write exception");
                Log.e(LCAT, e.toString());
                return;
            }
        }

        try {
            raf.close();
        } catch (IOException e) {
            Log.e(LCAT, "RandomAccessFile close exception");
            Log.e(LCAT, e.toString());
        }
    }

//	@Kroll.method
//	public HashMap popFilterableNotifications() {
//		NotificationsAndId notificationsAndId = NotificationBatches.popBatch();
//
//		HashMap<String, Object> result = new HashMap<String, Object>();
//		result.put("filterId", notificationsAndId.getId());
//		result.put("notifications", JsonUtil.notificationsToMap(notificationsAndId.getNotifications()));
//		return result;
//	}

	/*
	 * TODO: this is not used?
	 * @param batch
	 * /
	@Kroll.method
	public void sendNotifications(HashMap<String, Object> batch) {
		Log.d(LCAT, "sendNotifications");
		String filterId = (String) batch.get("filterId");
		List<FilterableNotification> notifications = NotificationBatches.getBatch(filterId);

		Object[] jsonNotifications = (Object[]) batch.get("notifications");
		List<FilterableNotification> notificationsToSend = JsonUtil.getNotifications(jsonNotifications, notifications);
		NotificationBatches.sendBatch(filterId, notificationsToSend);
	}
	*/

	@Kroll.method
	public HashMap<String, Object> popGeotriggers() {
		GeotriggersAndId geotriggersAndId = GeotriggerBatches.popBatch();

		HashMap<String, Object> result = new HashMap<>();
		result.put("handlerId", geotriggersAndId.getId());
		result.put("geotriggers", JsonUtil.geotriggersToMap(geotriggersAndId.getGeotriggers()));
		return result;
	}

	@Kroll.method
	public void markGeotriggersHandled(HashMap<String, Object> batch) {
		Log.d(LCAT, "markGeotriggersHandled - A");

		String handlerId = (String) batch.get("handlerId");
		List<Geotrigger> geotriggers = GeotriggerBatches.getBatch(handlerId);

		Log.d(LCAT, "markGeotriggersHandled - B");

		Object[] jsonGeotriggers = (Object[]) batch.get("geotriggers");
		List<Geotrigger> geotriggersHandled = JsonUtil.getGeotriggers(jsonGeotriggers, geotriggers);
		GeotriggerBatches.sendBatch(handlerId, geotriggersHandled);

		Log.d(LCAT, "markGeotriggersHandled - C");
	}

	@Kroll.method
	public void setStringSegmentationProperty(String property, String value) {
		Plot.setStringSegmentationProperty(property, value);
	}

	@Kroll.method
	public void setBooleanSegmentationProperty(String property, boolean value) {
		Plot.setBooleanSegmentationProperty(property, value);
	}

	@Kroll.method
	public void setIntegerSegmentationProperty(String property, int value) {
		Plot.setLongSegmentationProperty(property, value);
	}

	@Kroll.method
	public void setDoubleSegmentationProperty(String property, double value) {
		Plot.setDoubleSegmentationProperty(property, value);
	}

	@Kroll.method
	public void setDateSegmentationProperty(String property, Date value) {
		Plot.setDateSegmentationProperty(property, value.getTime() / 1000);
	}

	@Kroll.getProperty @Kroll.method
	public HashMap<String, Object>[] getLoadedNotifications() {
		return JsonUtil.notificationTriggersToMap(new ArrayList<NotificationTrigger>(Plot.getLoadedNotifications()));
	}

	@Kroll.getProperty @Kroll.method
	public HashMap<String,Object>[] getLoadedGeotriggers() {
		Log.d(LCAT, "getLoadedGeotriggers");
		return JsonUtil.geotriggersToMap(new ArrayList<Geotrigger>(Plot.getLoadedGeotriggers()));
	}

	@Kroll.getProperty @Kroll.method
	public HashMap<String,Object>[] getSentNotifications() {
		return JsonUtil.sentNotificationsToMap(new ArrayList<SentNotification>(Plot.getSentNotifications()));
	}

	@Kroll.getProperty @Kroll.method
	public HashMap<String,Object>[] getSentGeotriggers() {
		Log.d(LCAT, "getSentGeotriggers");
		return JsonUtil.sentGeotriggersToMap(new ArrayList<SentGeotrigger>(Plot.getSentGeotriggers()));
	}

	@Kroll.method
	public void clearSentNotifications() {
		Plot.clearSentNotifications();
	}

	@Kroll.method
	public void clearSentGeotriggers() {
		Plot.clearSentGeotriggers();
	}
}
