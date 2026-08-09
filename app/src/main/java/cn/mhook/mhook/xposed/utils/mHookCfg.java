package cn.mhook.mhook.xposed.utils;

import cn.mhook.mData;

public class mHookCfg {

    public static String mDir = "";
    public static String fixDir = "";
    public static String dumpDir = "";
    public static String SettingDir = "";
    public static String logDir = "";
    public static String fixType = "";

    public static void init(){
        mDir =  mData.mDir+ H.pkg+"/";
        fixDir = mDir+"fix/mk.apk";
        dumpDir = mDir+"dump/";
        SettingDir = mDir+"Setting.json";
        logDir = mDir+"log.txt";
        fixType = mDir+"fix/config.json";
    }
}
