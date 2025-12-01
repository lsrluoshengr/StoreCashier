package com.example.storecashier;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {
    private DBHelper dbHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        TextView tvSettingsTitle = view.findViewById(R.id.tv_settings_title);
        TextView tvSettingsInfo = view.findViewById(R.id.tv_settings_info);
        Button btnLocalBackup = view.findViewById(R.id.btn_local_backup);
        Button btnLocalRestore = view.findViewById(R.id.btn_local_restore);
        Button btnWebDAVConfig = view.findViewById(R.id.btn_webdav_config);
        Button btnWebDAVBackup = view.findViewById(R.id.btn_webdav_backup);
        Button btnWebDAVRestore = view.findViewById(R.id.btn_webdav_restore);
        Button btnDataExport = view.findViewById(R.id.btn_data_export);
        Button btnDataImport = view.findViewById(R.id.btn_data_import);

        tvSettingsTitle.setText("系统设置");
        tvSettingsInfo.setText("便利店收银系统设置界面");

        dbHelper = new DBHelper(requireContext());

        // 本地备份按钮点击事件
        btnLocalBackup.setOnClickListener(v -> {
            boolean success = dbHelper.backupDatabase(requireContext());
            if (success) {
                Toast.makeText(requireContext(), "本地备份成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "本地备份失败", Toast.LENGTH_SHORT).show();
            }
        });

        // 本地恢复按钮点击事件
        btnLocalRestore.setOnClickListener(v -> {
            boolean success = dbHelper.restoreDatabase(requireContext());
            if (success) {
                Toast.makeText(requireContext(), "本地恢复成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "本地恢复失败", Toast.LENGTH_SHORT).show();
            }
        });

        // WebDAV配置按钮点击事件
        btnWebDAVConfig.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "WebDAV配置功能开发中", Toast.LENGTH_SHORT).show();
        });

        // WebDAV备份按钮点击事件
        btnWebDAVBackup.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "WebDAV云备份功能开发中", Toast.LENGTH_SHORT).show();
        });

        // WebDAV恢复按钮点击事件
        btnWebDAVRestore.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "WebDAV云恢复功能开发中", Toast.LENGTH_SHORT).show();
        });

        // 数据导出按钮点击事件
        btnDataExport.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "数据导出功能开发中", Toast.LENGTH_SHORT).show();
        });

        // 数据导入按钮点击事件
        btnDataImport.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "数据导入功能开发中", Toast.LENGTH_SHORT).show();
        });

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}
