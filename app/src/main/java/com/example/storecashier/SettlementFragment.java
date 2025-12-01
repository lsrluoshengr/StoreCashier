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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SettlementFragment extends Fragment {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;
    private Button btnScanSettlement, btnClearSettlement, btnConfirmSettlement;
    private ListView lvSettlementList;
    private TextView tvTotalPrice;
    private DBHelper dbHelper;

    // 结算清单（存储购物车项，支持相同商品合并）
    private List<CartItem> settlementList = new ArrayList<>();
    private SettlementAdapter settlementAdapter;
    private double totalPrice = 0.0; // 总价

    // 扫码相关
    private BarcodeView barcodeView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settlement, container, false);

        // 初始化控件和数据库
        initViews(view);
        dbHelper = new DBHelper(requireContext());

        // 恢复状态
        if (savedInstanceState != null) {
            ArrayList<CartItem> savedList = savedInstanceState.getParcelableArrayList("settlementList");
            if (savedList != null) {
                settlementList = savedList;
            }
            totalPrice = savedInstanceState.getDouble("totalPrice", 0.0);
        }

        settlementAdapter = new SettlementAdapter();
        lvSettlementList.setAdapter(settlementAdapter);
        updateTotalPrice(); // Update total price display

        // 初始化扫码预览
        initBarcodeView(view);

        return view;
    }

    // 初始化控件
    private void initViews(View view) {
        btnScanSettlement = view.findViewById(R.id.btn_scan_settlement);
        btnClearSettlement = view.findViewById(R.id.btn_clear_settlement);
        btnConfirmSettlement = view.findViewById(R.id.btn_confirm_settlement);
        lvSettlementList = view.findViewById(R.id.lv_settlement_list);
        tvTotalPrice = view.findViewById(R.id.tv_total_price);

        // 扫码添加商品
        btnScanSettlement.setOnClickListener(v -> checkCameraPermissionAndScan());

        // 清空结算清单
        btnClearSettlement.setOnClickListener(v -> {
            if (settlementList.isEmpty()) {
                Toast.makeText(requireContext(), "结算清单为空", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(requireContext())
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
        lvSettlementList.setOnItemLongClickListener((parent, view1, position, id) -> {
            CartItem cartItem = settlementList.get(position);
            removeProductFromSettlement(position, cartItem);
            return true;
        });

        // 确认结算按钮点击事件
        btnConfirmSettlement.setOnClickListener(v -> confirmSettlement());
    }

    // 初始化扫码视图
    private void initBarcodeView(View view) {
        barcodeView = new BarcodeView(requireContext());
        ViewGroup previewContainer = view.findViewById(R.id.scan_preview_container);
        previewContainer.addView(barcodeView);

        // 设置解码器工厂
        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.CODE_128);
        barcodeView.setDecoderFactory(new DefaultDecoderFactory(formats));

        // 设置扫码回调 - 连续扫码模式
        final BarcodeCallback barcodeCallback = new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result != null && !result.getText().isEmpty()) {
                    String barcode = result.getText();
                    // 直接添加商品，不关闭摄像头
                    queryProductAndAddToSettlement(barcode);
                    // 重新启动扫码以实现连续扫描
                    barcodeView.decodeSingle(this);
                }
            }

            @Override
            public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {
                // 可以添加扫码点动画
            }
        };

        // 启动首次扫码
        barcodeView.decodeSingle(barcodeCallback);
    }

    // 检查相机权限并扫码
    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // 申请相机权限
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            startScan();
        }
    }

    // 启动扫码
    private void startScan() {
        ViewGroup previewContainer = requireView().findViewById(R.id.scan_preview_container);
        previewContainer.setVisibility(View.VISIBLE);
        btnScanSettlement.setVisibility(View.GONE);
        barcodeView.resume();
    }

    // 权限申请结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                Toast.makeText(requireContext(), "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 根据条形码查询商品并添加到结算清单（相同商品自动合并）
    private void queryProductAndAddToSettlement(String barcode) {
        Product product = dbHelper.getProductByBarcode(barcode);
        if (product == null) {
            Toast.makeText(requireContext(), "未找到该商品，请先录入", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(requireContext(), "已添加：" + product.getName() + "（" + product.getPrice() + "元）", Toast.LENGTH_SHORT).show();
    }

    // 从结算清单中删除商品
    private void removeProductFromSettlement(int position, CartItem cartItem) {
        new AlertDialog.Builder(requireContext())
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
            Toast.makeText(requireContext(), "结算清单为空", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(requireContext())
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
                            break; // 一旦失败立即终止循环，避免不必要的操作
                        }
                    }

                    if (allSuccess) {
                        Toast.makeText(requireContext(), "结算成功！", Toast.LENGTH_SHORT).show();
                        // 清空清单
                        settlementList.clear();
                        totalPrice = 0.0;
                        settlementAdapter.notifyDataSetChanged();
                        updateTotalPrice();
                    } else {
                        Toast.makeText(requireContext(), "部分商品库存更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 保存状态
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("settlementList", new ArrayList<>(settlementList));
        outState.putDouble("totalPrice", totalPrice);
    }

    // 生命周期方法
    @Override
    public void onResume() {
        super.onResume();
        if (barcodeView != null && requireView().findViewById(R.id.scan_preview_container).getVisibility() == View.VISIBLE) {
            barcodeView.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (barcodeView != null) {
            barcodeView.pause();
        }
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
                convertView = LayoutInflater.from(requireContext())
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }
}
