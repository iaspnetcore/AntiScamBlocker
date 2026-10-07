202610

The CallScreeningService in Android 15 (API level 35) requires a strict combination of manifest declarations, runtime role requests, and precise handling of the service lifecycle to prevent the system from killing your background process.

step 1. Register the Service in AndroidManifest.xml

~~~
 <service
            android:name=".AntiScamCallService"
            android:permission="android.permission.BIND_SCREENING_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.telecom.CallScreeningService" />
            </intent-filter>
</service>
~~~

step 2. Implement MyCallScreeningService.java

~~~
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.net.Uri;
import androidx.annotation.NonNull;

public class MyCallScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        // Only screen incoming calls
        if (callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING) {
            Uri handle = callDetails.getHandle();
            String phoneNumber = (handle != null) ? handle.getSchemeSpecificPart() : "";

            if (shouldBlockCall(phoneNumber)) {
                buildAndSendResponse(callDetails, true);
            } else {
                buildAndSendResponse(callDetails, false);
            }
        } else {
            // Pass through outgoing calls immediately
            buildAndSendResponse(callDetails, false);
        }
    }

    private boolean shouldBlockCall(String phoneNumber) {
        // Implement your spam detection or lookup logic here
        return phoneNumber.startsWith("+1000"); 
    }

    private void buildAndSendResponse(Call.Details callDetails, boolean shouldBlock) {
        CallResponse.Builder responseBuilder = new CallResponse.Builder();

        if (shouldBlock) {
            responseBuilder.setDisallowCall(true)
                           .setRejectCall(true)
                           .setSkipCallLog(false)
                           .setSkipNotification(true);
        } else {
            responseBuilder.setDisallowCall(false)
                           .setRejectCall(false)
                           .setSkipCallLog(false)
                           .setSkipNotification(false);
        }

        // Must provide the exact Call.Details object and the response
        respondToCall(callDetails, responseBuilder.build());
    }
}



~~~



step 3.  Requesting the RoleManager at Runtime

MainActivity.java

~~~
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private final ActivityResultLauncher<Intent> roleResultLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Role granted successfully
                } else {
                    // Role denied by user
                }
            });

    private void requestCallScreeningRole() {
        RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);
        
        if (roleManager != null && !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            roleResultLauncher.launch(intent);
        }
    }
}


~~~