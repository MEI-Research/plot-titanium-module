package mei;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import mei.ble.EncountersApi;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Debug {
    /** WARNING: do not fill applog with junk! */
    public static void log(String tag, String message, Object... kvs) {
        Log.d(tag, message + ": " + MapUtil.mapFromArray(kvs));
        EncountersApi.msgQueue.logToEma(tag + ": " + message, kvs);
        //logToEma(msg, MapUtil.mapFromArray(keyValues));
    }
}
