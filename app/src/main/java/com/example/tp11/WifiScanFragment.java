package com.example.tp11;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment responsible for scanning nearby Wi-Fi networks and connecting
 * using WifiNetworkSpecifier (Android 10+). Holds bound specifier network reference.
 */
public class WifiScanFragment extends Fragment implements WifiAdapter.OnNetworkClickListener {

    private static Network boundSpecifierNetwork = null;
    private static String connectedSpecifierSsid = null;

    private TextView tvScanStatus;
    private MaterialButton btnStartScan;
    private LinearProgressIndicator scanProgressBar;
    private LinearLayout layoutPermissionBanner;
    private LinearLayout layoutSpecifierStatus;
    private TextView tvSpecifierInfo;
    private MaterialButton btnDisconnectSpecifier;
    private TextView tvEmptyState;

    private WifiAdapter adapter;
    private WifiManager wifiManager;
    private ConnectivityManager connectivityManager;

    private ConnectivityManager.NetworkCallback specifierNetworkCallback;
    private BroadcastReceiver scanReceiver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Static getter for SocketChatFragment to bind sockets directly to the WifiNetworkSpecifier network.
     */
    public static Network getBoundSpecifierNetwork() {
        return boundSpecifierNetwork;
    }

    public static String getConnectedSpecifierSsid() {
        return connectedSpecifierSsid;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wifi_scan, container, false);

        tvScanStatus = view.findViewById(R.id.tvScanStatus);
        btnStartScan = view.findViewById(R.id.btnStartScan);
        scanProgressBar = view.findViewById(R.id.scanProgressBar);
        layoutPermissionBanner = view.findViewById(R.id.layoutPermissionBanner);
        layoutSpecifierStatus = view.findViewById(R.id.layoutSpecifierStatus);
        tvSpecifierInfo = view.findViewById(R.id.tvSpecifierInfo);
        btnDisconnectSpecifier = view.findViewById(R.id.btnDisconnectSpecifier);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        MaterialButton btnGrantPermission = view.findViewById(R.id.btnGrantPermission);

        RecyclerView rvWifiList = view.findViewById(R.id.rvWifiList);
        rvWifiList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new WifiAdapter(this);
        rvWifiList.setAdapter(adapter);

        Context context = requireContext();
        wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        btnStartScan.setOnClickListener(v -> startWifiScan());
        btnGrantPermission.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).requestRequiredPermissions();
            }
        });
        btnDisconnectSpecifier.setOnClickListener(v -> disconnectSpecifierNetwork());

        updateSpecifierUi();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        checkPermissionsAndState();
        registerScanReceiver();
        updateSpecifierUi();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterScanReceiver();
    }

    private void checkPermissionsAndState() {
        if (getContext() == null) return;
        boolean hasPermission = hasRequiredScanPermissions();
        layoutPermissionBanner.setVisibility(hasPermission ? View.GONE : View.VISIBLE);
    }

    private boolean hasRequiredScanPermissions() {
        Context context = getContext();
        if (context == null) return false;

        boolean locationGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean nearbyGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
            return locationGranted || nearbyGranted;
        }
        return locationGranted;
    }

    private void registerScanReceiver() {
        if (getContext() == null || scanReceiver != null) return;

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    scanProgressBar.setVisibility(View.GONE);
                    if (success) {
                        tvScanStatus.setText("Scan complete");
                        displayScanResults();
                    } else {
                        tvScanStatus.setText("Scan throttled or failed. Showing cached results.");
                        displayScanResults();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        requireContext().registerReceiver(scanReceiver, filter);
    }

    private void unregisterScanReceiver() {
        if (getContext() != null && scanReceiver != null) {
            try {
                requireContext().unregisterReceiver(scanReceiver);
            } catch (Exception ignored) {
            }
            scanReceiver = null;
        }
    }

    /**
     * Triggers Wi-Fi scan with location & throttling handling.
     */
    public void startWifiScan() {
        if (!hasRequiredScanPermissions()) {
            layoutPermissionBanner.setVisibility(View.VISIBLE);
            Toast.makeText(getContext(), "Location/Nearby permission required to scan Wi-Fi", Toast.LENGTH_SHORT).show();
            return;
        }

        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            Toast.makeText(getContext(), "Please enable Wi-Fi on device first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Location services check
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (lm != null && !lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && !lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(getContext(), "Location services (GPS) must be enabled to discover Wi-Fi SSIDs", Toast.LENGTH_LONG).show();
        }

        scanProgressBar.setVisibility(View.VISIBLE);
        tvScanStatus.setText("Scanning nearby Wi-Fi networks...");

        boolean scanSuccess = wifiManager.startScan();
        if (!scanSuccess) {
            scanProgressBar.setVisibility(View.GONE);
            tvScanStatus.setText("Scan throttled by Android. Showing last scan results.");
            displayScanResults();
        }
    }

    private void displayScanResults() {
        if (wifiManager == null || getContext() == null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        List<ScanResult> results = wifiManager.getScanResults();
        List<WifiNetworkItem> itemList = new ArrayList<>();

        if (results != null) {
            for (ScanResult result : results) {
                if (result.SSID != null && !result.SSID.isEmpty()) {
                    String security = parseSecurityType(result.capabilities);
                    int level;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        level = wifiManager.calculateSignalLevel(result.level);
                    } else {
                        level = WifiManager.calculateSignalLevel(result.level, 5);
                    }
                    itemList.add(new WifiNetworkItem(
                            result.SSID,
                            result.BSSID,
                            result.level,
                            level,
                            security,
                            result.capabilities
                    ));
                }
            }
        }

        adapter.setItems(itemList);
        tvEmptyState.setVisibility(itemList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String parseSecurityType(String capabilities) {
        if (capabilities == null) return "Open";
        if (capabilities.contains("WPA3") || capabilities.contains("SAE")) return "WPA3";
        if (capabilities.contains("WPA2") || capabilities.contains("PSK")) return "WPA2";
        if (capabilities.contains("WEP")) return "WEP";
        return "Open";
    }

    @Override
    public void onNetworkClick(WifiNetworkItem item) {
        showConnectDialog(item);
    }

    private void showConnectDialog(WifiNetworkItem item) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_connect_wifi, null);
        TextView tvDialogSsid = dialogView.findViewById(R.id.tvDialogSsid);
        TextView tvDialogSecurity = dialogView.findViewById(R.id.tvDialogSecurity);
        TextInputLayout tilPassword = dialogView.findViewById(R.id.tilPassword);
        EditText etPassword = dialogView.findViewById(R.id.etPassword);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelDialog);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmConnect);

        tvDialogSsid.setText(item.getSsid());
        tvDialogSecurity.setText("Security: " + item.getSecurityType());

        boolean isOpen = "Open".equalsIgnoreCase(item.getSecurityType());
        tilPassword.setVisibility(isOpen ? View.GONE : View.VISIBLE);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
            dialog.dismiss();
            connectWithSpecifier(item.getSsid(), password, isOpen);
        });

        dialog.show();
    }

    /**
     * Connects to a specific network using WifiNetworkSpecifier (Android 10+).
     */
    private void connectWithSpecifier(String ssid, String password, boolean isOpen) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(getContext(), "WifiNetworkSpecifier requires Android 10 (API 29)+", Toast.LENGTH_LONG).show();
            return;
        }

        if (connectivityManager == null) return;

        tvScanStatus.setText("Requesting specifier connection to " + ssid + "...");
        scanProgressBar.setVisibility(View.VISIBLE);

        // Disconnect previous specifier callback if exists
        disconnectSpecifierNetwork();

        WifiNetworkSpecifier.Builder builder = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid);

        if (!isOpen && !password.isEmpty()) {
            builder.setWpa2Passphrase(password);
        }

        WifiNetworkSpecifier specifier = builder.build();

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();

        specifierNetworkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                boundSpecifierNetwork = network;
                connectedSpecifierSsid = ssid;
                mainHandler.post(() -> {
                    scanProgressBar.setVisibility(View.GONE);
                    tvScanStatus.setText("Connected via Specifier to " + ssid);
                    updateSpecifierUi();
                    Toast.makeText(getContext(), "Specifier Network Connected! Socket traffic will route through this network.", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onUnavailable() {
                boundSpecifierNetwork = null;
                connectedSpecifierSsid = null;
                mainHandler.post(() -> {
                    scanProgressBar.setVisibility(View.GONE);
                    tvScanStatus.setText("Specifier connection failed or cancelled by user");
                    updateSpecifierUi();
                    Toast.makeText(getContext(), "Specifier connection unavailable", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                boundSpecifierNetwork = null;
                connectedSpecifierSsid = null;
                mainHandler.post(() -> {
                    tvScanStatus.setText("Specifier network lost");
                    updateSpecifierUi();
                });
            }
        };

        try {
            connectivityManager.requestNetwork(request, specifierNetworkCallback);
        } catch (Exception e) {
            scanProgressBar.setVisibility(View.GONE);
            tvScanStatus.setText("Specifier request error: " + e.getMessage());
        }
    }

    private void disconnectSpecifierNetwork() {
        if (connectivityManager != null && specifierNetworkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(specifierNetworkCallback);
            } catch (Exception ignored) {
            }
            specifierNetworkCallback = null;
        }
        boundSpecifierNetwork = null;
        connectedSpecifierSsid = null;
        updateSpecifierUi();
    }

    private void updateSpecifierUi() {
        if (layoutSpecifierStatus == null) return;
        if (boundSpecifierNetwork != null && connectedSpecifierSsid != null) {
            layoutSpecifierStatus.setVisibility(View.VISIBLE);
            tvSpecifierInfo.setText("Active Specifier Network: " + connectedSpecifierSsid);
        } else {
            layoutSpecifierStatus.setVisibility(View.GONE);
        }
    }
}