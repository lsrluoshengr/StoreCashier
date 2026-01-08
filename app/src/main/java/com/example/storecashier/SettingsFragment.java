package com.example.storecashier;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class SettingsFragment extends Fragment {
    private DBHelper dbHelper;
    private WebDAVManager webDAVManager; // WebDAV管理器实例
    private static final int REQUEST_CODE_PICK_JSON_FILE = 101;

    // 静态内部类用于WebDAV备份
    private static class WebDAVBackupTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<Context> contextRef;
        private WebDAVManager webDAVManager;

        public WebDAVBackupTask(Context context, WebDAVManager webDAVManager) {
            this.contextRef = new WeakReference<>(context);
            this.webDAVManager = webDAVManager;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                return webDAVManager.backup();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Context context = contextRef.get();
            if (context != null) {
                if (success) {
                    Toast.makeText(context, "WebDAV云备份成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "WebDAV云备份失败，请检查日志", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // ================== 新增：获取备份列表的任务 ==================
    private static class GetBackupListTask extends AsyncTask<Void, Void, List<String>> {
        private WeakReference<Context> contextRef;
        private WebDAVManager webDAVManager;
        private Callback callback;

        // 回调接口，用于把列表传回 Fragment
        public interface Callback {
            void onListLoaded(List<String> fileNames);
            void onError(String errorMsg);
        }

        public GetBackupListTask(Context context, WebDAVManager webDAVManager, Callback callback) {
            this.contextRef = new WeakReference<>(context);
            this.webDAVManager = webDAVManager;
            this.callback = callback;
        }

        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return webDAVManager.listBackupFiles();
            } catch (Exception e) {
                e.printStackTrace();
                return null; // 返回 null 表示出错
            }
        }

        @Override
        protected void onPostExecute(List<String> fileNames) {
            if (fileNames == null) {
                if (callback != null) callback.onError("获取备份列表失败，请检查网络或配置");
            } else if (fileNames.isEmpty()) {
                if (callback != null) callback.onError("云端没有找到 .json 备份文件");
            } else {
                if (callback != null) callback.onListLoaded(fileNames);
            }
        }
    }

    // ================== 修改：WebDAV恢复任务（接收文件名参数） ==================
    private static class WebDAVRestoreTask extends AsyncTask<String, Void, Boolean> {
        private WeakReference<Context> contextRef;
        private WebDAVManager webDAVManager;

        public WebDAVRestoreTask(Context context, WebDAVManager webDAVManager) {
            this.contextRef = new WeakReference<>(context);
            this.webDAVManager = webDAVManager;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String fileName = params[0]; // 获取传入的文件名
            try {
                return webDAVManager.restoreFile(fileName);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Context context = contextRef.get();
            if (context != null) {
                if (success) {
                    Toast.makeText(context, "数据恢复成功！", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "数据恢复失败，请查看日志", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    // 静态内部类用于WebDAV连接测试
    private static class WebDAVTestTask extends AsyncTask<Void, Void, Boolean> {
        private WeakReference<Context> contextRef;
        private WebDAVManager webDAVManager;
        private String url, username, password;
        private WeakReference<Button> btnRef;

        public WebDAVTestTask(Context context, WebDAVManager webDAVManager, String url, String username, String password, Button btnTest) {
            this.contextRef = new WeakReference<>(context);
            this.webDAVManager = webDAVManager;
            this.url = url;
            this.username = username;
            this.password = password;
            this.btnRef = new WeakReference<>(btnTest);
        }

        @Override
        protected void onPreExecute() {
            Button btn = btnRef.get();
            if (btn != null) {
                btn.setEnabled(false);
                btn.setText("正在连接...");
            }
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return webDAVManager.testConnection(url, username, password);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            Context context = contextRef.get();
            Button btn = btnRef.get();
            if (btn != null) {
                btn.setEnabled(true);
                btn.setText("测试连接");
            }
            if (context != null) {
                if (success) {
                    Toast.makeText(context, "连接成功！", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "连接失败，请检查配置或日志(403)", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

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
        webDAVManager = new WebDAVManager(requireContext(), dbHelper); // 初始化WebDAV管理器

        // WebDAV配置按钮点击事件
        btnWebDAVConfig.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
            builder.setTitle("WebDAV配置");

            View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_webdav_config, null);
            builder.setView(dialogView);

            final EditText etUrl = dialogView.findViewById(R.id.et_webdav_url);
            final EditText etUsername = dialogView.findViewById(R.id.et_webdav_username);
            final EditText etPassword = dialogView.findViewById(R.id.et_webdav_password);
            final EditText etFolder = dialogView.findViewById(R.id.et_webdav_folder);
            final Button btnTestConnection = dialogView.findViewById(R.id.btn_test_connection);

            SharedPreferences sharedPreferences = requireContext().getSharedPreferences("webdav_config", Context.MODE_PRIVATE);
            etUrl.setText(sharedPreferences.getString("url", ""));
            etUsername.setText(sharedPreferences.getString("username", ""));
            etPassword.setText(sharedPreferences.getString("password", ""));
            etFolder.setText(sharedPreferences.getString("folder", ""));

            btnTestConnection.setOnClickListener(testView -> {
                String url = etUrl.getText().toString().trim();
                String username = etUsername.getText().toString().trim();
                String password = etPassword.getText().toString().trim();

                if (url.isEmpty()) {
                    Toast.makeText(requireContext(), "请输入服务器地址", Toast.LENGTH_SHORT).show();
                    return;
                }

                new WebDAVTestTask(requireContext(), webDAVManager, url, username, password, btnTestConnection).execute();
            });

            builder.setPositiveButton("保存", (dialog, which) -> {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("url", etUrl.getText().toString().trim());
                editor.putString("username", etUsername.getText().toString().trim());
                editor.putString("password", etPassword.getText().toString().trim());
                editor.putString("folder", etFolder.getText().toString().trim());
                editor.apply();

                // 更新 Manager 中的配置
                webDAVManager.saveConfig(
                        etUrl.getText().toString().trim(),
                        etUsername.getText().toString().trim(),
                        etPassword.getText().toString().trim(),
                        etFolder.getText().toString().trim()
                );

                Toast.makeText(requireContext(), "WebDAV配置已保存", Toast.LENGTH_SHORT).show();
            });

            builder.setNegativeButton("取消", null);
            builder.create().show();
        });

        // WebDAV备份按钮点击事件
        btnWebDAVBackup.setOnClickListener(v -> {
            if (webDAVManager.getUrl().isEmpty()) {
                Toast.makeText(requireContext(), "请先配置WebDAV服务器地址", Toast.LENGTH_SHORT).show();
                return;
            }
            new WebDAVBackupTask(requireContext(), webDAVManager).execute();
        });

        // ================== 修改：WebDAV恢复按钮点击事件 ==================
        btnWebDAVRestore.setOnClickListener(v -> {
            if (webDAVManager.getUrl().isEmpty()) {
                Toast.makeText(requireContext(), "请先配置WebDAV服务器地址", Toast.LENGTH_SHORT).show();
                return;
            }

            // 显示加载提示
            Toast.makeText(requireContext(), "正在获取云端备份列表...", Toast.LENGTH_SHORT).show();

            // 执行获取列表任务
            new GetBackupListTask(requireContext(), webDAVManager, new GetBackupListTask.Callback() {
                @Override
                public void onListLoaded(List<String> fileNames) {
                    // 列表获取成功，显示选择对话框
                    showBackupSelectionDialog(fileNames);
                }

                @Override
                public void onError(String errorMsg) {
                    Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
                }
            }).execute();
        });

        // 数据导出按钮点击事件
        btnDataExport.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                exportProducts();
            } else {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
            }
        });

        // 数据导入按钮点击事件
        btnDataImport.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                openJsonFilePicker();
            } else {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PICK_JSON_FILE);
            }
        });

        // 关于按钮点击事件
        Button btnAbout = view.findViewById(R.id.btn_about);
        btnAbout.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AboutActivity.class);
            startActivity(intent);
        });

        return view;
    }

    // ================== 新增：显示选择文件的对话框 ==================
    private void showBackupSelectionDialog(List<String> fileNames) {
        if (fileNames == null || fileNames.isEmpty()) return;

        // 将 List 转换为数组供 Dialog 使用
        String[] items = fileNames.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("请选择要恢复的备份");

        // 设置列表项和点击事件
        builder.setItems(items, (dialog, which) -> {
            String selectedFileName = items[which];

            // 用户确认恢复
            new AlertDialog.Builder(requireContext())
                    .setTitle("确认恢复")
                    .setMessage("即将恢复文件：" + selectedFileName + "\n当前本地数据将被覆盖，是否继续？")
                    .setPositiveButton("确定", (confirmDialog, confirmWhich) -> {
                        // 执行恢复任务，传入选中的文件名
                        new WebDAVRestoreTask(requireContext(), webDAVManager).execute(selectedFileName);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void exportProducts() {
        boolean success = dbHelper.exportProductsToJson(requireContext());
        if (success) {
            Toast.makeText(requireContext(), "数据导出成功！文件保存在Download/Cashier目录", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "数据导出失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void openJsonFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_CODE_PICK_JSON_FILE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportProducts();
            } else {
                Toast.makeText(requireContext(), "需要存储权限才能导出数据", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_CODE_PICK_JSON_FILE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openJsonFilePicker();
            } else {
                Toast.makeText(requireContext(), "需要存储权限才能导入数据", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_JSON_FILE && resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                Uri jsonFileUri = data.getData();
                try (java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(jsonFileUri);
                     java.io.InputStreamReader reader = new java.io.InputStreamReader(inputStream, "UTF-8")) {
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