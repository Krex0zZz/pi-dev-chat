package com.pidev.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.button.MaterialButton;

public class ConfigActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);

        TextInputEditText ipInput = findViewById(R.id.ipInput);
        TextInputEditText portInput = findViewById(R.id.portInput);
        MaterialButton startBtn = findViewById(R.id.startBtn);
        Switch keepAliveSwitch = findViewById(R.id.keepAliveSwitch);

        // If already configured, show current values but still allow editing
        ipInput.setText(AppSettings.getServerIp(this));
        portInput.setText(String.valueOf(AppSettings.getPort(this)));

        // Check if we're returning from MainActivity to reconfigure
        boolean isReconfigure = getIntent().getBooleanExtra("return_to_main", false);
        if (isReconfigure) {
            startBtn.setText("Save & Return");
        } else if (AppSettings.isConfigured(this)) {
            startBtn.setText("Update & Continue");
        }

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

            if (isReconfigure) {
                // Return to MainActivity which will auto-reconnect
                setResult(RESULT_OK);
                finish();
            } else {
                goMain();
            }
        });
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
