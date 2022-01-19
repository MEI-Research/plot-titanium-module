package com.meiresearch.android.plotprojects;


import android.location.Location;
import android.util.Log;

// these are used for the Titanium libraries. these are USED even tho Android Studio reports they are not
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;
import org.json.*;
import org.json.JSONArray;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


public class EMADataAccess {

    /** The tag name to be used by the Log() method. Will prefix log message with this tag name. */
    private static final String TAG = "Plot.EMADataAccess";

    // saves a property name and value to the Ti.App.Properties persistent storage location.
    // this assumes all data is a string (json or otherwise).
    public static void saveStringProperty(String propName, String propVal){
        Log.i(TAG, "saveProperty: " + propName);
        Log.i(TAG, "propValue:" + propVal);
        TiProperties props = TiApplication.getInstance().getAppProperties();

        props.setString(propName, propVal);
    }

    public static String getStringProperty(String propName){
        TiProperties props = TiApplication.getInstance().getAppProperties();
        String str = props.getString(propName, "");
        return str;
    }

    public static int getInt(String propName, int defaultValue) {
        try {
            return Integer.parseInt(EMADataAccess.getStringProperty(propName));
        }
        catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    // appends an element to the specified persistent data array
    public static void appendToJsonArray(String propertyName, JSONObject elem){
        Log.d(TAG, "appendToJsonArray start");

        TiProperties props = TiApplication.getInstance().getAppProperties();

        String json = props.getString(propertyName, "[]");
        JSONArray json_ary = new JSONArray();

        boolean errord = false;

        try{
            if(json != null && !json.trim().isEmpty()) {
                json_ary =  new JSONArray(json);
            } else {
                json_ary =  new JSONArray();
            }

            json_ary.put(elem);
            saveStringProperty(propertyName, json_ary.toString());

        } catch (JSONException e) {
            Log.e(TAG, "appendToJsonArray error 1:");
            Log.e(TAG, propertyName);
            Log.e(TAG, json);
            e.printStackTrace();

            errord = true;
        }

        // if the last json persistent property errors, let's just create it fresh.
        if(errord == true){
            json_ary =  new JSONArray();
            json_ary.put(elem);
            saveStringProperty(propertyName, json_ary.toString());
        }

        Log.d(TAG, "appendToJsonArray end");
    }

    // generically returns a json array given a property name.
    // will return a blank json array if an error happens or the property is blank.
    public static JSONArray getJsonArray(String propertyName){
        Log.d(TAG, "getJsonArray start");

        TiProperties props = TiApplication.getInstance().getAppProperties();

        String prop_str = props.getString(propertyName, "[]");
        JSONArray ary = new JSONArray();

        try{
            ary =  new JSONArray(prop_str);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "getJsonArray end");

        return ary;
    }
}
