package com.ray.atten.desktop.utils;


public class SessionContext {

    private static String token;
    private static String username;
    private static boolean superAdmin;

    public static void setToken(String token) {
        SessionContext.token = token;
    }

    public static String getToken() {
        return token;
    }

    public static void setUsername(String username) {
        SessionContext.username = username;
    }

    public static String getUsername() {
        return username;
    }

    public static Boolean IsSuperAdmin() {
        return superAdmin;
    }

    public static void setSuperAdmin(Boolean superAdmin) {
        SessionContext.superAdmin = superAdmin;
    }


    public static void logout() {
        token = null;
        username = null;
    }

}
