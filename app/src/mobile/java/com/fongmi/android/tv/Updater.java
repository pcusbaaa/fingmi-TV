package com.fongmi.android.tv;

import android.app.Activity;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Github;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.File;

public class Updater implements Download.Callback {

    private DialogUpdateBinding binding;
    private final Download download;
    private AlertDialog dialog;
    private boolean dev;

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getJson() {
        return Github.getJson(dev, BuildConfig.FLAVOR_mode);
    }

    private String getApk() {
        return Github.getApk(dev, BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi);
    }

    public static Updater create() {
        return new Updater();
    }

    public Updater() {
        this.download = Download.create(getApk(), getFile(), this);
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public Updater release() {
        this.dev = false;
        return this;
    }

    public Updater dev() {
        this.dev = true;
        return this;
    }

    private Updater check() {
        dismiss();
        return this;
    }

    public void start(Activity activity) {
        App.execute(() -> doInBackground(activity));
    }

    private boolean need(int code, String name) {
        return Setting.getUpdate() && (dev ? !name.equals(BuildConfig.VERSION_NAME) && code >= BuildConfig.VERSION_CODE : code > BuildConfig.VERSION_CODE);
    }

    private void doInBackground(Activity activity) {
        try {
            JSONObject object = new JSONObject(OkHttp.string(getJson()));
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");
            if (need(code, name)) App.post(() -> show(activity, name, desc));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void show(Activity activity, String version, String desc) {
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        check().create(activity, ResUtil.getString(R.string.update_version, version)).show();
        // 只设置确认按钮的监听器，移除取消按钮的监听器设置
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(this::confirm);
        binding.desc.setText(desc);
    }

    private AlertDialog create(Activity activity, String title) {
        // 移除 NegativeButton，只保留 PositiveButton
        return dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.update_confirm, null)
                .setCancelable(false) // 保持不可取消
                .create();
    }

    private void confirm(View view) {
        // 直接退出应用
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress) {
        // 空实现
    }

    @Override
    public void error(String msg) {
        // 空实现
    }

    @Override
    public void success(File file) {
        // 空实现
    }
}