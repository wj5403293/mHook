package cn.mhook.mhook;

import android.content.Context;

import androidx.annotation.NonNull;

import com.lxj.xpopup.animator.PopupAnimator;
import com.lxj.xpopup.core.CenterPopupView;
import com.qmuiteam.qmui.widget.grouplist.QMUICommonListItemView;
import com.qmuiteam.qmui.widget.grouplist.QMUIGroupListView;

public  class CustomPopup extends CenterPopupView {

    String cfgVer,appVer;

    public CustomPopup(@NonNull Context context, String cfgVer, String appVer) {
        super(context);
        this.cfgVer = cfgVer;
        this.appVer = appVer;
    }

    
    @Override
    protected int getImplLayoutId() {
        return R.layout.hook_activity_status_popup;
    }
    
    @Override
    protected void onCreate() {
        super.onCreate();
        QMUIGroupListView mGroupListView = findViewById(R.id.groupListView);
        QMUICommonListItemView cfg = getQMUICommonListItemView(mGroupListView,"版本对比",cfgVer.equals(appVer)?"通过":"不通过");
        cfg.getDetailTextView().setTextColor(getResources().getColor(cfgVer.equals(appVer)?R.color.green:R.color.red));
        QMUIGroupListView.newSection(getContext())
                .setTitle("配置检测")
                .addItemView(cfg,null)
                .addItemView(getQMUICommonListItemView(mGroupListView,"配置版本",cfgVer),null)
                .addItemView(getQMUICommonListItemView(mGroupListView,"应用版本",appVer),null)
                .addTo(mGroupListView);
    }

    private QMUICommonListItemView getQMUICommonListItemView(QMUIGroupListView mGroupListView, String title,String detail){
        QMUICommonListItemView qmuiCommonListItemView = mGroupListView.createItemView(title);
        qmuiCommonListItemView.setAccessoryType(QMUICommonListItemView.ACCESSORY_TYPE_NONE);
        qmuiCommonListItemView.setDetailText(detail);
        return qmuiCommonListItemView;
    }

    
    @Override
    protected int getMaxWidth() {
        return super.getMaxWidth();
    }
    
    @Override
    protected int getMaxHeight() {
        return super.getMaxHeight();
    }
    
    @Override
    protected PopupAnimator getPopupAnimator() {
        return super.getPopupAnimator();
    }
    




    protected int getPopupWidth() {
        return 0;
    }

    




    protected int getPopupHeight() {
        return 0;
    }
}
