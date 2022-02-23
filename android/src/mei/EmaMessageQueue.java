package mei;

import android.icu.text.SimpleDateFormat;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;
import org.json.JSONObject;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;

/**
 * JSON encodes and queues up messages to be fetched by EMA.
 * A message is a JSON encodable Hash.  It must have an entry for key "type".
 * The special type "message" should be posted by EMA to the applog with tag=queueName
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class EmaMessageQueue {
    private static final String TAG = "EmaMessageQueue";

    public static final String EVENT_TYPE_MESSAGE = "message";

    private final String queueName;
    private final StringBuffer undeliveredMessages;

    /**
     *
     * @param name - used to form a unique property name for persisting the queue.
     */
    public EmaMessageQueue(String name) {
        this.queueName = name;
        undeliveredMessages = new StringBuffer(readState());
    }

    /**
     * Dequeue and return all queued messages.
     * A KrollProxy method typically delegates to this.
     *
     * @return JSON-encoding of the array of outstanding events.
     */
    public String fetchMessages() {
        String result;
        synchronized (this) {
            undeliveredMessages.insert(0, "[");
            undeliveredMessages.append("]");
            result = undeliveredMessages.toString();

            undeliveredMessages.setLength(0);
            saveState("");
        }
        Log.d(TAG, "fetchMessages DEBUG: queue = " + queueName +
                ", result=" + result.toString());
        return result;
    }

//    public void logToEma(String message, Object... keyValues) {
//        logToEma(message, MapUtil.mapFromArray(keyValues));
//    }

    public void logToEma(String message, HashMap<String, Object> more_data) {
        HashMap<String, Object> msg = new HashMap<String, Object>();
        msg.put("event_type", EVENT_TYPE_MESSAGE);
        msg.put("timestamp", encodeTimestamp(new Date()));
        msg.put("message", message);
        if (more_data != null) msg.put("more_data", more_data);
        sendMessage(msg);
    }

    /**
     * Queue an message to send to EMA
     * TODO: persist to survive restarts
     * @param message
     */
    public void sendMessage(HashMap<String,Object> message) {
        String messageEncoded = new JSONObject(message).toString();
        sendEncodedMessage(messageEncoded);
    }

    private void sendEncodedMessage(String messageEncoded) {
        synchronized (this) {
            if (undeliveredMessages.length() > 0)
                undeliveredMessages.append(",");
            undeliveredMessages.append(messageEncoded);
            saveState(undeliveredMessages.toString());
        }

        // Messageually EMA will only need to call fetchMessages() on open, resume and from this TI message.
        // For now, commenting out to avoid limitations on boot receiver runtime
        //        boolean hasListener = this.fireMessage(EVENT_ADDED, message);
        //        if (!hasListener) {
        //            Log.w(TAG, "No listener for messages: " + message);
        //        }
        Log.d(TAG, "sendMessage, undeliveredMessages=[" + undeliveredMessages + "]" );
    }

    private void saveState(String state) {
        TiProperties props = TiApplication.getInstance().getAppProperties();
        props.setString(tiPropName() , state);
    }

    private String readState() {
        TiProperties props = TiApplication.getInstance().getAppProperties();
        return props.getString(tiPropName(), "");
    }

    private String tiPropName() {
        return getClass().getName() + "." + queueName;
    }


    //////////////// ////////////////
    //  Move elsewhere

    public static String encodeTimestamp(Date timestamp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(timestamp);
        }
        else {
            return timestamp.toString();
        }
    }
    public static String encodeTimestamp(Instant timestamp) {
        return OffsetDateTime.ofInstant(timestamp, ZoneId.systemDefault()).toString();
    }


}
