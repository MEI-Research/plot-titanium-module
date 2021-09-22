package mei.ble;

import com.plotprojects.retail.android.Geotrigger;

/**
 * Adapt a plotprojects Geogtrigger for our purposes
 * TODO: rename.
 * */
public final class GeotriggerAdapter {
    final Geotrigger geotrigger;
    final String[] nameParts;

    public GeotriggerAdapter(Geotrigger geotrigger) {
        this.geotrigger = geotrigger;
        nameParts = geotrigger.getName().split(",");

//        String campaignName = geotrigger.getName();
//        if (!campaignName.startsWith("beacon,")) {
//            eventType = Encounter.EventType.nonBeacon;
//            return;
//        }
//        if (nameParts.length < 5) {
//            eventType = Encounter.EventType.invalid;
//            return;
//        }
//        Encounter.EventType t = Encounter.EventType.invalid;
//        try {
//            eventType = Encounter.EventType.valueOf(nameParts[4]);
//        } catch (IllegalArgumentException ex) {
//            eventType = Encounter.EventType.invalid;
//        }
//        kontactBeaconId = nameParts[1];
//        participantCode = nameParts[2];
//        friendCode = nameParts[3];
    }

    public boolean isBeaconEvent() {
        return nameParts.length >= 5 && nameParts[0].equals("beacon");
    }
    public boolean isBeaconEnter() {
        return isBeaconEvent() && geotrigger.getTrigger().equals("enter");
    }
    public boolean isBeaconExit() {
        return isBeaconEvent() && geotrigger.getTrigger().equals("exit");
    }
    public String kontactBeaconId() {
        return nameParts[1];
    }
    public String participantCode() {
        return nameParts[2];
    }
    public String friendCode() {
        return nameParts[3];
    }

    @Override
    public String toString() {
        return "Wrapped(" + geotrigger + ")";
    }
}
