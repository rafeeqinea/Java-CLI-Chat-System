package coursework;

import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Singleton server class for the chat application.
 * Manages client connections, message routing, and coordinator election.
 */
public class ChatServer {
    // Singleton instance
    private static volatile ChatServer instance;

    // State variables
    private final Map<String, ClientInfo> activeClients = new ConcurrentHashMap<>(); // Thread-safe map for active clients
    private volatile String coordinatorId = null; // ID of the current coordinator client
    private final List<String> messageLog = Collections.synchronizedList(new ArrayList<>()); // Thread-safe log for server events and messages
    private final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"); // For logging timestamps

    // Concurrency and Networking
    private ServerSocket serverSocket;
    private ExecutorService clientProcessingPool; // Thread pool for handling clients
    private ScheduledExecutorService activityCheckerScheduler; // For periodic tasks like inactivity checks
    private volatile boolean running = false; // Server running state flag

    // Private constructor for Singleton
    private ChatServer() {}

    /**
     * Gets the single instance of the ChatServer.
     * @return The ChatServer instance.
     */
    public static ChatServer getInstance() {
        // Double-checked locking for thread-safe lazy initialization
        if (instance == null) {
            synchronized (ChatServer.class) {
                if (instance == null) {
                    instance = new ChatServer();
                }
            }
        }
        return instance;
    }

    /**
     * Starts the server on the specified port.
     * Initializes thread pools, schedules tasks, and listens for client connections.
     * @param port The port number to listen on.
     * @throws IOException If an I/O error occurs when opening the socket.
     */
    public void start(int port) throws IOException {
        log("SERVER STARTUP initiating on port " + port + "...");
        running = true;

        // Initialize thread pools
        clientProcessingPool = Executors.newFixedThreadPool(ProtocolConstants.MAX_CLIENTS);
        activityCheckerScheduler = Executors.newScheduledThreadPool(1); // Single thread for scheduled tasks

        // Schedule the inactivity check
        activityCheckerScheduler.scheduleAtFixedRate(this::performActivityCheck,
                ProtocolConstants.ACTIVITY_CHECK_INTERVAL_SECONDS, // Initial delay
                ProtocolConstants.ACTIVITY_CHECK_INTERVAL_SECONDS, // Interval
                TimeUnit.SECONDS);
        log("Scheduled client activity checker to run every " + ProtocolConstants.ACTIVITY_CHECK_INTERVAL_SECONDS + " seconds.");

        // Add shutdown hook for graceful termination
        addShutdownHook();

        // Open the server socket
        serverSocket = new ServerSocket(port);
        log("Server listening on port " + port);

        // Main loop to accept client connections
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = serverSocket.accept(); // Blocks until a connection is made
                log("Accepted connection from " + clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort());

                // Create a handler for the new client and submit it to the thread pool
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clientProcessingPool.execute(handler);

            } catch (SocketException se) {
                // Expected when serverSocket.close() is called during shutdown
                if (!running) log("Server socket closed normally during shutdown.");
                else log("WARN: SocketException during accept: " + se.getMessage());
            } catch (IOException e) {
                if (!running) break; // Exit loop if server is stopped
                log("ERROR: IOException during accept: " + e.getMessage());
                // Brief pause to prevent tight loop on persistent error
                try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        // If the loop exits unexpectedly while running, initiate shutdown
        if (running) {
            log("WARN: Main server loop exited unexpectedly.");
            shutdownServer("Unexpected loop termination");
        }
    }

    /**
     * Performs a periodic check for inactive clients and disconnects them.
     */
    void performActivityCheck() {
        if (!running) return; // Don't run if server is stopping
        long now = System.currentTimeMillis();
        log("Performing client activity check...");

        List<String> clientsToDisconnect = new ArrayList<>();
        // Use ConcurrentHashMap's entrySet for potentially better concurrency if needed, though values() is often fine.
        for (ClientInfo info : activeClients.values()) {
             // Check if handler is non-null before getting activity time
             if (info.getHandler() != null && now - info.getHandler().getLastActivityTime() > ProtocolConstants.CLIENT_TIMEOUT_MS) {
                clientsToDisconnect.add(info.getId());
            }
        }

        if (!clientsToDisconnect.isEmpty()) {
            log("Found " + clientsToDisconnect.size() + " inactive client(s): " + String.join(", ", clientsToDisconnect));
            for (String clientId : clientsToDisconnect) {
                ClientInfo info = activeClients.get(clientId); // Re-fetch in case removed meanwhile
                if (info != null && info.getHandler() != null) { // Check handler again
                    log("Disconnecting client " + clientId + " due to inactivity (timeout).");
                    // Ensure handler is running before sending message, though it should be if timeout occurred.
                    if(info.getHandler().isRunning()) {
                        info.getHandler().sendMessage(MessageFactory.createSystemMessage("You have been disconnected due to inactivity.").toString());
                    }
                    info.getHandler().closeClientSocket("timeout");
                }
            }
        } else {
             log("No inactive clients found.");
        }
    }

    /**
     * Gracefully shuts down the server.
     * Closes sockets, stops threads, and notifies clients.
     * @param reason The reason for the shutdown.
     */
    public synchronized void shutdownServer(String reason) {
        if (!running) return; // Already shutting down
        running = false;
        log("SERVER SHUTDOWN initiated. Reason: " + reason);

        // 1. Stop accepting new connections
        try { if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close(); }
        catch (IOException e) { log("WARN: Error closing server socket: " + e.getMessage()); }

        // 2. Stop scheduled tasks
        if (activityCheckerScheduler != null && !activityCheckerScheduler.isShutdown()) {
            activityCheckerScheduler.shutdownNow();
        }


        // 3. Notify and disconnect all active clients
        log("Disconnecting all active clients...");
        List<ClientInfo> clientsToDisconnect = new ArrayList<>(activeClients.values());
        for (ClientInfo client : clientsToDisconnect) {
             if(client.getHandler() != null) { // Check handler exists
                 if(client.getHandler().isRunning()){ // Check handler is running before sending
                     client.getHandler().sendMessage(MessageFactory.createSystemMessage("Server is shutting down. " + reason).toString());
                 }
                 client.getHandler().closeClientSocket("server_shutdown");
             }
        }
         try { Thread.sleep(100); } catch (InterruptedException ignored) {} // Reduce sleep time


        // 4. Shut down the client processing thread pool
        if (clientProcessingPool != null && !clientProcessingPool.isShutdown()) {
            log("Shutting down client handler thread pool...");
            clientProcessingPool.shutdown();
            try {
                if (!clientProcessingPool.awaitTermination(2, TimeUnit.SECONDS)) { // Reduce wait time
                    log("WARN: Client handler pool did not terminate gracefully, forcing shutdown...");
                    clientProcessingPool.shutdownNow();
                    if (!clientProcessingPool.awaitTermination(2, TimeUnit.SECONDS))
                        log("ERROR: Client handler pool did not terminate after force.");
                }
            } catch (InterruptedException ie) {
                clientProcessingPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log("SERVER SHUTDOWN complete.");
        activeClients.clear();
        coordinatorId = null;
    }

    /** Adds a JVM shutdown hook to attempt graceful shutdown on Ctrl+C or OS signal. */
    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log("Shutdown hook triggered.");
            shutdownServer("JVM Shutdown Hook");
        }, "ServerShutdownHook"));
    }

    /**
     * Adds a successfully registered client to the server's state.
     * Assigns coordinator if needed and notifies relevant clients.
     * @param handler The ClientHandler for the newly registered client.
     */
    synchronized void addClient(ClientHandler handler) {
        // Simplified Check: Only prevent adding if server is genuinely stopped, not just reset for tests.
        // Rely on resetServerStateForTest setting running=true before test execution.
        if (!running) {
             log("WARN: Attempted to add client when server is not running: " + handler.getClientId());
             handler.closeClientSocket("server_not_running");
             return;
         }

        String clientId = handler.getClientId();
        if (clientId == null) {
            log("ERROR: Attempted to add client with null ID.");
            handler.closeClientSocket("internal_error_null_id");
            return;
        }
        if (activeClients.containsKey(clientId)) {
             log("ERROR: Attempted to add client with duplicate ID: " + clientId);
             handler.sendMessage(ProtocolConstants.ERROR + " Internal server error: Duplicate ID detected.");
             handler.closeClientSocket("duplicate_id_detected_on_add");
             return;
         }

        ClientInfo clientInfo = new ClientInfo(clientId, handler.getClientIP(), handler.getClientPort(), handler);
        activeClients.put(clientId, clientInfo);
        log("Client added: " + clientId + " from " + handler.getClientIP() + ":" + handler.getClientPort());

        boolean wasFirstClient = false;
        if (coordinatorId == null) {
            coordinatorId = clientId;
            wasFirstClient = true;
            log(clientId + " is the first client and becomes the coordinator.");
             // Check handler before sending - important as MockClientHandler might be set non-running by test logic before addClient finishes?
             if(handler.isRunning()){
                handler.sendMessage(MessageFactory.createSystemMessage("You are the first client and the coordinator.").toString());
             }
        } else {
            // Check handler before sending
             if(handler.isRunning()){
                handler.sendMessage(ProtocolConstants.COORDINATOR_INFO + " " + coordinatorId);
             }
        }

        // Broadcast join message (broadcast method already checks if target handlers are running)
        broadcast(MessageFactory.createSystemMessage(clientId + " joined the chat.").toString(), handler);

         if (wasFirstClient) {
             // Broadcast coordinator info (broadcast method already checks if target handlers are running)
             broadcastCoordinatorInfo();
         }

        log("Current active clients: " + activeClients.size());
    }

    /**
     * Removes a client from the server's state.
     * Handles coordinator reassignment if the departing client was the coordinator.
     * @param handler The handler of the client to remove.
     * @param reason The reason for removal (e.g., "disconnected", "timeout", "quit").
     */
    synchronized void removeClientFromServer(ClientHandler handler, String reason) {
        String clientId = handler.getClientId();
        if (clientId == null) {
            log("WARN: Attempted to remove client with null ID. Reason: " + reason);
            return;
        }

        ClientInfo removedInfo = activeClients.remove(clientId);
        if (removedInfo != null) {
            log("Client removed: " + clientId + ". Reason: " + reason + ". Remaining clients: " + activeClients.size());
            // Broadcast departure (broadcast method checks target handlers)
            broadcast(MessageFactory.createSystemMessage(clientId + " left the chat (" + reason + ").").toString(), null);

            if (clientId.equals(coordinatorId)) {
                log("Coordinator (" + clientId + ") left. Electing new coordinator...");
                Set<String> remainingClientIds = activeClients.keySet();
                String newCoordinatorId = FaultToleranceManager.assignNewCoordinator(remainingClientIds);

                if (newCoordinatorId != null) {
                    coordinatorId = newCoordinatorId;
                    log("New coordinator is: " + coordinatorId);
                    ClientInfo newCoordInfo = activeClients.get(coordinatorId);
                    // Check handler before sending message
                    if (newCoordInfo != null && newCoordInfo.getHandler() != null && newCoordInfo.getHandler().isRunning()) {
                        newCoordInfo.getHandler().sendMessage(MessageFactory.createSystemMessage("You are now the coordinator.").toString());
                    }
                    // Broadcast new coordinator info (broadcast checks target handlers)
                    broadcastCoordinatorInfo();
                } else {
                    coordinatorId = null;
                    log("No clients remaining to elect a new coordinator.");
                }
            }
        } else {
            // This warning is less critical now, as remove might be called after reset clears the map.
             log("INFO: Attempted to remove client " + clientId + " but they were not found in the active list (possibly due to test reset). Reason: " + reason);
        }
    }

    /**
     * Sends a message to all connected clients, optionally excluding one.
     * @param message The message string to broadcast.
     * @param excludeHandler The handler of the client to exclude (typically the sender), or null to send to all.
     */
    void broadcast(String message, ClientHandler excludeHandler) {
        String excludeId = (excludeHandler != null) ? excludeHandler.getClientId() : null;
        log("Broadcasting message (excluding " + (excludeId != null ? excludeId : "nobody") + "): " + message);

        Collection<ClientInfo> currentClients = new ArrayList<>(activeClients.values());
        int sentCount = 0;
        for (ClientInfo clientInfo : currentClients) {
             // Check client still exists in map AND handler exists AND handler is running
             if (activeClients.containsKey(clientInfo.getId())
                 && clientInfo.getHandler() != null
                 && clientInfo.getHandler().isRunning() // Check handler running state
                 && (excludeId == null || !clientInfo.getId().equals(excludeId)))
             {
                 clientInfo.getHandler().sendMessage(message);
                 sentCount++;
            } else if (activeClients.containsKey(clientInfo.getId()) && (excludeId == null || !clientInfo.getId().equals(excludeId))) {
                 // Log if we skipped due to non-running handler
                 log("Skipping broadcast to non-running handler for client: " + clientInfo.getId());
            }
        }
         log("Broadcast sent to " + sentCount + " running client(s).");
    }

    /**
     * Sends a private message from one client to another.
     * Checks if handlers are running before sending.
     * @param senderId The ID of the sending client.
     * @param recipientId The ID of the recipient client.
     * @param messageText The message content.
     * @return true if the message was sent successfully, false if the recipient was not found or not running.
     */
    boolean sendPrivateMessage(String senderId, String recipientId, String messageText) {
        ClientInfo recipientInfo = activeClients.get(recipientId);
        ClientInfo senderInfo = activeClients.get(senderId);

        // Check if recipient exists and its handler is running
        if (recipientInfo != null && recipientInfo.getHandler() != null && recipientInfo.getHandler().isRunning()) {
            String formattedMessage = MessageFactory.createPrivateMessage(senderId, messageText).toString();
            recipientInfo.getHandler().sendMessage(formattedMessage);

            // Check if sender exists and its handler is running before sending confirmation
            if (senderInfo != null && senderInfo.getHandler() != null && senderInfo.getHandler().isRunning()) {
                senderInfo.getHandler().sendMessage(MessageFactory.createInfoMessage("Private message sent to " + recipientId + ".").toString());
            }

            log("Private message from " + senderId + " to " + recipientId + " delivered.");
             logMessage(LocalDateTime.now().format(timestampFormatter) + " | PM From " + senderId + " To " + recipientId + ": " + messageText);
            return true;
        } else {
            // Inform sender if possible (handler running)
            if (senderInfo != null && senderInfo.getHandler() != null && senderInfo.getHandler().isRunning()) {
                 String errorReason = (recipientInfo == null) ? "not found" : "is offline";
                senderInfo.getHandler().sendMessage(MessageFactory.createErrorMessage("User '" + recipientId + "' " + errorReason + ".").toString());
            }
            log("Private message failed: Recipient '" + recipientId + "' not found or handler not running for sender '" + senderId + "'.");
            return false;
        }
    }

    /**
     * Sends the list of currently active clients to the requesting client.
     * Checks if the requesting handler is running.
     * @param requesterHandler The handler of the client who requested the list.
     */
    void sendClientList(ClientHandler requesterHandler) {
        // Check requester handler before proceeding
        if (requesterHandler == null || !requesterHandler.isRunning()) {
             log("WARN: Cannot send client list to non-running handler for client: " + (requesterHandler != null ? requesterHandler.getClientId() : "null"));
             return;
         }
        String requesterId = requesterHandler.getClientId();
        log("Sending client list to " + requesterId);

        requesterHandler.sendMessage(ProtocolConstants.CLIENT_LIST_START);

        List<ClientInfo> sortedClients = activeClients.values().stream()
                .sorted(Comparator.comparing(ClientInfo::getId))
                .collect(Collectors.toList());

        if (sortedClients.isEmpty()) {
            requesterHandler.sendMessage("- No other clients currently connected.");
        } else {
            for (ClientInfo info : sortedClients) {
                String line = "- ID: " + info.getId() + " (Host: " + info.getIpAddress() + ")";
                if (info.getId().equals(coordinatorId)) {
                    line += " [Coordinator]";
                }
                requesterHandler.sendMessage(line);
            }
        }

        requesterHandler.sendMessage(ProtocolConstants.CLIENT_LIST_END);
        log("Client list sent successfully to " + requesterId);
    }

    /** Checks if a given client ID (username) is already in use. */
    synchronized boolean isNameInUse(String id) {
        return activeClients.containsKey(id);
    }

    /** Broadcasts the current coordinator ID to all clients. */
    private void broadcastCoordinatorInfo() {
        if (coordinatorId != null) {
            broadcast(ProtocolConstants.COORDINATOR_INFO + " " + coordinatorId, null);
        } else {
             broadcast(MessageFactory.createSystemMessage("There is currently no coordinator.").toString(), null);
         }
    }

    /**
     * Adds a message to the central server log with a timestamp.
     * Also prints to the console.
     * @param message The message to log.
     */
    synchronized void log(String message) {
        String timestampedMessage = LocalDateTime.now().format(timestampFormatter) + " | [Server] " + message;
        messageLog.add(timestampedMessage);
        System.out.println(timestampedMessage);
    }

     /**
      * Adds a pre-formatted message (potentially from a handler with its own timestamp) to the log.
      * @param timestampedMessage The already timestamped message string.
      */
     synchronized void logMessage(String timestampedMessage) {
         messageLog.add(timestampedMessage);
         System.out.println(timestampedMessage);
     }

    // --- Methods for testing ---
    synchronized List<String> getLogMessagesForTest() { return new ArrayList<>(messageLog); }
    synchronized void clearLogForTest() { messageLog.clear(); }
    Map<String, ClientInfo> getActiveClientsForTest() { return new ConcurrentHashMap<>(activeClients); } // Return a copy
    String getCoordinatorIdForTest() { return coordinatorId; }
    void setCoordinatorIdForTest(String coordId) { this.coordinatorId = coordId; } // Use with caution in tests

    /**
     * Resets the server's core state variables for testing purposes.
     * Avoids full shutdown logic to prevent interference between tests.
     * Ensures server is marked as 'running' for the next test.
     */
    synchronized void resetServerStateForTest() {
        log("Resetting server state for test...");

        // Shutdown executors and close socket if they exist from a previous test
         if (activityCheckerScheduler != null && !activityCheckerScheduler.isShutdown()) {
             activityCheckerScheduler.shutdownNow();
         }
         if (clientProcessingPool != null && !clientProcessingPool.isShutdown()) {
             clientProcessingPool.shutdownNow();
         }
         try {
             if (serverSocket != null && !serverSocket.isClosed()) {
                 serverSocket.close();
             }
         } catch (IOException e) { log("Ignoring IOException during test server socket close: " + e.getMessage()); }

        // Nullify resources
        serverSocket = null;
        clientProcessingPool = null;
        activityCheckerScheduler = null;

        // Directly clear the state maps and variables
        activeClients.clear();
        coordinatorId = null;
        messageLog.clear();

        // Set server to running state, ready for the next test to add clients/etc.
        running = true; // <<< ENSURE THIS LINE IS PRESENT AND `true`

        log("Server state reset for test complete (running=true).");
    }


    // --- Main method to start the server ---
    public static void main(String[] args) {
        int port = 59001; // Default port
        if (args.length > 0) {
            try {
                int parsedPort = Integer.parseInt(args[0]);
                if (parsedPort >= 1024 && parsedPort <= 65535) {
                    port = parsedPort;
                } else {
                    System.err.println("Invalid port number: " + args[0] + ". Using default " + port + ".");
                }
            } catch (NumberFormatException e) {
                System.err.println("Invalid port argument: " + args[0] + ". Must be a number. Using default " + port + ".");
            }
        }

        try {
            ChatServer server = ChatServer.getInstance();
            server.start(port);
        } catch (BindException e) {
             System.err.println("FATAL: Could not bind to port " + port + ". Address already in use?");
             System.err.println("Check if another server instance is running.");
         } catch (IOException e) {
            System.err.println("FATAL: Server failed to start on port " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}