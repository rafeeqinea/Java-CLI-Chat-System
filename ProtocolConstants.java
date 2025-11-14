package coursework;

/**
 * Defines constants for the chat protocol messages and commands.
 * Enhanced with history command and more specific system messages.
 */
public final class ProtocolConstants {

    // Client -> Server & Server -> Client status/info messages
    public static final String SUBMIT_NAME = "SUBMITNAME";       // Server asks client for username
    public static final String NAME_ACCEPTED = "NAMEACCEPTED";   // Server confirms username is ok
    public static final String NAME_IN_USE = "NAMEINUSE";       // Server informs username is taken
    public static final String MESSAGE = "MESSAGE";             // Prefix for standard broadcast messages received by client
    public static final String PRIVATE_MESSAGE = "PRIVATE";     // Prefix for private messages received by client
    public static final String SYSTEM = "SYSTEM";               // Prefix for general system messages (joins, leaves)
    public static final String COORDINATOR_UPDATE = "COORD_UPDATE"; // Specific message indicating coordinator change
    public static final String COORDINATOR_INFO = "COORDINATOR_INFO"; // Specific message providing coordinator information
    public static final String CLIENT_JOIN = "CLIENT_JOIN";     // Specific message indicating a client joined
    public static final String CLIENT_LEAVE = "CLIENT_LEAVE";   // Specific message indicating a client left
    public static final String PONG = "PONG";                   // Server response to client PING
    public static final String INFO = "INFO";                   // Prefix for informational messages from server to a specific client (e.g., PM sent confirmation)
    public static final String ERROR = "ERROR";                 // Prefix for error messages from server to a specific client
    public static final String HISTORY_START = "HISTORY_START"; // Start of message history transmission
    public static final String HISTORY_ENTRY = "HISTORY_ENTRY"; // A single message history entry
    public static final String HISTORY_END = "HISTORY_END";     // End of message history transmission
    public static final String CLIENT_LIST_START = "CLIENTLIST_START"; // Start of client list transmission
    public static final String CLIENT_LIST_END = "CLIENTLIST_END";     // End of client list transmission

    // Client -> Server commands
    public static final String CMD_QUIT = "/quit";              // Command to disconnect
    public static final String CMD_WHO = "/who";                // Command to request the client list
    public static final String CMD_MSG = "/msg";                // Command prefix for sending a private message
    public static final String CMD_PING = "/ping";              // Command for client to signal activity (now requires PONG response)
    public static final String CMD_HISTORY = "/history";        // Command to request recent message history
    public static final String CMD_HELP = "/help";              // Command to request help information

    // Server configuration
    public static final long CLIENT_TIMEOUT_MS = 60000; // 60 seconds timeout
    public static final long PING_INTERVAL_MS = CLIENT_TIMEOUT_MS / 3; // Client should ping roughly every 20s
    public static final int ACTIVITY_CHECK_INTERVAL_SECONDS = 15; // How often server checks for unresponsive clients (missed PONGs)
    public static final int MAX_CLIENTS = 50;                  // Maximum concurrent clients
    public static final int MESSAGE_HISTORY_LENGTH = 20;        // Number of recent messages to keep

    // Private constructor to prevent instantiation
    private ProtocolConstants() {}
}