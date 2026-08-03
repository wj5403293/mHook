package cn.mhook.activity.dialog;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSONObject;
import com.lxj.xpopup.core.BottomPopupView;
import com.lxj.xpopup.util.XPopupUtils;

import cn.mhook.mhook.R;

public class DialogPopup extends BottomPopupView {

    JSONObject data;

    public DialogPopup(@NonNull Context context, JSONObject data) {
        super(context);
        this.data = data;
    }
    @Override
    protected int getImplLayoutId() {
        return R.layout.popup_appxw_dialog;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        TextView viewInfo = findViewById(R.id.viewInfo);
      
    }
    
    @Override
    protected int getMaxHeight() {
        return (int) (XPopupUtils.getWindowHeight(getContext())*.55f);
    }

}
