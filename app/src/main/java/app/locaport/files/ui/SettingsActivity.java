package app.locaport.files.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import app.locaport.files.R;
import app.locaport.files.util.Prefs;

public class SettingsActivity extends AppCompatActivity {

    private static final String PROJECT_URL = "https://github.com/heyharshitrai/locaport";

    private TextInputEditText usernameInput;
    private TextInputEditText deviceNameInput;
    private TextInputEditText httpPortInput;
    private TextInputEditText pinInput;
    private TextInputLayout pinLayout;
    private MaterialSwitch fixedPinSwitch;
    private MaterialSwitch httpCompatSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        usernameInput = findViewById(R.id.usernameInput);
        usernameInput.setText(Prefs.username(this));

        deviceNameInput = findViewById(R.id.deviceNameInput);
        deviceNameInput.setText(Prefs.deviceName(this));

        httpPortInput = findViewById(R.id.httpPortInput);
        httpPortInput.setText(String.valueOf(Prefs.httpPort(this)));

        httpCompatSwitch = findViewById(R.id.httpCompatSwitch);
        httpCompatSwitch.setChecked(Prefs.httpCompat(this));
        httpCompatSwitch.setOnCheckedChangeListener(
                (button, checked) -> Prefs.setHttpCompat(this, checked));

        fixedPinSwitch = findViewById(R.id.fixedPinSwitch);
        pinLayout = findViewById(R.id.pinLayout);
        pinInput = findViewById(R.id.pinInput);
        fixedPinSwitch.setChecked(Prefs.fixedPinEnabled(this));
        pinInput.setText(Prefs.fixedPin(this));
        pinLayout.setVisibility(fixedPinSwitch.isChecked() ? View.VISIBLE : View.GONE);
        fixedPinSwitch.setOnCheckedChangeListener((button, checked) ->
                pinLayout.setVisibility(checked ? View.VISIBLE : View.GONE));

        TextView creditsVersion = findViewById(R.id.creditsVersion);
        creditsVersion.setText(getString(R.string.settings_credits, versionName()));

        TextView creditsGithub = findViewById(R.id.creditsGithub);
        creditsGithub.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL)));
            } catch (Exception ignored) {
                // No browser installed; the URL is still visible as text.
            }
        });
    }

    @Override
    protected void onPause() {
        Prefs.setUsername(this, text(usernameInput));
        Prefs.setDeviceName(this, text(deviceNameInput));
        Prefs.setHttpPort(this, readPort(httpPortInput, 8080));
        savePin();
        super.onPause();
    }

    private void savePin() {
        boolean fixed = fixedPinSwitch.isChecked();
        String pin = text(pinInput);
        if (fixed && !pin.matches("\\d{6}")) {
            Toast.makeText(this, R.string.toast_pin_invalid, Toast.LENGTH_LONG).show();
            Prefs.setFixedPinEnabled(this, false);
            Prefs.setFixedPin(this, "");
            return;
        }
        Prefs.setFixedPinEnabled(this, fixed);
        Prefs.setFixedPin(this, pin.matches("\\d{6}") ? pin : "");
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int readPort(TextInputEditText input, int fallback) {
        String raw = text(input);
        try {
            int port = Integer.parseInt(raw);
            if (port >= 1024 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }
        Toast.makeText(this, R.string.toast_port_invalid, Toast.LENGTH_SHORT).show();
        input.setText(String.valueOf(fallback));
        return fallback;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}
