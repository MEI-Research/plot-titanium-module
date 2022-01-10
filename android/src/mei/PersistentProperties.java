package mei;

import android.os.Build;

import androidx.annotation.RequiresApi;

import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiProperties;

import java.util.HashMap;

/**
 * A set of key/values that persist over a device reboot.
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class PersistentProperties<Key extends Enum<Key>> {
    private static final String TAG = PersistentProperties.class.getName();

    private final String prefix;
    private final HashMap<Key,String> map = new HashMap<>();

    /**
     * @param keyClass - the enum class of keys
     */
    public PersistentProperties(Class<Key> keyClass) {
        this.prefix = keyClass.getName() + '.';

        // Restore persisted values, if they exist
        for (Key key: keyClass.getEnumConstants()) {
            String savedValue = tiProps().getString(tiName(key), null);
            EmaLog.info(TAG, "restore",
                    "property", key.name(), "savedValue", savedValue);
            if (savedValue != null) {
                setString(key, savedValue);
            }
        }
    }

    public String getString(Key key, String dflt) {
//        Log.d(TAG, "getString: map=" + map);
//        Log.d(TAG, "getString: dflt=" + String.valueOf(dflt));
        return map.getOrDefault(key, dflt);
    }

    public void setString(Key key, String value) {
        EmaLog.info(TAG, "EMA set", "property", key.name(), "value", value);
        map.put(key, value);
        tiProps().setString(tiName(key), value);
    }

    public Long getLong(Key key, Long dflt) {
        try {
            return Long.valueOf(map.get(key));
        }
        catch (NumberFormatException ex) {
            return dflt;
        }
    }
    public void setLong(Key key, Long value) {
        setString(key, String.valueOf(value));
    }

    private TiProperties tiProps() {
        return TiApplication.getInstance().getAppProperties();
    }

    private String tiName(Key key) {
        return key.getClass() + "." + key.name();
    }
}
