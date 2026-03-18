package com.ray.atten.desktop.utils;

import lombok.Data;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.prefs.Preferences;

public class CredentialManager {
    private static final String NODE_NAME = "/com/ray/atten/auth";
    private static final String AES_KEY = "RayAttenAppKey!!"; // 建议 16 位，实际可根据机器码动态生成
    private static final Preferences prefs = Preferences.userRoot().node(NODE_NAME);

    @Data
    public static class LoginInfo {
        private String username;
        private String password;
        private boolean rememberMe;
    }

    public static void saveCredentials(String user, String pass, boolean remember) {
        prefs.putBoolean("remember", remember);
        if (remember) {
            prefs.put("user", user);
            prefs.put("pass", encrypt(pass));
        } else {
            prefs.remove("user");
            prefs.remove("pass");
        }
    }

    public static LoginInfo loadCredentials() {
        LoginInfo info = new LoginInfo();
        info.rememberMe = prefs.getBoolean("remember", false);
        if (info.rememberMe) {
            info.username = prefs.get("user", "");
            info.password = decrypt(prefs.get("pass", ""));
        }
        return info;
    }

    private static String encrypt(String content) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] result = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) { return ""; }
    }

    private static String decrypt(String content) {
        try {
            SecretKeySpec key = new SecretKeySpec(AES_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] result = cipher.doFinal(Base64.getDecoder().decode(content));
            return new String(result, StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
}