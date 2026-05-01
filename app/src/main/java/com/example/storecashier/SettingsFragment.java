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
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import static android.app.Activity.RESULT_OK;

public class SettingsFragment extends Fragment {
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

    // 获取云端备份文件夹列表的任务
    private static class GetBackupListTask extends AsyncTask<Void, Void, List<String>> {
        private WeakReference<Context> contextRef;
        private WebDAVManager webDAVManager;
        private Callback callback;

        public interface Callback {
            void onListLoaded(List<String> folderNames);
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
                return webDAVManager.listBackupFolders();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(List<String> folderNames) {
            if (folderNames == null) {
                if (callback != null) callback.onError("获取备份列表失败，请检查网络或配置");
            } else if (folderNames.isEmpty()) {
                if (callback != null) callback.onError("云端没有找到备份记录");
            } else {
                if (callback != null) callback.onListLoaded(folderNames);
            }
        }
    }

    private static class WebDAVRestoreTask extends AsyncTask<String, Void, Boolean> {
        private WeakReference<Context> contextRef;
        private WebDAVManager webDAVManager;

        public WebDAVRestoreTask(Context context, WebDAVManager webDAVManager) {
            this.contextRef = new WeakReference<>(context);
            this.webDAVManager = webDAVManager;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            String folderName = params[0];
            try {
                return webDAVManager.restoreFile(folderName);
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
                    Toast.makeText(context, "数据和图片恢复成功！", Toast.LENGTH_SHORT).show();
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

        webDAVManager = new WebDAVManager(requireContext()); // 初始化WebDAV管理器

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

            Toast.makeText(requireContext(), "正在获取云端备份列表...", Toast.LENGTH_SHORT).show();

            new GetBackupListTask(requireContext(), webDAVManager, new GetBackupListTask.Callback() {
                @Override
                public void onListLoaded(List<String> folderNames) {
                    showBackupSelectionDialog(folderNames);
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

        // 查看历史订单按钮点击事件
        Button btnViewOrders = view.findViewById(R.id.btn_view_orders);
        btnViewOrders.setOnClickListener(v -> {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new OrderHistoryFragment())
                    .addToBackStack(null)
                    .commit();
        });

        // 关于按钮点击事件
        Button btnAbout = view.findViewById(R.id.btn_about);
        btnAbout.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AboutActivity.class);
            startActivity(intent);
        });

        return view;
    }

    private void showBackupSelectionDialog(List<String> folderNames) {
        if (folderNames == null || folderNames.isEmpty()) return;

        String[] items = folderNames.toArray(new String[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("选择要恢复的备份（含图片）");

        builder.setItems(items, (dialog, which) -> {
            String selectedFolder = items[which];

            new AlertDialog.Builder(requireContext())
                    .setTitle("确认恢复")
                    .setMessage("即将恢复备份：" + selectedFolder + "\n商品数据和图片将一并恢复，当前本地数据将被覆盖，是否继续？")
                    .setPositiveButton("确定", (confirmDialog, confirmWhich) -> {
                        new WebDAVRestoreTask(requireContext(), webDAVManager).execute(selectedFolder);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void exportProducts() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                ProductDao dao = AppDatabase.getDatabase(requireContext()).productDao();
                List<Product> allProducts = dao.getAllProductsSync();

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());
                String folderName = "Backup_" + sdf.format(new java.util.Date());

                java.io.File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                java.io.File backupDir = new java.io.File(downloadDir, "Cashier/" + folderName);
                if (!backupDir.exists()) backupDir.mkdirs();

                // 导出 JSON
                java.io.File exportFile = new java.io.File(backupDir, "products.json");
                com.google.gson.Gson gson = new com.google.gson.Gson();
                try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(exportFile), "UTF-8")) {
                    gson.toJson(allProducts, writer);
                    writer.flush();
                }

                // 复制商品图片到 images 子目录
                java.io.File imagesDir = new java.io.File(backupDir, "images");
                boolean hasImages = false;
                for (Product product : allProducts) {
                    String imagePath = product.getImagePath();
                    if (imagePath == null || imagePath.isEmpty()) continue;

                    java.io.File srcFile = new java.io.File(imagePath);
                    if (!srcFile.exists()) continue;

                    if (!hasImages) {
                        imagesDir.mkdirs();
                        hasImages = true;
                    }

                    java.io.File dstFile = new java.io.File(imagesDir, srcFile.getName());
                    try (java.io.InputStream in = new java.io.FileInputStream(srcFile);
                         java.io.OutputStream out = new java.io.FileOutputStream(dstFile)) {
                        byte[] buf = new byte[4096];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                    }
                }

                String msg = hasImages
                        ? "导出成功！含图片，保存在 Download/Cashier/" + folderName
                        : "导出成功！保存在 Download/Cashier/" + folderName;
                String finalMsg = msg;
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), finalMsg, Toast.LENGTH_SHORT).show()
                );
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "数据导出失败", Toast.LENGTH_SHORT).show()
                );
            }
        });
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
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    try (java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(jsonFileUri);
                         java.io.InputStreamReader reader = new java.io.InputStreamReader(inputStream, "UTF-8")) {
                        
                        com.google.gson.Gson gson = new com.google.gson.Gson();
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<List<Product>>(){}.getType();
                        List<Product> products = gson.fromJson(reader, listType);
                        
                        ProductDao dao = AppDatabase.getDatabase(requireContext()).productDao();
                        dao.insertAll(products);
                        
                        requireActivity().runOnUiThread(() -> 
                            Toast.makeText(requireContext(), "数据导入成功", Toast.LENGTH_SHORT).show()
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                        requireActivity().runOnUiThread(() -> 
                            Toast.makeText(requireContext(), "数据导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
                    }
                });
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}