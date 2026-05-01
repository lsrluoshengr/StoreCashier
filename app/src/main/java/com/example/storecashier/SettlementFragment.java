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

import androidx.lifecycle.ViewModelProvider;

import androidx.recyclerview.widget.RecyclerView;

public class SettlementFragment extends Fragment implements CartItemAdapter.OnCartItemChangeListener {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;

    // UI控件
    private RelativeLayout layoutScanContainer;
    private DecoratedBarcodeView barcodeView;
    private Button btnStopScan;
    private TextView tvScanStatus; // 新增：扫码状态提示

    private Button btnScanSettlement, btnClearSettlement, btnConfirmSettlement;
    private RecyclerView rvSettlementList;
    private TextView tvTotalPrice;
    private ProductViewModel productViewModel;

    // 逻辑变量
    private List<CartItem> settlementList = new ArrayList<>();
    private CartItemAdapter settlementAdapter;
    private double totalPrice = 0.0;

    private ToneGenerator toneGenerator;
    private long lastScanTime = 0; // 用于防抖动

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settlement, container, false);

        productViewModel = new ViewModelProvider(requireActivity()).get(ProductViewModel.class);
        initViews(view);

        initBeepSound();

        // 恢复状态
        if (savedInstanceState != null) {
            ArrayList<CartItem> savedList = savedInstanceState.getParcelableArrayList("settlementList");
            if (savedList != null) {
                settlementList = savedList;
            }
            totalPrice = savedInstanceState.getDouble("totalPrice", 0.0);
        }

        settlementAdapter = new CartItemAdapter(settlementList, this);
        rvSettlementList.setAdapter(settlementAdapter);
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
        rvSettlementList = view.findViewById(R.id.rv_settlement_list);
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

        btnConfirmSettlement.setOnClickListener(v -> confirmSettlement());
    }

    @Override
    public void onCartUpdated() {
        calculateTotalPrice();
        updateTotalPrice();
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
            queryProductAndAddToSettlement(barcode);
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) { }
    };

    private void queryProductAndAddToSettlement(String barcode) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Product product = productViewModel.getProductByBarcodeSync(barcode);
            requireActivity().runOnUiThread(() -> {
                if (product == null) {
                    Toast.makeText(requireContext(), "未找到商品：" + barcode, Toast.LENGTH_SHORT).show();
                    return;
                }

                boolean productFound = false;
                for (int i = 0; i < settlementList.size(); i++) {
                    CartItem cartItem = settlementList.get(i);
                    if (cartItem.getProduct().getBarcode().equals(barcode)) {
                        cartItem.incrementQuantity();
                        settlementAdapter.notifyItemChanged(i);
                        productFound = true;
                        break;
                    }
                }

                if (!productFound) {
                    // 新商品添加到列表最前面，方便用户看到
                    settlementList.add(0, new CartItem(product));
                    settlementAdapter.notifyItemInserted(0);
                }

                calculateTotalPrice();
                updateTotalPrice();

                // 更新状态提示
                tvScanStatus.setText("已添加：" + product.getName());

                // 自动滚动到列表顶部，让用户看到最新添加的商品
                rvSettlementList.smoothScrollToPosition(0);
                playBeepAndVibrate();
            });
        });
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
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
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
                    // 调用 ViewModel 进行原子化结算
                    productViewModel.processCheckout(new ArrayList<>(settlementList), totalPrice, () -> {
                        // 结算完成后的 UI 操作，回到主线程执行
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "结算成功！", Toast.LENGTH_SHORT).show();
                            
                            // 清空清单和总价
                            settlementList.clear();
                            totalPrice = 0.0;
                            
                            // 刷新 UI
                            settlementAdapter.notifyDataSetChanged();
                            updateTotalPrice();
                            tvScanStatus.setText("结算完成");
                        });
                    });
                })
                .setNegativeButton("取消", null)
                .show();
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