package mei.ble;

import com.meiresearch.android.plotprojects.EMADataAccess;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.LinkedTransferQueue;

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

    public long transientTimeoutSecs = 2 * 60;
    @Kroll.setProperty
    public void setTransientTimeoutSecs(long val) {
        transientTimeoutSecs = val;
    }

    public long actualTimeoutSecs = 10 * 60;
    @Kroll.setProperty
    public void setActualTimeoutSecs(long val) {
        actualTimeoutSecs = val;
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
            result.put(new JSONObject(evt));
        }
        return result.toString();
    }

    public void sendEmaEvent(HashMap<String,Object> event) {
        Log.d(TAG, "sendEmaEvent: " + event);
        synchronized (undeliveredEncounterEvents) {
            undeliveredEncounterEvents.add(new HashMap<String,Object>(event));
        }
        boolean hasListener = this.fireEvent("ble.event", event);
        if (!hasListener) {
            Log.w(TAG, "No listener for event: " + event);
        }
    }

    public String encodeTimestamp(Instant timestamp) {
        return OffsetDateTime.ofInstant(timestamp, ZoneId.systemDefault()).toString();
    }

    public enum EmaEvent { start_actual_encounter, end_actual_encounter, transient_encounter}
}
