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
    private ProductDao productDao;
    private Sardine sardine;

    public WebDAVManager(Context context) {
        this.context = context;
        this.productDao = AppDatabase.getDatabase(context).productDao();
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

    // ================== 备份：文件夹结构（products.json + images/） ==================
    public boolean backup() {
        try {
            if (url.isEmpty()) {
                Log.e(TAG, "Backup failed: URL is empty");
                return false;
            }

            // 1. 生成备份文件夹名
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String backupFolderName = "Backup_" + sdf.format(new Date());

            // 2. 创建本地 JSON 文件
            List<Product> allProducts = productDao.getAllProductsSync();
            File localJsonFile = createLocalJsonBackup(allProducts);
            if (localJsonFile == null) return false;

            // 3. 确保云端根目录存在，然后创建备份子文件夹
            String remoteRootUrl = ensureFolderExists();
            String backupFolderUrl = remoteRootUrl + "/" + backupFolderName;
            ensureSubfolderExists(backupFolderUrl);

            // 4. 上传 products.json
            String remoteJsonUrl = backupFolderUrl + "/products.json";
            Log.d(TAG, "Uploading JSON to: " + remoteJsonUrl);
            sardine.put(remoteJsonUrl, localJsonFile, "application/json");

            // 5. 上传商品图片
            uploadImages(allProducts, backupFolderUrl);

            Log.d(TAG, "Backup successful: " + backupFolderName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Backup failed exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private File createLocalJsonBackup(List<Product> allProducts) {
        try {
            File backupDir = new File(context.getExternalFilesDir(null), "webdav_cache");
            if (!backupDir.exists()) backupDir.mkdirs();

            File backupFile = new File(backupDir, "products.json");

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

    private void uploadImages(List<Product> products, String backupFolderUrl) {
        String imagesFolderUrl = backupFolderUrl + "/images";
        boolean imagesFolderCreated = false;

        for (Product product : products) {
            String imagePath = product.getImagePath();
            if (imagePath == null || imagePath.isEmpty()) continue;

            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                Log.w(TAG, "Image file not found, skipping: " + imagePath);
                continue;
            }

            if (!imagesFolderCreated) {
                try {
                    ensureSubfolderExists(imagesFolderUrl);
                    imagesFolderCreated = true;
                } catch (IOException e) {
                    Log.e(TAG, "Failed to create images folder: " + e.getMessage());
                    return;
                }
            }

            String remoteImageUrl = imagesFolderUrl + "/" + imageFile.getName();
            try {
                Log.d(TAG, "Uploading image: " + remoteImageUrl);
                sardine.put(remoteImageUrl, imageFile, "image/jpeg");
            } catch (IOException e) {
                Log.e(TAG, "Failed to upload image " + imageFile.getName() + ": " + e.getMessage());
            }
        }
    }

    // ================== 获取备份列表和恢复 ==================

    // 获取云端备份文件夹列表
    public List<String> listBackupFolders() throws IOException {
        if (url.isEmpty()) return new ArrayList<>();

        String remoteFolderUrl = url + "/" + folder;
        Log.d(TAG, "Listing folders from: " + remoteFolderUrl);

        List<DavResource> resources = sardine.list(remoteFolderUrl);
        List<String> folderNames = new ArrayList<>();

        for (DavResource res : resources) {
            if (res.isDirectory() && res.getName().startsWith("Backup_")) {
                folderNames.add(res.getName());
            }
        }
        // 按名称倒序排列（时间最新的在最上面）
        Collections.sort(folderNames, Collections.reverseOrder());
        return folderNames;
    }

    // 恢复指定备份文件夹
    public boolean restoreFile(String folderName) {
        try {
            if (url.isEmpty()) return false;

            String remoteFolderUrl = url + "/" + folder;
            String backupFolderUrl = remoteFolderUrl + "/" + folderName;

            // 1. 下载 products.json
            String jsonUrl = backupFolderUrl + "/products.json";
            Log.d(TAG, "Downloading JSON: " + jsonUrl);

            byte[] jsonData;
            try (InputStream inputStream = sardine.get(jsonUrl);
                 ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] data = new byte[4096];
                int nRead;
                while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                jsonData = buffer.toByteArray();
            }

            String jsonStr = new String(jsonData, "UTF-8");
            Type listType = new TypeToken<List<Product>>(){}.getType();
            List<Product> products = new Gson().fromJson(jsonStr, listType);

            // 2. 下载图片文件到本地
            downloadImages(backupFolderUrl, products);

            // 3. 入库
            productDao.insertAll(products);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Restore failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void downloadImages(String backupFolderUrl, List<Product> products) {
        String imagesFolderUrl = backupFolderUrl + "/images";
        File localImagesDir = new File(context.getFilesDir(), "images");
        if (!localImagesDir.exists()) localImagesDir.mkdirs();

        // 列出远程 images 目录
        List<DavResource> imageResources;
        try {
            imageResources = sardine.list(imagesFolderUrl);
        } catch (IOException e) {
            Log.w(TAG, "No images folder in backup, skipping image restore");
            return;
        }

        for (DavResource res : imageResources) {
            if (res.isDirectory()) continue;

            String fileName = res.getName();
            String remoteImageUrl = imagesFolderUrl + "/" + fileName;
            File localImageFile = new File(localImagesDir, fileName);

            try (InputStream is = sardine.get(remoteImageUrl);
                 java.io.FileOutputStream fos = new java.io.FileOutputStream(localImageFile)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                Log.d(TAG, "Downloaded image: " + fileName);
            } catch (IOException e) {
                Log.e(TAG, "Failed to download image " + fileName + ": " + e.getMessage());
            }
        }

        // 更新所有商品的 imagePath 为新的本地路径
        for (Product product : products) {
            String oldPath = product.getImagePath();
            if (oldPath == null || oldPath.isEmpty()) continue;

            String fileName = new File(oldPath).getName();
            File newFile = new File(localImagesDir, fileName);
            if (newFile.exists()) {
                product.setImagePath(newFile.getAbsolutePath());
            }
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

    private void ensureSubfolderExists(String subfolderUrl) throws IOException {
        try {
            if (sardine.exists(subfolderUrl)) {
                return;
            }
        } catch (IOException e) {
            Log.w(TAG, "Exists check failed for subfolder, trying to create: " + e.getMessage());
        }

        try {
            Log.d(TAG, "Creating subfolder: " + subfolderUrl);
            sardine.createDirectory(subfolderUrl);
        } catch (IOException e) {
            if (!e.getMessage().contains("405") && !e.getMessage().contains("Method Not Allowed")) {
                throw e;
            }
        }
    }
}