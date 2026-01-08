package com.example.storecashier;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WebDAVManager {
    private static final String TAG = "WebDAVManager";
    private static final String PREFS_NAME = "webdav_config";

    // 伪装成 PC 端 Chrome 浏览器，解决坚果云 403 Forbidden 问题
    private static final String FAKE_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // 配置字段
    private String url;
    private String username;
    private String password;
    private String folder;

    private Context context;
    private DBHelper dbHelper;
    private Sardine sardine;

    public WebDAVManager(Context context, DBHelper dbHelper) {
        this.context = context;
        this.dbHelper = dbHelper;
        loadConfig();
        initSardine();
    }

    // 构建带有强伪装 Header 的 OkHttpClient
    private OkHttpClient createCustomClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS) // 下载大文件时增加超时时间
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request original = chain.request();
                        Request request = original.newBuilder()
                                .removeHeader("User-Agent") // 移除旧的
                                .header("User-Agent", FAKE_USER_AGENT) // 只有这一个 User-Agent
                                .header("Accept", "*/*")
                                .header("Cache-Control", "no-cache")
                                .build();
                        return chain.proceed(request);
                    }
                })
                .build();
    }

    private void initSardine() {
        // 使用自定义 Client 初始化 Sardine
        sardine = new OkHttpSardine(createCustomClient());
        if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
            sardine.setCredentials(username, password);
        }
    }

    /**
     * 测试连接
     */
    public boolean testConnection(String testUrl, String testUsername, String testPassword) {
        try {
            Log.d(TAG, "Testing connection to: " + testUrl);

            // 测试时也必须用伪装 Client
            Sardine testSardine = new OkHttpSardine(createCustomClient());
            testSardine.setCredentials(testUsername, testPassword);

            String validUrl = formatUrl(testUrl);

            // 尝试列出根目录，如果能列出说明连接和权限都 OK
            testSardine.list(validUrl);
            Log.d(TAG, "Connection successful (List root ok)");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Test connection failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private String formatUrl(String url) {
        if (url == null) return "";
        String formatted = url.trim();
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "https://" + formatted;
        }
        if (formatted.endsWith("/")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    private void loadConfig() {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        url = formatUrl(sharedPreferences.getString("url", ""));
        username = sharedPreferences.getString("username", "");
        password = sharedPreferences.getString("password", "");
        folder = sharedPreferences.getString("folder", "cashier");
    }

    public void saveConfig(String url, String username, String password, String folder) {
        this.url = formatUrl(url);
        this.username = username;
        this.password = password;
        this.folder = folder.replaceAll("^/|/$", "");
        if (this.folder.isEmpty()) {
            this.folder = "cashier";
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("url", this.url);
        editor.putString("username", username);
        editor.putString("password", password);
        editor.putString("folder", this.folder);
        editor.apply();

        initSardine();
    }

    public String getUrl() { return url; }

    // ================== 修改部分：统一文件名备份 ==================
    public boolean backup() {
        try {
            if (url.isEmpty()) {
                Log.e(TAG, "Backup failed: URL is empty");
                return false;
            }

            // 1. 统一生成文件名 (格式：Backup_yyyy-MM-dd_HH-mm-ss.json)
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String unifiedFileName = "Backup_" + sdf.format(new Date()) + ".json";

            // 2. 使用该文件名创建本地文件
            File localBackupFile = createLocalBackup(unifiedFileName);
            if (localBackupFile == null) return false;

            // 3. 确保云端文件夹存在
            String remoteFolderUrl = ensureFolderExists();

            // 4. 构建上传 URL (云端文件名与本地保持一致)
            String remoteFileUrl = remoteFolderUrl + "/" + unifiedFileName;

            // 5. 上传
            Log.d(TAG, "Uploading to: " + remoteFileUrl);
            sardine.put(remoteFileUrl, localBackupFile, "application/json");

            Log.d(TAG, "Backup successful: " + unifiedFileName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Backup failed exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private File createLocalBackup(String fileName) {
        try {
            File backupDir = new File(context.getExternalFilesDir(null), "webdav_cache");
            if (!backupDir.exists()) backupDir.mkdirs();

            File backupFile = new File(backupDir, fileName);

            List<Product> allProducts = dbHelper.getAllProducts();
            Gson gson = new Gson();
            String json = gson.toJson(allProducts);

            try (FileWriter writer = new FileWriter(backupFile)) {
                writer.write(json);
            }
            return backupFile;
        } catch (Exception e) {
            Log.e(TAG, "Local backup creation failed: " + e.getMessage());
            return null;
        }
    }

    // ================== 新增部分：获取备份列表和指定恢复 ==================

    // 获取云端备份文件列表
    public List<String> listBackupFiles() throws IOException {
        if (url.isEmpty()) return new ArrayList<>();

        String remoteFolderUrl = url + "/" + folder;
        Log.d(TAG, "Listing files from: " + remoteFolderUrl);

        List<DavResource> resources = sardine.list(remoteFolderUrl);
        List<String> fileNames = new ArrayList<>();

        for (DavResource res : resources) {
            // 过滤非文件夹且以.json结尾的文件
            if (!res.isDirectory() && res.getName().toLowerCase().endsWith(".json")) {
                fileNames.add(res.getName());
            }
        }
        // 按名称倒序排列（时间最新的在最上面）
        Collections.sort(fileNames, Collections.reverseOrder());
        return fileNames;
    }

    // 恢复指定文件名的备份
    public boolean restoreFile(String targetFileName) {
        try {
            if (url.isEmpty()) return false;

            // 1. 构建下载链接
            String remoteFolderUrl = url + "/" + folder;
            String downloadUrl = remoteFolderUrl + "/" + targetFileName;

            Log.d(TAG, "Downloading specific file: " + downloadUrl);

            // 2. 下载数据
            byte[] jsonData;
            try (InputStream inputStream = sardine.get(downloadUrl);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

                byte[] data = new byte[4096];
                int nRead;
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                jsonData = buffer.toByteArray();
            }

            // 3. 解析并入库
            String jsonStr = new String(jsonData, "UTF-8");
            Type listType = new TypeToken<List<Product>>(){}.getType();
            List<Product> products = new Gson().fromJson(jsonStr, listType);

            return dbHelper.importProductsFromList(products);

        } catch (Exception e) {
            Log.e(TAG, "Restore specific file failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // 优化的文件夹检查逻辑
    private String ensureFolderExists() throws IOException {
        String folderUrl = url + "/" + folder;

        try {
            if (sardine.exists(folderUrl)) {
                return folderUrl;
            }
        } catch (IOException e) {
            Log.w(TAG, "Exists check failed, trying to create anyway: " + e.getMessage());
        }

        try {
            Log.d(TAG, "Creating directory: " + folderUrl);
            sardine.createDirectory(folderUrl);
        } catch (IOException e) {
            if (!e.getMessage().contains("405") && !e.getMessage().contains("Method Not Allowed")) {
                throw e;
            }
        }
        return folderUrl;
    }
}