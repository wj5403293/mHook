package cn.mhook.floatprint;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.lzf.easyfloat.EasyFloat;
import com.lzf.easyfloat.enums.ShowPattern;
import com.lzf.easyfloat.enums.SidePattern;
import com.lzf.easyfloat.interfaces.OnFloatCallbacks;
import com.lzf.easyfloat.interfaces.OnInvokeView;
import com.lzf.easyfloat.permission.PermissionUtils;
import com.qmuiteam.qmui.util.QMUIDisplayHelper;
import com.qmuiteam.qmui.widget.dialog.QMUIDialog;
import com.qmuiteam.qmui.widget.dialog.QMUIDialogAction;
import com.qmuiteam.qmui.widget.tab.QMUITabBuilder;
import com.qmuiteam.qmui.widget.tab.QMUITabIndicator;
import com.qmuiteam.qmui.widget.tab.QMUITabSegment2;
import com.tamsiree.rxkit.RxTool;
import com.tamsiree.rxkit.view.RxToast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

import cn.mhook.floatprint.log.FloatPrintLog;
import cn.mhook.floatprint.setting.Setting;
import cn.mhook.mhook.EventMessage;
import cn.mhook.mhook.R;

public class FloatActivity {

    private Context context;
    private Activity activity;
    private QMUITabSegment2 qmuiTabSegment;
    private List<Fragment> mFragments;
    private ViewPager2 viewPager;
    Boolean canshow = false;

    public FloatActivity(Activity activity,Context context){
        this.activity = activity;
        this.context = context;
        initFloat();
    }
    private void initFloat(){
        if ( PermissionUtils.checkPermission(context)){
            showFloat();
        }else {
            new QMUIDialog.MessageDialogBuilder(context)
                    .setTitle("提示")
                    .setMessage("使用调试功能需要您授予悬浮窗权限")
                    .addAction("取消", new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                        }
                    })
                    .addAction(0, "去开启", QMUIDialogAction.ACTION_PROP_POSITIVE, new QMUIDialogAction.ActionListener() {
                        @Override
                        public void onClick(QMUIDialog dialog, int index) {
                            dialog.dismiss();
                            showFloat();
                        }
                    })
                    .create(com.qmuiteam.qmui.R.style.QMUI_Dialog).show();
        }
    }

    private void showFloat(){
        OnInvokeView onInvokeView = new OnInvokeView() {
            @Override
            public void invoke(final View view) {
                viewPager = view.findViewById(R.id.contentViewPager);
                mFragments = new ArrayList<>();
                mFragments.add(new FloatPrintLog());
                mFragments.add(new Setting());
                viewPager.setAdapter(new MyFragmentPagerAdapter((FragmentActivity) activity,mFragments));
                viewPager.setOffscreenPageLimit(2);
                viewPager.setUserInputEnabled(false);
                qmuiTabSegment =view.findViewById(R.id.tabSegment);
                QMUITabBuilder tabBuilder = qmuiTabSegment.tabBuilder()
                        .setGravity(Gravity.CENTER);
                tabBuilder.setColor(context.getResources().getColor(R.color.white),context.getResources().getColor(R.color.app_color_theme_7));
                qmuiTabSegment.setIndicator(new QMUITabIndicator(
                        QMUIDisplayHelper.dp2px(context, 2), false, false));
                qmuiTabSegment.addTab(tabBuilder
                        .setText("调试")
                        .build(context));
                qmuiTabSegment.addTab(tabBuilder
                        .setText("其他")
                        .build(context));
                qmuiTabSegment.setupWithViewPager(viewPager);
                qmuiTabSegment.notifyDataChanged();
                qmuiTabSegment.selectTab(0);

                view.findViewById(R.id.reduce).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        canshow=false;
                        EasyFloat.hideAppFloat("print");
                        initIcon();
                    }
                });


                view.findViewById(R.id.bar).setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()){
                            case MotionEvent.ACTION_DOWN:
                                EasyFloat.appFloatDragEnable(true,"print");
                                break;
                        }
                        return false;
                    }
                });

            }
        };
        EasyFloat.with(activity)
                .setLayout(R.layout.float_layout,onInvokeView)
                
                .setShowPattern(ShowPattern.ALL_TIME)
                
                .setSidePattern(SidePattern.RESULT_HORIZONTAL)
                
                .setMatchParent(true,false)
                .setTag("print")
                .setDragEnable(false)
                .registerCallbacks(new OnFloatCallbacks() {
                    @Override
                    public void createdResult(boolean isCreated, @Nullable String msg, @Nullable View view) {

                    }

                    @Override
                    public void show(View view) {

                    }

                    @Override
                    public void hide(View view) {

                    }

                    @Override
                    public void dismiss() {

                    }

                    @Override
                    public void touchEvent(View view, MotionEvent event) {

                    }

                    @Override
                    public void drag(View view, MotionEvent event) {

                    }

                    @Override
                    public void dragEnd(View view) {
                        EasyFloat.appFloatDragEnable(false,"print");
                    }
                })
                .show();
        EasyFloat.showAppFloat("print");
    }

    private void initIcon(){
        if (EasyFloat.getAppFloatView("icon")!=null){
            EasyFloat.showAppFloat("icon");
            return;
        }
        OnInvokeView onInvokeView = new OnInvokeView() {
            @Override
            public void invoke(final View view) {

                view.findViewById(R.id.fd).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        canshow=true;
                        EasyFloat.showAppFloat("print");
                        EasyFloat.hideAppFloat("icon");
                    }
                });
            }
        };
        EasyFloat.with(activity)
                .setLayout(R.layout.float_print_icon,onInvokeView)
                
                .setShowPattern(ShowPattern.ALL_TIME)
                
                .setSidePattern(SidePattern.RESULT_HORIZONTAL)
                .setTag("icon")
                .setAppFloatAnimator(null)
                .setLocation(0,300)
                .show();
    }

    class MyFragmentPagerAdapter extends FragmentStateAdapter {

        private List<Fragment> mFragments;

        public MyFragmentPagerAdapter(@NonNull FragmentActivity fragmentActivity, List<Fragment> fragments) {
            super(fragmentActivity);
            this.mFragments = fragments;
        }
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            return mFragments.get(position);
        }
        @Override
        public int getItemCount() {
            return mFragments.size();
        }
    }


}
