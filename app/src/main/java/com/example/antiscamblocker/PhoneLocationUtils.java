package com.example.antiscamblocker;




import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class PhoneLocationUtils {
    private static final String TAG = "PhoneLocationUtils";
    private static final String FILE_NAME = "phone.dat";

    private static PhoneLocationUtils instance;
    private RandomAccessFile datFile;

    private int recordCount;
    private int indexOffset;
    private int dictOffset;
    private int dictLength;

    /**
     * 归属地查询结果封装类
     */
    public static class LocationResult {
        public String province; // 省份
        public String city;     // 城市
        public int carrierId;   // 运营商 ID

        public String getCarrierName() {
            switch (carrierId) {
                case 1: return "中国移动";
                case 2: return "中国联通";
                case 3: return "中国电信";
                case 4: return "中国广电";
                default: return "未知运营商";
            }
        }
    }

    private PhoneLocationUtils(Context context) {
        init(context);
    }

    /**
     * 静态初始化方法（建议在自定义 Application 的 onCreate 中调用一次，也可以由工具类自动持有）
     */
    public static void initContext(Context context) {
        if (instance == null) {
            synchronized (PhoneLocationUtils.class) {
                if (instance == null) {
                    instance = new PhoneLocationUtils(context.getApplicationContext());
                }
            }
        }
    }

    private static PhoneLocationUtils getInstance(Context context) {
        if (instance == null) {
            initContext(context);
        }
        return instance;
    }

    /**
     * 对外提供的静态便捷方法
     * 满足调用：PhoneLocationUtils.LocationResult result = PhoneLocationUtils.getPhoneLocation("13812345678");
     * 注：若全局已在 Application 初始化过 context，调用此方法时直接传 null 或使用重载均可。
     */
    public static LocationResult getPhoneLocation(Context context, String phoneNumber) {
        PhoneLocationUtils utils = getInstance(context);
        if (utils == null) {
            return null;
        }
        return utils.query(phoneNumber);
    }

    private void init(Context context) {
        try {
            File cacheFile = new File(context.getCacheDir(), FILE_NAME);
            if (!cacheFile.exists() || cacheFile.length() == 0) {
                try (InputStream is = context.getAssets().open(FILE_NAME);
                     FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
            }

            datFile = new RandomAccessFile(cacheFile, "r");

            byte[] header = new byte[36];
            datFile.seek(0);
            datFile.readFully(header);

            ByteBuffer buffer = ByteBuffer.wrap(header);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            String magic = new String(header, 0, 4, StandardCharsets.US_ASCII);
            if (!"JPHN".equals(magic)) {
                Log.e(TAG, "Phone.dat magic number mismatch!");
                return;
            }

            recordCount = buffer.getInt(8);
            indexOffset = buffer.getInt(12);
            dictOffset = buffer.getInt(20);
            dictLength = buffer.getInt(24);

            Log.i(TAG, "Phone.dat initialized successfully. Record count: " + recordCount);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize phone.dat", e);
        }
    }

    private LocationResult query(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7 || datFile == null) {
            return null;
        }

        try {
            int prefix = Integer.parseInt(phoneNumber.substring(0, 7));

            int low = 0;
            int high = recordCount - 1;
            int recordSize = 10;

            while (low <= high) {
                int mid = (low + high) >>> 1;
                long currentRecordOffset = (long) indexOffset + (long) mid * recordSize;

                datFile.seek(currentRecordOffset);
                byte[] indexBytes = new byte[recordSize];
                datFile.readFully(indexBytes);

                ByteBuffer buf = ByteBuffer.wrap(indexBytes);
                buf.order(ByteOrder.LITTLE_ENDIAN);

                int currentPrefix = buf.getInt(0);

                if (currentPrefix == prefix) {
                    int cityId = buf.getInt(4);
                    int carrierId = buf.getShort(8);

                    LocationResult result = new LocationResult();
                    result.carrierId = carrierId;

                    // 从字典区解析省份和城市（假设字典区存储格式为 "省份 城市" 或由特定分隔符隔开，也可以根据实际 dat 格式调整）
                    parseLocationText(cityId, result);
                    return result;
                } else if (currentPrefix < prefix) {
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying phone number", e);
        }
        return null;
    }

    /**
     * 解析字典文本并拆分省份与城市
     */
    private void parseLocationText(int cityId, LocationResult result) {
        try {
            long targetOffset = (long) dictOffset + cityId;
            if (targetOffset >= dictOffset + dictLength) {
                result.province = "未知";
                result.city = "未知";
                return;
            }

            datFile.seek(targetOffset);

            byte[] lenBytes = new byte[2];
            datFile.readFully(lenBytes);
            int strLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

            if (strLen <= 0 || strLen > 512) {
                result.province = "未知";
                result.city = "未知";
                return;
            }

            byte[] strBytes = new byte[strLen];
            datFile.readFully(strBytes);
            String fullLocation = new String(strBytes, StandardCharsets.UTF_8); // 例如格式为 "广东 深圳" 或 "广东省深圳市"

            // 依据实际存储格式进行拆分（以下以常见的空格分隔或无分隔符为例）
            if (fullLocation.contains(" ")) {
                String[] parts = fullLocation.split(" ", 2);
                result.province = parts[0];
                result.city = parts.length > 1 ? parts[1] : "";
            } else {
                // 如果没有空格，可以简单将全称赋给省或城，或按常规省份简称切割
                result.province = fullLocation;
                result.city = "";
            }

        } catch (Exception e) {
            Log.e(TAG, "Error reading dictionary", e);
            result.province = "未知";
            result.city = "未知";
        }
    }
}