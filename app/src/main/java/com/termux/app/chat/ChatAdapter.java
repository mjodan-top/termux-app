package com.termux.app.chat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying chat messages with three view types:
 * sent, received, and system messages.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    // --- ViewHolder classes ---

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        final TextView messageText;
        final TextView timeText;

        SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.chat_sent_message_text);
            timeText = itemView.findViewById(R.id.chat_sent_time_text);
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        final TextView senderText;
        final TextView messageText;
        final TextView timeText;

        ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            senderText = itemView.findViewById(R.id.chat_received_sender_text);
            messageText = itemView.findViewById(R.id.chat_received_message_text);
            timeText = itemView.findViewById(R.id.chat_received_time_text);
        }
    }

    static class SystemMessageViewHolder extends RecyclerView.ViewHolder {
        final TextView systemMessageText;

        SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            systemMessageText = itemView.findViewById(R.id.chat_system_message_text);
        }
    }

    // --- Adapter overrides ---

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case ChatMessage.TYPE_SENT:
                return new SentMessageViewHolder(
                    inflater.inflate(R.layout.item_chat_message_sent, parent, false));
            case ChatMessage.TYPE_RECEIVED:
                return new ReceivedMessageViewHolder(
                    inflater.inflate(R.layout.item_chat_message_received, parent, false));
            case ChatMessage.TYPE_SYSTEM:
            default:
                return new SystemMessageViewHolder(
                    inflater.inflate(R.layout.item_chat_message_system, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        String formattedTime = timeFormat.format(new Date(message.getTimestamp()));

        switch (message.getType()) {
            case ChatMessage.TYPE_SENT: {
                SentMessageViewHolder sentHolder = (SentMessageViewHolder) holder;
                sentHolder.messageText.setText(message.getContent());
                sentHolder.timeText.setText(formattedTime);
                setupLongClickCopy(sentHolder.itemView, message.getContent());
                break;
            }
            case ChatMessage.TYPE_RECEIVED: {
                ReceivedMessageViewHolder receivedHolder = (ReceivedMessageViewHolder) holder;
                receivedHolder.messageText.setText(message.getContent());
                receivedHolder.timeText.setText(formattedTime);
                String remoteHost = message.getRemoteHost();
                if (remoteHost != null && !remoteHost.isEmpty()) {
                    receivedHolder.senderText.setText(remoteHost);
                    receivedHolder.senderText.setVisibility(View.VISIBLE);
                } else {
                    receivedHolder.senderText.setVisibility(View.GONE);
                }
                setupLongClickCopy(receivedHolder.itemView, message.getContent());
                break;
            }
            case ChatMessage.TYPE_SYSTEM: {
                SystemMessageViewHolder systemHolder = (SystemMessageViewHolder) holder;
                systemHolder.systemMessageText.setText(message.getContent());
                setupLongClickCopy(systemHolder.itemView, message.getContent());
                break;
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    // --- Public methods ---

    /** Add a single message and notify insertion. */
    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    /** Replace all messages and notify full data change. */
    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    /** Return the current list of messages. */
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    // --- Private helpers ---

    private void setupLongClickCopy(View view, String text) {
        view.setOnLongClickListener(v -> {
            Context context = v.getContext();
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("chat message", text));
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }
}
