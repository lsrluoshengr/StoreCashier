package com.example.storecashier;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class AddProductFragment extends Fragment {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private EditText etBarcode, etProductName, etProductPrice, etProductStock;
    private Button btnScanBarcode, btnSaveProduct;
    private DBHelper dbHelper;

    // 扫码回调
    private final ActivityResultLauncher<ScanOptions> barcodeLauncher = registerForActivityResult(new ScanContract(), result -> {
        if (result != null && result.getContents() != null) {
            etBarcode.setText(result.getContents()); // 扫码结果填充到条形码输入框
        }
    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_add_product, container, false);

        // 初始化控件和数据库
        initViews(view);
        dbHelper = new DBHelper(requireContext());

        // 扫码按钮点击事件
        btnScanBarcode.setOnClickListener(v -> checkCameraPermissionAndScan());

        // 保存商品按钮点击事件
        btnSaveProduct.setOnClickListener(v -> saveProduct());

        return view;
    }

    // 初始化控件
    private void initViews(View view) {
        etBarcode = view.findViewById(R.id.et_barcode);
        etProductName = view.findViewById(R.id.et_product_name);
        etProductPrice = view.findViewById(R.id.et_product_price);
        etProductStock = view.findViewById(R.id.et_product_stock);
        btnScanBarcode = view.findViewById(R.id.btn_scan_barcode);
        btnSaveProduct = view.findViewById(R.id.btn_save_product);
    }

    // 检查相机权限，无权限则申请
    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // 申请相机权限
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            // 已有权限，启动扫码
            startScan();
        }
    }

    // 启动扫码（ZXing配置）
    private void startScan() {
        ScanOptions scanOptions = new ScanOptions();
        scanOptions.setPrompt("请对准商品条形码扫码"); // 扫码提示文字
        scanOptions.setBeepEnabled(true); // 扫码成功蜂鸣
        scanOptions.setOrientationLocked(true); // 锁定竖屏
        barcodeLauncher.launch(scanOptions);
    }

    // 权限申请结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan(); // 权限通过，启动扫码
            } else {
                Toast.makeText(requireContext(), "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 保存商品到数据库
    private void saveProduct() {
        // 获取输入内容
        String barcode = etBarcode.getText().toString().trim();
        String name = etProductName.getText().toString().trim();
        String priceStr = etProductPrice.getText().toString().trim();
        String stockStr = etProductStock.getText().toString().trim();

        // 校验输入（非空）
        if (barcode.isEmpty() || name.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
            Toast.makeText(requireContext(), "请填写完整商品信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 转换价格和库存为对应类型
        double price;
        int stock;
        try {
            price = Double.parseDouble(priceStr);
            stock = Integer.parseInt(stockStr);
            if (price <= 0 || stock < 0) {
                Toast.makeText(requireContext(), "价格需大于0，库存不能为负数", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "价格或库存格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建商品对象并保存
        Product product = new Product(barcode, name, price, stock);
        boolean isSaved = dbHelper.addProduct(product);
        if (isSaved) {
            Toast.makeText(requireContext(), "商品录入成功！", Toast.LENGTH_SHORT).show();
            // 清空输入框，方便继续录入
            clearInput();
        } else {
            Toast.makeText(requireContext(), "该条形码已存在，请勿重复录入", Toast.LENGTH_SHORT).show();
        }
    }

    // 清空输入框
    private void clearInput() {
        etBarcode.setText("");
        etProductName.setText("");
        etProductPrice.setText("");
        etProductStock.setText("");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dbHelper.close(); // 关闭数据库，避免内存泄漏
    }
}
