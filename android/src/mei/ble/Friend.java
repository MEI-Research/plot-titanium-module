package mei.ble;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import mei.Debug;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Friend {
    //private static final String TAG = Friend.class.getName();

    ////////////////
    // non-static

    final String name, majorId, minorId, tag;

    /**
     *
     * @param dsv - dash-separated friend name, major-, and minor-ids.
     */
    public Friend(String dsv) {
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
