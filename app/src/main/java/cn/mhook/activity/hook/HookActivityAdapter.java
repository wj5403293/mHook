package cn.mhook.activity.hook;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.lxj.xpopup.XPopup;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.nightonke.boommenu.BoomButtons.HamButton;
import com.nightonke.boommenu.BoomButtons.OnBMClickListener;
import com.nightonke.boommenu.BoomMenuButton;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.tamsiree.rxkit.RxActivityTool;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxEncodeTool;
import com.tamsiree.rxkit.RxTool;
import com.tamsiree.rxkit.view.RxToast;

import org.greenrobot.eventbus.EventBus;

import java.util.Base64;
import java.util.List;

import cn.mhook.activity.MainActivity;
import cn.mhook.activity.editcfg.EditHookActivity;
import cn.mhook.floatprint.FloatActivity;
import cn.mhook.mhook.CustomPopup;
import cn.mhook.mhook.EventMessage;
import cn.mhook.mhook.R;
import cn.mhook.mhook.contentprovider.jsonCfg;


public class HookActivityAdapter extends BaseQuickAdapter<HookActivityItem, BaseViewHolder> {

    private Activity activity;

    public HookActivityAdapter(@LayoutRes int layoutResId, @Nullable List<HookActivityItem> data, Activity activity) {
        super(layoutResId, data);
        this.activity = activity;
    }

    @Override
    protected void convert(final BaseViewHolder helper, final HookActivityItem item) {
        helper.setText(R.id.appName,item.getAppName())
                .setText(R.id.ver,item.getVer())
                .setText(R.id.pkg,item.getPkg())
                .setText(R.id.detail,item.getDetail())
                .setText(R.id.author,item.getAuthor())
                .setText(R.id.time,item.getTime());


        if (item.getCfgId()!=null&&!item.getCfgId().isEmpty()){
            helper.setVisible(R.id.num_backgroud,true);
        }else {
        }


        final BoomMenuButton bmb2 = helper.getView(R.id.bmb2);
        initMenu(bmb2,item);
        helper.getView(R.id.cfgInfoItem).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bmb2.boom();
            }
        });
        helper.getView(R.id.cfgInfoItem).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new QMUIDialog.MessageDialogBuilder(getContext())
                        .setTitle("标题")
                        .setMessage("确定要删除吗？")
                        .setSkinManager(QMUISkinManager.defaultInstance(getContext()))
                        .addAction("取消", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                dialog.dismiss();
                            }
                        })
                        .addAction(0, "删除", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                jsonCfg.delConfig(item.getPkg(),item.getCfgKey());
                                EventBus.getDefault().post(new EventMessage("sync",""));
                                dialog.dismiss();
                            }
                        })
                        .create().show();
                return true;
            }
        });
        final TextView enableTip = helper.getView(R.id.enableTip);
        final LinearLayout enableLay = helper.getView(R.id.enableLayout);
        if (item.getEnable()){
            enableTip.setText("已启用");
            enableLay.setBackgroundColor(getContext().getResources().getColor(R.color.green));
        }else {
            enableTip.setText("已禁用");
            enableLay.setBackgroundColor(getContext().getResources().getColor(R.color.app_color_theme_3));
        }
        enableLay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (enableTip.getText().equals("已启用")){
                    enableTip.setText("已禁用");
                    jsonCfg.setEnable(false,item.getCfgKey());
                    enableLay.setBackgroundColor(getContext().getResources().getColor(R.color.app_color_theme_3));
                }else {
                    enableTip.setText("已启用");
                    jsonCfg.setEnable(true,item.getCfgKey());
                    enableLay.setBackgroundColor(getContext().getResources().getColor(R.color.green));
                }
            }
        });
        final String appver = RxAppTool.getAppVersionName(getContext(),item.getPkg());
        int err = 1;
        if (appver!=null&&appver.equals(item.getVer())){
            err--;
        }
        if (err==0){
            helper.setBackgroundColor(R.id.errLayout,getContext().getResources().getColor(R.color.green));
        }
        helper.getView(R.id.errLayout).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new XPopup.Builder(getContext())
                        .asCustom(new CustomPopup(getContext(),item.getVer(),appver))
                        .show();
            }
        });
        helper.setText(R.id.err," "+err+" ");
    }

    private void initMenu(BoomMenuButton bmb2,final HookActivityItem item){
        bmb2.clearBuilders();
        bmb2.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("打开应用")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        if (RxAppTool.isInstallApp(getContext(),item.getPkg())){
                            RxAppTool.launchApp(getContext(),item.getPkg());
                        }else {
                            RxToast.error("未安装该应用");
                        }
                    }
                })
                .subNormalText("打开配置对应的应用"));
        bmb2.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("修改配置")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        Bundle bundle = new Bundle();
                        bundle.putString("KeyStr",item.getCfgKey());
                        RxActivityTool.skipActivity(getContext(), EditHookActivity.class,bundle);
                    }
                })
                .subNormalText("修改配置文件"));
        bmb2.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("以调试模式启动")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        if (RxAppTool.isInstallApp(getContext(),item.getPkg())){
                            new FloatActivity(activity,getContext());
                            if (PermissionUtils.checkPermission(getContext())){
                                RxAppTool.launchApp(RxTool.getContext(),item.getPkg());
                            }
                        }else {
                            RxToast.error("未安装该应用");
                        }
                    }
                })
                .subNormalText("打开调试窗并启动应用"));
    }

}
