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


import androidx.lifecycle.ViewModelProvider;

import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

import android.net.Uri;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import java.io.File;

public class AddProductFragment extends Fragment {
    private EditText etBarcode, etProductName, etProductPrice, etProductStock;
    private AutoCompleteTextView actvCategory;
    private ImageView ivProductImage;
    private Button btnScanBarcode, btnSaveProduct, btnSelectImage;
    private ProductViewModel productViewModel;
    
    private Uri selectedImageUri; // 选中的图片 Uri

    // 图片选择器
    private final ActivityResultLauncher<String> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    Glide.with(this).load(uri).into(ivProductImage);
                }
            }
    );

    // 相机权限请求码
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    // 扫码请求码
    private static final int SCAN_REQUEST_CODE = 1001;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_product, container, false);
        productViewModel = new ViewModelProvider(requireActivity()).get(ProductViewModel.class);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        etBarcode = view.findViewById(R.id.et_barcode);
        etProductName = view.findViewById(R.id.et_product_name);
        etProductPrice = view.findViewById(R.id.et_product_price);
        etProductStock = view.findViewById(R.id.et_product_stock);
        actvCategory = view.findViewById(R.id.actv_category);
        ivProductImage = view.findViewById(R.id.iv_product_image);
        btnScanBarcode = view.findViewById(R.id.btn_scan_barcode);
        btnSaveProduct = view.findViewById(R.id.btn_save_product);
        btnSelectImage = view.findViewById(R.id.btn_select_image);

        // 设置分类建议
        setupCategorySuggestions();

        btnScanBarcode.setOnClickListener(v -> checkCameraPermissionAndScan());
        btnSaveProduct.setOnClickListener(v -> saveProduct());
        btnSelectImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
    }

    private void setupCategorySuggestions() {
        productViewModel.getAllCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_dropdown_item_1line, categories);
                actvCategory.setAdapter(adapter);
            }
        });
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
        String category = actvCategory.getText().toString().trim();

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

        // 使用 Room 后台操作
        AppDatabase.databaseWriteExecutor.execute(() -> {
            Product existing = productViewModel.getProductByBarcodeSync(barcode);
            if (existing != null) {
                requireActivity().runOnUiThread(() -> 
                    Toast.makeText(requireContext(), "该条形码已存在，请勿重复录入", Toast.LENGTH_SHORT).show()
                );
            } else {
                // 如果选择了图片，拷贝到私有目录
                String imagePath = null;
                if (selectedImageUri != null) {
                    String fileName = "img_" + barcode + "_" + System.currentTimeMillis() + ".jpg";
                    imagePath = FileUtil.copyImageToInternal(requireContext(), selectedImageUri, fileName);
                }

                Product product = new Product(barcode, name, price, stock, category, imagePath);
                productViewModel.insert(product);
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "商品录入成功！", Toast.LENGTH_SHORT).show();
                    clearInput();
                });
            }
        });
    }

    private void clearInput() {
        etBarcode.setText("");
        etProductName.setText("");
        etProductPrice.setText("");
        etProductStock.setText("");
        actvCategory.setText("");
        ivProductImage.setImageResource(R.drawable.default_img);
        selectedImageUri = null;
        etBarcode.requestFocus(); // 让光标回到条码输入框
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}