package cn.mhook.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.arlib.floatingsearchview.FloatingSearchView;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemChildClickListener;
import com.lxj.xpopup.core.CenterPopupView;
import com.lxj.xpopup.util.XPopupUtils;
import com.tamsiree.rxkit.RxAppTool;

import java.util.ArrayList;
import java.util.List;

import cn.mhook.mhook.R;
import cn.mhook.mhook.mHookUtils;

public class SelectApp extends CenterPopupView {


    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private Handler handler;
    private List<SelectAppItem> datas = new ArrayList<>();
    private SelectAppAdapter adapter;
    private FloatingSearchView floatingSearchView;
    private String pkg;

    public SelectApp(@NonNull Context context) {
        super(context);
    }



    @Override
    protected int getImplLayoutId() {
        return R.layout.select_app;
    }

    @Override
    protected void onCreate() {
        super.onCreate();
        handler = new Handler();
        initListView();
    }

    @SuppressLint("ResourceAsColor")
    private void initListView(){
        findViewById(R.id.bac).setBackgroundColor(R.color.white);
        recyclerView = (RecyclerView) findViewById(R.id.config_recycler_view);
        refreshLayout=(SwipeRefreshLayout)findViewById(R.id.refresh_layout);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        recyclerView.setLayoutManager(layoutManager);
        refreshLayout.setRefreshing(true);
        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                initList("");
            }
        });
        initList("");
        adapter = new SelectAppAdapter(R.layout.select_app_item, datas);
        adapter.addChildClickViewIds(R.id.appInfoLayout);
        adapter.setOnItemChildClickListener(new OnItemChildClickListener() {
            @Override
            public void onItemChildClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                
                pkg = datas.get(position).getAppPkg();
                dismiss();
            }
        });
        recyclerView.setAdapter(adapter);
        floatingSearchView = findViewById(R.id.floating_search_view);
        floatingSearchView.setOnQueryChangeListener(new FloatingSearchView.OnQueryChangeListener() {
            @Override
            public void onSearchTextChanged(String oldQuery, String newQuery) {
                initList(newQuery);
            }
        });
    }


    private  void initList(final String query){
        new Thread(new Runnable(){
            @Override
            public void run(){
                if (datas.size()>0){
                    datas.clear();
                }
                List<RxAppTool.AppInfo> list;
                list = RxAppTool.getAllAppsInfo(getContext());
                for (RxAppTool.AppInfo info:list) {
                    if (info.getPackageName().contains(query)||info.getName().contains(query)){
                        datas.add(new SelectAppItem(info.getName(),info.getVersionName(),info.getPackageName(),info.getIcon()));
                    }
                }
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                        refreshLayout.setRefreshing(false);
                    }
                }, 0);
            }
        }).start();
    }

    @Override
    protected void onShow() {
        super.onShow();
    }

    @Override
    protected void onDismiss() {
        super.onDismiss();
    }

    
    @Override
    protected int getMaxWidth() {
        return (int) (XPopupUtils.getWindowHeight(getContext())*.99f);
    }
    
    @Override
    protected int getMaxHeight() {
        return (int) (XPopupUtils.getWindowHeight(getContext())*.80f);
    }

    public String getPkg() {
        return pkg;
    }

}

