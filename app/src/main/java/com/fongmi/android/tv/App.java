package com.fongmi.android.tv;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.EventIndex;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Path;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.LogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {

    private final ExecutorService executor;
    private final Handler handler;
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private final long time;
    private Hook hook;

    public App() {
        instance = this;
        executor = Executors.newFixedThreadPool(Constant.THREAD_POOL);
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
        time = System.currentTimeMillis();
        gson = new Gson();
    }

    // ... [其他静态方法保持不变] ...

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 新增：每次启动都清除所有数据
        clearAllApplicationData();
        
        Notify.createChannel();
        Logger.addLogAdapter(getLogAdapter());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        EventBus.builder().addIndex(new EventIndex()).installDefaultEventBus();
        CaocConfig.Builder.create().backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT).errorActivity(CrashActivity.class).apply();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }
        });
    }

    /**
     * 清除所有应用数据，恢复到初始状态
     */
    private void clearAllApplicationData() {
        try {
            // 1. 清空数据库所有配置
            AppDatabase.get().getConfigDao().deleteAll();
            
            // 2. 清空SharedPreferences中的配置标记
            Prefers.remove("config_0"); // 点播
            Prefers.remove("config_1"); // 直播
            Prefers.remove("config_2"); // 壁纸
            
            // 3. 清空内存配置缓存
            VodConfig.get().clear();
            LiveConfig.get().clear();
            WallConfig.get().clear();
            OkHttp.get().clear();
            
            // 4. 清除壁纸缓存文件
            clearWallpaperCache();
            
            // 5. 清除其他缓存文件（可选）
            clearTempFiles();
            
        } catch (Exception e) {
            Logger.e("Clear application data error: " + e.getMessage());
        }
    }
    
    /**
     * 清除壁纸缓存
     */
    private void clearWallpaperCache() {
        try {
            File wallDir = new File(getCacheDir(), "wall");
            if (wallDir.exists() && wallDir.isDirectory()) {
                deleteDirectory(wallDir);
            }
        } catch (Exception e) {
            Logger.e("Clear wallpaper cache error: " + e.getMessage());
        }
    }
    
    /**
     * 清除临时文件
     */
    private void clearTempFiles() {
        try {
            // 清除catvod相关缓存
            File cacheDir = Path.cache();
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                for (File file : cacheDir.listFiles()) {
                    if (file.getName().startsWith("tmp_") || file.getName().endsWith(".temp")) {
                        file.delete();
                    }
                }
            }
        } catch (Exception e) {
            Logger.e("Clear temp files error: " + e.getMessage());
        }
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        dir.delete();
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }
}