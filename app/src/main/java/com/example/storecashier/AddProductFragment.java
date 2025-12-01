package com.example.storecashier;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.journeyapps.barcodescanner.CaptureActivity;
import com.journeyapps.barcodescanner.ScanOptions;


public class AddProductFragment extends Fragment {
    private EditText etBarcode, etProductName, etProductPrice, etProductStock;
    private Button btnScanBarcode, btnSaveProduct;
    private DBHelper dbHelper;
    // 相机权限请求码
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    // 扫码请求码
    private static final int SCAN_REQUEST_CODE = 1001;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_product, container, false);
        initViews(view);
        dbHelper = new DBHelper(requireContext());
        return view;
    }

    private void initViews(View view) {
        etBarcode = view.findViewById(R.id.et_barcode);
        etProductName = view.findViewById(R.id.et_product_name);
        etProductPrice = view.findViewById(R.id.et_product_price);
        etProductStock = view.findViewById(R.id.et_product_stock);
        btnScanBarcode = view.findViewById(R.id.btn_scan_barcode);
        btnSaveProduct = view.findViewById(R.id.btn_save_product);

        btnScanBarcode.setOnClickListener(v -> checkCameraPermissionAndScan());
        btnSaveProduct.setOnClickListener(v -> saveProduct());
    }

    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    // 直接构造 Intent 启动扫码（绕开所有不存在的方法）
    private void startScan() {
        Intent scanIntent = new Intent(requireActivity(), CaptureActivity.class);
        // 扫码参数配置
        scanIntent.putExtra("PROMPT_MESSAGE", "请对准商品条形码扫码");
        scanIntent.putExtra("BEEP_ENABLED", true);
        scanIntent.putExtra("ORIENTATION_LOCKED", true);
        // 启用单次扫码模式，扫码完成后关闭相机并返回结果
        scanIntent.putExtra("CONTINUOUS_SCAN", false);
        scanIntent.putExtra("SINGLE_SCAN", true);
        // 启动扫码
        startActivityForResult(scanIntent, SCAN_REQUEST_CODE);
    }

    // 直接从 Intent 获取结果（4.3.0版本唯一可靠方式）
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SCAN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            String barcode = data.getStringExtra("SCAN_RESULT");
            if (barcode != null) {
                etBarcode.setText(barcode);
            }
        }
    }


    private void saveProduct() {
        String barcode = etBarcode.getText().toString().trim();
        String name = etProductName.getText().toString().trim();
        String priceStr = etProductPrice.getText().toString().trim();
        String stockStr = etProductStock.getText().toString().trim();

        if (barcode.isEmpty() || name.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
            Toast.makeText(requireContext(), "请填写完整商品信息", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        int stock;
        try {
            price = Double.parseDouble(priceStr);
            stock = Integer.parseInt(stockStr);
            if (price <= 0) {
                Toast.makeText(requireContext(), "价格需大于0", Toast.LENGTH_SHORT).show();
                return;
            }
            if (stock < 0) {
                Toast.makeText(requireContext(), "库存不能为负数", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "价格或库存格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        // 在后台线程执行数据库操作
        new Thread(() -> {
            Product product = new Product(barcode, name, price, stock);
            boolean isSaved = dbHelper.addProduct(product);

            requireActivity().runOnUiThread(() -> {
                if (isSaved) {
                    Toast.makeText(requireContext(), "商品录入成功！", Toast.LENGTH_SHORT).show();
                    clearInput();
                } else {
                    Toast.makeText(requireContext(), "该条形码已存在，请勿重复录入", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void clearInput() {
        etBarcode.setText("");
        etProductName.setText("");
        etProductPrice.setText("");
        etProductStock.setText("");
        etBarcode.requestFocus(); // 让光标回到条码输入框
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }
}