package mei.ble;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.meiresearch.android.plotprojects.GeotriggerHandlerService;
import com.plotprojects.retail.android.Geotrigger;

import mei.EmaMessageQueue;
import mei.Debug;


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
    static Map<String,Encounter> encounterByFriendName = new HashMap<String,Encounter>();

    public static void logToEma(String message, HashMap<String, Object> more_data) {
        EncountersApi.msgQueue.logToEma(message, more_data);
//        HashMap<String, Object> msg = new HashMap<>();
//        msg.put("event_type", "message");
//        msg.put("timestamp", EncountersApi.instance.encodeTimestamp(Instant.now()));
//        msg.put("message", message);
//        if (more_data != null) msg.put("more_data", more_data);
//        EncountersApi.instance.sendEmaEvent(msg);
    }

    public static void logEma(String msg, Object... keyValues) {
        EncountersApi.msgQueue.logToEma(msg, keyValues);
        //logToEma(msg, MapUtil.mapFromArray(keyValues));
    }

    public static void reset() {
        Log.i(TAG, "Reset encounters");
        encounterByFriendName.clear();
    }

    /**
     * Update each active encounter to reflect that no geotrigger was received since the previous one.
     * This must be called right before handling any new Enter events in a batch.
     * @param now - for test we can mess with time
     *
     * TODO: this could be eliminated now that EmaNotification works
     */
    public static void updateAllForPassedTime(Instant now) {
        Log.d(TAG, "updateAllForPassedTime, keys= " + encounterByFriendName.keySet() + ", now=" + now);
        for (Encounter encounter: encounterByFriendName.values()) {
            encounter.updateForCurrentTime(now);
        }
    }

    /**
     * Process a Geotrigger if it is part of a beacon data campaign.
     * Responsible for sending Encounter start events.
     * Assumes that expired encounters have already been removed.
     *
     * @param geotrigger - a Geotrigger that might be for a beacon data campaign
     * @param eventTime - estimate of when the event happened; defaults to current time.
     *                  Geotrigger has no time filed & we get them in batche so the caller might attempt to distribute
     *                  the events over the previous interval.
     * @return true if the geotrigger was a beacon event that is now processed
     */
    public static boolean handleGeotrigger(Geotrigger geotrigger, Instant eventTime) {
        Log.d(TAG, "handleGeotrigger: geotrigger=" + geotrigger);
        BeaconEvent beaconEvent = BeaconEvent.forGeotrigger(geotrigger);
        if (beaconEvent == null) {
            Debug.log(TAG, "not a friend beacon event",
                    "matchPayload", geotrigger.getMatchPayload(),
                    "friendList", EncountersApi.friendList.toString());
            return false;
        }
        Friend friend = beaconEvent.getFriend();
        Log.d(TAG, "friend=" + friend + ", handleGeotrigger " + beaconEvent);
        if (friend == null) {
            Debug.log(TAG, "can't happen");
            return false;
        }

        if (eventTime == null) {
            eventTime = Instant.now();
        }
        Encounter currentEncounter = encounterByFriendName.get(friend.name);
        if (currentEncounter != null) {
            currentEncounter.updateForBeaconEvent(beaconEvent, eventTime);
            return true;
        }

        Debug.log(TAG, "no existing encounter", "friend", friend);
        if (!beaconEvent.isBeaconEnter()) {
            Debug.log(TAG, "Ignoring non-enter beacon event w/o existing encounter, map=" + encounterByFriendName.toString());
            return true;
        }
        // A new encounter
        if (beaconEvent.isBeaconEnter()) {
            encounterByFriendName.put(
                    friend.name,
                    new Encounter(friend, eventTime));
            Debug.log(TAG, "started new encounter",
                    encounterByFriendName, "encounterByFriendName");
        }
        return true;
    }


    ////////////////////////
    //// Properties
    
    //// Encounter fields
    
    final Friend friend;

    //// Encounter state is a function of these 3 initialEnter, recentExit, mostRecentEnter and currentTime
    
    /** Time of the initial Enter geotrigger */
    final Instant initialEnter;
    
    /**
     * For Transient encounter only: time of most recent Exit geotrigger that is not followed by an Enter geotrigger.
     */
    Optional<Instant> recentExit = Optional.empty();
    /**
     * Time of the most reccent Enter geotrigger
     */
    Instant mostRecentEnter;
    
    
    EncounterType encounterType = EncounterType.TRANSIENT;
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
        this.friend = friend;
        initialEnter = eventTime;
        mostRecentEnter = eventTime;

        EncounterUpdateReceiver.scheduleNextUpdateBefore(becomesActualAt());

        clearStats();
        Log.d(TAG, "Start " + this.toString() + " at " + eventTime);
    }

    /** Apply clock ticks */
    void updateForCurrentTime(Instant now) {
        //Log.d(TAG, "updateForCurrentTime: " + this);

        if (recentExit.isPresent()) {
            Instant applyExitAt  = recentExit.get().plus(
                    isTransient() ?
                            EncountersApi.instance.getTransientTimeout() :
                            EncountersApi.instance.getActualTimeout()
            );
            if (now.isAfter(applyExitAt))  {
                terminateEncounter(recentExit.get());
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
                Debug.log(TAG, "aging out", "encounter", this);
                terminateEncounter(ageOutAt());
            }
            else {
                EncounterUpdateReceiver.scheduleNextUpdateBefore(ageOutAt());
            }
        }
        //Debug.log(TAG, "updateForCurrentTime result", "encounter", this.toString());
    }

    void terminateEncounter(Instant endTime) {
        Debug.log(TAG, "terminateEncounter", "encounter", this);
        HashMap<String, Object> event = this.toMap();
        switch (encounterType) {
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
        encounterByFriendName.remove(friend.name);
    }

    void becomeActual() {
        Debug.log(TAG, "transient encounter becomes actual");
        encounterType = EncounterType.ACTUAL;
        signalStartActual();

        // TODO: fix this to allow enter & exit notifications
        GeotriggerHandlerService.sendNotification("enter");

        clearStats();
        EncounterUpdateReceiver.scheduleNextUpdateBefore(ageOutAt());
    }

    /** Apply a geotrigger */
    void updateForBeaconEvent(BeaconEvent beaconEvent, Instant now) {
        Log.d(TAG, "updateForBeaconEvent:" + this);
        if (beaconEvent.isBeaconExit()) {
            recentExit = Optional.of(now);
        }
        else { // isBeaconEnter()
            if (recentExit.isPresent()) {
                Log.d(TAG, "Resume from spurious exit ");

                updateStats(now, recentExit.get());
                recentExit = Optional.empty();
            }
            if (isActual()) {
                //update stats on spurious Enters for end_actual rows
                updateStats(now, mostRecentEnter);
            }
            mostRecentEnter = now;
        }
        updateAllForPassedTime(now);
        Debug.log(TAG, "updateForBeaconEvent", "encounter", this);
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
        return encounterType == EncounterType.ACTUAL;
    }
    public boolean isTransient() {
        return encounterType == EncounterType.TRANSIENT;
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
    private void signalEnd(Instant endAt) {
        Debug.log(TAG, "signalEnd", "encounter", this);
        HashMap<String, Object> event = this.toMap();
        switch (encounterType) {
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
        event.put("timestamp", EmaMessageQueue.encodeTimestamp(endAt));
        EncountersApi.instance.sendEmaEvent(event);
    }

    private HashMap<String,Object> toMap() {
        HashMap<String,Object>  map = new HashMap<String,Object> ();
        map.put("friend_name", friend.name);
        map.put("kontakt_beacon_id", friend.tag);
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
    //// Boiler plate

    @Override
    public String toString() {
        return "Encounter(friend=" + friend + ", start=" + initialEnter + ", type=" + encounterType +
                ", recentExit=" + recentExit +
                ", mostRecentEnter=" + mostRecentEnter +
                ", actualAt=" + becomesActualAt() +
                ", nDT=" + (numDeltaT) + ", maxDT=" + maxDeltaT + ", avgDT=" + avgDeltaT() + ", sdDT=" + sdDeltaT() +
                ")";
    }
}
