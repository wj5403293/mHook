package cn.mhook.mhook.config;

import android.content.Context;
import android.util.Log;

import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxFileTool;

import java.io.File;
import java.util.List;

import cn.mhook.mhook.contentprovider.jsonCfg;

import static cn.mhook.mData.jsonDir;

public class jsonUtils {
    public static void syncCfg(Context context){
        new Thread(new Runnable(){
            @Override
            public void run(){
              RxAppTool.getAllAppsInfo(context);
               if(jsonCfg.getAllCfg().isEmpty()){
                   for (File file:RxFileTool.listFilesInDir(jsonDir,false)){
                       if (RxFileTool.isFileExists(file.getPath()+"/jsonCfg")){
                           Log.i("test---",file.getPath());
                           RxFileTool.delAllFile(file.getPath()+"/jsonCfg");
                       }
                   }
                }
            }
        }).start();
    }
}
