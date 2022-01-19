package mei;

import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;

import mei.ble.EncountersApi;

@RequiresApi(api = Build.VERSION_CODES.O)
public class EmaLog {
    /**
     * WARNING: do not fill applog with junk!
     */
    public static void info(String tag, String message, Object... kvs) {
        Log.d(tag, message + ": " + MapUtil.mapFromArray(kvs));
        EncountersApi.msgQueue.logToEma(tag + ": " + message, kvs);
    }

    public static void error(String tag, String message, Object... kvs) {
        Log.e(tag, "[ERROR] " + message + ": " + MapUtil.mapFromArray(kvs));
        EncountersApi.msgQueue.logToEma(tag + ": " + message, kvs);
    }
    public static void error(String tag, Exception ex, Object... kvs) {
        HashMap<String, Object> details = MapUtil.mapFromArray(kvs);
        Log.e(tag, "[ERROR] " + ex.getMessage() + ": " + details, ex);
        details.put("stacktrace", stacktraceString(ex));
        EncountersApi.msgQueue.logToEma(tag, ex.toString(), details);
    }

    public static String stacktraceString(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String sStackTrace = sw.toString(); // stack trace as a string
        return sStackTrace;
    }
}