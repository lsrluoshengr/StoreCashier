package com.example.storecashier;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class SettingsFragment extends Fragment {
    private DBHelper dbHelper;
    private static final int REQUEST_CODE_PICK_JSON_FILE = 101;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        TextView tvSettingsTitle = view.findViewById(R.id.tv_settings_title);
        TextView tvSettingsInfo = view.findViewById(R.id.tv_settings_info);

        Button btnWebDAVConfig = view.findViewById(R.id.btn_webdav_config);
        Button btnWebDAVBackup = view.findViewById(R.id.btn_webdav_backup);
        Button btnWebDAVRestore = view.findViewById(R.id.btn_webdav_restore);
        Button btnDataExport = view.findViewById(R.id.btn_data_export);
        Button btnDataImport = view.findViewById(R.id.btn_data_import);

        tvSettingsTitle.setText("系统设置");
        tvSettingsInfo.setText("便利店收银系统设置界面");

        dbHelper = new DBHelper(requireContext());



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
            // 检查是否有写入存储的权限
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                // 已有权限，执行导出
                exportProducts();
            } else {
                // 请求权限
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        });

        // 数据导入按钮点击事件
        btnDataImport.setOnClickListener(v -> {
            // 检查是否有读取存储的权限
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                // 已有权限，打开文件选择器
                openJsonFilePicker();
            } else {
                // 请求权限
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PICK_JSON_FILE);
            }
        });

        // 关于按钮点击事件
        Button btnAbout = view.findViewById(R.id.btn_about);
        btnAbout.setOnClickListener(v -> {
            // 跳转到关于页面
            Intent intent = new Intent(requireContext(), AboutActivity.class);
            startActivity(intent);
        });

        return view;
    }

    // 执行商品数据导出
    private void exportProducts() {
        boolean success = dbHelper.exportProductsToJson(requireContext());
        if (success) {
            Toast.makeText(requireContext(), "数据导出成功！文件保存在Download/Cashier目录", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "数据导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    // 打开JSON文件选择器
    private void openJsonFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_PICK_JSON_FILE);
    }

    // 权限请求结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) { // 导出权限请求
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限通过，执行导出
                exportProducts();
            } else {
                Toast.makeText(requireContext(), "需要存储权限才能导出数据", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_PICK_JSON_FILE) { // 导入权限请求
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限通过，打开文件选择器
                openJsonFilePicker();
            } else {
                Toast.makeText(requireContext(), "需要存储权限才能导入数据", Toast.LENGTH_SHORT).show();
            }
        }
    }
    // 处理文件选择结果
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_JSON_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                // 获取选中的JSON文件Uri
                Uri jsonFileUri = data.getData();
                try (java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(jsonFileUri);
                     java.io.InputStreamReader reader = new java.io.InputStreamReader(inputStream)) {
                    // 执行数据导入
                    boolean success = dbHelper.importProductsFromReader(reader);
                    if (success) {
                        Toast.makeText(requireContext(), "数据导入成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "数据导入失败", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(requireContext(), "数据导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}
