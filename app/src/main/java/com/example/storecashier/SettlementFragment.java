package com.example.storecashier;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.camera.CameraSettings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SettlementFragment extends Fragment {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;

    // UI控件
    private RelativeLayout layoutScanContainer;
    private DecoratedBarcodeView barcodeView;
    private Button btnStopScan;
    private TextView tvScanStatus; // 新增：扫码状态提示

    private Button btnScanSettlement, btnClearSettlement, btnConfirmSettlement;
    private ListView lvSettlementList;
    private TextView tvTotalPrice;
    private DBHelper dbHelper;

    // 逻辑变量
    private List<CartItem> settlementList = new ArrayList<>();
    private SettlementAdapter settlementAdapter;
    private double totalPrice = 0.0;

    private ToneGenerator toneGenerator;
    private long lastScanTime = 0; // 用于防抖动

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settlement, container, false);

        initViews(view);
        dbHelper = new DBHelper(requireContext());

        initBeepSound();

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
        updateTotalPrice();

        return view;
    }

    private void initViews(View view) {
        // 扫码界面控件
        layoutScanContainer = view.findViewById(R.id.layout_scan_container);
        barcodeView = view.findViewById(R.id.dbv_custom);
        btnStopScan = view.findViewById(R.id.btn_stop_scan); // 这是上面的关闭小按钮

        // 主界面控件
        btnScanSettlement = view.findViewById(R.id.btn_scan_settlement); // 这是下面的开启按钮
        btnClearSettlement = view.findViewById(R.id.btn_clear_settlement);
        btnConfirmSettlement = view.findViewById(R.id.btn_confirm_settlement);
        lvSettlementList = view.findViewById(R.id.lv_settlement_list);
        tvTotalPrice = view.findViewById(R.id.tv_total_price);
        tvScanStatus = view.findViewById(R.id.tv_scan_status);

        // 配置扫码视图
        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.UPC_A, BarcodeFormat.EAN_13, BarcodeFormat.CODE_128);
        barcodeView.getBarcodeView().setDecoderFactory(new DefaultDecoderFactory(formats));
        CameraSettings settings = new CameraSettings();
        settings.setRequestedCameraId(0); // 后置摄像头
        barcodeView.getBarcodeView().setCameraSettings(settings);
        barcodeView.setStatusText(""); // 隐藏库自带的底部文字

        // 按钮事件
        btnScanSettlement.setOnClickListener(v -> checkCameraPermissionAndStartScan());

        // 点击上面的关闭按钮，停止扫码
        btnStopScan.setOnClickListener(v -> stopContinuousScan());

        // 点击列表清空
        btnClearSettlement.setOnClickListener(v -> {
            if (settlementList.isEmpty()) return;
            new AlertDialog.Builder(requireContext())
                    .setTitle("确认清空")
                    .setMessage("是否清空当前结算清单？")
                    .setPositiveButton("确认", (dialog, which) -> {
                        settlementList.clear();
                        totalPrice = 0.0;
                        settlementAdapter.notifyDataSetChanged();
                        updateTotalPrice();
                        tvScanStatus.setText("清单已清空");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        lvSettlementList.setOnItemLongClickListener((parent, view1, position, id) -> {
            CartItem cartItem = settlementList.get(position);
            removeProductFromSettlement(position, cartItem);
            return true;
        });

        btnConfirmSettlement.setOnClickListener(v -> confirmSettlement());
    }

    private void initBeepSound() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_SYSTEM, 100);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playBeepAndVibrate() {
        if (toneGenerator != null) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
        }
        try {
            Vibrator v = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(100);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void checkCameraPermissionAndStartScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startContinuousScan();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    // 开启分屏扫码
    private void startContinuousScan() {
        // 显示顶部的扫码区域
        layoutScanContainer.setVisibility(View.VISIBLE);
        // 隐藏底部的“开启扫码”按钮，避免重复点击
        btnScanSettlement.setVisibility(View.GONE);

        tvScanStatus.setText("扫码中...请对准条形码");

        barcodeView.resume();
        barcodeView.decodeContinuous(callback);
    }

    // 停止扫码，收起顶部区域
    private void stopContinuousScan() {
        barcodeView.pause();
        layoutScanContainer.setVisibility(View.GONE);
        btnScanSettlement.setVisibility(View.VISIBLE); // 重新显示开启按钮
        tvScanStatus.setText("扫码已暂停");
    }

    private BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result.getText() == null) return;

            // 防抖动：1.5秒内不重复识别
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastScanTime < 1500) {
                return;
            }
            lastScanTime = currentTime;

            String barcode = result.getText();
            boolean success = queryProductAndAddToSettlement(barcode);

            if (success) {
                playBeepAndVibrate();
            }
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) { }
    };

    private boolean queryProductAndAddToSettlement(String barcode) {
        Product product = dbHelper.getProductByBarcode(barcode);
        if (product == null) {
            // 未找到商品时，仅提示，不打断扫码
            // 这里使用 runOnUiThread 确保 Toast 在主线程显示
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), "未找到商品：" + barcode, Toast.LENGTH_SHORT).show()
            );
            return false;
        }

        boolean productFound = false;
        for (CartItem cartItem : settlementList) {
            if (cartItem.getProduct().getBarcode().equals(barcode)) {
                cartItem.incrementQuantity();
                productFound = true;
                break;
            }
        }

        if (!productFound) {
            // 新商品添加到列表最前面，方便用户看到
            settlementList.add(0, new CartItem(product));
        }

        calculateTotalPrice();
        settlementAdapter.notifyDataSetChanged();
        updateTotalPrice();

        // 更新状态提示
        tvScanStatus.setText("已添加：" + product.getName());

        // 自动滚动到列表顶部，让用户看到最新添加的商品
        // 因为我们将新商品添加到了 list.add(0, ...)，所以滚动到 0
        lvSettlementList.smoothScrollToPosition(0);

        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (layoutScanContainer.getVisibility() == View.VISIBLE) {
            barcodeView.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        barcodeView.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dbHelper.close();
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void removeProductFromSettlement(int position, CartItem cartItem) {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除商品")
                .setMessage("是否删除 " + cartItem.getProduct().getName() + "？")
                .setPositiveButton("确认", (dialog, which) -> {
                    settlementList.remove(position);
                    calculateTotalPrice();
                    settlementAdapter.notifyDataSetChanged();
                    updateTotalPrice();
                    tvScanStatus.setText("已删除商品");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void calculateTotalPrice() {
        totalPrice = 0.0;
        for (CartItem cartItem : settlementList) {
            totalPrice += cartItem.getItemTotal();
        }
    }

    private void updateTotalPrice() {
        tvTotalPrice.setText(String.format("%.2f元", totalPrice));
    }

    private void confirmSettlement() {
        if (settlementList.isEmpty()) {
            Toast.makeText(requireContext(), "结算清单为空", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("确认结算")
                .setMessage("合计金额：" + String.format("%.2f元", totalPrice) + "，是否确认结算？")
                .setPositiveButton("确认", (dialog, which) -> {
                    boolean allSuccess = true;
                    for (CartItem cartItem : settlementList) {
                        Product product = cartItem.getProduct();
                        int newStock = product.getStock() - cartItem.getQuantity();
                        if (!dbHelper.updateProductStock(product.getBarcode(), newStock)) {
                            allSuccess = false;
                            break;
                        }
                    }
                    if (allSuccess) {
                        Toast.makeText(requireContext(), "结算成功！", Toast.LENGTH_SHORT).show();
                        settlementList.clear();
                        totalPrice = 0.0;
                        settlementAdapter.notifyDataSetChanged();
                        updateTotalPrice();
                        tvScanStatus.setText("结算完成");
                    } else {
                        Toast.makeText(requireContext(), "部分商品库存更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class SettlementAdapter extends BaseAdapter {
        @Override
        public int getCount() { return settlementList.size(); }
        @Override
        public Object getItem(int position) { return settlementList.get(position); }
        @Override
        public long getItemId(int position) { return position; }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(requireContext()).inflate(R.layout.item_settlement, parent, false);
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
            holder.tvName.setText(product.getName());
            holder.tvPrice.setText(String.format("单价：%.2f元", product.getPrice()));
            holder.tvQuantity.setText(String.format("数量：%d", cartItem.getQuantity()));
            holder.tvItemTotal.setText(String.format("小计：%.2f元", cartItem.getItemTotal()));

            // 为了视觉体验，可以给第一项（最新扫描的）加个背景色高亮，这里简单处理
            if (position == 0 && layoutScanContainer.getVisibility() == View.VISIBLE) {
                convertView.setBackgroundColor(0xFFE3F2FD); // 浅蓝色高亮最新项
            } else {
                convertView.setBackgroundColor(0xFFFFFFFF);
            }

            return convertView;
        }
        class ViewHolder { TextView tvName, tvPrice, tvQuantity, tvItemTotal; }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList("settlementList", new ArrayList<>(settlementList));
        outState.putDouble("totalPrice", totalPrice);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startContinuousScan();
            } else {
                Toast.makeText(requireContext(), "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }
}