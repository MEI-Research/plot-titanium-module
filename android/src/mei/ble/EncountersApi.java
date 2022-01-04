package mei.ble;

import android.os.Build;

import androidx.annotation.RequiresApi;

import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;

import org.appcelerator.titanium.util.TiConvert;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.LinkedTransferQueue;

import mei.Debug;
import mei.EmaMessageQueue;
import mei.PersistentProperties;

@RequiresApi(api = Build.VERSION_CODES.O)
@Kroll.proxy() // doesn't work:!(propertyAccessors={"transientTimeoutSecs", "actualTimeoutSecs"})
public class EncountersApi extends KrollProxy {
    private static final String TAG = EncountersApi.class.getName();

    public static final EmaMessageQueue msgQueue = new EmaMessageQueue("");


    enum PropKey {
        FRIEND_LIST,
        MIN_DUR,
        MAX_DUR,
        ACTUAL_TIMEOUT,
        TRANSIENT_TIMEOUT,
    }

    public static List<Friend> friendList = new ArrayList<Friend>();

    private static void _setFriendList(String friendCsv) {
        friendList.clear();
        if (friendCsv.isEmpty())
            return;
        for (String dsv: friendCsv.split(", *")) {
            friendList.add(new Friend(dsv));
        }
    }

    private final PersistentProperties<PropKey> props =
            new PersistentProperties<>(PropKey.class);

    /** Singleton */
    public static final EncountersApi instance = new EncountersApi();
    private EncountersApi() {
        try {
            _setFriendList(props.getString(PropKey.FRIEND_LIST, ""));
        }
        catch(Exception ex) {
            Log.d(TAG, "error creating", ex);
        }
    }

    /**
     * Undelivered encounters
     * TODO: persist thru restart?
     */
    private LinkedTransferQueue<HashMap<String,Object>> undeliveredEncounterEvents
            = new LinkedTransferQueue<HashMap<String,Object>>();

    @Kroll.setProperty
    public void setFriendList(String friendCsv) {
        Debug.log(TAG, "setFriendList", "friendCsv", friendCsv);
        props.setString(PropKey.FRIEND_LIST, friendCsv);

        _setFriendList(friendCsv);
        Log.d(TAG, "DEBUG>>>>>>> setFriendlist result=" + friendList);
    }

    public Duration getMinDuration() {
        return Duration.ofSeconds(getMinDurationSecs());
    }
    public long getMinDurationSecs() {
        return props.getLong(PropKey.MIN_DUR, 5L * 60);
    }
    @Kroll.setProperty
    public void setMinDurationSecs(long val) {
        props.setLong(PropKey.MIN_DUR, val);
    }

    public Duration getTransientTimeout() {
        return Duration.ofSeconds(props.getLong(PropKey.TRANSIENT_TIMEOUT, 2 * 60L));
    }
    @Kroll.setProperty
    public void setTransientTimeoutSecs(long val) {
        props.setLong(PropKey.TRANSIENT_TIMEOUT, val);
    }

    public Duration getActualTimeout() {
        return Duration.ofSeconds(props.getLong(PropKey.ACTUAL_TIMEOUT, 10 * 60L));
    }
    @Kroll.setProperty
    public void setActualTimeoutSecs(long val) {
        props.setLong(PropKey.ACTUAL_TIMEOUT, val);
    }

    private Duration maxEncounterDuration = Duration.ofHours(8);
    public Duration getMaxEncounterDuration() {
        return Duration.ofSeconds(props.getLong(PropKey.MAX_DUR, 8L * 60 * 60));
    }
    @Kroll.setProperty
    public void setMaxEncounterDurationHours(Object hours) {
        long seconds = Math.round(TiConvert.toDouble(hours) * 3600);
        props.setLong(PropKey.MAX_DUR, seconds);
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
        return msgQueue.fetchMessages();
//        JSONArray result = new JSONArray();
//        while (true) synchronized (undeliveredEncounterEvents) {
//            HashMap<String, Object> evt = undeliveredEncounterEvents.poll();
//            if (evt == null)
//                break;
//            try {
//                result.put(new JSONObject(evt));
//            } catch (Exception e) {
//                Debug.log(TAG, "Can't convert to JSON" , "evt", evt);
//            }
//        }
//        return result.toString();
    }

    public void sendEmaEvent(HashMap<String,Object> event) {
        Log.d(TAG, "sendEmaEvent: " + event);
        msgQueue.sendMessage(event);
//        synchronized (undeliveredEncounterEvents) {
//            undeliveredEncounterEvents.add(new HashMap<String,Object>(event));
//        }
        boolean hasListener = this.fireEvent("ble.event", null);
        if (!hasListener) {
            Log.w(TAG, "No listener for event: " + event);
        }
    }
}
