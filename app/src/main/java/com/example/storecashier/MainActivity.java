package com.example.storecashier;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    // 保存所有Fragment实例
    private SettlementFragment settlementFragment;
    private AddProductFragment addProductFragment;
    private InventoryFragment inventoryFragment;
    private SettingsFragment settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);

        // 初始化所有Fragment
        settlementFragment = new SettlementFragment();
        addProductFragment = new AddProductFragment();
        inventoryFragment = new InventoryFragment();
        settingsFragment = new SettingsFragment();

        // 初始Fragment设置
        if (savedInstanceState == null) {
            // 设置默认选中项
            bottomNavigationView.setSelectedItemId(R.id.nav_settlement);

            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, settlementFragment)
                    .add(R.id.fragment_container, addProductFragment)
                    .add(R.id.fragment_container, inventoryFragment)
                    .add(R.id.fragment_container, settingsFragment)
                    .hide(addProductFragment)
                    .hide(inventoryFragment)
                    .hide(settingsFragment)
                    .commit();
        }

        // 设置导航项选中事件
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // 隐藏所有Fragment
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.hide(settlementFragment)
                    .hide(addProductFragment)
                    .hide(inventoryFragment)
                    .hide(settingsFragment);

            // 显示选中的Fragment
            if (itemId == R.id.nav_settlement) {
                transaction.show(settlementFragment);
            } else if (itemId == R.id.nav_add_product) {
                transaction.show(addProductFragment);
            } else if (itemId == R.id.nav_inventory) {
                transaction.show(inventoryFragment);
            } else if (itemId == R.id.nav_settings) {
                transaction.show(settingsFragment);
            }

            transaction.commit();
            return true;
        });
    }
}
