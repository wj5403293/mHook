package cn.mhook.mhook.xposed.dump;

import com.tamsiree.rxkit.RxFileTool;

import cn.mhook.mhook.xposed.utils.H;

import static cn.mhook.mhook.xposed.utils.mHookCfg.dumpDir;

public class StartDump {
    public static void init() {
        if (RxFileTool.isFileExists(dumpDir)) {
            MemoryDexDumper.init(H.loadPackageParam);
        }
    }
}
