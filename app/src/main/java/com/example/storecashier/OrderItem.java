package com.example.storecashier;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "order_items",
        foreignKeys = @ForeignKey(entity = Order.class,
                parentColumns = "orderId",
                childColumns = "orderId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("orderId")})
public class OrderItem {
    @PrimaryKey(autoGenerate = true)
    private long orderItemId;
    private long orderId;
    private String barcode;
    private String productName;
    private double price;
    private int quantity;

    public OrderItem(long orderId, String barcode, String productName, double price, int quantity) {
        this.orderId = orderId;
        this.barcode = barcode;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    // Getter and Setter
    public long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(long orderItemId) { this.orderItemId = orderItemId; }
    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
