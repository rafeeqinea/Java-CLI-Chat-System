package coursework;

/**
 * Factory class for creating standardized Message strings for server->client communication.
 * Updated with new protocol constants.
 */
public class MessageFactory {

    // --- Message Creation Methods ---

    public static Message createBroadcastMessage(String senderId, String text) {
        return new Message(ProtocolConstants.MESSAGE + " " + senderId + ": " + text);
    }

    public static Message createPrivateMessage(String senderId, String text) {
        // Format used when RECEIVING a PM
        return new Message(ProtocolConstants.PRIVATE_MESSAGE + " from " + senderId + ": " + text);
    }

     public static Message createClientJoinMessage(String clientId) {
         return new Message(ProtocolConstants.CLIENT_JOIN + " " + clientId);
     }

     public static Message createClientLeaveMessage(String clientId, String reason) {
         return new Message(ProtocolConstants.CLIENT_LEAVE + " " + clientId + " (" + reason + ")");
     }

     public static Message createCoordinatorUpdate(String coordinatorId) {
         // Send "null" string if no coordinator
         return new Message(ProtocolConstants.COORDINATOR_UPDATE + " " + (coordinatorId != null ? coordinatorId : "null"));
     }

    public static Message createSystemMessage(String info) {
        // General system messages
        return new Message(ProtocolConstants.SYSTEM + " " + info);
    }

    public static Message createInfoMessage(String info) {
        // Informational messages (e.g., PM confirmation)
        return new Message(ProtocolConstants.INFO + " " + info);
    }

    public static Message createErrorMessage(String error) {
        return new Message(ProtocolConstants.ERROR + " " + error);
    }

    // --- Simple Message Wrappers (Optional, depending on usage) ---
    // These could return static strings if the protocol messages are fixed

    public static Message getSubmitNameMessage() {
        return new Message(ProtocolConstants.SUBMIT_NAME);
    }

     public static Message getNameAcceptedMessage(String acceptedName) {
         return new Message(ProtocolConstants.NAME_ACCEPTED + " " + acceptedName);
     }

    // Add wrappers for other simple protocol constants if needed...


    // Private constructor to prevent instantiation
    private MessageFactory() {}
}