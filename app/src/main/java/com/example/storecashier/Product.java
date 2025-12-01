package com.example.storecashier;

import android.os.Parcel;
import android.os.Parcelable;

public class Product implements Parcelable {
    private int id; // 自增ID（数据库主键）
    private String barcode; // 商品条形码（唯一标识）
    private String name; // 商品名称
    private double price; // 商品单价
    private int stock; // 库存数量

    // 构造方法（无参+有参）
    public Product() {}

    public Product(String barcode, String name, double price, int stock) {
        this.barcode = barcode;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    // Parcelable 构造方法
    protected Product(Parcel in) {
        id = in.readInt();
        barcode = in.readString();
        name = in.readString();
        price = in.readDouble();
        stock = in.readInt();
    }

    public static final Creator<Product> CREATOR = new Creator<Product>() {
        @Override
        public Product createFromParcel(Parcel in) {
            return new Product(in);
        }

        @Override
        public Product[] newArray(int size) {
            return new Product[size];
        }
    };

    // Getter和Setter（必须，用于数据读写）
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    // Parcelable 方法
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(barcode);
        dest.writeString(name);
        dest.writeDouble(price);
        dest.writeInt(stock);
    }
}