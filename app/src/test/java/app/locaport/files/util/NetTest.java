package app.locaport.files.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NetTest {

    private static List<Net.Address> addresses(Net.Address... entries) {
        return Arrays.asList(entries);
    }

    @Test
    public void hotspotIsPreferredWhenNotAWifiClient() {
        List<Net.Address> all = addresses(
                new Net.Address("wlan0", "192.168.1.20"),
                new Net.Address("ap0", "192.168.43.1"));
        assertEquals("192.168.43.1", Net.pickAddress(all, false));
    }

    @Test
    public void stationIsPreferredWhenAWifiClient() {
        List<Net.Address> all = addresses(
                new Net.Address("ap0", "192.168.43.1"),
                new Net.Address("wlan0", "192.168.1.20"));
        assertEquals("192.168.1.20", Net.pickAddress(all, true));
    }

    @Test
    public void usbTetheringWorks() {
        List<Net.Address> all = addresses(new Net.Address("rndis0", "192.168.42.129"));
        assertEquals("192.168.42.129", Net.pickAddress(all, false));
    }

    @Test
    public void ethernetWorks() {
        List<Net.Address> all = addresses(new Net.Address("eth0", "10.0.0.8"));
        // 10.x is private but not site-local in the InetAddress sense; the
        // picker itself only receives addresses already filtered upstream.
        assertEquals("10.0.0.8", Net.pickAddress(all, false));
    }

    @Test
    public void fallsBackToFirstAddress() {
        List<Net.Address> all = addresses(new Net.Address("swlan0", "192.168.232.1"));
        assertEquals("192.168.232.1", Net.pickAddress(all, false));
    }

    @Test
    public void noAddressesMeansNull() {
        assertNull(Net.pickAddress(Collections.emptyList(), false));
        assertNull(Net.pickAddress(null, true));
    }
}
