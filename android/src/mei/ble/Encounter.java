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


/**
 * Implements the state machine from the BLE Encounter Definition document
 *
 * Every encounter needs to send both a Start and and End event to the Encounter dataset.
 * Only actual encounters trigger a notification and survey.
 * Therefore: for transient encounters, we send one event to EMA when the transient encounter ends which includes start
 * and end info; for actual encounters, we send the notification and the event to EMA after min_dur has passed, then
 * another event when the actual encounter expires.
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
    static Map<String,Encounter> encounterByFriend = new HashMap<>();

    public static void logToEma(String message, HashMap<String, Object> more_data) {
        HashMap<String, Object> msg = new HashMap<>();
        msg.put("event_type", "message");
        msg.put("timestamp", EncountersApi.instance.encodeTimestamp(Instant.now()));
        msg.put("message", message);
        if (more_data != null) msg.put("more_data", more_data);
        EncountersApi.instance.sendEmaEvent(msg);
    }

    public static void reset() {
        Log.i(TAG, "Reset encounters");
        encounterByFriend.clear();
    }

    /**
     * Update each active encounter to reflect that no geotrigger was received since the previous one.
     * This must be called right before handling any new Enter events in a batch.
     * @param now - for test we can mess with time
     */
    public static void updateAllForPassedTime(Instant now) {
        Log.d(TAG, "updateAllForPassedTime, keys= " + encounterByFriend.keySet() + ", now=" + now);
        for (Encounter encounter: encounterByFriend.values()) {
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
        BeaconEvent beaconEvent = BeaconEvent.forGeotrigger(geotrigger);
        Log.d(TAG, "TRACE>>>>handleGeotrigger:" + beaconEvent);
        if (beaconEvent == null) {
            return false;
        }
        Friend friend = beaconEvent.getFriend();
        Log.d(TAG, "friend=" + friend + ", handleGeotrigger " + beaconEvent);
        if (friend == null)
            return false;

        if (eventTime == null) {
            eventTime = Instant.now();
        }
        Encounter encounter = encounterByFriend.get(friend);

        if (encounter == null) {
            // A new encounter
            if (beaconEvent.isBeaconEnter()) {
                encounterByFriend.put(
                        friend.name,
                        new Encounter(friend, eventTime));
            }
            // ignore subsequent enter events & spurious exits
        }
        else {
            encounter.updateForBeaconEvent(beaconEvent, eventTime);
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
        return initialEnter.plusSeconds(EncountersApi.instance.minDurationSecs);
    }

    double avgDeltaT() {
        return sumDeltaT / numDeltaT;
    }

    double sdDeltaT() {
        double avgDT = avgDeltaT();
        return Math.sqrt(sumDeltaT2 / numDeltaT - avgDT * avgDT);
    }


    ////////////////////////
    //// State transition

    /** Start new encounter */
    Encounter(Friend friend, Instant eventTime) {
        this.friend = friend;
        initialEnter = eventTime;
        mostRecentEnter = eventTime;

        clearStats();
        Log.d(TAG, "Start " + this.toString() + " at " + eventTime);
    }

    /** Apply clock ticks */
    void updateForCurrentTime(Instant now) {
        Log.d(TAG, "updateForCurrentTime: " + this);

        switch(encounterType) {
            case TRANSIENT:
                if (recentExit.isPresent()) {
                    Instant endTransientAt = recentExit.get().plusSeconds(EncountersApi.instance.transientTimeoutSecs);
                    if (now.isAfter(endTransientAt)) {
                        Log.d(TAG, "will signalEnd");
                        signalEnd(recentExit.get());
                        encounterByFriend.remove(friend.name);
                    }
                } else if (now.isAfter(becomesActualAt())) {
                    Log.d(TAG, "will signalStartActual");
                    encounterType = EncounterType.ACTUAL;
                    signalStartActual();

                    clearStats();
                }
                break;

            case ACTUAL:
                Instant endActualAt = mostRecentEnter.plusSeconds(EncountersApi.instance.actualTimeoutSecs);
                if (now.isAfter(endActualAt)) {
                    signalEnd(endActualAt);
                    //signalEnd(mostRecentEnter.plusSeconds(EncountersApi.instance.minDurationSecs));
                    encounterByFriend.remove(friend.name);
                }
                break;
        }
        Log.d(TAG, "updateForCurrentTime result: " + this);
    }

    /** Apply a geotrigger */
    void updateForBeaconEvent(BeaconEvent beaconEvent, Instant now) {
        Log.d(TAG, "updateForBeaconEvent:" + this);
        if (beaconEvent.isBeaconExit()) {
            if (encounterType == EncounterType.TRANSIENT) {
                // Start the clock to end the transient encounter
                recentExit = Optional.of(now);
            }

        } else if (beaconEvent.isBeaconEnter()) {
            double deltaT = -1;
            if (recentExit.isPresent()) {
                Log.d(TAG, "Resume from exiting ");

                // Update stats on spurious Exits for start_* rows
                updateStats(now, recentExit.get());

                recentExit = Optional.empty();
            }
            if (encounterType == EncounterType.ACTUAL) {
                //update stats on spurious Enters for end_actual rows
                updateStats(now, mostRecentEnter);
            }
            mostRecentEnter = now;
        }
        Log.d(TAG, "updateForGeotrigger result: " + this);
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
        event.put("timestamp", EncountersApi.instance.encodeTimestamp(initialEnter));
        event.put("notif_at", EncountersApi.instance.encodeTimestamp(Instant.now()));
        EncountersApi.instance.sendEmaEvent(event);
    }

    /**
     * Signal the end of any encounter
     * For transients, send *both* the start and end encounter events to EMA
     * For actual encounters, just send the end event.
     */
    private void signalEnd(Instant endAt) {
        Log.d(TAG, "signalEnd");
        HashMap<String, Object> event = this.toMap();
        switch (encounterType) {
            case TRANSIENT:
                event.put("event_type", "start_transient_encounter");
                event.put("timestamp", EncountersApi.instance.encodeTimestamp(initialEnter));
                EncountersApi.instance.sendEmaEvent(event);

                event.put("event_type", "end_transient_encounter");
                break;
            case ACTUAL:
                event.put("event_type", "end_actual_encounter");
                break;
        }
        event.put("timestamp", EncountersApi.instance.encodeTimestamp(endAt));
        EncountersApi.instance.sendEmaEvent(event);
    }

    private HashMap<String,Object> toMap() {
        HashMap<String,Object>  map = new HashMap<String,Object> ();
        map.put("friend_name", friend.name);
        map.put("kontakt_beacon_id", friend.tag);
        map.put("min_duration_secs", EncountersApi.instance.minDurationSecs);
        map.put("actual_enc_timeout_secs", EncountersApi.instance.actualTimeoutSecs);
        map.put("transient_enc_timeout_secs", EncountersApi.instance.transientTimeoutSecs);
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
