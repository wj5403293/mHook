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

    // 返回自定义弹窗的布局
    @Override
    protected int getImplLayoutId() {
        return R.layout.hook_activity_status_popup;
    }
    // 执行初始化操作，比如：findView，设置点击，或者任何你弹窗内的业务逻辑
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

    // 设置最大宽度，看需要而定
    @Override
    protected int getMaxWidth() {
        return super.getMaxWidth();
    }
    // 设置最大高度，看需要而定
    @Override
    protected int getMaxHeight() {
        return super.getMaxHeight();
    }
    // 设置自定义动画器，看需要而定
    @Override
    protected PopupAnimator getPopupAnimator() {
        return super.getPopupAnimator();
    }
    /**
     * 弹窗的宽度，用来动态设定当前弹窗的宽度，受getMaxWidth()限制
     *
     * @return
     */
    protected int getPopupWidth() {
        return 0;
    }

    /**
     * 弹窗的高度，用来动态设定当前弹窗的高度，受getMaxHeight()限制
     *
     * @return
     */
    protected int getPopupHeight() {
        return 0;
    }
}
