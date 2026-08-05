package cn.mhook.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.alibaba.fastjson.JSONObject;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.XPopupCallback;

import cn.mhook.activity.dialog.DialogPopup;
import cn.mhook.mhook.R;

public class DialogActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dialog);
        String json = getIntent().getStringExtra("data");
        getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        new XPopup.Builder(this)
                .hasShadowBg(false)
                .moveUpToKeyboard(false) //如果不加这个，评论弹窗会移动到软键盘上面
                .setPopupCallback(new XPopupCallback() {
                    @Override
                    public void onCreated() {

                    }

                    @Override
                    public void beforeShow() {

                    }

                    @Override
                    public void onShow() {

                    }

                    @Override
                    public void onDismiss() {
                        finish();
                    }

                    @Override
                    public boolean onBackPressed() {
                        return false;
                    }
                })
                .asCustom(new DialogPopup(this,JSONObject.parseObject(json)))
                .show();
    }
}
