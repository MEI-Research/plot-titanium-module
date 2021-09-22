package mei.ble;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import android.util.Log;
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
public class Encounter {

    static enum EncounterType { TRANSIENT, ACTUAL }

    private static final String TAG = Encounter.class.getName();

    //// Ti App Property names
    static final String MIN_DUR_SECS = "plot.min_duration_secs";
    static final String TRANSIENT_TIMEOUT_SECS = "plot.transient_enc_timeout_secs";
    static final String ACTUAL_TIMEOUT_SECS = "plot.actual_enc_timeout_secs";


    /** Active encounter map */
    static Map<String,Encounter> encounterByFriend = new HashMap<>();

    //// Encounter fields
    final String friendCode;
    final String kontaktId;
    final Instant firstEventTime;
    Optional<Instant> exitTime = Optional.empty();
    // Instant mostRecentEventTime;
    EncounterType encounterType = EncounterType.TRANSIENT;
    int numDeltaT = 0;
    double sumDeltaT = 0;
    double sumDeltaT2 = 0;
    double maxDeltaT = -1;

    public Encounter(String friendCode, String kontaktId, Instant eventTime) {
        this.friendCode = friendCode;
        this.kontaktId = kontaktId;
        firstEventTime = eventTime;

        // mostRecentEventTime = eventTime;

        Log.d(TAG, "Start " + this.toString() + " at " + eventTime);
    }

    public static void reset() {
        encounterByFriend.clear();
    }

    /**
     * Update each active encounter to reflect that no geotrigger was received since the previous one.
     * This must be called right before handling any new Enter events in a batch.
     * @param now
     */
    public static void updateAllForPassedTime(Instant now) {
        Log.d(TAG, "updateAllForPassedTime, keys= " + encounterByFriend.keySet() + ", now=" + now);
        for (Encounter encounter: encounterByFriend.values()) {
            Log.d(TAG, "updateAllForPassedTime: " + encounter);
            if (encounter.isExpiredAt(now)) {
                Log.d(TAG, "will signalEndEvent");
                encounter.signalEndEvent();
                encounterByFriend.remove(encounter.friendCode);
            }
            else if (encounter.encounterType == EncounterType.TRANSIENT
                    && !encounter.exitTime.isPresent()
                    && encounter.becomesActualAt().isBefore(now)) {
                Log.d(TAG, "will signalStartActual");
                encounter.signalStartActual();
            }
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
     * @return true if the geotrigger has been completely processed
     */
    public static boolean handleGeotrigger(GeotriggerAdapter geotrigger, Instant eventTime) {
        Log.d(TAG, "handleGeotrigger " + geotrigger);
        if (eventTime == null) {
            eventTime = Instant.now();
        }
        String friendCode = geotrigger.friendCode();
        Encounter encounter = encounterByFriend.get(friendCode);

        if (encounter == null) {
            // A new encounter
            if (geotrigger.isBeaconEnter()) {
                encounterByFriend.put(
                        friendCode,
                        new Encounter(friendCode, geotrigger.kontactBeaconId(), eventTime));
            }
            // ignore subsequent enter events & spurious exits
        }
        else if (geotrigger.isBeaconExit()) {
            // Start the clock to end the encounter
            encounter.exitTime = Optional.of(eventTime);

        } else if (geotrigger.isBeaconEnter()) {
            // Update for non-initial Enter Detection
            encounter.updateForAbortedExit(eventTime);
        }
//        else {
//            double deltaT = (eventTime.toEpochMilli() - encounter.mostRecentEventTime.toEpochMilli()) / 1000.0;
//            //long deltaT = Duration.between(encounter.mostRecentEventTime, eventTime).getSeconds();
//            encounter.maxDeltaT = Math.max(deltaT, encounter.maxDeltaT);
//            encounter.sumDeltaT += deltaT;
//            encounter.sumDeltaT2 += deltaT * deltaT;
//            encounter.numDeltaT += 1;
//            encounter.mostRecentEventTime = eventTime;
//            Log.d(TAG, "Updated " + encounter);
//        }
        return true;
    }

    private void updateForAbortedExit(Instant now) {
        if (!exitTime.isPresent())
            return;
        double deltaT = (now.toEpochMilli() - exitTime.get().toEpochMilli()) / 1000.0;
        maxDeltaT = Math.max(deltaT, maxDeltaT);
        sumDeltaT += deltaT;
        sumDeltaT2 += deltaT * deltaT;
        numDeltaT += 1;

        exitTime = Optional.empty();
        Log.d(TAG, "Updated " + this);
    }

    /**
     * Signal the start of an actual encounter.
     * Notify the participant & send the Start Encounter event to EMA
     */
    void signalStartActual() {
        encounterType = EncounterType.ACTUAL;
        GeotriggerHandlerService.sendNotification("enter");

        HashMap<String, Object> event = this.toMap();
        event.put("event_type", "start_actual_encounter");
        event.put("timestamp", EncountersApi.instance.encodeTimestamp(firstEventTime));
        event.put("notif_at", EncountersApi.instance.encodeTimestamp(Instant.now()));
        EncountersApi.instance.sendEmaEvent(event);
    }

    /**
     * Signal the end of any encounter
     * For transients, send both the start and end encounter events to EMA
     * For actual encounters, just send the end event.
     */
    void signalEndEvent() {
        HashMap<String, Object> event = this.toMap();
        switch (encounterType) {
            case TRANSIENT:
                event.put("event_type", "start_transient_encounter");
                event.put("timestamp", EncountersApi.instance.encodeTimestamp(firstEventTime));
                EncountersApi.instance.sendEmaEvent(event);

                event.put("event_type", "end_transient_encounter");
                break;
            case ACTUAL:
                event.put("event_type", "end_actual_encounter");
                break;
        }
        event.put("timestamp", EncountersApi.instance.encodeTimestamp(exitTime.get()));
        EncountersApi.instance.sendEmaEvent(event);
    }

    boolean isExpiredAt(Instant t) {
        if (!exitTime.isPresent())
            return false;
        return exitTime.get().plusSeconds(currentTimeout()).isBefore(t);
    }

    long currentTimeout() {
        if (encounterType == EncounterType.TRANSIENT)
            return EncountersApi.instance.transientTimeoutSecs;
        else
            return EncountersApi.instance.actualTimeoutSecs;

    }

    boolean isActualAt(Instant t) {
        return becomesActualAt().isBefore(t);
    }

//    Instant expiresAt() {
//        long timeout;
//        if (encounterType == EncounterType.TRANSIENT)
//            timeout = EncountersApi.instance.transientTimeoutSecs;
//        else
//            timeout = EncountersApi.instance.actualTimeoutSecs; // EMADataAccess.getInt(ACTUAL_TIMEOUT_SECS, 15 * 60);
//        return mostRecentEventTime.plusSeconds(timeout);
//    }

    Instant becomesActualAt() {
        return firstEventTime.plusSeconds(EncountersApi.instance.minDurationSecs);
    }

    double avgDeltaT() {
        return sumDeltaT / numDeltaT;
    }

    double sdDeltaT() {
        double avgDT = avgDeltaT();
        return Math.sqrt(sumDeltaT2 / numDeltaT - avgDT * avgDT);
    }

    public HashMap<String,Object> toMap() {
        HashMap<String,Object>  map = new HashMap<String,Object> ();
        map.put("kontakt_beacon_id", kontaktId);
        map.put("friend_name", friendCode);
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

    @Override
    public String toString() {
        return "Encounter(friend=" + friendCode + ", start=" + firstEventTime + ", type=" + encounterType +
                ", exitTime=" + exitTime + ", actualAt=" + becomesActualAt() +
                ", nDT=" + (numDeltaT) + ", maxDT=" + maxDeltaT + ", avgDT=" + avgDeltaT() + ", sdDT=" + sdDeltaT() +
                ")";
    }
}
