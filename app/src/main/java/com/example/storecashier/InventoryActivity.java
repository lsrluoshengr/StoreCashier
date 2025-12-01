package com.example.storecashier;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class InventoryActivity extends AppCompatActivity {
    private ListView lvProductInventory;
    private List<Product> productList;
    private ProductAdapter productAdapter;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        // 初始化控件和数据库
        lvProductInventory = findViewById(R.id.lv_product_inventory);
        dbHelper = new DBHelper(this);

        // 加载商品列表
        loadProductList();

        // 长按商品修改库存
        lvProductInventory.setOnItemLongClickListener((parent, view, position, id) -> {
            Product product = productList.get(position);
            showModifyStockDialog(product);
            return true;
        });
    }

    // 加载所有商品到列表
    private void loadProductList() {
        productList = dbHelper.getAllProducts();
        productAdapter = new ProductAdapter();
        lvProductInventory.setAdapter(productAdapter);
    }

    // 弹出修改库存对话框
    private void showModifyStockDialog(Product product) {
        // 加载对话框布局（复用输入框）
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_modify_stock, null);
        EditText etNewStock = dialogView.findViewById(R.id.et_new_stock);
        etNewStock.setText(String.valueOf(product.getStock())); // 初始值为当前库存

        new AlertDialog.Builder(this)
                .setTitle("修改库存：" + product.getName())
                .setView(dialogView)
                .setPositiveButton("确认", (dialog, which) -> {
                    String newStockStr = etNewStock.getText().toString().trim();
                    if (newStockStr.isEmpty()) {
                        Toast.makeText(InventoryActivity.this, "库存不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int newStock;
                    try {
                        newStock = Integer.parseInt(newStockStr);
                        if (newStock < 0) {
                            Toast.makeText(InventoryActivity.this, "库存不能为负数", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(InventoryActivity.this, "库存格式错误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 更新库存到数据库
                    boolean isUpdated = dbHelper.updateProductStock(product.getBarcode(), newStock);
                    if (isUpdated) {
                        Toast.makeText(InventoryActivity.this, "库存更新成功", Toast.LENGTH_SHORT).show();
                        product.setStock(newStock); // 更新列表数据
                        productAdapter.notifyDataSetChanged(); // 刷新列表
                    } else {
                        Toast.makeText(InventoryActivity.this, "库存更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 商品列表适配器（BaseAdapter）
    private class ProductAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return productList.size();
        }

        @Override
        public Object getItem(int position) {
            return productList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return productList.get(position).getId();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(InventoryActivity.this)
                        .inflate(R.layout.item_product, parent, false);
                holder = new ViewHolder();
                holder.tvName = convertView.findViewById(R.id.tv_product_name);
                holder.tvBarcode = convertView.findViewById(R.id.tv_product_barcode);
                holder.tvPrice = convertView.findViewById(R.id.tv_product_price);
                holder.tvStock = convertView.findViewById(R.id.tv_product_stock);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // 填充数据
            Product product = productList.get(position);
            holder.tvName.setText(product.getName());
            holder.tvBarcode.setText(product.getBarcode());
            holder.tvPrice.setText(String.format("%.2f元", product.getPrice())); // 价格保留2位小数
            holder.tvStock.setText(String.valueOf(product.getStock()));

            return convertView;
        }

        // ViewHolder优化列表性能
        class ViewHolder {
            TextView tvName, tvBarcode, tvPrice, tvStock;
        }
    }

    // 创建修改库存对话框布局（dialog_modify_stock.xml）
    // 右键res/layout → New → Layout resource file → 命名为dialog_modify_stock
    // 布局内容：
    /*
    <?xml version="1.0" encoding="utf-8"?>
    <EditText
        xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/et_new_stock"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="输入新库存"
        android:inputType="number"
        android:padding="10dp"/>
    */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}