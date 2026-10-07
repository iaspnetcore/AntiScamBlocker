package com.example.antiscamblocker;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import androidx.annotation.NonNull;

public class AntiScamCallService extends CallScreeningService {

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        // Step 3.1: Check if the call is incoming
        if (callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING) {

            // Step 3.2: Extract the incoming phone number
            Uri handle = callDetails.getHandle();
            String phoneNumber = (handle != null) ? handle.getSchemeSpecificPart() : "";

            // Step 3.3: Validate against anti-scam logic
            if (isScamNumber(phoneNumber)) {
                // Execute the intercept block and hang up
                blockAndRejectCall(callDetails);
                return;
            }
        }

        // Pass-through: Allow normal calls to ring through smoothly
        respondToCall(callDetails, new CallResponse.Builder().build());
    }

    /**
     * Define your anti-fraud filter logic here.
     */
    private boolean isScamNumber(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }

        // Example Rule 1: Block known dummy scam prefixes (e.g., +18005550199)
        if (number.startsWith("+1800555")) {
            return true;
        }

        // Example Rule 2: Block international calls posing as local numbers or suspicious lengths
        // Customize this placeholder matching with actual local telecom scam patterns
        return number.contains("scam-pattern-here");
    }

    /**
     * Sends the execution command back to Android 15 to completely reject the call.
     */
    private void blockAndRejectCall(Call.Details callDetails) {
        CallResponse.Builder response = new CallResponse.Builder();

        // 1. Disallow the incoming call from ringing
        response.setDisallowCall(true);
        // 2. Do not log this unwanted call in the system call log (Optional, cleaner user experience)
        response.setSkipCallLog(true);
        // 3. Prevent the system from firing a "missed call" notification
        response.setSkipNotification(true);

        // Instruct the OS Telecom system to process this execution response
        respondToCall(callDetails, response.build());
    }
}
