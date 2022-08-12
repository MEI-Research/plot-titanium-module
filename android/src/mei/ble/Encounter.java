package mei.ble;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.meiresearch.android.plotprojects.GeotriggerHandlerService;
import com.plotprojects.retail.android.Geotrigger;

import org.appcelerator.titanium.TiApplication;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import meipp.EmaMessageQueue;
import meipp.EmaLog;

/**
 * Implements the state machine from the BLE Encounter Definition document
 *
 * Every encounter needs to send both a Start and and End event to the Encounter dataset.
 * Only actual encounters trigger a notification and survey.
 * Therefore: for transient encounters, we send one event to EMA when the transient encounter ends which includes start
 * and end info; for actual encounters, we send the notification and the event to EMA after min_dur has passed, then
 * another event when the actual  encounter expires.
 *
 * # Terminology
 *
 * actual encounter - an encounter that has lasted longer than minDuaration (now >= startTime + minDur)
 * spurious enter - an enter geotrigger for a friend with an ongoing encounter. These don't happen
 *     with interaction tracer, but the code should handle them just in case.
 * spurious exit - an exit shortly followed by an enter for the same friend. These are ignored except
 *    for updating the encounter statistics.
 * potential exit - an exit that may or may not be spurious. We must wait.
 * transient encounter - an encounter that has not lasted minDuration (now < startTime + minDur)
 *
 * TODO: persist & reload encounterByKey
 * What if a transient encounter gets no Enters until after
 *
 * @see <a href="https://docs.google.com/document/d/1vmxGhzkQfuyQNmo0G6gC79s13p938QfATBzHeLTZynA/edit#">BLE Encounter Definition</a>
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class Encounter {

    private static final String TAG = Encounter.class.getName();

    ////////////////////////
    //// Static features

    static enum EncounterType { TRANSIENT, ACTUAL }

    /** Active encounter map */
    private static Map<String,Encounter> _encounterByFriendName = null;
    private static Map<String,Encounter> encounterByFriendName() {
        if (_encounterByFriendName == null) {
             _encounterByFriendName = new HashMap<String,Encounter>();
             restoreEncounters();
        }
        return _encounterByFriendName;
    }

    /**
     * Guard against modifying the state of Encounters in multiple threads: the AlarmManager &
     * PlotProjects can both call the state changing public static methods
     */
    private static Object STATE_LOCK = new Object();

    public static void clearAllEncounters() {
        synchronized(STATE_LOCK) {
            EmaLog.info(TAG, "clearAllEncounters");
            encounterByFriendName().clear();
            saveEncounters();
        }
    }

    /**
     * Updates all Encounters for the given time and optionally for a geotrigger event at that time.
     *
     * @param now - estimate of when the event happened; defaults to current time.
     *                  Geotrigger has no time filed & we get them in batche so the caller might attempt to distribute
     *                  the events over the previous interval.
     * @param geotrigger - a Geotrigger that might be for a beacon data campaign. Null if just updating
     *                   due to a timer.
     * @return true if the geotrigger was a beacon event that is now processed
     */
    public static boolean updateEncounters(
        Instant now,
        @Nullable Geotrigger geotrigger
    ) {
        synchronized(STATE_LOCK) {
            try {
                // Update all current encounters for passage of time

                trace("updateAllForPassedTime, keys= " + encounterByFriendName().keySet() + ", now=" + now);
                Iterator<Map.Entry<String, Encounter>> itr = encounterByFriendName().entrySet().iterator();
                while (itr.hasNext()) {
                    Encounter encounter = itr.next().getValue();
                    boolean terminate = encounter.updateForCurrentTime(now);
                    if (terminate) {
                        itr.remove();
                    }
                }

                // Apply the geotrigger, possibly creating, updating, or removing an Encounter

                trace("handleGeotrigger: geotrigger=" + geotrigger);
                if (geotrigger == null)
                    return false;

                BeaconEvent beaconEvent = BeaconEvent.forGeotrigger(geotrigger);
                if (beaconEvent == null) {
                    EmaLog.info(TAG, "not a friend beacon event",
                        "matchPayload", geotrigger.getMatchPayload(),
                        "friendList", EncountersApi.friendList.toString());
                    return false;
                }
                Friend friend = beaconEvent.getFriend();
                trace("friend=" + friend + ", handleGeotrigger " + beaconEvent);
                if (friend == null) {
                    EmaLog.info(TAG, "can't happen");
                    return false;
                }
                if (now == null) {
                    now = Instant.now();
                }
                Encounter currentEncounter = encounterByFriendName().get(friend.name);
                if (currentEncounter != null) {
                    currentEncounter.updateForBeaconEvent(beaconEvent, now);
                    return true;
                }

                EmaLog.info(TAG, "no existing encounter", "friend", friend);
                if (!beaconEvent.isBeaconEnter()) {
                    EmaLog.info(TAG, "Ignoring non-enter beacon event w/o existing encounter, map=" + encounterByFriendName().toString());
                    return true;
                }
                // A new encounter
                if (beaconEvent.isBeaconEnter()) {
                    Encounter unused = new Encounter(friend, now);
                    EmaLog.info(TAG, "started new encounter",
                        encounterByFriendName(), "encounterByFriendName()");
                }
                return true;
            } catch (Exception ex) {
                EmaLog.error(TAG, ex);
                return true;
            } finally {
                saveEncounters();
            }
        }
    }

    ////////////////////////
    //// Properties

    //// Encounter fields

    private Friend _friend;
    public Friend getFriend() {
        return _friend;
    }
    public void setFriend(Friend friend) {
        this._friend = friend;
        encounterByFriendName().put(friend.name, this);
    }

    //// Encounter state is a function of these 3 initialEnter, recentExit, mostRecentEnter and currentTime

    /** Time of the initial Enter geotrigger */
    Instant initialEnter;

    /**
     * Time of the most reccent Enter geotrigger
     */
    Instant mostRecentEnter;

    EncounterType _encounterType = EncounterType.TRANSIENT;
    EncounterType getEncounterType() {
        return _encounterType;
    }
    void setEncounterType(EncounterType et) {
        _encounterType = et;
    }


    /**
     * For Transient encounter only: time of most recent Exit geotrigger that is not followed by an Enter geotrigger.
     */
    Optional<Instant> recentExit = Optional.empty();

    // Stats
    int numDeltaT = 0;
    double sumDeltaT = 0;
    double sumDeltaT2 = 0;
    double maxDeltaT = -1;

    Instant becomesActualAt() {
        return initialEnter.plus(EncountersApi.instance.getMinDuration());
    }

    double avgDeltaT() {
        return sumDeltaT / numDeltaT;
    }

    double sdDeltaT() {
        double avgDT = avgDeltaT();
        return Math.sqrt(sumDeltaT2 / numDeltaT - avgDT * avgDT);
    }


    ////////////////////////////////////////////////
    //// State transition

    /** Start new encounter */
    Encounter(Friend friend, Instant eventTime) {
        setFriend(friend);
        initialEnter = eventTime;
        mostRecentEnter = eventTime;

        EncounterUpdateReceiver.scheduleNextUpdateBefore(becomesActualAt());

        clearStats();
       trace("Start " + this.toString() + " at " + eventTime);
    }

    /**
     * Apply clock ticks.
     * @return true if the Encounter should be deleted
     */
    boolean updateForCurrentTime(Instant now) {
       trace("updateForCurrentTime: " + this);
        boolean terminate = false;
        if (recentExit.isPresent()) {
            Instant applyExitAt  = recentExit.get().plus(
                    isTransient() ?
                            EncountersApi.instance.getTransientTimeout() :
                            EncountersApi.instance.getActualTimeout()
            );
            if (now.isAfter(applyExitAt))  {
                terminateEncounter(recentExit.get());
                terminate = true;
            }
            else {
                EncounterUpdateReceiver.scheduleNextUpdateBefore(applyExitAt);
            }
        }
        if (isTransient()) {
            if (now.isAfter(becomesActualAt())) {
                becomeActual();
            }
            else {
                EncounterUpdateReceiver.scheduleNextUpdateBefore(becomesActualAt());
            }
        }
        else { // isActual
            if (now.isAfter(ageOutAt())) {
                EmaLog.info(TAG, "aging out", "encounter", this.toString());
                terminateEncounter(ageOutAt());
            }
            else {
                EncounterUpdateReceiver.scheduleNextUpdateBefore(ageOutAt());
            }
        }
       trace("updateForCurrentTime result: encounter=" + this);
        //Debug.log(TAG, "updateForCurrentTime result", "encounter", this.toString());
        return terminate;
    }

    void terminateEncounter(Instant endTime) {
        EmaLog.info(TAG, "terminateEncounter", "encounter", this.toString());
        HashMap<String, Object> event = this.toMap();
        switch (getEncounterType()) {
            case TRANSIENT:
                event.put("event_type", "start_transient_encounter");
                event.put("timestamp", EmaMessageQueue.encodeTimestamp(initialEnter));
                EncountersApi.instance.sendEmaEvent(event);

                event.put("event_type", "end_transient_encounter");
                break;
            case ACTUAL:
                event.put("event_type", "end_actual_encounter");
                break;
        }
        event.put("timestamp", EmaMessageQueue.encodeTimestamp(endTime));
        EncountersApi.instance.sendEmaEvent(event);
        //Encounter.deleteEncounter(this);
    }

    void becomeActual() {
        EmaLog.info(TAG, "transient encounter becomes actual",
            "encounter", this.toString());
        setEncounterType(EncounterType.ACTUAL);
        signalStartActual();

        // TODO: fix this to allow enter & exit notifications
        GeotriggerHandlerService.sendNotification("enter");

        clearStats();
        EncounterUpdateReceiver.scheduleNextUpdateBefore(ageOutAt());
    }

    /** Apply a geotrigger */
    void updateForBeaconEvent(BeaconEvent beaconEvent, Instant now) {
       trace("updateForBeaconEvent:" + this);
        if (beaconEvent.isBeaconExit()) {
            recentExit = Optional.of(now);
        }
        else { // isBeaconEnter()
            if (recentExit.isPresent()) {
               trace("Resume from spurious exit ");

                updateStats(now, recentExit.get());
                recentExit = Optional.empty();
            }
            if (isActual()) {
                //update stats on spurious Enters for end_actual rows
                updateStats(now, mostRecentEnter);
            }
            mostRecentEnter = now;
        }
        updateEncounters(now, null);
        EmaLog.info(TAG, "updateForBeaconEvent", "encounter", this.toString());
    }

    private void clearStats() {
        numDeltaT = 0;
        sumDeltaT = 0;
        sumDeltaT2 = 0;
        maxDeltaT = -1;
    }

    private void updateStats(Instant now, Instant prev) {
        double deltaT = (now.toEpochMilli() - prev.toEpochMilli()) / 1000.0;
        maxDeltaT = Math.max(deltaT, maxDeltaT);
        sumDeltaT += deltaT;
        sumDeltaT2 += deltaT * deltaT;
        numDeltaT += 1;
    }


    ////////////////////////////////////////////////
    //// misc

    public boolean isActual() {
        return getEncounterType() == EncounterType.ACTUAL;
    }
    public boolean isTransient() {
        return getEncounterType() == EncounterType.TRANSIENT;
    }

    /**
     * Time when encounter reaches maximum duration
     * @return
     */
    private Instant ageOutAt() {
        return initialEnter.plus(EncountersApi.instance.getMaxEncounterDuration());
    }

    ///////////////////////////
    //// Signal events for EMA

    /**
     * Signal the start of an actual encounter.
     * Notify the participant & send the Start Encounter event to EMA
     */
    private void signalStartActual() {
        GeotriggerHandlerService.sendNotification("enter");

        HashMap<String, Object> event = this.toMap();
        event.put("event_type", "start_actual_encounter");
        event.put("timestamp", EmaMessageQueue.encodeTimestamp(initialEnter));
        event.put("notif_at", EmaMessageQueue.encodeTimestamp(Instant.now()));
        EncountersApi.instance.sendEmaEvent(event);
    }

    /**
     * Signal the end of any encounter
     * For transients, send *both* the start and end encounter events to EMA
     * For actual encounters, just send the end event.
     */
//    private void signalEnd(Instant endAt) {
//        EmaLog.info(TAG, "signalEnd", "encounter", this.toString());
//        HashMap<String, Object> event = this.toMap();
//        switch (getEncounterType()) {
//            case TRANSIENT:
//                event.put("event_type", "start_transient_encounter");
//                event.put("timestamp", EmaMessageQueue.encodeTimestamp(initialEnter));
//                EncountersApi.instance.sendEmaEvent(event);
//
//                event.put("event_type", "end_transient_encounter");
//                break;
//            case ACTUAL:
//                event.put("event_type", "end_actual_encounter");
//                break;
//        }
//        event.put("timestamp", EmaMessageQueue.encodeTimestamp(endAt));
//        EncountersApi.instance.sendEmaEvent(event);
//    }

    private HashMap<String,Object> toMap() {
        HashMap<String,Object>  map = new HashMap<String,Object> ();
        map.put("friend_name", getFriend().name);
        map.put("kontakt_beacon_id", getFriend().tag);
        map.put("min_duration_secs", EncountersApi.instance.getMinDuration().getSeconds());
        map.put("actual_enc_timeout_secs", EncountersApi.instance.getActualTimeout().getSeconds());
        map.put("transient_enc_timeout_secs", EncountersApi.instance.getTransientTimeout().getSeconds());
        map.put("max_detect_event_delta_t", maxDeltaT);
        map.put("num_events", numDeltaT);
        if (numDeltaT > 0) {
            map.put("avg_detect_event_delta_t", avgDeltaT());
            map.put("sd_detect_event_delta_t", sdDeltaT());
        }
        return map;
    }

    ///////////////////////////
    //// Encounter persistence over reboot

    static final String TI_PROP_KEY = Encounter.class.getName() + ".STORE";

    private static void saveEncounters() {
        JSONArray encounters = new JSONArray();
        for (Encounter e: encounterByFriendName().values()) {
            encounters.put(e.toJson());
        }
        TiApplication.getInstance().getAppProperties().setString(
                TI_PROP_KEY,
                encounters.toString());
    }

    private static void restoreEncounters() {
        String savedValue =
                TiApplication.getInstance().getAppProperties().getString(TI_PROP_KEY, "[]");
        EmaLog.info(TAG, "DEBUG>>>>>> restoreEncounters", "savedValue", savedValue);
        try {
            JSONArray encounters = new JSONArray(savedValue);
            for (int i = 0; i < encounters.length(); ++i) {
                JSONObject encounterJson = encounters.getJSONObject(i);
                new Encounter(encounterJson);
            }
            EmaLog.info(TAG, "DEBUG>>>>>> restoreEncounters result",
                    "encounterByFriendName", encounterByFriendName().toString());

        } catch (JSONException e) {
            EmaLog.error(TAG, "bad stored encounter list",
                    "savedValue", savedValue);
        }
    }

    JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("friendName", getFriend().name);
            json.put("initialEnter", initialEnter.getEpochSecond());
            json.put("mostRecentEnter", mostRecentEnter.getEpochSecond());
            json.put("recentExit", recentExit.isPresent()
                    ? recentExit.get().getEpochSecond()
                    : -1);
            json.put("encounterType", getEncounterType().name());
            json.put("numDeltaT", numDeltaT);
            json.put("sumDeltaT", sumDeltaT);
            json.put("sumDeltaT2", sumDeltaT2);
            json.put("maxDeltaT", maxDeltaT);

        } catch (JSONException e) {
           EmaLog.error(TAG, "while serializing encounter" + e.getMessage(),
                   "encounter", this.toString()
           );
        }
        return json;
    }

    /** Create encounter by deserializing */
    private Encounter(JSONObject json) {
        try {
            setFriend(findFriend((String) json.get("friendName")));

            initialEnter = Instant.ofEpochSecond(json.getLong("initialEnter"));
            mostRecentEnter = Instant.ofEpochSecond(json.getLong("mostRecentEnter"));
            long  t = json.getLong("recentExit");
            recentExit = t == -1 ? Optional.empty() : Optional.of(Instant.ofEpochSecond(t));
            setEncounterType(EncounterType.valueOf(
                    json.getString("encounterType")));
            numDeltaT = json.getInt("numDeltaT");
            sumDeltaT = json.getDouble("sumDeltaT");
            sumDeltaT2 = json.getDouble("sumDeltaT2");
            maxDeltaT = json.getInt("maxDeltaT");

        } catch (JSONException e) {
            EmaLog.error(TAG, "Got exception" + e.getMessage(),
                    "serializedEncounter", json.toString()
            );
        }
    }

    private static Friend findFriend(String friendName) {
        for (Friend f: EncountersApi.friendList) {
            if (f.name.equals(friendName)) {
                return f;
            }
        }
        EmaLog.error(TAG, "Friend does not exist",
                "friendName", friendName);
        return null;
    }

    private static void trace(String message) {
        Log.i(TAG, message);
    }

    ///////////////////////////
    //// Boiler plate

    @Override
    public String toString() {
        return "Encounter(friend=" + getFriend() + ", start=" + initialEnter + ", type=" + getEncounterType() +
                ", recentExit=" + recentExit +
                ", mostRecentEnter=" + mostRecentEnter +
                ", actualAt=" + becomesActualAt() +
                ", nDT=" + (numDeltaT) + ", maxDT=" + maxDeltaT + ", avgDT=" + avgDeltaT() + ", sdDT=" + sdDeltaT() +
                ")";
    }
}
