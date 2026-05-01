package com.example.storecashier;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

public class ProductViewModel extends AndroidViewModel {
    private ProductDao productDao;
    private OrderDao orderDao;
    private AppDatabase db;
    private LiveData<List<Product>> allProducts;

    public ProductViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getDatabase(application);
        productDao = db.productDao();
        orderDao = db.orderDao();
        allProducts = productDao.getAllProducts();
    }

    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    public LiveData<List<String>> getAllCategories() {
        return productDao.getAllCategories();
    }

    public LiveData<List<Order>> getAllOrders() {
        return orderDao.getAllOrders();
    }

    public void getOrderItems(long orderId, OnOrderItemsLoadedListener listener) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<OrderItem> items = orderDao.getOrderItemsSync(orderId);
            if (listener != null) {
                listener.onLoaded(items);
            }
        });
    }

    public interface OnOrderItemsLoadedListener {
        void onLoaded(List<OrderItem> items);
    }

    /**
     * 处理结算：创建订单、记录明细、扣减库存
     * 这是一个原子操作，必须在后台线程的事务中执行
     */
    public void processCheckout(List<CartItem> cartItems, double totalAmount, Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            db.runInTransaction(() -> {
                // 1. 创建并插入订单
                Order order = new Order(System.currentTimeMillis(), totalAmount);
                long orderId = orderDao.insertOrder(order);

                // 2. 准备订单明细
                List<OrderItem> orderItems = new ArrayList<>();
                for (CartItem cartItem : cartItems) {
                    Product product = cartItem.getProduct();
                    
                    // 创建明细快照
                    OrderItem orderItem = new OrderItem(
                            orderId,
                            product.getBarcode(),
                            product.getName(),
                            product.getPrice(),
                            cartItem.getQuantity()
                    );
                    orderItems.add(orderItem);

                    // 3. 扣减库存
                    int newStock = product.getStock() - cartItem.getQuantity();
                    productDao.updateStock(product.getBarcode(), newStock);
                }

                // 4. 批量插入明细
                orderDao.insertOrderItems(orderItems);
            });

            // 事务完成后回调
            if (onComplete != null) {
                onComplete.run();
            }
        });
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
