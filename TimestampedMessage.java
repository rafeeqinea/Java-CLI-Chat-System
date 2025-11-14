package coursework;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a message with a timestamp, sender, type, and content.
 * Used for storing message history.
 */
public class TimestampedMessage {
    public enum MessageType { BROADCAST, PRIVATE, SYSTEM }

    private final LocalDateTime timestamp;
    private final String sender; // Can be "System"
    private final String recipient; // null for broadcast/system, specific user for private
    private final MessageType type;
    private final String content;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public TimestampedMessage(String sender, String recipient, MessageType type, String content) {
        this.timestamp = LocalDateTime.now();
        this.sender = sender;
        this.recipient = recipient;
        this.type = type;
        this.content = content;
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public MessageType getType() { return type; }
    public String getContent() { return content; }

    @Override
    public String toString() {
        String prefix;
        switch (type) {
            case BROADCAST: prefix = "[" + sender + " -> ALL]"; break;
            case PRIVATE: prefix = "[" + sender + " -> " + recipient + "]"; break;
            case SYSTEM: prefix = "[System]"; break;
            default: prefix = "[Unknown]"; break;
        }
        return timestamp.format(formatter) + " " + prefix + " " + content;
    }

     /** Formats the message for sending to a client as part of history. */
     public String formatForHistory() {
         // Example: 2025-03-28 08:15:00 | BROADCAST | Alice: Hello!
         // Example: 2025-03-28 08:16:00 | PRIVATE | Alice -> Bob: Hi there
         // Example: 2025-03-28 08:17:00 | SYSTEM | Charlie joined
         String detail;
         if (type == MessageType.PRIVATE) {
             detail = sender + " -> " + recipient + ": " + content;
         } else {
             detail = sender + ": " + content; // Sender is "System" for system messages
         }
         return timestamp.format(formatter) + " | " + type.name() + " | " + detail;
     }
}