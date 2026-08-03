package cn.mhook.activity.fix;

import android.os.Bundle;
import android.os.Handler;

import cn.mhook.BaseActivity;
import cn.mhook.mhook.R;

public class MKFix extends BaseActivity {

    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mkfix);
        handler = new Handler();
    }

}
