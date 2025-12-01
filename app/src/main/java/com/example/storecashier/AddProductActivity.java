package com.example.storecashier;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import java.util.Arrays;
import java.util.Collection;

public class AddProductActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private EditText etBarcode, etProductName, etProductPrice, etProductStock;
    private Button btnScanBarcode, btnSaveProduct;
    private DBHelper dbHelper;

    // 扫码相关
    private BarcodeView barcodeView;
    private FrameLayout scanPreviewContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        // 初始化控件和数据库
        initViews();
        dbHelper = new DBHelper(this);

        // 初始化扫码视图
        scanPreviewContainer = findViewById(R.id.scan_preview_container);
        barcodeView = new BarcodeView(this);
        scanPreviewContainer.addView(barcodeView);

        // 设置解码器工厂
        Collection<BarcodeFormat> formats = Arrays.asList(BarcodeFormat.UPC_A, BarcodeFormat.UPC_E, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.CODE_128);
        barcodeView.setDecoderFactory(new DefaultDecoderFactory(formats));

        // 设置扫码回调 - 单次扫码模式
        barcodeView.decodeSingle(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result != null && !result.getText().isEmpty()) {
                    String barcode = result.getText();
                    etBarcode.setText(barcode); // 扫码结果填充到条形码输入框
                }
            }

            @Override
            public void possibleResultPoints(java.util.List<com.google.zxing.ResultPoint> resultPoints) {
                // 可以添加扫码点动画
            }
        });

        // 扫码按钮点击事件
        btnScanBarcode.setOnClickListener(v -> checkCameraPermissionAndScan());

        // 保存商品按钮点击事件
        btnSaveProduct.setOnClickListener(v -> saveProduct());
    }

    // 初始化控件
    private void initViews() {
        etBarcode = findViewById(R.id.et_barcode);
        etProductName = findViewById(R.id.et_product_name);
        etProductPrice = findViewById(R.id.et_product_price);
        etProductStock = findViewById(R.id.et_product_stock);
        btnScanBarcode = findViewById(R.id.btn_scan_barcode);
        btnSaveProduct = findViewById(R.id.btn_save_product);
    }

    // 检查相机权限，无权限则申请
    private void checkCameraPermissionAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // 申请相机权限
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE
            );
        } else {
            // 已有权限，启动扫码
            startScan();
        }
    }

    // 启动/停止扫码
    private void startScan() {
        if (scanPreviewContainer.getVisibility() == View.VISIBLE) {
            // 停止扫码，隐藏预览
            scanPreviewContainer.setVisibility(View.GONE);
            barcodeView.pause();
        } else {
            // 开始扫码，显示预览
            scanPreviewContainer.setVisibility(View.VISIBLE);
            barcodeView.resume();
        }
    }

    // 权限申请结果回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan(); // 权限通过，启动扫码
            } else {
                Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "请填写完整商品信息", Toast.LENGTH_SHORT).show();
            return;
        }

        // 转换价格和库存为对应类型
        double price;
        int stock;
        try {
            price = Double.parseDouble(priceStr);
            stock = Integer.parseInt(stockStr);
            if (price <= 0 || stock < 0) {
                Toast.makeText(this, "价格需大于0，库存不能为负数", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "价格或库存格式错误", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建商品对象并保存
        Product product = new Product(barcode, name, price, stock);
        boolean isSaved = dbHelper.addProduct(product);
        if (isSaved) {
            Toast.makeText(this, "商品录入成功！", Toast.LENGTH_SHORT).show();
            // 清空输入框，方便继续录入
            clearInput();
        } else {
            Toast.makeText(this, "该条形码已存在，请勿重复录入", Toast.LENGTH_SHORT).show();
        }
    }

    // 清空输入框
    private void clearInput() {
        etBarcode.setText("");
        etProductName.setText("");
        etProductPrice.setText("");
        etProductStock.setText("");
    }

    // 保存activity状态，防止扫码后activity重建导致数据丢失
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // 保存输入框内容
        outState.putString("barcode", etBarcode.getText().toString());
        outState.putString("productName", etProductName.getText().toString());
        outState.putString("productPrice", etProductPrice.getText().toString());
        outState.putString("productStock", etProductStock.getText().toString());
    }

    // 恢复activity状态
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // 恢复输入框内容
        etBarcode.setText(savedInstanceState.getString("barcode"));
        etProductName.setText(savedInstanceState.getString("productName"));
        etProductPrice.setText(savedInstanceState.getString("productPrice"));
        etProductStock.setText(savedInstanceState.getString("productStock"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (barcodeView != null && scanPreviewContainer.getVisibility() == View.VISIBLE) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close(); // 关闭数据库，避免内存泄漏
    }
}