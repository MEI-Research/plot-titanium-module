package mei;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import mei.ble.Encounter;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Debug {
    /** WARNING: do not fill applog with junk! */
    public static void log(String tag, String message, Object... kvs) {

        Log.d(tag, message + ": " + MapUtil.mapFromArray(kvs));
        Encounter.logEma(tag + ": " + message, kvs);
    }
}
