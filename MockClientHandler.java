package coursework;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock implementation of ClientHandler for testing purposes.
 * Captures sent messages and allows setting state.
 * Extends the real ClientHandler to allow testing server interactions.
 */
public class MockClientHandler extends ClientHandler {
    private final List<String> sentMessages = new ArrayList<>();
    private volatile boolean running = true; // Simulate running state, overrides superclass field conceptually
    private volatile long lastActivityTime; // Overrides superclass field conceptually
    private final String clientId; // Store client ID locally for the mock
    private final ChatServer mockServerInstance; // Store reference to the server instance

    // Constructor
    public MockClientHandler(String clientId, ChatServer server) {
        super(new Socket(), server); // Pass dummy socket and the server instance up
        this.clientId = clientId;
        this.mockServerInstance = server; // Store the server instance locally
        this.lastActivityTime = System.currentTimeMillis();
    }

    /** Override sendMessage to capture messages instead of sending over network. */
    @Override
    public void sendMessage(String message) {
        // Uses the mock's running state
        if (this.running) {
            sentMessages.add(message);
            System.out.println("MockClientHandler [" + getClientId() + "] CAPTURED SEND >>> " + message);
        } else {
             System.out.println("MockClientHandler [" + getClientId() + "] Tried to send when not running: " + message);
         }
    }

    /** Override closeClientSocket to simulate closure, update running state, AND trigger server removal. */
    @Override
    public synchronized void closeClientSocket(String reason) {
        if (!this.running) return;
        this.running = false; // Update mock's running state

        System.out.println("MockClientHandler [" + getClientId() + "] Marked as closed. Reason: " + reason);

        // Reinstate the call to remove the client from the server's state,
        // as some tests (like timeout) expect this side effect.
        ChatServer serverInstance = this.mockServerInstance;
        if (serverInstance != null && getClientId() != null) {
            serverInstance.removeClientFromServer(this, reason);
        }
    }


    /** Override getClientId to return the mock's ID. */
    @Override
    public String getClientId() {
        // Return the ID stored in this mock instance
        return this.clientId;
    }

    // --- Methods specific to the mock for testing ---

    /** Get all messages captured by this mock handler. */
    public List<String> getAllMessagesSent() {
        return new ArrayList<>(sentMessages); // Return a copy
    }

    /** Get the last message captured. Returns null if none. */
    public String getLastMessageSent() {
        if (sentMessages.isEmpty()) {
            return null;
        }
        return sentMessages.get(sentMessages.size() - 1);
    }

    /** Check if the mock handler considers itself running. */
    @Override
    public boolean isRunning() {
        // Return the mock's running state
        return this.running;
    }

    /** Allows tests to manually set the last activity time for the mock. */
    public void setLastActivityTime(long time) {
        this.lastActivityTime = time;
    }

    /** Override getLastActivityTime to return the mock's value. */
    @Override
    public long getLastActivityTime() {
        // Return the mock's activity time
        return this.lastActivityTime;
    }

    // Optional: Override other methods if their behavior needs to be mocked or checked
     @Override
     public String getClientIP() { return "127.0.0.1"; } // Mock IP
     @Override
     public int getClientPort() { return 12345; } // Mock Port

     // Ensure equals/hashCode uses the mock's clientId consistently with getClientId override
     @Override
     public boolean equals(Object o) {
         if (this == o) return true;
         // Use instanceof ClientHandler for comparison symmetry with superclass instances
         if (o == null || !(o instanceof ClientHandler)) return false;
         ClientHandler that = (ClientHandler) o;
         // Compare based on the result of getClientId(), which is overridden
         String thisId = this.getClientId();
         String thatId = that.getClientId();
         return (thisId == null) ? (thatId == null) : thisId.equals(thatId);
     }

     @Override
     public int hashCode() {
         // Consistent with equals override
         String thisId = this.getClientId();
         return (thisId != null) ? thisId.hashCode() : 0;
     }
}