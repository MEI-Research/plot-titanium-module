package mei.ble;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import mei.Debug;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Friend {
    private static final String TAG = Friend.class.getName();

    public static List<Friend> friendList = new ArrayList<Friend>();
    public static Friend forBeacon(String majorId, String minorId) {
        if (majorId == null || minorId == null)
            return null;

        Log.d(TAG, "forBeacon: " + majorId + ", " + minorId + ", " + friendList.toString());
        for (Friend friend : friendList) {
            if (friend.majorId.equals(majorId) && friend.minorId.endsWith(minorId))
                return friend;
        }
        return null;
    }
    public static void setFriendList(String friendCsv) {
        Log.d(TAG, "setFriendList(" + friendCsv + ")");
        Debug.log(TAG, "setFriendList", "friendCsv", friendCsv);
        friendList.clear();
        for (String dsv: friendCsv.split(", *")) {
            friendList.add(new Friend(dsv));
        }
    }

    ////////////////
    // non-static

    final String name, majorId, minorId, tag;

    private Friend(String dsv) {
        String[] parts = dsv.split("-", 4);
        String tag = parts.length < 4 ? parts[1] + "-" + parts[2] : parts[3];
        this.name = parts[0];
        this.majorId = parts[1];
        this.minorId = parts[2];
        this.tag = tag;
    }

    @Override
    public String toString() {
        return "Friend(" + name + ", " + majorId + ", " + minorId + ", " + tag + ")";
    }

}
