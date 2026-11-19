package app.locaport.files.server;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import java.io.File;
import java.security.SecureRandom;

import javax.net.ssl.SSLContext;

import app.locaport.files.ui.Notifications;
import app.locaport.files.util.Net;
import app.locaport.files.util.Prefs;

// Runs the listeners, notification, locks and shutdown rules.
public class LocaportService extends Service {

    public static final String ACTION_START = "app.locaport.files.action.START";
    public static final String ACTION_STOP = "app.locaport.files.action.STOP";
    public static final int NOTIFICATION_ID = 4210;
    private static final long IDLE_TIMEOUT_MS = 10 * 60 * 1000L;

    public static final class Snapshot {
        public final boolean running;
        public final String url;
        public final String httpUrl;
        public final String uncPath;
        public final String username;
        public final String pin;

        Snapshot(boolean running, String url, String httpUrl, String uncPath,
                 String username, String pin) {
            this.running = running;
            this.url = url;
            this.httpUrl = httpUrl;
            this.uncPath = uncPath;
            this.username = username;
            this.pin = pin;
        }
    }

    private static volatile Snapshot snapshot = new Snapshot(false, null, null, null, null, null);

    public static Snapshot snapshot() {
        return snapshot;
    }

    public static boolean isRunning() {
        return snapshot.running;
    }

    private final IBinder binder = new LocalBinder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private LocaportServer secureServer;
    private LocaportServer plainServer;
    private Mdns mdns;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private SSLContext sslContext;

    private boolean receiversRegistered;
    private boolean tornDown;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) && Prefs.stopOnLock(context)) {
                shutdownNow();
            }
        }
    };

    private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.postDelayed(() -> {
                if (Net.localIpv4(LocaportService.this) == null) {
                    shutdownNow();
                }
            }, 1500L);
        }
    };

    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            if (anyServer() && Prefs.autoStop(LocaportService.this)
                    && idleMillis() > IDLE_TIMEOUT_MS) {
                shutdownNow();
                return;
            }
            handler.postDelayed(this, 60_000L);
        }
    };

    public final class LocalBinder extends Binder {
        public LocaportService service() {
            return LocaportService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notifications.createChannel(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            shutdownNow();
            return START_NOT_STICKY;
        }

        if (anyServer()) {
            return START_STICKY;
        }

        startInForeground(getString(app.locaport.files.R.string.status_starting));

        if (!Environment.isExternalStorageManager() || Net.localIpv4(this) == null) {
            shutdownNow();
            return START_NOT_STICKY;
        }

        try {
            startServers();
            return START_STICKY;
        } catch (Exception e) {
            shutdownNow();
            return START_NOT_STICKY;
        }
    }

    private void startServers() throws Exception {
        tornDown = false;
        String ip = Net.localIpv4(this);
        if (ip == null) {
            throw new IllegalStateException("No local address");
        }

        int port = Prefs.port(this);
        int httpPort = Prefs.httpPort(this);
        File root = Environment.getExternalStorageDirectory();
        boolean readOnly = Prefs.readOnly(this);

        String username = Prefs.username(this);
        String fixedPin = Prefs.fixedPin(this);
        String pin = Prefs.fixedPinEnabled(this) && !fixedPin.isEmpty()
                ? fixedPin
                : Auth.randomPin(new SecureRandom());
        Auth auth = new Auth(username, pin);
        String deviceName = Prefs.deviceName(this);
        sslContext = Tls.createContext(
                new File(getFilesDir(), "locaport-tls.p12"), Prefs.tlsPassword(this));

        secureServer = new LocaportServer(ip, root, port, auth, readOnly, sslContext, true, deviceName);
        secureServer.startServer();

        if (Prefs.httpCompat(this) && httpPort != port) {
            try {
                plainServer = new LocaportServer(ip, root, httpPort, auth, readOnly, null, false, deviceName);
                plainServer.startServer();
            } catch (Exception ignored) {
                // Port busy or unavailable: keep HTTPS only.
                plainServer = null;
            }
        }

        String httpUrl = plainServer != null
                ? "http://" + ip + ":" + httpPort + "/"
                : null;
        String uncPath = plainServer != null
                ? "\\\\" + ip + "@" + httpPort + "\\DavWWWRoot"
                : null;

        mdns = new Mdns(this);
        mdns.register(deviceName, plainServer != null ? httpPort : port, username);

        acquireLocks();
        registerReceivers();

        snapshot = new Snapshot(true, Net.baseUrl(ip, port), httpUrl, uncPath, username, pin);
        updateForeground(getString(app.locaport.files.R.string.notification_text,
                httpUrl != null ? httpUrl : snapshot.url));

        handler.removeCallbacks(idleCheck);
        handler.postDelayed(idleCheck, 60_000L);
    }

    private boolean anyServer() {
        return (secureServer != null && secureServer.isAlive())
                || (plainServer != null && plainServer.isAlive());
    }

    private long idleMillis() {
        long idle = Long.MAX_VALUE;
        if (secureServer != null) {
            idle = Math.min(idle, secureServer.idleMillis());
        }
        if (plainServer != null) {
            idle = Math.min(idle, plainServer.idleMillis());
        }
        return idle == Long.MAX_VALUE ? 0 : idle;
    }

    private void startInForeground(String text) {
        android.app.Notification notification = Notifications.build(this, text);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateForeground(String text) {
        startInForeground(text);
    }

    private void acquireLocks() {
        PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (power != null && wakeLock == null) {
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Locaport:server");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(6 * 60 * 60 * 1000L);
        }
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null && wifiLock == null) {
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Locaport:wifi");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    private void registerReceivers() {
        if (receiversRegistered) {
            return;
        }
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        registerReceiver(connectivityReceiver,
                new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));
        receiversRegistered = true;
    }

    // Tear down first: stopSelf() alone leaves a bound service running.
    private void shutdownNow() {
        teardown();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private synchronized void teardown() {
        if (tornDown) {
            return;
        }
        tornDown = true;
        handler.removeCallbacksAndMessages(null);

        if (mdns != null) {
            mdns.unregister();
            mdns = null;
        }
        secureServer = stopServer(secureServer);
        plainServer = stopServer(plainServer);

        if (wakeLock != null) {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            wakeLock = null;
        }
        if (wifiLock != null) {
            if (wifiLock.isHeld()) {
                wifiLock.release();
            }
            wifiLock = null;
        }
        if (receiversRegistered) {
            try {
                unregisterReceiver(screenReceiver);
                unregisterReceiver(connectivityReceiver);
            } catch (Exception ignored) {
            }
            receiversRegistered = false;
        }
        snapshot = new Snapshot(false, null, null, null, null, null);
    }

    private static LocaportServer stopServer(LocaportServer server) {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    public void setReadOnly(boolean readOnly) {
        if (secureServer != null) {
            secureServer.setReadOnly(readOnly);
        }
        if (plainServer != null) {
            plainServer.setReadOnly(readOnly);
        }
    }
}
