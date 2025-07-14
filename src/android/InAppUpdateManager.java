package InAppUpdateManager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;

import com.google.android.gms.tasks.Task;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONObject;

public class InAppUpdateManager extends CordovaPlugin {

    public static final int REQUEST_CODE = 108108;
    protected AppUpdateManager appUpdateManager;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        if (action.equals("immediate")) {
            Context context = cordova.getActivity().getApplicationContext();
            this.startUpdateCheck(context);
            return true;
        }

        if (action.equals("isUpdateAvailable")) {
            Context context = cordova.getActivity().getApplicationContext();
            this.isUpdateAvailable(context, callbackContext);
            return true;
        }
        return false;
    }

    private void isUpdateAvailable(Context context, CallbackContext callbackContext) {
        appUpdateManager = AppUpdateManagerFactory.create(context);
        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                int availableCodes = appUpdateInfo.availableVersionCode();
                callbackContext.success(availableCodes);
            }
        });
    }

    private void startUpdateCheck(Context context) {
        appUpdateManager = AppUpdateManagerFactory.create(context);

        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                    && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {

                try {
                    cordova.setActivityResultCallback(this); // <--- Needed!
                    appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            AppUpdateType.IMMEDIATE,
                            cordova.getActivity(),
                            REQUEST_CODE);
                } catch (IntentSender.SendIntentException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // This is where cancellation will be caught
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_CANCELED) {
                try {
                    JSONObject event = new JSONObject();
                    event.put("status", "cancelled");
                    fireEvent("updateCancelled", event);  // JS event: updateCancelled
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void fireEvent(String eventName, JSONObject data) {
        final String js = String.format("cordova.fireWindowEvent('%s', %s);", eventName, data.toString());
        if (webView != null) {
            webView.sendJavascript(js); // emits event to JS
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);

        if (appUpdateManager == null) return;

        appUpdateManager.getAppUpdateInfo()
                .addOnSuccessListener(appUpdateInfo -> {
                    if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                        try {
                            appUpdateManager.startUpdateFlowForResult(
                                    appUpdateInfo,
                                    AppUpdateType.IMMEDIATE,
                                    cordova.getActivity(),
                                    REQUEST_CODE);
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }
}
