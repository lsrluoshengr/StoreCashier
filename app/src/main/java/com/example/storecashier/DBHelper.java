package com.example.storecashier;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class DBHelper extends SQLiteOpenHelper {
    // 数据库配置
    private static final String DB_NAME = "ConvenienceStoreDB"; // 数据库名
    private static final int DB_VERSION = 1; // 版本号
    private static final String TABLE_PRODUCT = "product"; // 商品表名

    // 表字段
    private static final String KEY_ID = "id";
    private static final String KEY_BARCODE = "barcode";
    private static final String KEY_NAME = "name";
    private static final String KEY_PRICE = "price";
    private static final String KEY_STOCK = "stock";

    // 构造方法
    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // 创建表（首次启动时执行）
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTableSql = "CREATE TABLE " + TABLE_PRODUCT + " (" +
                KEY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                KEY_BARCODE + " TEXT UNIQUE NOT NULL, " + // 条形码唯一
                KEY_NAME + " TEXT NOT NULL, " +
                KEY_PRICE + " REAL NOT NULL, " + // 价格（浮点型）
                KEY_STOCK + " INTEGER NOT NULL DEFAULT 0)"; // 库存默认0
        db.execSQL(createTableSql);
    }

    // 数据库升级（版本号变更时执行）
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PRODUCT);
        onCreate(db);
    }

    // 1. 添加商品（录入商品）
    public boolean addProduct(Product product) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_BARCODE, product.getBarcode());
        values.put(KEY_NAME, product.getName());
        values.put(KEY_PRICE, product.getPrice());
        values.put(KEY_STOCK, product.getStock());

        // 插入数据（返回-1表示失败，如条形码重复）
        long result = db.insert(TABLE_PRODUCT, null, values);
        db.close();
        return result != -1;
    }

    // 2. 根据条形码查询商品（扫码结算/库存查询）
    public Product getProductByBarcode(String barcode) {
        SQLiteDatabase db = this.getReadableDatabase();
        Product product = null;

        // 查询条件：条形码匹配
        Cursor cursor = db.query(
                TABLE_PRODUCT,
                new String[]{KEY_ID, KEY_BARCODE, KEY_NAME, KEY_PRICE, KEY_STOCK},
                KEY_BARCODE + " = ?",
                new String[]{barcode},
                null, null, null
        );

        if (cursor.moveToFirst()) { // 找到商品
            product = new Product();
            product.setId(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID)));
            product.setBarcode(cursor.getString(cursor.getColumnIndexOrThrow(KEY_BARCODE)));
            product.setName(cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)));
            product.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_PRICE)));
            product.setStock(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STOCK)));
        }
        cursor.close();
        db.close();
        return product;
    }

    // 3. 更新商品库存（结算后扣减库存）
    public boolean updateProductStock(String barcode, int newStock) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_STOCK, newStock);

        // 更新条件：条形码匹配
        int rowsAffected = db.update(
                TABLE_PRODUCT,
                values,
                KEY_BARCODE + " = ?",
                new String[]{barcode}
        );
        db.close();
        return rowsAffected > 0;
    }

    // 更新商品信息（名称、价格、库存）
    public boolean updateProduct(String barcode, String name, double price, int stock) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_NAME, name);
        values.put(KEY_PRICE, price);
        values.put(KEY_STOCK, stock);

        // 更新条件：条形码匹配
        int rowsAffected = db.update(
                TABLE_PRODUCT,
                values,
                KEY_BARCODE + " = ?",
                new String[]{barcode}
        );
        db.close();
        return rowsAffected > 0;
    }

    // 本地备份：将商品数据导出为JSON文件
    public boolean backupDatabase(Context context) {
        try {
            // 获取所有商品数据
            List<Product> allProducts = getAllProducts();

            // 创建Gson实例
            Gson gson = new Gson();

            // 将商品列表转换为JSON字符串
            String json = gson.toJson(allProducts);

            // 创建备份文件
            File backupDir = context.getFilesDir();
            File backupFile = new File(backupDir, "product_backup.json");

            // 写入JSON到文件
            try (FileWriter writer = new FileWriter(backupFile)) {
                writer.write(json);
                writer.flush();
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 本地恢复：从JSON文件导入商品数据
    public boolean restoreDatabase(Context context) {
        try {
            // 创建Gson实例
            Gson gson = new Gson();

            // 读取备份文件
            File backupDir = context.getFilesDir();
            File backupFile = new File(backupDir, "product_backup.json");

            // 将JSON文件内容转换为商品列表
            Type productListType = new TypeToken<List<Product>>(){}.getType();
            List<Product> restoredProducts;

            try (Reader reader = new FileReader(backupFile)) {
                restoredProducts = gson.fromJson(reader, productListType);
            }

            // 清空当前商品表
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_PRODUCT, null, null);

            // 插入恢复的商品数据
            for (Product product : restoredProducts) {
                ContentValues values = new ContentValues();
                values.put(KEY_BARCODE, product.getBarcode());
                values.put(KEY_NAME, product.getName());
                values.put(KEY_PRICE, product.getPrice());
                values.put(KEY_STOCK, product.getStock());

                db.insert(TABLE_PRODUCT, null, values);
            }

            db.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 导出商品数据到Download/Cashier目录
    public boolean exportProductsToJson(Context context) {
        try {
            // 创建Gson实例
            Gson gson = new Gson();

            // 获取所有商品数据
            List<Product> allProducts = getAllProducts();

            // 生成以当前日期为名称的文件名（格式：yyyy-MM-dd.json）
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String fileName = sdf.format(new Date()) + ".json";

            File exportFile;

            // 处理不同Android版本的文件保存路径
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用MediaStore API，这里简化处理，仍使用旧路径但添加权限标记
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File cashierDir = new File(downloadDir, "Cashier");
                if (!cashierDir.exists()) {
                    cashierDir.mkdirs();
                }
                exportFile = new File(cashierDir, fileName);
            } else {
                // Android 10以下直接使用旧路径
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File cashierDir = new File(downloadDir, "Cashier");
                if (!cashierDir.exists()) {
                    cashierDir.mkdirs();
                }
                exportFile = new File(cashierDir, fileName);
            }

            // 将商品列表转换为JSON并写入文件
            try (FileWriter writer = new FileWriter(exportFile)) {
                gson.toJson(allProducts, writer);
                writer.flush();
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 4. 获取所有商品（库存管理页面展示）
    public List<Product> getAllProducts() {
        List<Product> productList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        // 查询所有商品（按名称排序）
        Cursor cursor = db.query(
                TABLE_PRODUCT,
                null, null, null,
                null, null, KEY_NAME + " ASC"
        );

        if (cursor.moveToFirst()) {
            do {
                Product product = new Product();
                product.setId(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_ID)));
                product.setBarcode(cursor.getString(cursor.getColumnIndexOrThrow(KEY_BARCODE)));
                product.setName(cursor.getString(cursor.getColumnIndexOrThrow(KEY_NAME)));
                product.setPrice(cursor.getDouble(cursor.getColumnIndexOrThrow(KEY_PRICE)));
                product.setStock(cursor.getInt(cursor.getColumnIndexOrThrow(KEY_STOCK)));
                productList.add(product);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return productList;
    }

    // 5. 根据条形码删除商品
    public boolean deleteProduct(String barcode) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = db.delete(TABLE_PRODUCT, KEY_BARCODE + " = ?", new String[]{barcode});
        db.close();
        return rowsAffected > 0;
    }

    // 6. 根据ID删除商品
    public boolean deleteProductById(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsAffected = db.delete(TABLE_PRODUCT, KEY_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rowsAffected > 0;
    }

    // 导入商品数据从JSON文件（支持用户选择文件）
    public boolean importProductsFromJson(File jsonFile) {
        try {
            return importProductsFromReader(new FileReader(jsonFile));
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 从Reader导入商品数据（更灵活，支持ContentResolver）
    public boolean importProductsFromReader(Reader reader) {
        try {
            // 创建Gson实例并设置为 lenient 模式，以处理可能的格式问题
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            // 将JSON内容转换为商品列表
            Type productListType = new TypeToken<List<Product>>(){}.getType();
            List<Product> importedProducts = gson.fromJson(reader, productListType);

            // 清空当前商品表
            SQLiteDatabase db = this.getWritableDatabase();
            db.delete(TABLE_PRODUCT, null, null);

            // 插入导入的商品数据
            for (Product product : importedProducts) {
                ContentValues values = new ContentValues();
                values.put(KEY_BARCODE, product.getBarcode());
                values.put(KEY_NAME, product.getName());
                values.put(KEY_PRICE, product.getPrice());
                values.put(KEY_STOCK, product.getStock());

                db.insert(TABLE_PRODUCT, null, values);
            }

            db.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}