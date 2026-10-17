package com.example.antiscamblocker;

import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import androidx.annotation.NonNull;

import android.util.Log;

/**
* 电话拦截服务程序
 * When an incoming or outgoing call occurs, the system binds to your service and invokes onScreenCall.
 * You must respond using respondToCall before your method execution completes to avoid timeouts.
 */
public class AntiScamCallService extends CallScreeningService {

    private static final String TAG = "AntiScamCallService";
    // 声明归属地工具类实例 -
    // PhoneLocationUtils 的初始化应该放在 CallScreeningService 的 onCreate() 生命周期方法中
    private PhoneLocationUtils phoneLocationUtils;

    @Override
    public void onCreate() {
        super.onCreate();
        // 2. 在服务被系统绑定初始化时，加载一次 phone.dat
        // 使用 getApplicationContext() 确保生命周期安全
        phoneLocationUtils = PhoneLocationUtils.getInstance(getApplicationContext());
        Log.i(TAG, "AntiScamCallService created, PhoneLocationUtils initialized.");
    }

    @Override
    public void onScreenCall(@NonNull Call.Details callDetails) {
        // Step 3.1: Check if the call is incoming
        if (callDetails.getCallDirection() == Call.Details.DIRECTION_INCOMING) {

            // Step 3.2: Extract the incoming phone number
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


        // Example Rule 1: Block known dummy scam prefixes (e.g., 95562)
        if (phoneNumber.startsWith("95")) {
            return true;
        }

        if (phoneNumber.startsWith("400")) {
            return true;
        }

        if (phoneNumber.startsWith("1520390")) {
            return true;
        }

        if (phoneNumber.startsWith("1561611")) {
            return true;
        }



        if (phoneNumber.startsWith("1823905")
                || phoneNumber.startsWith("1783721")
        )
        {
            return true;
        }

        if (phoneNumber.startsWith("15197620871")) {
            return true;
        }


        PhoneLocationUtils.PhoneInfo info = phoneLocationUtils.query(phoneNumber);



        return phoneNumber.startsWith("0");
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
