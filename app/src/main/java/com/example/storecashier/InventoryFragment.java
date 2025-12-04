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
import android.widget.CheckBox;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class InventoryFragment extends Fragment {
    private ListView lvProductInventory;
    private List<Product> productList;
    private ProductAdapter productAdapter;
    private DBHelper dbHelper;
    private Button btnManage;

    // 管理模式状态
    private boolean isManageMode = false;
    // 选中的商品列表
    private List<Product> selectedProducts = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_inventory, container, false);

        // 初始化控件和数据库
        lvProductInventory = view.findViewById(R.id.lv_product_inventory);
        btnManage = view.findViewById(R.id.btn_manage);
        dbHelper = new DBHelper(requireContext());

        // 加载商品列表
        loadProductList();

        // 长按商品自由编辑
        lvProductInventory.setOnItemLongClickListener((parent, view1, position, id) -> {
            Product product = productList.get(position);
            showEditProductDialog(product);
            return true;
        });

        // 管理按钮点击事件
        btnManage.setOnClickListener(v -> toggleManageMode());

        return view;
    }

    // 加载所有商品到列表
    private void loadProductList() {
        productList = dbHelper.getAllProducts();
        productAdapter = new ProductAdapter();
        lvProductInventory.setAdapter(productAdapter);
    }

    // 切换管理模式
    private void toggleManageMode() {
        isManageMode = !isManageMode;
        if (isManageMode) {
            btnManage.setText("取消"); // 进入管理模式时先显示"取消"
            // 清空选中列表
            selectedProducts.clear();
        } else {
            btnManage.setText("管理");
            // 如果是退出管理模式并点击了删除按钮，则执行删除操作
            if (selectedProducts.size() > 0) {
                deleteSelectedProducts();
            }
        }
        // 通知适配器管理模式变化
        productAdapter.notifyDataSetChanged();
    }

    // 删除选中的商品
    private void deleteSelectedProducts() {
        // 弹出确认对话框
        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除选中的" + selectedProducts.size() + "个商品吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    // 执行删除操作
                    for (Product product : selectedProducts) {
                        dbHelper.deleteProduct(product.getBarcode());
                    }
                    // 刷新商品列表
                    loadProductList();
                    Toast.makeText(requireContext(), "删除成功！", Toast.LENGTH_SHORT).show();
                    // 重置选中列表
                    selectedProducts.clear();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 弹出修改库存对话框
    private void showModifyStockDialog(Product product) {
        // 加载对话框布局
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_modify_stock, null);
        EditText etNewStock = dialogView.findViewById(R.id.et_new_stock);
        etNewStock.setText(String.valueOf(product.getStock())); // 初始值为当前库存

        new AlertDialog.Builder(requireContext())
                .setTitle("修改库存：" + product.getName())
                .setView(dialogView)
                .setPositiveButton("确认", (dialog, which) -> {
                    String newStockStr = etNewStock.getText().toString().trim();
                    if (newStockStr.isEmpty()) {
                        Toast.makeText(requireContext(), "库存不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int newStock;
                    try {
                        newStock = Integer.parseInt(newStockStr);
                        if (newStock < 0) {
                            Toast.makeText(requireContext(), "库存不能为负数", Toast.LENGTH_SHORT).show();
                            return;
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "库存格式错误", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 更新库存到数据库
                    boolean isUpdated = dbHelper.updateProductStock(product.getBarcode(), newStock);
                    if (isUpdated) {
                        Toast.makeText(requireContext(), "库存更新成功", Toast.LENGTH_SHORT).show();
                        product.setStock(newStock); // 更新列表数据
                        productAdapter.notifyDataSetChanged(); // 刷新列表
                    } else {
                        Toast.makeText(requireContext(), "库存更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 弹出自由编辑商品信息对话框
    private void showEditProductDialog(Product product) {
        // 创建对话框布局
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        View dialogView = inflater.inflate(R.layout.dialog_edit_product, null);

        // 找到对话框中的控件
        EditText etProductName = dialogView.findViewById(R.id.et_edit_product_name);
        EditText etProductPrice = dialogView.findViewById(R.id.et_edit_product_price);
        EditText etProductStock = dialogView.findViewById(R.id.et_edit_product_stock);

        // 设置初始值
        etProductName.setText(product.getName());
        etProductPrice.setText(String.format("%.2f", product.getPrice()));
        etProductStock.setText(String.valueOf(product.getStock()));

        new AlertDialog.Builder(requireContext())
                .setTitle("编辑商品信息")
                .setView(dialogView)
                .setPositiveButton("保存", (dialog, which) -> {
                    // 获取输入值
                    String name = etProductName.getText().toString().trim();
                    String priceStr = etProductPrice.getText().toString().trim();
                    String stockStr = etProductStock.getText().toString().trim();

                    // 验证输入
                    if (name.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
                        Toast.makeText(requireContext(), "所有字段都不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        double price = Double.parseDouble(priceStr);
                        int stock = Integer.parseInt(stockStr);

                        if (price <= 0) {
                            Toast.makeText(requireContext(), "价格必须大于0", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (stock < 0) {
                            Toast.makeText(requireContext(), "库存不能为负数", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // 更新商品信息到数据库
                        boolean isUpdated = dbHelper.updateProduct(product.getBarcode(), name, price, stock);
                        if (isUpdated) {
                            // 更新列表中的商品信息
                            product.setName(name);
                            product.setPrice(price);
                            product.setStock(stock);
                            productAdapter.notifyDataSetChanged();
                            Toast.makeText(requireContext(), "商品信息更新成功", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "商品信息更新失败", Toast.LENGTH_SHORT).show();
                        }

                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "价格或库存格式错误", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 商品列表适配器
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
                convertView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_product, parent, false);
                holder = new ViewHolder();
                holder.cbSelect = convertView.findViewById(R.id.cb_select_product);
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

            // 设置管理模式下的复选框状态
            if (isManageMode) {
                holder.cbSelect.setVisibility(View.VISIBLE);
                // 设置复选框选中状态
                holder.cbSelect.setChecked(selectedProducts.contains(product));

                // 保存当前位置和产品引用，用于点击事件
                int finalPosition = position;
                Product finalProduct = product;

                // 复选框点击事件
                holder.cbSelect.setOnClickListener(v -> {
                    if (holder.cbSelect.isChecked()) {
                        selectedProducts.add(finalProduct);
                    } else {
                        selectedProducts.remove(finalProduct);
                    }
                    // 更新按钮文本：有选中项时显示"删除选中"，否则显示"取消"
                    if (selectedProducts.size() > 0) {
                        btnManage.setText("删除选中");
                    } else {
                        btnManage.setText("取消");
                    }
                });
            } else {
                holder.cbSelect.setVisibility(View.GONE);
                holder.cbSelect.setChecked(false); // 退出管理模式时取消勾选
            }

            return convertView;
        }

        // ViewHolder优化列表性能
        class ViewHolder {
            CheckBox cbSelect;
            TextView tvName, tvBarcode, tvPrice, tvStock;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProductList(); // 每次回到该Fragment时刷新商品列表
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}
