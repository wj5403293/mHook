package cn.mhook.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.didikee.donate.AlipayDonate;
import android.didikee.donate.WeiXinDonate;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import com.alibaba.fastjson.JSONObject;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;
import com.tamsiree.rxkit.view.RxToast;

import java.io.File;
import java.io.InputStream;

import cn.mhook.App;
import cn.mhook.BaseActivity;
import cn.mhook.activity.intro.IntroActivity;
import cn.mhook.mhook.R;
import eu.darken.rxshell.cmd.Cmd;
import cn.mhook.msu.su;

import static cn.mhook.mData.mDir;


public class SettingActivity extends BaseActivity {

    QMUIGroupListView mGroupListView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        initGroupList();
    }

    private void initGroupList(){
        mGroupListView = findViewById(R.id.groupListView);
        QMUIGroupListView.newSection(this)
                .setTitle("设置")
                .addItemView(getListItem("调试模式","调试日志存放在mhook包名路径"), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        App.setEnable("debug",!App.enable("debug"));
                        RxToast.info(App.enable("debug")?"已启用调试":"已禁用调试");
                    }
                })
                .addTo(mGroupListView);
        QMUIGroupListView.newSection(this)
                .setTitle("关于")
                .addItemView(getListItem("用户协议","重新阅读用户协议与使用需知"), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(SettingActivity.this, IntroActivity.class);
                        SettingActivity.this.startActivity(intent);
                        finish();
                    }
                })
                .addItemView(getListItem("打赏支持", "请若雪吃鸡腿哦"), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String[] items = new String[]{"支付宝", "微信"};
                        new QMUIDialog.MenuDialogBuilder(SettingActivity.this)
                                .setSkinManager(QMUISkinManager.defaultInstance(SettingActivity.this))
                                .addItems(items, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        switch (which){
                                            case 0:
                                                donateAlipay("fkx19481bhemqj0n3fkdr69");
                                                break;
                                            case 1:
                                                donateWeixin();
                                                break;
                                        }
                                        dialog.dismiss();
                                    }
                                })
                                .create().show();
                    }
                })
                .addTo(mGroupListView);

    }

    private void donateAlipay(String payCode) {
        boolean hasInstalledAlipayClient = AlipayDonate.hasInstalledAlipayClient(this);
        if (hasInstalledAlipayClient) {
            AlipayDonate.startAlipayClient(this, payCode);
        }
    }

    private void donateWeixin() {
        InputStream weixinQrIs = getResources().openRawResource(R.raw.happy);
        String qrPath = "/sdcard/data/Android/data/cn.mhook.mhook/files" + File.separator + "AndroidDonateSample" + File.separator +
                "didikee_weixin.png";
        RxToast.success("二维码保存在相册哦");
        WeiXinDonate.saveDonateQrImage2SDCard(qrPath, BitmapFactory.decodeStream(weixinQrIs));
        WeiXinDonate.donateViaWeiXin(this, qrPath);
    }

    private QMUICommonListItemView getListItem(String title,String detail){
        QMUICommonListItemView statusCheck = mGroupListView.createItemView(title);
        statusCheck.setOrientation(QMUICommonListItemView.VERTICAL);
        statusCheck.setDetailText(detail);
        statusCheck.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_NONE);
        return statusCheck;
    }
}
