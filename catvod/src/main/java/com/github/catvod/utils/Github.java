package com.github.catvod.utils;

import android.provider.Settings;
import android.util.Base64;

import com.fongmi.android.tv.BuildConfig;

public class Github {

    // 从BuildConfig读取Base64编码的URL（由Gradle在编译时注入）
    private static final String ENCODED_URL = BuildConfig.ENCODED_BASE_URL;

    // 解码后的基础URL
    private static String getBaseUrl() {
        return decodeUrl();
    }

    private static String getUrl(String path, String name) {
        return getBaseUrl() + "/" + path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        // 在固定JSON地址后面追加设备ID参数
        String baseUrl = getBaseUrl() + "/notice.php";
        return appendDeviceId(baseUrl);
    }

    public static String getApk(boolean dev, String name) {
        String apkUrl = getUrl("apk/" + (dev ? "dev" : "release"), name + ".apk");
        return appendDeviceId(apkUrl);
    }

    // 为URL追加设备ID参数
    private static String appendDeviceId(String url) {
        String deviceId = getDeviceId();
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "device_id=" + deviceId;
    }

    // 获取设备ID
    private static String getDeviceId() {
        try {
            String deviceId = Settings.Secure.getString(com.github.catvod.Init.context().getContentResolver(), Settings.Secure.ANDROID_ID);
            return deviceId != null ? deviceId : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Base64解码URL
     */
    private static String decodeUrl() {
        try {
            byte[] decodedBytes = Base64.decode(ENCODED_URL, Base64.DEFAULT);
            String decodedUrl = new String(decodedBytes, "UTF-8").trim();
            return decodedUrl;
        } catch (Exception e) {
            // 解码失败时返回空字符串或默认值
            return "";
        }
    }
}