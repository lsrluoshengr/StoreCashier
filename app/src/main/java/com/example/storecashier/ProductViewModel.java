package com.example.storecashier;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class ProductViewModel extends AndroidViewModel {
    private ProductDao productDao;
    private LiveData<List<Product>> allProducts;

    public ProductViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        allProducts = productDao.getAllProducts();
    }

    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    public void insert(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> productDao.insert(product));
    }

    public void update(Product product) {
        AppDatabase.databaseWriteExecutor.execute(() -> productDao.update(product));
    }

    public void updateStock(String barcode, int newStock) {
        AppDatabase.databaseWriteExecutor.execute(() -> productDao.updateStock(barcode, newStock));
    }

    public void updateProductInfo(String barcode, String name, double price, int stock) {
        AppDatabase.databaseWriteExecutor.execute(() -> productDao.updateProductInfo(barcode, name, price, stock));
    }

    public Product getProductByBarcodeSync(String barcode) {
        return productDao.getProductByBarcode(barcode);
    }
}
