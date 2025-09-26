package com.fongmi.android.tv.api;

import android.util.Base64;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;
import okhttp3.Response;

public class Decoder {

    private static final Pattern JS_URI = Pattern.compile("\"(\\.|\\.\\.)/(.?|.+?)\\.js\\?(.?|.+?)\"");

    public static String getJson(String url, String tag) throws Exception {
        // 为所有域名添加完整参数集
        url = addUniversalQueryParams(url);

        try (Response res = OkHttp.newCall(url, tag).execute()) {
            HttpUrl httpUrl = res.request().url();
            int size = HttpUrl.parse(url).querySize();
            if (httpUrl.querySize() == size) url = httpUrl.toString();
            return verify(url, res.body().string());
        }
    }

    private static String addUniversalQueryParams(String url) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) return url;

        HttpUrl.Builder builder = httpUrl.newBuilder();

        // 时间相关参数
        builder.addQueryParameter("timestamp", String.valueOf(System.currentTimeMillis()));
        builder.addQueryParameter("time", String.valueOf(System.currentTimeMillis() / 1000));

        // 应用信息
        builder.addQueryParameter("app_version", BuildConfig.VERSION_NAME);
        builder.addQueryParameter("app_build", String.valueOf(BuildConfig.VERSION_CODE));
        builder.addQueryParameter("package_name", BuildConfig.APPLICATION_ID);

        // 设备信息
        builder.addQueryParameter("platform", "android");
        builder.addQueryParameter("device_type", "tv");
        builder.addQueryParameter("sdk_version", String.valueOf(android.os.Build.VERSION.SDK_INT));

        // 业务参数
        builder.addQueryParameter("source", "fongmi");
        builder.addQueryParameter("channel", "official");
        builder.addQueryParameter("language", Locale.getDefault().getLanguage());
        builder.addQueryParameter("device_id", getDeviceId());

        return builder.build().toString();
    }

    // 获取设备ID
    private static String getDeviceId() {
        try {
            return android.provider.Settings.Secure.getString(
                    com.fongmi.android.tv.App.get().getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String verify(String url, String data) throws Exception {
        if (data.isEmpty()) throw new Exception();
        if (Json.isObj(data)) return fix(url, data);
        if (data.contains("**")) data = base64(data);
        if (data.startsWith("2423")) data = cbc(data);
        return fix(url, data);
    }

    private static String fix(String url, String data) {
        Matcher matcher = JS_URI.matcher(data);
        while (matcher.find()) data = replace(url, data, matcher.group());
        if (data.contains("../")) data = data.replace("../", UrlUtil.resolve(url, "../"));
        if (data.contains("./")) data = data.replace("./", UrlUtil.resolve(url, "./"));
        if (data.contains("__JS1__")) data = data.replace("__JS1__", "./");
        if (data.contains("__JS2__")) data = data.replace("__JS2__", "../");
        return data;
    }

    private static String replace(String url, String data, String ext) {
        String t = ext.replace("\"./", "\"" + UrlUtil.resolve(url, "./"));
        t = t.replace("\"../", "\"" + UrlUtil.resolve(url, "../"));
        t = t.replace("./", "__JS1__").replace("../", "__JS2__");
        return data.replace(ext, t);
    }

    /**
     * CBC 解密逻辑
     * 数据格式：
     * 2423 + [HEX密文] + "$#" + key + "#$" + iv(13字节)
     */
    private static String cbc(String data) {
        try {
            // 1. 去掉前缀 2423
            String body = data.substring(4);

            // 2. 找到 key 标记
            int keyStart = body.indexOf("$#");
            int keyEnd = body.indexOf("#$");
            if (keyStart < 0 || keyEnd < 0 || keyEnd <= keyStart) {
                throw new Exception("Invalid key marker format");
            }

            // 3. 提取密文 hex
            String encryptedHex = body.substring(0, keyStart);

            // 4. 提取 key
            String key = body.substring(keyStart + 2, keyEnd);

            // 5. 提取 iv（#$/之后）
            String iv = body.substring(keyEnd + 2);

            // 6. 补齐 key 和 iv 到 16 字节
            key = padEnd(key);
            iv = padEnd(iv);

            // 7. 解密
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

            byte[] encryptedBytes = Util.hex2byte(encryptedHex);
            byte[] decryptData = cipher.doFinal(encryptedBytes);

            return new String(decryptData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return data;
        }
    }

    private static String base64(String data) {
        String extract = extract(data);
        if (extract.isEmpty()) return data;
        return new String(Base64.decode(extract, Base64.DEFAULT));
    }

    private static String extract(String data) {
        Matcher matcher = Pattern.compile("[A-Za-z0-9]{8}\\*\\*").matcher(data);
        return matcher.find() ? data.substring(data.indexOf(matcher.group()) + 10) : "";
    }

    private static String padEnd(String text) {
        if (text == null) return "0000000000000000";
        if (text.length() >= 16) return text.substring(0, 16);
        return text + "0000000000000000".substring(text.length());
    }
}
