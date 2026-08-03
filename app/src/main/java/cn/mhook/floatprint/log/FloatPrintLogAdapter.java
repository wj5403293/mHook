package cn.mhook.floatprint.log;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSONObject;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.SimpleCallback;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.tamsiree.rxkit.RxTool;
import com.tamsiree.rxkit.view.RxToast;
import com.tamsiree.rxui.view.dialog.RxDialog;

import java.util.List;

import cn.mhook.activity.MainActivity;
import cn.mhook.activity.SelectApp;
import cn.mhook.mhook.R;

public class FloatPrintLogAdapter extends BaseQuickAdapter<FloatPrintLogItem, BaseViewHolder> {


    public FloatPrintLogAdapter(@LayoutRes int layoutResId, @Nullable List<FloatPrintLogItem> data) {
        super(layoutResId, data);
    }

    @Override
    protected void convert(final BaseViewHolder helper, final FloatPrintLogItem item) {
        JSONObject msg = JSONObject.parseObject(item.getMsg());
        JSONObject j = new JSONObject(true);
        j.put("type",msg.getString("type"));
        j.put("msg",msg.getString("msg"));
        helper.setText(R.id.text_item,JSONObject.toJSONString(j,true));
        helper.getView(R.id.print_item).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View view = LayoutInflater.from(getContext()).inflate(R.layout.float_print_dialog,null);
                TextView t = view.findViewById(R.id.con);
                t.setText(JSONObject.toJSONString(msg,true));
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.activity);
                builder.setView(view);
                AlertDialog alert = builder.create();
                if (Build.VERSION.SDK_INT >= 23){
                    alert.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
                }else {
                    alert.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                }
                alert.show();
            }
        });
    }
}