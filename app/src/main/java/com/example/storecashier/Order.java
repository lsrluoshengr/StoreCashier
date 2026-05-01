package com.example.storecashier;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "orders")
public class Order {
    @PrimaryKey(autoGenerate = true)
    private long orderId;
    private long timestamp;
    private double totalAmount;

    public Order(long timestamp, double totalAmount) {
        this.timestamp = timestamp;
        this.totalAmount = totalAmount;
    }

    // Getter and Setter
    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
}
