package com.github.catvod.utils;

import android.provider.Settings;

public class Github {

    public static final String URL = "http://28918185.xyz:2504/tvbox_admin";

    private static String getUrl(String path, String name) {
        return URL + "/" + path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        // 在固定JSON地址后面追加设备ID参数
        String baseUrl = URL + "/notice/server.php";
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
}