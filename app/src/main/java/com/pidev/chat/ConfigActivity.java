package com.pidev.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton;

public class ConfigActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        if (AppSettings.isConfigured(this)) {
            goMain();
            return;
        }

        TextInputEditText ipInput = findViewById(R.id.ipInput);
        TextInputEditText portInput = findViewById(R.id.portInput);
        MaterialButton startBtn = findViewById(R.id.startBtn);

        ipInput.setText(AppSettings.getServerIp(this));
        portInput.setText(String.valueOf(AppSettings.getPort(this)));

        startBtn.setEnabled(ipInput.getText().toString().trim().length() > 0);
        ipInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                startBtn.setEnabled(s.toString().trim().length() > 0);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        startBtn.setOnClickListener(v -> {
            AppSettings.setServerIp(this, ipInput.getText().toString().trim());
            try {
                AppSettings.setPort(this, Integer.parseInt(portInput.getText().toString().trim()));
            } catch (NumberFormatException e) {
                AppSettings.setPort(this, 8765);
            }
            AppSettings.setConfigured(this, true);
            goMain();
        });
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
