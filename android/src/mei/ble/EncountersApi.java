package mei.ble;

import android.os.Build;

import androidx.annotation.RequiresApi;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;

import org.appcelerator.titanium.util.TiConvert;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.LinkedTransferQueue;

import mei.Debug;

@RequiresApi(api = Build.VERSION_CODES.O)
@Kroll.proxy() // doesn't work:!(propertyAccessors={"transientTimeoutSecs", "actualTimeoutSecs"})
public class EncountersApi extends KrollProxy {
    private static final String TAG = EncountersApi.class.getName();

    public static final String BLE_EVENTS_PROP = "ble.events";

    /** Singleton */
    public static final EncountersApi instance = new EncountersApi();
    private EncountersApi() {}

    /**
     * Undelivered encounters
     * TODO: persist thru restart?
     */
    private LinkedTransferQueue<HashMap<String,Object>> undeliveredEncounterEvents
            = new LinkedTransferQueue<HashMap<String,Object>>();

    @Kroll.setProperty
    public void setFriendList(String friendCsv) {
        Log.d(TAG, "DEBUG###### setFriendList=" + friendCsv);
        Friend.setFriendList(friendCsv);
    }

    // Default values for testing
    public long minDurationSecs = 5 * 60;
    @Kroll.setProperty
    public void setMinDurationSecs(long val) {
        Log.d(TAG, "DEBUG###### setMin  DurationSecs " + val);
        minDurationSecs = val;
    }

    public Duration transientTimeout = Duration.ofMinutes(2);
    @Kroll.setProperty
    public void setTransientTimeoutSecs(Object val) {
        transientTimeout = Duration.ofSeconds(Math.round(TiConvert.toDouble(val)));
    }

    public Duration actualTimeout = Duration.ofMinutes(10);
    @Kroll.setProperty
    public void setActualTimeoutSecs(Object val) {
        actualTimeout = Duration.ofSeconds(Math.round(TiConvert.toDouble(val)));
    }

    public Duration maxEncounterDuration = Duration.ofHours(8);
    @Kroll.setProperty
    public void setMaxEncounterDurationHours(Object hours) {
        maxEncounterDuration = Duration.ofSeconds(Math.round(TiConvert.toDouble(hours) *  3600));
        Log.debug(TAG, "DEBUG>>>>>>> setMaxEncounterDurationHours," + hours + ", " + maxEncounterDuration);
    }

    /**
     * Retrieves events detected
     * This lets the module manage persistence itself.
     * @return an array of encounter rows (javascript Objects)
     */
    @Kroll.method()
    public String fetchEvents() {
        Encounter.updateAllForPassedTime(Instant.now());
        Log.d(TAG, "fetchEvents: updated size=" + undeliveredEncounterEvents.size());
        JSONArray result = new JSONArray();
        while (true) synchronized (undeliveredEncounterEvents) {
            HashMap<String, Object> evt = undeliveredEncounterEvents.poll();
            if (evt == null)
                break;
            try {
                result.put(new JSONObject(evt));
            } catch (Exception e) {
                Debug.log(TAG, "Can't convert to JSON" , "evt", evt);
            }
        }
        return result.toString();
    }

    public void sendEmaEvent(HashMap<String,Object> event) {
        Log.d(TAG, "sendEmaEvent: " + event);
        synchronized (undeliveredEncounterEvents) {
            undeliveredEncounterEvents.add(new HashMap<String,Object>(event));
        }
        boolean hasListener = this.fireEvent("ble.event", null);
        if (!hasListener) {
            Log.w(TAG, "No listener for event: " + event);
        }
    }

    public String encodeTimestamp(Instant timestamp) {
        return OffsetDateTime.ofInstant(timestamp, ZoneId.systemDefault()).toString();
    }

    public enum EmaEvent { start_actual_encounter, end_actual_encounter, transient_encounter}
}
