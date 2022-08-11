package meipp;

import java.util.HashMap;

public class MapUtil {
    public static HashMap<String,Object> mapWith(Object... keyValues) {
        return mapFromArray(keyValues);
    }
    public static HashMap<String,Object>mapFromArray(Object[] keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Must be key & value pairs:" + keyValues);
        }
        HashMap<String, Object> result = new HashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(keyValues[i].toString(), keyValues[i+1]);
        }
        return result;
    }
}
