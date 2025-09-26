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
        // 尝试解混淆
        if (isHex(data)) {
            String plain = deobfuscate(data);
            return fix(url, plain);
        }
        // 兼容 envelope/aes_cbc 格式
        if (Json.isObj(data)) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject(data);
                if (obj.has("type") && "aes_cbc".equals(obj.getString("type")) &&
                    obj.has("key") && obj.has("iv") && obj.has("data")) {

                    String keyHex = obj.getString("key");
                    String ivHex = obj.getString("iv");
                    String encData = obj.getString("data");

                    String plain = aesCbcDecrypt(encData, keyHex, ivHex);
                    return fix(url, plain);
                }
            } catch (Exception e) {
                // 不是信封格式，继续后面流程
            }
            return fix(url, data);
        }
        if (data.contains("**")) data = base64(data);
        if (data.startsWith("2423")) data = cbc(data);
        return fix(url, data);
    }

    /** 判断是否为 hex 字符串 **/
    private static boolean isHex(String s) {
        return s.matches("[0-9a-fA-F]+") && s.length() % 2 == 0 && s.length() > 20;
    }

    /**
     * 针对 obfuscate_payload 方案的解混淆
     * 步骤: hex解码 -> 去干扰符 -> 反转 -> 自定义base64表还原 -> base64解码
     */
    private static String deobfuscate(String data) {
        // 1. hex 解码
        byte[] bytes = hexStringToByteArray(data);
        String noisy = new String(bytes, StandardCharsets.UTF_8);
        // 2. 去除所有干扰符
        String clean = noisy.replaceAll("[#@&%\\*]", "");
        // 3. 反转字符串
        String rev = new StringBuilder(clean).reverse().toString();
        // 4. 自定义base64表还原
        String tableSrc = "QWERTYUIOPASDFGHJKLZXCVBNMpoiuytrewqasdfghjklmnbvcxz1234567890-_";
        String tableDst = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder b64 = new StringBuilder();
        for (int i = 0; i < rev.length(); ++i) {
            char c = rev.charAt(i);
            int idx = tableSrc.indexOf(c);
            b64.append(idx >= 0 ? tableDst.charAt(idx) : c);
        }
        // 5. base64 解码
        byte[] decoded = Base64.decode(b64.toString(), Base64.DEFAULT);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    // AES/CBC/PKCS5Padding 解密，key/iv 为 hex，data 为 base64
    private static String aesCbcDecrypt(String base64Data, String keyHex, String ivHex) throws Exception {
        byte[] key = hexStringToByteArray(keyHex);
        byte[] iv = hexStringToByteArray(ivHex);
        byte[] enc = Base64.decode(base64Data, Base64.DEFAULT);

        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decrypted = cipher.doFinal(enc);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
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

    // 保留原有 CBC/BASE64 解密方法
    private static String cbc(String data) throws Exception {
        String decode = new String(Util.hex2byte(data)).toLowerCase();
        String key = padEnd(decode.substring(decode.indexOf("$#") + 2, decode.indexOf("#$")));
        String iv = padEnd(decode.substring(decode.length() - 13));
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes());
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        data = data.substring(data.indexOf("2324") + 4, data.length() - 26);
        byte[] decryptData = cipher.doFinal(Util.hex2byte(data));
        return new String(decryptData, StandardCharsets.UTF_8);
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

    private static String padEnd(String key) {
        return key + "0000000000000000".substring(key.length());
    }
}