package app.locaport.files.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Picks a reachable IPv4 address: station, hotspot, then wired.
public final class Net {

    private Net() {
    }

    public static final class Address {
        public final String iface;
        public final String ip;

        public Address(String iface, String ip) {
            this.iface = iface;
            this.ip = ip;
        }

        @Override
        public String toString() {
            return iface + "=" + ip;
        }
    }

    public static boolean onWifi(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    public static List<Address> localAddresses() {
        List<Address> result = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif == null || !nif.isUp() || nif.isLoopback()) {
                    continue;
                }
                if (isExcluded(nif.getName())) {
                    continue;
                }
                for (InterfaceAddress address : nif.getInterfaceAddresses()) {
                    InetAddress inet = address.getAddress();
                    if (inet instanceof Inet4Address
                            && inet.isSiteLocalAddress()
                            && !inet.isLoopbackAddress()) {
                        result.add(new Address(nif.getName(), inet.getHostAddress()));
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return result;
    }

    private static boolean isExcluded(String name) {
        return name.startsWith("rmnet")      // cellular
                || name.startsWith("tun")    // VPN
                || name.startsWith("tap")
                || name.startsWith("dummy")
                || name.startsWith("p2p")    // Wi-Fi Direct
                || name.startsWith("aware")  // Wi-Fi Aware
                || name.startsWith("nan");
    }

    private static boolean isHotspot(String name) {
        return name.startsWith("ap")
                || name.startsWith("swlan")
                || name.startsWith("softap");
    }

    private static boolean isWifiClient(String name) {
        return name.startsWith("wlan");
    }

    private static boolean isWired(String name) {
        return name.startsWith("usb")
                || name.startsWith("rndis")
                || name.startsWith("eth");
    }

    public static String pickAddress(List<Address> addresses, boolean wifiClient) {
        if (addresses == null || addresses.isEmpty()) {
            return null;
        }
        if (wifiClient) {
            for (Address address : addresses) {
                if (isWifiClient(address.iface)) {
                    return address.ip;
                }
            }
        }
        for (Address address : addresses) {
            if (isHotspot(address.iface)) {
                return address.ip;
            }
        }
        for (Address address : addresses) {
            if (isWifiClient(address.iface)) {
                return address.ip;
            }
        }
        for (Address address : addresses) {
            if (isWired(address.iface)) {
                return address.ip;
            }
        }
        return addresses.get(0).ip;
    }

    public static String localIpv4(Context ctx) {
        return pickAddress(localAddresses(), onWifi(ctx));
    }

    public static String baseUrl(String ip, int port) {
        return "https://" + ip + ":" + port + "/";
    }
}
