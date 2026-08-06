package cn.mhook.activity.editcfg;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.DialogInterface;
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
import com.tamsiree.rxkit.RxActivityTool;
import com.tamsiree.rxkit.RxAppTool;
import com.tamsiree.rxkit.RxEncryptTool;
import com.tamsiree.rxkit.RxFileTool;
import com.tamsiree.rxkit.RxTimeTool;
import com.tamsiree.rxkit.view.RxToast;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.mhook.BaseActivity;
import cn.mhook.activity.SelectApp;
import cn.mhook.activity.appxw.AppXWActivity;
import cn.mhook.activity.selectapp.SelectActivity;
import cn.mhook.mhook.EventMessage;
import cn.mhook.mhook.R;
import cn.mhook.mhook.contentprovider.appCfg;
import cn.mhook.mhook.contentprovider.jsonCfg;

public class EditHookActivity extends BaseActivity {

    QMUIGroupListView mGroupListView;
    QMUICommonListItemView edit;
    JSONArray hookList = new JSONArray();
    JSONObject config;
    QMUICommonListItemView appNameItem;

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
        Bundle extras = getIntent().getExtras();
        if (extras!=null&&extras.containsKey("KeyStr")){
            JSONObject jsonObject = jsonCfg.getCfgByKey(extras.getString("KeyStr"));
            config = jsonObject;
        }else if (extras!=null&&extras.containsKey("AiCfg")){
            config = JSONObject.parseObject(extras.getString("AiCfg"));
        }else {
            config = new JSONObject(true);
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
        /*
        bmb.addBuilder(new HamButton.Builder()
                .normalImageRes(R.drawable.eagle)
                .normalText("修改参数")
                .listener(new OnBMClickListener() {
                    @Override
                    public void onBoomButtonClick(int index) {
                        edit=null;
                        Intent intent = new Intent();
                        intent.setClass(EditHookActivity.this, EditSetParms.class);
                        startActivityForResult(intent, 1);
                    }
                })
                .subNormalText("修改方法的参数"));*/
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
                            String pkg = config.getString("appPkg");
                            Boolean hook_= !((appCfg.getAppCfg(pkg)!=null)&&(appCfg.getAppCfg(pkg).containsKey("hook+"))&&(appCfg.getAppCfg(pkg).getBoolean("hook+")));
                            appCfg.setAppCfg(pkg,"hook+",hook_);
                            bmb.getBoomButton(1).getTextView().setText(hook_?"禁用HOOK+":"启用HOOK+");
                        }
                    }
                })
                .subNormalText("解决部分应用HOOK失败"));
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == 2){
            Bundle b = data.getExtras();
            JSONObject hookCfg = JSONObject.parseObject(b.getString("data"));
            onHookResult(hookCfg);
        }
        if(requestCode==9008&&resultCode==RESULT_OK){
            String comment = data.getStringExtra("pkg");
            if (comment != null && !comment.isEmpty()) {
                appNameItem.setDetailText(RxAppTool.getAppName(EditHookActivity.this, comment));
                config.put("appPkg", comment);
                config.put("appName", RxAppTool.getAppName(EditHookActivity.this, comment));
                config.put("appVer", RxAppTool.getAppVersionName(EditHookActivity.this, comment));
            }

        }
    }

    private void onHookResult(final JSONObject hookCfg){
        final QMUICommonListItemView replaceItem = edit;
        final List<QMUICommonListItemView> dups = findDuplicates(hookCfg, replaceItem);
        if (dups.isEmpty()){
            doAddHook(hookCfg, replaceItem);
            return;
        }
        String cls = hookCfg.getString("className");
        String mtd = hookCfg.getString("methodName");
        if (dups.size() == 1){
            new QMUIDialog.MessageDialogBuilder(EditHookActivity.this)
                    .setTitle("重复配置")
                    .setMessage("已存在相同Hook配置\n类："+cls+"\n方法："+mtd+"\n是否覆盖？")
                    .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this))
                    .addAction("跳过", new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                        }
                    })
                    .addAction(0, "覆盖", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            removeHookItems(dups);
                            doAddHook(hookCfg, replaceItem);
                            dialog.dismiss();
                        }
                    })
                    .create().show();
        }else {
            QMUIDialog.MenuDialogBuilder menuBuilder = new QMUIDialog.MenuDialogBuilder(EditHookActivity.this)
                    .setTitle("存在 "+dups.size()+" 个相同配置，请选择")
                    .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this));
            menuBuilder.addItem("跳过当前", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            menuBuilder.addItem("覆盖当前", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removeHookItems(Collections.singletonList(dups.get(0)));
                    doAddHook(hookCfg, replaceItem);
                    dialog.dismiss();
                }
            });
            menuBuilder.addItem("跳过全部", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            menuBuilder.addItem("覆盖全部", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    removeHookItems(dups);
                    doAddHook(hookCfg, replaceItem);
                    dialog.dismiss();
                }
            });
            menuBuilder.create().show();
        }
    }

    private List<QMUICommonListItemView> findDuplicates(JSONObject hookCfg, QMUICommonListItemView exclude){
        List<QMUICommonListItemView> dups = new ArrayList<>();
        String cls = hookCfg.getString("className");
        String mtd = hookCfg.getString("methodName");
        if (cls == null || mtd == null){
            return dups;
        }
        for (Object o : hookList){
            QMUICommonListItemView item = (QMUICommonListItemView) o;
            if (item == exclude){
                continue;
            }
            Object tag = item.getTag();
            if (tag instanceof JSONObject){
                JSONObject j = (JSONObject) tag;
                if (cls.equals(j.getString("className")) && mtd.equals(j.getString("methodName"))){
                    dups.add(item);
                }
            }
        }
        return dups;
    }

    private void removeHookItems(List<QMUICommonListItemView> items){
        for (QMUICommonListItemView item : items){
            hookList.remove(item);
            mGroupListView.removeView(item);
        }
    }

    private void doAddHook(JSONObject hookCfg, QMUICommonListItemView replaceItem){
        if (replaceItem == null){
            addItem(hookCfg, getLongDetailItem());
        }else {
            hookList.remove(replaceItem);
            addItem(hookCfg, replaceItem);
            edit = null;
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
                final String pkg = config.getString("appPkg");
                final String newKey = RxEncryptTool.encryptMD5ToString(config.toJSONString());
                if (jsonCfg.getCfgByKey(newKey) != null){
                    new QMUIDialog.MessageDialogBuilder(EditHookActivity.this)
                            .setTitle("重复配置")
                            .setMessage("已存在相同配置，是否覆盖？")
                            .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this))
                            .addAction("跳过", new QMUIDialogAction.ActionListener() {
                                @Override
                                public void onClick(QMUIDialog dialog, int index) {
                                    dialog.dismiss();
                                }
                            })
                            .addAction(0, "覆盖", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                                @Override
                                public void onClick(QMUIDialog dialog, int index) {
                                    jsonCfg.delConfig(pkg,newKey);
                                    doAddNewConfig(pkg,newKey);
                                    dialog.dismiss();
                                }
                            })
                            .create().show();
                }else {
                    final List<JSONObject> samePkg = getCfgByPkg(pkg);
                    if (samePkg.isEmpty()){
                        doAddNewConfig(pkg,newKey);
                    }else if (samePkg.size() == 1){
                        new QMUIDialog.MessageDialogBuilder(EditHookActivity.this)
                                .setTitle("重复配置")
                                .setMessage("该软件已存在 1 个配置，是否覆盖？")
                                .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this))
                                .addAction("跳过", new QMUIDialogAction.ActionListener() {
                                    @Override
                                    public void onClick(QMUIDialog dialog, int index) {
                                        dialog.dismiss();
                                    }
                                })
                                .addAction(0, "覆盖", QMUIDialogAction.ACTION_PROP_NEGATIVE, new QMUIDialogAction.ActionListener() {
                                    @Override
                                    public void onClick(QMUIDialog dialog, int index) {
                                        jsonCfg.delConfig(pkg,samePkg.get(0).getString("KeyStr"));
                                        doAddNewConfig(pkg,newKey);
                                        dialog.dismiss();
                                    }
                                })
                                .create().show();
                    }else {
                        QMUIDialog.MenuDialogBuilder menuBuilder = new QMUIDialog.MenuDialogBuilder(EditHookActivity.this)
                                .setTitle("该软件已存在 "+samePkg.size()+" 个配置，请选择")
                                .setSkinManager(QMUISkinManager.defaultInstance(EditHookActivity.this));
                        menuBuilder.addItem("跳过当前", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        menuBuilder.addItem("覆盖当前", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                jsonCfg.delConfig(pkg,samePkg.get(0).getString("KeyStr"));
                                doAddNewConfig(pkg,newKey);
                                dialog.dismiss();
                            }
                        });
                        menuBuilder.addItem("跳过全部", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                        menuBuilder.addItem("覆盖全部", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                for (JSONObject j : samePkg){
                                    jsonCfg.delConfig(pkg,j.getString("KeyStr"));
                                }
                                doAddNewConfig(pkg,newKey);
                                dialog.dismiss();
                            }
                        });
                        menuBuilder.create().show();
                    }
                }
            }
        }else {
            RxToast.warning("似乎忘了点什么");
        }
    }

    private void doAddNewConfig(String pkg,String newKey){
        config.put("keyStr",newKey);
        Boolean success = jsonCfg.addCfg(pkg,true,false,newKey,config,false);
        if (success){
            RxToast.success("添加成功");
        }else {
            RxToast.warning("已存在相同配置");
        }
        finish();
    }

    private List<JSONObject> getCfgByPkg(String pkg){
        List<JSONObject> list = new ArrayList<>();
        JSONArray all = jsonCfg.getAllCfg();
        for (Object o : all){
            JSONObject j = JSONObject.parseObject(o.toString());
            if (pkg.equals(j.getString("pkg"))){
                list.add(j);
            }
        }
        return list;
    }

    private void SelectApp() {
        Bundle bundle = new Bundle();
        bundle.putString("appType", "all");
        RxActivityTool.skipActivityForResult(EditHookActivity.this, SelectActivity.class, bundle, 9008);
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
                appNameItem = selectApp ;
                SelectApp();
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
