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

        if (phoneNumber.startsWith("1")) {
            // 1. 直接调用静态方法（第一次调用时会自动完成 assets 解压与初始化，后续直接走内存二分查找）
            PhoneLocationUtils.LocationResult result = PhoneLocationUtils.getPhoneLocation(this, phoneNumber);

            if (result != null) {
                Log.d(TAG, "号码: " + phoneNumber + " | 省份: " + result.province + " | 城市: " + result.city + " | 运营商: " + result.getCarrierName());

                // 示例：如果你想根据省份或城市拦截，可以直接写在这里
                // if ("北京市".equals(result.city)) { return true; }
                // if (!"北京市".equals(result.city)) { return true; }

                //if ("湖南省".equals(result.province)) {
                //   return true;
                //}


                String province = result.province;

                if ("湖南".equals(province) || "湖南省".equals(province) ||
                        "江苏".equals(province) || "江苏省".equals(province) ||
                        "浙江".equals(province) || "浙江省".equals(province)) {
                    return false;
                } else {

                    return true;
                }

            }
        }






        return phoneNumber.startsWith("0");
    }

    private void buildAndSendResponse(Call.Details callDetails, boolean shouldBlock) {
        CallResponse.Builder responseBuilder = new CallResponse.Builder();

        if (shouldBlock) {
            // 直接拦截并拒接
            responseBuilder
                    .setDisallowCall(true)     // 拦截
                    .setRejectCall(true)       // 挂断:像用户手动拒接一样断开电话
                    .setSkipCallLog(false)     // 在通话记录中显示
                    .setSkipNotification(true);// 不显示未接来电通知
                  //.setSilenceCall(true);     // 不播放系统来电铃声:对方通常仍然听到正常回铃音，电话继续呼叫

        } else {
            // 正常接听，不干预，设置
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
