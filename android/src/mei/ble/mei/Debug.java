package mei.ble.mei;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import mei.ble.Encounter;

@RequiresApi(api = Build.VERSION_CODES.O)
public class Debug {
    /** WARNING: do not fill applog with junk! */
    public static void log(String tag, String message, Object... kvs) {
        Log.d(tag, message + ": " + kvs.toString());
        Encounter.logEma(tag + ": " + message, kvs);
    }
}
