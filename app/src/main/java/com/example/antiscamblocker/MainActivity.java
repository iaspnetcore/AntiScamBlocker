package com.example.antiscamblocker;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // 1. 注册结果监听器，用来接收系统角色弹窗的返回结果
    private final ActivityResultLauncher<Intent> roleRequestLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Toast.makeText(this, "Success: Anti-Scam Interceptor Enabled!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Permission Denied: Cannot block calls without this role.", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 保留 Android 15 默认的边缘到边缘（全屏沉浸式）设计
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 保留并调整窗口边距，防止刘海屏或系统导航栏遮挡界面元素
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 2. 初始化并绑定界面上的开启防护按钮
        Button btnEnableProtection = findViewById(R.id.btn_enable_protection);

        // 3. 设置点击事件：点击时去向系统申请角色
        btnEnableProtection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkAndRequestCallScreeningRole();
            }
        });
    }

    /**
     * 核心逻辑：检查是否已经是官方拦截器，不是则唤起系统级角色申请弹窗。
     */
    private void checkAndRequestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(Context.ROLE_SERVICE);

            if (roleManager != null) {
                // 检查：我们 App 此时有没有拿着“来电拦截角色”
                boolean isRoleHeld = roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

                if (!isRoleHeld) {
                    // 没有权限，创建隐式意图，准备蹦出官方勾选弹窗
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                    roleRequestLauncher.launch(intent);
                } else {
                    Toast.makeText(this, "Anti-Scam active. Background screening is running.", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "This feature requires Android 10 (API 29) or higher.", Toast.LENGTH_SHORT).show();
        }
    }
}
