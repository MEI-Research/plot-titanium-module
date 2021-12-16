package mei.ble;

import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Friend {
    private static final String TAG = Friend.class.getName();

    private static List<Friend> friendList = new ArrayList<Friend>();

    public static Friend forBeacon(String majorId, String minorId) {
        if (majorId == null || minorId == null)
            return null;

        for (Friend friend : friendList) {
            if (friend.majorId.equals(majorId) && friend.minorId.endsWith(minorId))
                return friend;
        }
        return null;
    }
    public static void setFriendList(String friendCsv) {
        Log.d(TAG, "setFriendList(" + friendCsv + ")");
        friendList.clear();
        for (String friend: friendCsv.split(",")) {
            String[] parts = friend.split("-");
            friendList.add(new Friend(parts[0], parts[1], parts[2]));
        }
    }

    final String name, majorId, minorId;
    private Friend(String name, String majorId, String minorId) {
        this.name = name;
        this.majorId = majorId;
        this.minorId = minorId;
    }

    @Override
    public String toString() {
        return "Friend(" + name + ", " + majorId + ", " + minorId + ")";
    }

}
