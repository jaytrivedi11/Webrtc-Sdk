package co.in.vegg.webrtc_chat_sdk;

public class ChatMessage {
    private String sender;
    private String content;
    private boolean isOwnMessage;

    public ChatMessage(String sender, String content, boolean isOwnMessage) {
        this.sender = sender;
        this.content = content;
        this.isOwnMessage = isOwnMessage;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public boolean isOwnMessage() {
        return isOwnMessage;
    }
}