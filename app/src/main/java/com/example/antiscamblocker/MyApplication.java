package com.example.antiscamblocker;

import android.app.Application;
import android.util.Log;
public class MyApplication extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MyApplication onCreate: 初始化全局组件");

        // 在这里全局初始化 PhoneLocationUtils，确保 APP 启动时或者第一次用到时将 dat 文件准备好
        PhoneLocationUtils.initContext(this);
    }
}
