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
import java.util.ArrayList;
import java.util.List;

public class PhoneLocationUtils {
    private static final String TAG = "PhoneLocationUtils";
    private static final String FILE_NAME = "phone.dat";

    private static PhoneLocationUtils instance;
    private RandomAccessFile datFile;

    private int recordCount;
    private int indexOffset;
    private int dictOffset;
    private int dictLength;
    private int cityCount;

    // 缓存城市字典数组，利用 cityId 下标直接 O(1) 获取
    private CityEntry[] cityDictionary;

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

    private static class CityEntry {
        String province;
        String city;

        public CityEntry(String province, String city) {
            this.province = province;
            this.city = city;
        }
    }

    private PhoneLocationUtils(Context context) {
        init(context);
    }

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
        if (instance == null && context != null) {
            initContext(context);
        }
        return instance;
    }

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
            cityCount = buffer.getInt(28);

            // 初始化时加载并解析整个城市字典区
            loadCityDictionary();

            Log.i(TAG, "Phone.dat initialized successfully. Record count: " + recordCount + ", City count: " + cityCount);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize phone.dat", e);
        }
    }

    /**
     * 一次性将字典区全部加载到内存数组中
     */
    private void loadCityDictionary() {
        try {
            datFile.seek(dictOffset);
            byte[] dictBytes = new byte[dictLength];
            datFile.readFully(dictBytes);

            ByteBuffer buffer = ByteBuffer.wrap(dictBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            List<CityEntry> entries = new ArrayList<>();
            while (buffer.hasRemaining()) {
                // 1. 读取省份长度 (uint16)
                if (buffer.remaining() < 2) break;
                int pLen = buffer.getShort() & 0xFFFF;

                // 2. 读取省份文本
                byte[] pBytes = new byte[pLen];
                buffer.get(pBytes);
                String province = new String(pBytes, StandardCharsets.UTF_8);

                // 3. 读取城市长度 (uint16)
                int cLen = buffer.getShort() & 0xFFFF;

                // 4. 读取城市文本
                byte[] cBytes = new byte[cLen];
                buffer.get(cBytes);
                String city = new String(cBytes, StandardCharsets.UTF_8);

                entries.add(new CityEntry(province, city));
            }

            cityDictionary = entries.toArray(new CityEntry[0]);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load city dictionary", e);
            cityDictionary = new CityEntry[0];
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
            int recordSize = 10; // phone(4) + city_id(4) + carrier_id(2)

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
                    int carrierId = buf.getShort(8) & 0xFFFF;

                    LocationResult result = new LocationResult();
                    result.carrierId = carrierId;

                    // 直接通过 cityId 下标从内存字典中获取省市
                    if (cityDictionary != null && cityId >= 0 && cityId < cityDictionary.length) {
                        CityEntry entry = cityDictionary[cityId];
                        result.province = entry.province;
                        result.city = entry.city;
                    } else {
                        result.province = "未知";
                        result.city = "未知";
                    }

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
}