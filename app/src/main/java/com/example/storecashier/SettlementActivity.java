package com.example.storecashier;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;
import java.util.ArrayList;
import java.util.List;

public class SettlementActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private Button btnScanSettlement, btnClearSettlement, btnConfirmSettlement;
    private ListView lvSettlementList;
    private TextView tvTotalPrice;
    private DBHelper dbHelper;

    // 结算清单（存储购物车项，支持相同商品合并）
    private List<CartItem> settlementList = new ArrayList<>();
    private SettlementAdapter settlementAdapter;
    private double totalPrice = 0.0; // 总价

    // 扫码回调
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result != null && result.getContents() != null) {
            String barcode = result.getContents();
            queryProductAndAddToSettlement(barcode); // 扫码后查询商品并添加到结算清单
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settlement);

        // 初始化控件和数据库
        initViews();
        dbHelper = new DBHelper(this);
        settlementAdapter = new SettlementAdapter();
        lvSettlementList.setAdapter(settlementAdapter);
    }

    // 初始化控件
    private void initViews() {
        btnScanSettlement = findViewById(R.id.btn_scan_settlement);
        btnClearSettlement = findViewById(R.id.btn_clear_settlement);
        btnConfirmSettlement = findViewById(R.id.btn_confirm_settlement);
        lvSettlementList = findViewById(R.id.lv_settlement_list);
        tvTotalPrice = findViewById(R.id.tv_total_price);

        // 扫码添加商品
        btnScanSettlement.setOnClickListener(v -> checkCameraPermissionAndScan());

        // 清空结算清单
        btnClearSettlement.setOnClickListener(v -> {
            if (settlementList.isEmpty()) {
                Toast.makeText(this, "结算清单为空", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("确认清空")
                    .setMessage("是否清空当前结算清单？")
                    .setPositiveButton("确认", (dialog, which) -> {
                        settlementList.clear();
                        totalPrice = 0.0;
                        settlementAdapter.notifyDataSetChanged();
                        updateTotalPrice();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 长按结算清单项，删除该商品
        lvSettlementList.setOnItemLongClickListener((parent, view, position, id) -> {
            CartItem cartItem = settlementList.get(position);
            removeProductFromSettlement(position, cartItem);
            return true;
        });

        // 确认结算按钮点击事件
        btnConfirmSettlement.setOnClickListener(v -> confirmSettlement());
    }

    // 检查相机权限并扫码
    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            startScan();
        }
    }

    // 启动扫码
    private void startScan() {
        ScanOptions scanOptions = new ScanOptions();
        scanOptions.setPrompt("请对准商品条形码扫码");
        scanOptions.setBeepEnabled(true);
        scanOptions.setOrientationLocked(true);
        barcodeLauncher.launch(scanOptions);
    }

    // 权限申请结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 根据条形码查询商品并添加到结算清单（相同商品自动合并）
    private void queryProductAndAddToSettlement(String barcode) {
        Product product = dbHelper.getProductByBarcode(barcode);
        if (product == null) {
            Toast.makeText(this, "未找到该商品，请先录入", Toast.LENGTH_SHORT).show();
            return;
        }

        // 检查是否已经在结算清单中，相同商品合并
        boolean productFound = false;
        for (CartItem cartItem : settlementList) {
            if (cartItem.getProduct().getBarcode().equals(barcode)) {
                // 商品已存在，数量+1
                cartItem.incrementQuantity();
                productFound = true;
                break;
            }
        }

        // 商品不存在，新增购物车项
        if (!productFound) {
            settlementList.add(new CartItem(product));
        }

        // 更新总价
        calculateTotalPrice();
        settlementAdapter.notifyDataSetChanged();
        updateTotalPrice();

        // 提示添加成功
        Toast.makeText(this, "已添加：" + product.getName() + "（" + product.getPrice() + "元）", Toast.LENGTH_SHORT).show();
    }

    // 从结算清单中删除商品
    private void removeProductFromSettlement(int position, CartItem cartItem) {
        new AlertDialog.Builder(this)
                .setTitle("删除商品")
                .setMessage("是否删除 " + cartItem.getProduct().getName() + "？")
                .setPositiveButton("确认", (dialog, which) -> {
                    settlementList.remove(position);
                    calculateTotalPrice(); // 重新计算总价
                    settlementAdapter.notifyDataSetChanged();
                    updateTotalPrice();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 计算总价
    private void calculateTotalPrice() {
        totalPrice = 0.0;
        for (CartItem cartItem : settlementList) {
            totalPrice += cartItem.getItemTotal();
        }
    }

    // 更新总价显示
    private void updateTotalPrice() {
        tvTotalPrice.setText(String.format("%.2f元", totalPrice));
    }

    // 结算完成：用户确认结算后，批量扣减库存
    private void confirmSettlement() {
        if (settlementList.isEmpty()) {
            Toast.makeText(this, "结算清单为空", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("确认结算")
                .setMessage("合计金额：" + String.format("%.2f元", totalPrice) + "，是否确认结算？")
                .setPositiveButton("确认", (dialog, which) -> {
                    // 批量扣减库存
                    boolean allSuccess = true;
                    for (CartItem cartItem : settlementList) {
                        Product product = cartItem.getProduct();
                        int newStock = product.getStock() - cartItem.getQuantity(); // 扣减对应数量的库存
                        if (!dbHelper.updateProductStock(product.getBarcode(), newStock)) {
                            allSuccess = false;
                        }
                    }

                    if (allSuccess) {
                        Toast.makeText(this, "结算成功！", Toast.LENGTH_SHORT).show();
                        // 清空清单
                        settlementList.clear();
                        totalPrice = 0.0;
                        settlementAdapter.notifyDataSetChanged();
                        updateTotalPrice();
                    } else {
                        Toast.makeText(this, "部分商品库存更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 结算清单适配器（更新为显示购物车项：包含数量和单项总价）
    private class SettlementAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return settlementList.size();
        }

        @Override
        public Object getItem(int position) {
            return settlementList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(SettlementActivity.this)
                        .inflate(R.layout.item_settlement, parent, false);
                holder = new ViewHolder();
                holder.tvName = convertView.findViewById(R.id.tv_settlement_name);
                holder.tvPrice = convertView.findViewById(R.id.tv_settlement_price);
                holder.tvQuantity = convertView.findViewById(R.id.tv_settlement_quantity);
                holder.tvItemTotal = convertView.findViewById(R.id.tv_settlement_item_total);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            CartItem cartItem = settlementList.get(position);
            Product product = cartItem.getProduct();
            int quantity = cartItem.getQuantity();
            double itemTotal = cartItem.getItemTotal();

            holder.tvName.setText(product.getName());
            holder.tvPrice.setText(String.format("单价：%.2f元", product.getPrice()));
            holder.tvQuantity.setText(String.format("数量：%d", quantity));
            holder.tvItemTotal.setText(String.format("小计：%.2f元", itemTotal));

            return convertView;
        }

        class ViewHolder {
            TextView tvName, tvPrice, tvQuantity, tvItemTotal;
        }
    }

    // 保存activity状态，防止扫码后activity重建导致数据丢失
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存结算清单和总价
        outState.putParcelableArrayList("settlementList", new ArrayList<>(settlementList));
        outState.putDouble("totalPrice", totalPrice);
    }

    // 恢复activity状态
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 恢复结算清单和总价
        settlementList = savedInstanceState.getParcelableArrayList("settlementList");
        totalPrice = savedInstanceState.getDouble("totalPrice");
        // 通知适配器数据变化
        settlementAdapter.notifyDataSetChanged();
        // 更新总价显示
        updateTotalPrice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}
