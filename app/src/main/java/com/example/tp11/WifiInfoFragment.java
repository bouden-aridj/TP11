package com.example.tp11;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

/**
 * Fragment displaying current Wi-Fi connection details:
 * SSID, IPv4 address, RSSI signal strength, link speed, frequency, and gateway.
 */
public class WifiInfoFragment extends Fragment {

    private View vStatusIndicator;
    private TextView tvConnectionStatus;
    private TextView tvInfoSsid;
    private TextView tvInfoIp;
    private TextView tvInfoRssi;
    private TextView tvInfoLinkSpeed;
    private TextView tvInfoFrequency;
    private TextView tvInfoGateway;
    private MaterialButton btnRefreshInfo;

    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wifi_info, container, false);

        vStatusIndicator = view.findViewById(R.id.vStatusIndicator);
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
        tvInfoSsid = view.findViewById(R.id.tvInfoSsid);
        tvInfoIp = view.findViewById(R.id.tvInfoIp);
        tvInfoRssi = view.findViewById(R.id.tvInfoRssi);
        tvInfoLinkSpeed = view.findViewById(R.id.tvInfoLinkSpeed);
        tvInfoFrequency = view.findViewById(R.id.tvInfoFrequency);
        tvInfoGateway = view.findViewById(R.id.tvInfoGateway);
        btnRefreshInfo = view.findViewById(R.id.btnRefreshInfo);

        Context context = requireContext();
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        btnRefreshInfo.setOnClickListener(v -> updateWifiDetails());

        setupNetworkCallback();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWifiDetails();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
        }
    }

    private void setupNetworkCallback() {
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                mainHandler.post(() -> updateWifiDetails());
            }

            @Override
            public void onLost(@NonNull Network network) {
                mainHandler.post(() -> updateWifiDetails());
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
                mainHandler.post(() -> updateWifiDetails());
            }
        };

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
        } catch (Exception ignored) {
        }
    }

    /**
     * Reads and displays current Wi-Fi status and connection parameters.
     */
    public void updateWifiDetails() {
        if (wifiManager == null || getContext() == null) return;

        boolean isWifiEnabled = wifiManager.isWifiEnabled();
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();

        boolean isConnected = isWifiEnabled && wifiInfo != null &&
                wifiInfo.getNetworkId() != -1 &&
                !"<unknown ssid>".equalsIgnoreCase(wifiInfo.getSSID());

        if (isConnected) {
            vStatusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_connected));
            tvConnectionStatus.setText("Connected to Wi-Fi");

            // Format SSID
            String rawSsid = wifiInfo.getSSID();
            String ssid = (rawSsid != null && rawSsid.startsWith("\"") && rawSsid.endsWith("\"")) ?
                    rawSsid.substring(1, rawSsid.length() - 1) : rawSsid;
            tvInfoSsid.setText(ssid != null ? ssid : "Connected Wi-Fi");

            // Format IPv4 Address
            int ipInt = wifiInfo.getIpAddress();
            String ipAddress = Formatter.formatIpAddress(ipInt);
            tvInfoIp.setText(ipAddress.equals("0.0.0.0") ? "Unavailable" : ipAddress);

            // Signal Strength (RSSI dBm & Level)
            int rssi = wifiInfo.getRssi();
            int level;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                level = wifiManager.calculateSignalLevel(rssi);
            } else {
                level = WifiManager.calculateSignalLevel(rssi, 5);
            }
            tvInfoRssi.setText(rssi + " dBm (Level " + level + "/4)");

            // Link Speed
            int linkSpeed = wifiInfo.getLinkSpeed();
            tvInfoLinkSpeed.setText(linkSpeed + " Mbps");

            // Frequency
            int frequency = wifiInfo.getFrequency();
            String band = (frequency >= 4900 && frequency <= 5900) ? " (5 GHz)" : " (2.4 GHz)";
            tvInfoFrequency.setText(frequency + " MHz" + band);

            // Gateway IP Address
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                tvInfoGateway.setText(Formatter.formatIpAddress(dhcpInfo.gateway));
            } else {
                tvInfoGateway.setText("N/A");
            }

        } else {
            vStatusIndicator.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.status_disconnected));
            tvConnectionStatus.setText(isWifiEnabled ? "Disconnected from Wi-Fi" : "Wi-Fi Disabled");
            tvInfoSsid.setText("Not Connected");
            tvInfoIp.setText("0.0.0.0");
            tvInfoRssi.setText("N/A");
            tvInfoLinkSpeed.setText("N/A");
            tvInfoFrequency.setText("N/A");
            tvInfoGateway.setText("N/A");
        }
    }
}