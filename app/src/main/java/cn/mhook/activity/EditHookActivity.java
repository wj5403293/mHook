package cn.mhook.activity;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.SimpleCallback;
import com.nightonke.boommenu.BoomButtons.HamButton;
import com.nightonke.boommenu.BoomButtons.OnBMClickListener;
import com.nightonke.boommenu.BoomMenuButton;
import com.qmuiteam.qmui.skin.QMUISkinManager;
import com.qmuiteam.qmui.util.QMUIDisplayHelper;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxEncryptTool;
import com.tamsiree.rxkit.RxFileTool;
import com.tamsiree.rxkit.RxTimeTool;
import com.tamsiree.rxkit.view.RxToast;

import org.greenrobot.eventbus.EventBus;

import cn.mhook.BaseActivity;
import cn.mhook.mhook.EventMessage;
import cn.mhook.mhook.R;
import cn.mhook.mhook.contentprovider.appCfg;
import cn.mhook.mhook.contentprovider.jsonCfg;

public class EditHookActivity extends BaseActivity {

    QMUIGroupListView mGroupListView;
    QMUICommonListItemView edit;
    JSONArray hookList = new JSONArray();
    JSONObject config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_hook);
        mGroupListView = findViewById(R.id.groupListView);
        initEdit();
        initBoomMenu();
        initGroupList();
        CardView save = findViewById(R.id.save);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCfg();
            }
        });
    }

    private void initEdit(){
        if (getIntent().getExtras()!=null&&getIntent().getExtras().containsKey("KeyStr")){
            JSONObject jsonObject = jsonCfg.getCfgByKey(getIntent().getExtras().getString("KeyStr"));
            config = jsonObject;
        }else {
            config = new JSONObject();
        }
    }

    private void initBoomMenu(){
        BoomMenuButton bmb = findViewById(R.id.bmb);
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("修改返回值")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        edit=null;
                        Intent intent = new Intent();
                        intent.setClass(EditHookActivity.this, EditSetReturn.class);
                        startActivityForResult(intent, 1);
                    }
                })
                .subNormalText("修改程序的指定类指定方法的返回值"));
        String pkg = config.getString("appPkg");
        String nText;
        if (pkg==null){
            nText = "启用HOOK+";
        }else {
            nText = ((appCfg.getAppCfg(pkg)!=null)&&(appCfg.getAppCfg(pkg).containsKey("hook+"))&&(appCfg.getAppCfg(pkg).getBoolean("hook+")))?"禁用HOOK+":"启用HOOK+";
        }
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText(nText)
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        if (config.containsKey("appPkg")){
                            Boolean hook_= !((appCfg.getAppCfg(pkg)!=null)&&(appCfg.getAppCfg(pkg).containsKey("hook+"))&&(appCfg.getAppCfg(pkg).getBoolean("hook+")));
                            appCfg.setAppCfg(pkg,"hook+",hook_);
                            bmb.getBoomButton(1).getTextView().setText(hook_?"禁用HOOK+":"启用HOOK+");
                        }
                    }
                })
                .subNormalText("解决部分应用HOOK失败"));
        /*
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("附加设置")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        edit=null;
                        Intent intent = new Intent();
                        intent.setClass(EditHookActivity.this, SettingProActivity.class);
                        startActivityForResult(intent, 1);
                    }
                })
                .subNormalText("对应用程序设置的扩展"));*/
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == 2){
            Bundle b = data.getExtras();
            JSONObject hookCfg = JSONObject.parseObject(b.getString("data"));
            if (edit==null){
                addItem(hookCfg,getLongDetailItem());
            }else {
                hookList.remove(edit);
                addItem(hookCfg,edit);
                edit = null;
            }
        }
    }

    private void addItem(final JSONObject cfg, final QMUICommonListItemView item){
        switch (cfg.getString("hookType")){
            case "setRet":
                item.setText("修改返回值");
                String detail ="类： "+ cfg.getString("className")+"\n方法： "+cfg.getString("methodName");
                item.setDetailText(detail);
                item.setTag(cfg);
                item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        edit = item;
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("data",cfg.toJSONString());
                        intent.putExtras(bundle);
                        intent.setClass(EditHookActivity.this, EditSetReturn.class);
                        startActivityForResult(intent, 1);
                    }
                });
                break;
        }
        if (edit==null){
            mGroupListView.addView(item);
        }
        hookList.add(item);
    }

    private void saveCfg(){
        if (config.containsKey("appPkg")&&hookList.size()>0){
            JSONArray hooks = new JSONArray();
            for (Object o:hookList) {
                QMUICommonListItemView test = (QMUICommonListItemView)o;
                hooks.add(JSONObject.parseObject(test.getTag().toString()));
            }
            config.put("hooks",hooks);
            config.put("time",RxTimeTool.getCurTimeString());
            if (config.containsKey("keyStr")){
                new QMUIDialog.MessageDialogBuilder(EditHookActivity.this)
                        .setTitle("标题")
                        .setMessage("是否覆盖原配置")
                        .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this))
                        .addAction("否", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                config.put("keyStr",RxEncryptTool.encryptMD5ToString(config.toJSONString()));
                                Boolean success = jsonCfg.addCfg(config.getString("appPkg"),true,false,config.getString("keyStr"),config,false);
                                if (success){
                                    RxToast.success("添加成功");
                                }else {
                                    RxToast.warning("已存在相同配置");
                                }
                                dialog.dismiss();
                                finish();
                            }
                        })
                        .addAction(0, "是", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                jsonCfg.delConfig(config.getString("appPkg"),config.getString("keyStr"));
                                Boolean success = jsonCfg.addCfg(config.getString("appPkg"),true,false,config.getString("keyStr"),config,false);
                                if (success){
                                    RxToast.success("添加成功");
                                }else {
                                    RxToast.warning("已存在相同配置");
                                }
                                dialog.dismiss();
                                finish();
                            }
                        })
                        .create().show();
            }else {
                config.put("keyStr",RxEncryptTool.encryptMD5ToString(config.toJSONString()));
                Boolean success = jsonCfg.addCfg(config.getString("appPkg"),true,false,config.getString("keyStr"),config,false);
                if (success){
                    RxToast.success("添加成功");
                }else {
                    RxToast.warning("已存在相同配置");
                }
                finish();
            }
        }else {
            RxToast.warning("似乎忘了点什么");
        }
    }

    private void SelectApp(final QMUICommonListItemView qmuiCommonListItemView){
        final SelectApp selectApp = new SelectApp(this);
        new XPopup.Builder(this)
                .autoOpenSoftInput(false)
                .hasShadowBg(true)
                .setPopupCallback(new SimpleCallback() {
                    @Override
                    public void onShow() {

                    }

                    @Override
                    public void onDismiss() {
                        String comment = selectApp.getPkg();
                        if (comment!=null&&!comment.isEmpty()) {
                            qmuiCommonListItemView.setDetailText(RxAppTool.getAppName(EditHookActivity.this,comment));
                            config.put("appPkg",comment);
                            config.put("appName",RxAppTool.getAppName(EditHookActivity.this,comment));
                            config.put("appVer",RxAppTool.getAppVersionName(EditHookActivity.this,comment));
                        }
                    }
                })
                .asCustom(selectApp)
                .show();
    }

    private QMUICommonListItemView getLongDetailItem(){
        final QMUICommonListItemView longTitleAndDetail = mGroupListView.createItemView(null,
                "",
                "",
                QMUICommonListItemView.VERTICAL,
                QMUICommonListItemView.ACCESSORY_TYPE_NONE,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        int paddingVer = QMUIDisplayHelper.dp2px(this, 12);
        longTitleAndDetail.setPadding(longTitleAndDetail.getPaddingLeft(), paddingVer,
                longTitleAndDetail.getPaddingRight(), paddingVer);
        longTitleAndDetail.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                new QMUIDialog.MessageDialogBuilder(EditHookActivity.this)
                        .setTitle("标题")
                        .setMessage("确定要删除吗？")
                        .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this))
                        .addAction("取消", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                dialog.dismiss();
                            }
                        })
                        .addAction(0, "删除", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                hookList.remove(longTitleAndDetail);
                                mGroupListView.removeView(longTitleAndDetail);
                                dialog.dismiss();
                            }
                        })
                        .create().show();
                return true;
            }
        });
        return longTitleAndDetail;
    }

    private void initGroupList(){
        final QMUICommonListItemView selectApp = mGroupListView.createItemView("选择应用");
        selectApp.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_CHEVRON);
        selectApp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectApp(selectApp);
            }
        });
        mGroupListView.addView(selectApp);
        final QMUICommonListItemView author = mGroupListView.createItemView("作者");
        author.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_NONE);
        author.setDetailText("匿名作者");
        author.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final QMUIDialog.EditTextDialogBuilder builder = new QMUIDialog.EditTextDialogBuilder(EditHookActivity.this);
                builder.setTitle("作者")
                        .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this))
                        .setPlaceholder("在此输入昵称")
                        .setDefaultText(author.getDetailText())
                        .setInputType(InputType.TYPE_CLASS_TEXT)
                        .addAction("取消", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                dialog.dismiss();
                            }
                        })
                        .addAction("确定", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                CharSequence text = builder.getEditText().getText();
                                author.setDetailText(text);
                                config.put("author",text);
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
        mGroupListView.addView(author);
        final QMUICommonListItemView detail = getLongDetailItem();
        detail.setText("备注");
        detail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final QMUIDialog.EditTextDialogBuilder builder = new QMUIDialog.EditTextDialogBuilder(EditHookActivity.this);
                builder.setTitle("备注")
                        .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this))
                        .setPlaceholder("在此输入备注")
                        .setDefaultText(detail.getDetailText())
                        .setInputType(InputType.TYPE_CLASS_TEXT)
                        .addAction("取消", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                dialog.dismiss();
                            }
                        })
                        .addAction("确定", new QMUIDialogAction.ActionListener() {
                            @Override
                            public void onClick(QMUIDialog dialog, int index) {
                                CharSequence text = builder.getEditText().getText();
                                detail.setDetailText(text);
                                config.put("detail",text);
                                dialog.dismiss();
                            }
                        })
                        .create().show();
            }
        });
        mGroupListView.addView(detail);
        if (config.containsKey("hooks")){
            selectApp.setDetailText(config.getString("appName"));
            detail.setDetailText(config.getString("detail"));
            author.setDetailText(config.getString("author"));
            for (Object o:config.getJSONArray("hooks")){
                addItem(JSONObject.parseObject(o.toString()),getLongDetailItem());
            }
        }
    }
}
