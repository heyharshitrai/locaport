package app.locaport.files.server;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

public final class Mdns {

    public static final String SERVICE_TYPE = "_webdav._tcp";

    private final NsdManager manager;
    private NsdManager.RegistrationListener listener;
    private String registeredAs;

    public Mdns(Context context) {
        this.manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    }

    public void register(String desiredName, int port, String username) {
        unregister();
        if (manager == null) {
            return;
        }
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName(desiredName);
        info.setServiceType(SERVICE_TYPE);
        info.setPort(port);

        info.setAttribute("path", "/");
        info.setAttribute("u", username);

        listener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                registeredAs = serviceInfo.getServiceName();
                Log.i("LocaportMdns", "registered as " + registeredAs);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.w("LocaportMdns", " registration failed: " + errorCode);
                listener = null;
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            }
        };
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener);
        } catch (Exception e) {
            listener = null;
        }
    }

    public String registeredAs() {
        return registeredAs;
    }

    public void unregister() {
        if (manager != null && listener != null) {
            try {
                manager.unregisterService(listener);
            } catch (Exception ignored) {
            }
        }
        listener = null;
        registeredAs = null;
    }
}
