package com.example.tp11;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying TCP socket chat messages in a RecyclerView.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;
    private static final int TYPE_SYSTEM = 3;

    private final List<ChatMessage> messages = new ArrayList<>();

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage msg = messages.get(position);
        if (msg.getSenderType() == ChatMessage.SenderType.SENT) {
            return TYPE_SENT;
        } else if (msg.getSenderType() == ChatMessage.SenderType.RECEIVED) {
            return TYPE_RECEIVED;
        } else {
            return TYPE_SYSTEM;
        }
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        int type = getItemViewType(position);
        holder.bind(message, type);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final View layoutSent;
        private final View layoutReceived;
        private final TextView tvSentMessage;
        private final TextView tvSentTime;
        private final TextView tvReceivedMessage;
        private final TextView tvReceivedTime;
        private final TextView tvSystemMessage;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutSent = itemView.findViewById(R.id.layoutSent);
            layoutReceived = itemView.findViewById(R.id.layoutReceived);
            tvSentMessage = itemView.findViewById(R.id.tvSentMessage);
            tvSentTime = itemView.findViewById(R.id.tvSentTime);
            tvReceivedMessage = itemView.findViewById(R.id.tvReceivedMessage);
            tvReceivedTime = itemView.findViewById(R.id.tvReceivedTime);
            tvSystemMessage = itemView.findViewById(R.id.tvSystemMessage);
        }

        public void bind(ChatMessage msg, int viewType) {
            if (viewType == TYPE_SENT) {
                layoutSent.setVisibility(View.VISIBLE);
                layoutReceived.setVisibility(View.GONE);
                tvSystemMessage.setVisibility(View.GONE);
                tvSentMessage.setText(msg.getMessage());
                tvSentTime.setText(msg.getTimestamp());
            } else if (viewType == TYPE_RECEIVED) {
                layoutSent.setVisibility(View.GONE);
                layoutReceived.setVisibility(View.VISIBLE);
                tvSystemMessage.setVisibility(View.GONE);
                tvReceivedMessage.setText(msg.getMessage());
                tvReceivedTime.setText(msg.getTimestamp());
            } else { // TYPE_SYSTEM
                layoutSent.setVisibility(View.GONE);
                layoutReceived.setVisibility(View.GONE);
                tvSystemMessage.setVisibility(View.VISIBLE);
                tvSystemMessage.setText(msg.getMessage());
            }
        }
    }
}