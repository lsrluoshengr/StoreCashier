package com.example.storecashier;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // 初始化界面元素
        TextView tvSoftwareName = findViewById(R.id.tv_software_name);
        TextView tvSoftwareVersion = findViewById(R.id.tv_software_version);
        TextView tvSoftwareDescription = findViewById(R.id.tv_software_description);
        TextView tvSoftwareDeveloper = findViewById(R.id.tv_software_developer);

        // 这里可以根据需要修改或加载软件详情
        // 例如，从配置文件或网络加载最新信息
    }
}
