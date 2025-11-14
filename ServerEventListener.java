package coursework;

/**
 * Observer interface for server events.
 * Allows components (like ClientHandlers) to react to state changes.
 */
public interface ServerEventListener {
    void onClientJoined(String clientId);
    void onClientLeft(String clientId, String reason);
    void onCoordinatorChanged(String newCoordinatorId);
    void onBroadcastMessage(String senderId, String message);
    void onPrivateMessage(String senderId, String recipientId, String message);
    void onSystemMessage(String message); // Generic system messages if needed
}