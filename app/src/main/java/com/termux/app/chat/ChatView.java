package com.termux.app.chat;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.termux.R;

import java.util.List;

/**
 * Custom compound view that provides a chat-style input UI.
 * Messages typed here are sent directly to the current terminal session.
 */
public class ChatView extends RelativeLayout {

    /** Callback interface for chat actions. */
    public interface ChatCallback {
        void onSendMessage(String message);
    }

    private RecyclerView messageList;
    private ChatAdapter chatAdapter;
    private LinearLayoutManager layoutManager;
    private EditText inputEditText;
    private ImageButton sendButton;
    private TextView statusText;

    private ChatCallback chatCallback;

    public ChatView(Context context) {
        super(context);
        init(context);
    }

    public ChatView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ChatView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_chat_container, this, true);

        messageList = findViewById(R.id.chat_message_list);
        inputEditText = findViewById(R.id.chat_input_edittext);
        sendButton = findViewById(R.id.chat_send_button);
        statusText = findViewById(R.id.chat_status_text);

        // Setup RecyclerView
        chatAdapter = new ChatAdapter();
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        messageList.setLayoutManager(layoutManager);
        messageList.setAdapter(chatAdapter);

        // Send button click
        sendButton.setOnClickListener(v -> {
            String text = inputEditText.getText().toString().trim();
            if (!text.isEmpty() && chatCallback != null) {
                chatCallback.onSendMessage(text);
                clearInput();
            }
        });
    }

    // --- Public methods ---

    /** Set the callback for chat events. */
    public void setChatCallback(ChatCallback callback) {
        this.chatCallback = callback;
    }

    /** Add a message and auto-scroll to bottom. */
    public void addMessage(ChatMessage message) {
        chatAdapter.addMessage(message);
        messageList.scrollToPosition(chatAdapter.getItemCount() - 1);
    }

    /** Set the full message list. */
    public void setMessages(List<ChatMessage> messages) {
        chatAdapter.setMessages(messages);
        if (chatAdapter.getItemCount() > 0) {
            messageList.scrollToPosition(chatAdapter.getItemCount() - 1);
        }
    }

    /** Update the status text at the top. */
    public void setStatusText(String text) {
        statusText.setText(text);
    }

    /** Clear the input field. */
    public void clearInput() {
        inputEditText.setText("");
    }
}
