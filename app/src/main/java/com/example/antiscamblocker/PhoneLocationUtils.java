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

    // 头部元数据
    private int recordCount;
    private int indexOffset;
    private int indexLength;
    private int dictOffset;
    private int dictLength;

    private PhoneLocationUtils(Context context) {
        init(context);
    }

    public static synchronized PhoneLocationUtils getInstance(Context context) {
        if (instance == null) {
            instance = new PhoneLocationUtils(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 步骤一：初始化文件并解析头部信息
     * 为什么这样做：将 assets 中的 dat 文件释放到沙盒缓存区，获取 RandomAccessFile 句柄，
     * 并提前解析出索引区与字典区的偏移量，避免每次查询都重复计算。
     */
    private void init(Context context) {
        try {
            File cacheFile = new File(context.getCacheDir(), FILE_NAME);
            // 如果缓存文件不存在或大小为0，从 assets 复制
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

            // 读取前 36 字节的头部信息
            byte[] header = new byte[36];
            datFile.seek(0);
            datFile.readFully(header);

            ByteBuffer buffer = ByteBuffer.wrap(header);
            buffer.order(ByteOrder.LITTLE_ENDIAN); // 假设为小端序，若文件是大端序请改为 BIG_ENDIAN

            // 校验魔数 "JPHN" (偏移 0，大小 4)
            String magic = new String(header, 0, 4, StandardCharsets.US_ASCII);
            if (!"JPHN".equals(magic)) {
                Log.e(TAG, "Phone.dat magic number mismatch!");
                return;
            }

            // 版本号 (偏移 4，大小 2) -> buffer.getShort(4)
            // 保留字段 (偏移 6，大小 2)
            recordCount = buffer.getInt(8);   // 记录总数 (偏移 8)
            indexOffset = buffer.getInt(12);  // 索引区偏移 (偏移 12)
            indexLength = buffer.getInt(16);  // 索引区长度 (偏移 16)
            dictOffset = buffer.getInt(20);   // 字典区偏移 (偏移 20)
            dictLength = buffer.getInt(24);   // 字典区长度 (偏移 24)

            Log.i(TAG, "Phone.dat initialized successfully. Record count: " + recordCount);

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize phone.dat", e);
        }
    }

    /**
     * 归属地查询结果封装类
     */
    public static class PhoneInfo {
        public int phonePrefix;
        public String location; // 省份/城市
        public int carrierId;

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

    /**
     * 步骤二：根据手机号查询归属地
     * @param phoneNumber 完整手机号或前7位
     */
    public PhoneInfo query(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7 || datFile == null) {
            return null;
        }

        try {
            // 截取前 7 位并转为 uint32
            int prefix = Integer.parseInt(phoneNumber.substring(0, 7));

            // 步骤三：利用二分查找在索引区（每条固定 10 字节）中查找
            int low = 0;
            int high = recordCount - 1;
            int recordSize = 10; // 每条索引 10 字节：前7位(4) + city_id(4) + carrier_id(2)

            while (low <= high) {
                int mid = (low + high) >>> 1;
                long currentRecordOffset = (long) indexOffset + (long) mid * recordSize;

                datFile.seek(currentRecordOffset);
                byte[] indexBytes = new byte[recordSize];
                datFile.readFully(indexBytes);

                ByteBuffer buf = ByteBuffer.wrap(indexBytes);
                buf.order(ByteOrder.LITTLE_ENDIAN);

                int currentPrefix = buf.getInt(0); // 手机号前7位

                if (currentPrefix == prefix) {
                    int cityId = buf.getInt(4);       // city_id
                    int carrierId = buf.getShort(8);  // carrier_id (uint16)

                    PhoneInfo info = new PhoneInfo();
                    info.phonePrefix = currentPrefix;
                    info.carrierId = carrierId;
                    info.location = readDictionary(cityId); // 步骤四：从字典区读取文本
                    return info;
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
     * 步骤四：根据 city_id 或偏移从字典区读取文本数据
     * 为什么这样做：索引区只存储数字 ID（节省空间），实际省份/城市文本统一存放在字典区，
     * 通过 "uint16 长度前缀 + 文本数据" 的格式进行解码。
     */
    private String readDictionary(int cityId) {
        try {
            // 根据你的 dat 文件定义，cityId 可能是字典区的绝对偏移，也可能是索引指针。
            // 这里兼容常规的：cityId 代表字典区内的偏移量（或相对地址）。
            // 如果设计中 cityId 是字节偏移量：
            long targetOffset = (long) dictOffset + cityId;
            if (targetOffset >= dictOffset + dictLength) {
                return "未知地区";
            }

            datFile.seek(targetOffset);

            // 读取 uint16 长度前缀 (2字节)
            byte[] lenBytes = new byte[2];
            datFile.readFully(lenBytes);
            int strLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

            if (strLen <= 0 || strLen > 512) {
                return "未知地区";
            }

            // 读取文本数据
            byte[] strBytes = new byte[strLen];
            datFile.readFully(strBytes);
            return new String(strBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            Log.e(TAG, "Error reading dictionary", e);
        }
        return "未知地区";
    }
}
