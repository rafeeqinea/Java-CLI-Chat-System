package coursework;

/**
 * A simple wrapper class for messages exchanged in the system.
 */
public class Message {
    private String content;

    public Message(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    @Override
    public String toString() {
        return content != null ? content : "";
    }
}