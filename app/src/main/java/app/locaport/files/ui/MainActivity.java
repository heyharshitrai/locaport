package app.locaport.files.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import app.locaport.files.R;
import app.locaport.files.server.LocaportService;
import app.locaport.files.util.Net;
import app.locaport.files.util.Prefs;
import app.locaport.files.util.Qr;

public class MainActivity extends AppCompatActivity {

    private TextView statusTitle;
    private TextView statusSubtitle;
    private TextView addressText;
    private TextView certNote;
    private TextView uncText;
    private TextView userText;
    private TextView pinText;
    private ImageView qrImage;
    private View detailsGroup;
    private View httpGroup;
    private MaterialButton startStopButton;
    private MaterialButton copyButton;
    private MaterialButton copyUncButton;

    private LocaportService service;
    private boolean bound;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((LocaportService.LocalBinder) binder).service();
            bound = true;
            render();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            service = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });

        statusTitle = findViewById(R.id.statusTitle);
        statusSubtitle = findViewById(R.id.statusSubtitle);
        addressText = findViewById(R.id.addressText);
        certNote = findViewById(R.id.certNote);
        uncText = findViewById(R.id.uncText);
        userText = findViewById(R.id.userText);
        pinText = findViewById(R.id.pinText);
        qrImage = findViewById(R.id.qrImage);
        detailsGroup = findViewById(R.id.detailsGroup);
        httpGroup = findViewById(R.id.httpGroup);
        startStopButton = findViewById(R.id.startStopButton);
        copyButton = findViewById(R.id.copyButton);
        copyUncButton = findViewById(R.id.copyUncButton);

        startStopButton.setOnClickListener(v -> toggleServer());
        copyButton.setOnClickListener(v -> copyPrimaryAddress());
        copyUncButton.setOnClickListener(v -> copyUncPath());

        bindSwitch(R.id.readOnlySwitch, Prefs.readOnly(this), (button, checked) -> {
            Prefs.setReadOnly(this, checked);
            if (bound && service != null) {
                service.setReadOnly(checked);
            }
        });
        bindSwitch(R.id.autoStopSwitch, Prefs.autoStop(this),
                (button, checked) -> Prefs.setAutoStop(this, checked));
        bindSwitch(R.id.stopOnLockSwitch, Prefs.stopOnLock(this),
                (button, checked) -> Prefs.setStopOnLock(this, checked));

        render();
    }

    private interface SwitchListener {
        void onChanged(MaterialSwitch button, boolean checked);
    }

    private void bindSwitch(int id, boolean initial, SwitchListener listener) {
        MaterialSwitch button = findViewById(id);
        button.setChecked(initial);
        button.setOnCheckedChangeListener((view, checked) -> listener.onChanged(button, checked));
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, LocaportService.class), connection, BIND_AUTO_CREATE);
        render();
    }

    @Override
    protected void onStop() {
        if (bound) {
            unbindService(connection);
            bound = false;
            service = null;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void toggleServer() {
        if (LocaportService.isRunning()) {
            startService(new Intent(this, LocaportService.class)
                    .setAction(LocaportService.ACTION_STOP));
            handler.postDelayed(this::render, 700);
            return;
        }

        if (Net.localIpv4(this) == null) {
            Toast.makeText(this, R.string.toast_no_wifi, Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this, R.string.toast_permission_needed, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
            return;
        }

        ContextCompat.startForegroundService(this,
                new Intent(this, LocaportService.class)
                        .setAction(LocaportService.ACTION_START));
        handler.postDelayed(this::render, 900);
    }

    private void copyPrimaryAddress() {
        LocaportService.Snapshot snapshot = LocaportService.snapshot();
        String value = snapshot.httpUrl != null ? snapshot.httpUrl : snapshot.url;
        if (snapshot.running && value != null) {
            copyToClipboard("Locaport address", value);
        }
    }

    private void copyUncPath() {
        LocaportService.Snapshot snapshot = LocaportService.snapshot();
        if (snapshot.running && snapshot.uncPath != null) {
            copyToClipboard("Locaport Explorer path", snapshot.uncPath);
        }
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void render() {
        LocaportService.Snapshot snapshot = LocaportService.snapshot();
        boolean running = snapshot.running;

        statusTitle.setText(running ? R.string.status_running : R.string.status_stopped);
        statusSubtitle.setText(running
                ? R.string.status_subtitle_running
                : R.string.status_subtitle_stopped);
        startStopButton.setText(running ? R.string.action_stop : R.string.action_start);
        copyButton.setVisibility(running ? View.VISIBLE : View.GONE);
        detailsGroup.setVisibility(running ? View.VISIBLE : View.GONE);

        if (running) {
            String primary = snapshot.httpUrl != null ? snapshot.httpUrl : snapshot.url;
            addressText.setText(primary);
            userText.setText(snapshot.username);
            pinText.setText(snapshot.pin);
            qrImage.setImageBitmap(Qr.encode(primary, 512));
            certNote.setVisibility(snapshot.httpUrl == null ? View.VISIBLE : View.GONE);
        } else {
            qrImage.setImageDrawable(null);
            certNote.setVisibility(View.GONE);
        }

        boolean hasWindows = running && snapshot.uncPath != null;
        httpGroup.setVisibility(hasWindows ? View.VISIBLE : View.GONE);
        if (hasWindows) {
            uncText.setText(snapshot.uncPath);
        }
    }
}
