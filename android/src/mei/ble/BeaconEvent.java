package mei.ble;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.plotprojects.retail.android.Geotrigger;

import java.time.Instant;
import java.util.List;

import meipp.EmaLog;

/**
 * A wrapper for Geotriggers that are beacon detections.
 * Hides the details of how PP stores things in the Geotrigger.
 * TODO: move all friend-stuff to Encounter class.
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public final class BeaconEvent {
    private static final String TAG = BeaconEvent.class.getName();

    private enum MatchPayloadKey {
        triggerTimeInMilli,
        majorId,
        minorId
    }

    private final Geotrigger geotrigger;

    /**
     * @param geotrigger
     * @return a BeaconEvent wrapping the geotrigger if it is valid, else null
     */
    public static BeaconEvent forGeotrigger(Geotrigger geotrigger) {
        BeaconEvent beaconEvent = new BeaconEvent(geotrigger);
        EmaLog.info(TAG, "forGeotrigger=" + beaconEvent);
        if (beaconEvent.getFriend() == null) {
            return null;
        }
        return beaconEvent;
    }

    private BeaconEvent(Geotrigger geotrigger) {
        this.geotrigger = geotrigger;
    }

    public boolean isBeaconEnter() {
        return geotrigger.getTrigger().equals("enter");
    }
    public boolean isBeaconExit() {
        return geotrigger.getTrigger().equals("exit");
    }

    /**
     * @return friend name or null if the beacon isn't in friend list
     */
    public Friend getFriend() {
        String majorId = geotrigger.getMatchPayload().get(MatchPayloadKey.majorId.toString());
        String minorId = geotrigger.getMatchPayload().get(MatchPayloadKey.minorId.toString());
        if (majorId == null || minorId == null)
            return null;
        final List<Friend> friendList = EncountersApi.friendList;
        Log.d(TAG, "forBeacon: " + majorId + ", " + minorId + ", " + friendList.toString());
        for (Friend friend : friendList) {
            if (friend.majorId.equals(majorId) && friend.minorId.endsWith(minorId))
                return friend;
        }
        return null;
    }

    public Instant getTime() {
        String triggerTimeInMilli = geotrigger.getMatchPayload().get(MatchPayloadKey.triggerTimeInMilli.toString());
        try {
            return Instant.ofEpochMilli(Long.valueOf(triggerTimeInMilli));
        }
        catch (NumberFormatException ex) {
            return Instant.now();
        }
    }

    @Override
    public String toString() {
        StringBuffer b = new StringBuffer();
        appendTo(b);
        return b.toString();
    }

    public void appendTo(StringBuffer b) {
        b.append("BeaconEvent(").append("friend=").append(getFriend());
        b.append(", geotrig.trig=").append(geotrigger.getTrigger());
        b.append(", isEnter=").append(isBeaconEnter());
        b.append(", isExit=").append(isBeaconExit());
        b.append(")");
    }
}
