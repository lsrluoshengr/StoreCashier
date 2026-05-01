package com.example.storecashier;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insert(Product product);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);

    @Query("SELECT * FROM product WHERE barcode = :barcode LIMIT 1")
    Product getProductByBarcode(String barcode);

    @Query("SELECT * FROM product ORDER BY name ASC")
    LiveData<List<Product>> getAllProducts();

    @Query("SELECT * FROM product ORDER BY name ASC")
    List<Product> getAllProductsSync();

    @Query("UPDATE product SET stock = :newStock WHERE barcode = :barcode")
    void updateStock(String barcode, int newStock);

    @Query("UPDATE product SET name = :name, price = :price, stock = :stock WHERE barcode = :barcode")
    void updateProductInfo(String barcode, String name, double price, int stock);

    @Query("SELECT DISTINCT category FROM product WHERE category IS NOT NULL AND category != ''")
    LiveData<List<String>> getAllCategories();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Product> products);
}
