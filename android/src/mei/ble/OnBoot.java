package mei.ble;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.meiresearch.android.plotprojects.GeotriggerHandlerService;

/**
 * Start services that must run after device restart before EMA is opened
 *
 * # TODO
 * For now, just ensure detection resumes. This requires persisting friend list and configure PP
 * Should active encounters survive reboot?
 * Persist & restore alarms.
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class OnBoot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent ignored) {
        Log.i("OnBoot", "starting services");
        GeotriggerHandlerService.onBoot(context);
    }
}
