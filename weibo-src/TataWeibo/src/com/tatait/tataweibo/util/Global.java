package com.tatait.tataweibo.util;

import android.content.Context;
import android.os.Handler;

/**
 * 全局静态参数集合
 */
public class Global {
    public static Context mContext;
    public static final String PREFERENCES_NAME = "tatait_tataweibo";
    public static String app_name = "com.tatait.tataweibo";// app名称
    public static boolean hasSenAutoLoc = false;// 默认没有发送
    public static boolean voideohasplay = false;// 默认没有播放
    public static Handler handler;// 发布线程
    public static Handler handlerForLogin;
    public static Handler handlerForSMSLogin;
    public static Handler handlerForLogoff;

    public static final String SMSAPPKEY = "15bbdc943c550";
    public static final String SMSAPPSECRET = "0db671c382de62d6c0ef8079fdde79d6";

    // 判断首次启动的相关sharedPreferences
    public static final String LOAD = "tatait_load";
    public static final String NIGHT = "tatait_night_style";
    public static final String LOGIN_TYPE = "tatait_login_type";
    public static final String SMSUID = "sms_uid";

    public static final String MUSIC = "tatait_music";
    public static final String WQLOGINID = "wqloginid";

    public static final String QQ = "QQ";
    public static final String LOGIN_TYPE_NULL = "00";
    public static final String LOGIN_TYPE_WB = "01";
    public static final String LOGIN_TYPE_QQ = "02";
    public static final String LOGIN_TYPE_WX = "03";
    public static final String LOGIN_TYPE_YK = "04";
    public static final String LOGIN_TYPE_TEL = "05";
    public static final String IMAGE_URL = "image_url";
    public static final String GENDER = "gender";
    public static final String USERNAME = "username";
    public static final String VIDEOURL = "videoUrl";
    public static final String VIDEOIMAGE = "videoimage";

    public synchronized static void setApplicationContext(Context context) {
        mContext = context;
    }

    public static Context getApplicationContext() {
        return mContext;
    }
}
