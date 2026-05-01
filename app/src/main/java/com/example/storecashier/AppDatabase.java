package com.example.storecashier;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Product.class, Order.class, OrderItem.class}, version = 4, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ProductDao productDao();
    public abstract OrderDao orderDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // 迁移：版本 1 到 2，添加 category 列
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE product ADD COLUMN category TEXT");
        }
    };

    // 迁移：版本 2 到 3，添加 Order 和 OrderItem 表
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 创建 orders 表
            database.execSQL("CREATE TABLE IF NOT EXISTS `orders` (" +
                    "`orderId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`totalAmount` REAL NOT NULL)");
            
            // 创建 order_items 表
            database.execSQL("CREATE TABLE IF NOT EXISTS `order_items` (" +
                    "`orderItemId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`orderId` INTEGER NOT NULL, " +
                    "`barcode` TEXT, " +
                    "`productName` TEXT, " +
                    "`price` REAL NOT NULL, " +
                    "`quantity` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`orderId`) REFERENCES `orders`(`orderId`) ON UPDATE NO ACTION ON DELETE CASCADE )");
            
            // 为 orderId 添加索引
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderId` ON `order_items` (`orderId`) ");
        }
    };

    // 迁移：版本 3 到 4，添加 imagePath 列
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE product ADD COLUMN imagePath TEXT");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "StoreCashierDB")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
