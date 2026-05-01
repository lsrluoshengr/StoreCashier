package com.example.storecashier;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface OrderDao {
    @Insert
    long insertOrder(Order order);

    @Insert
    void insertOrderItems(List<OrderItem> items);

    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    LiveData<List<Order>> getAllOrders();

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    List<OrderItem> getOrderItemsSync(long orderId);
}
