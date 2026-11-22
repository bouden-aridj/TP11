package com.example.tp11;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying scanned Wi-Fi networks in a RecyclerView.
 */
public class WifiAdapter extends RecyclerView.Adapter<WifiAdapter.WifiViewHolder> {

    public interface OnNetworkClickListener {
        void onNetworkClick(WifiNetworkItem item);
    }

    private final List<WifiNetworkItem> items = new ArrayList<>();
    private final OnNetworkClickListener listener;

    public WifiAdapter(OnNetworkClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<WifiNetworkItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WifiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wifi_network, parent, false);
        return new WifiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WifiViewHolder holder, int position) {
        WifiNetworkItem item = items.get(position);
        holder.bind(item, listener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class WifiViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvSsid;
        private final TextView tvBssid;
        private final TextView tvSecurity;
        private final TextView tvRssi;
        private final TextView tvSignalLevel;
        private final MaterialButton btnConnect;

        public WifiViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSsid = itemView.findViewById(R.id.tvSsid);
            tvBssid = itemView.findViewById(R.id.tvBssid);
            tvSecurity = itemView.findViewById(R.id.tvSecurity);
            tvRssi = itemView.findViewById(R.id.tvRssi);
            tvSignalLevel = itemView.findViewById(R.id.tvSignalLevel);
            btnConnect = itemView.findViewById(R.id.btnConnect);
        }

        public void bind(WifiNetworkItem item, OnNetworkClickListener listener) {
            tvSsid.setText(item.getSsid());
            tvBssid.setText(item.getBssid());
            tvSecurity.setText(item.getSecurityType());
            tvRssi.setText(item.getRssi() + " dBm");
            tvSignalLevel.setText(item.getLevel() + "/4");

            View.OnClickListener clickListener = v -> {
                if (listener != null) {
                    listener.onNetworkClick(item);
                }
            };

            itemView.setOnClickListener(clickListener);
            btnConnect.setOnClickListener(clickListener);
        }
    }
}