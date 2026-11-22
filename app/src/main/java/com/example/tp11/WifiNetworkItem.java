package com.example.tp11;

/**
 * Model representing an available Wi-Fi network discovered during scanning.
 */
public class WifiNetworkItem {
    private final String ssid;
    private final String bssid;
    private final int rssi;
    private final int level; // 0 to 4 signal bars
    private final String securityType; // WPA2, WPA3, WEP, or Open
    private final String capabilities;

    public WifiNetworkItem(String ssid, String bssid, int rssi, int level, String securityType, String capabilities) {
        this.ssid = ssid != null && !ssid.isEmpty() ? ssid : "<Hidden SSID>";
        this.bssid = bssid != null ? bssid : "00:00:00:00:00:00";
        this.rssi = rssi;
        this.level = level;
        this.securityType = securityType != null ? securityType : "Open";
        this.capabilities = capabilities != null ? capabilities : "";
    }

    public String getSsid() {
        return ssid;
    }

    public String getBssid() {
        return bssid;
    }

    public int getRssi() {
        return rssi;
    }

    public int getLevel() {
        return level;
    }

    public String getSecurityType() {
        return securityType;
    }

    public String getCapabilities() {
        return capabilities;
    }
}