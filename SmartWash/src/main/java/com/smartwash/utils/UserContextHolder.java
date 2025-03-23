package com.smartwash.utils;

public class UserContextHolder {
    private static final ThreadLocal<LoginUser> userThreadLocal = new ThreadLocal<>();

    public static void setUser(LoginUser user) {
        userThreadLocal.set(user);
    }

    public static LoginUser getUser() {
        return userThreadLocal.get();
    }

    public static void clear() {
        userThreadLocal.remove();
    }
}
