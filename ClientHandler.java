package coursework;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles communication for a single client connected to the ChatServer.
 * Each instance runs on its own thread.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private String clientId; // Unique ID assigned after registration
    private PrintWriter out; // Sends messages to the client
    private BufferedReader in; // Reads messages from the client
    private volatile long lastActivityTime; // Timestamp of the last received message
    private volatile boolean running = true; // Controls the main loop
    private final DateTimeFormatter timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    // Regex to parse private messages: /msg <recipient> <message text>
    private static final Pattern privateMsgPattern = Pattern.compile(
            "^" + ProtocolConstants.CMD_MSG + "\\s+(\\S+)\\s+(.*)", Pattern.CASE_INSENSITIVE);

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
        this.lastActivityTime = System.currentTimeMillis(); // Initial activity time
    }

    @Override
    public void run() {
        String remoteDesc = getClientDescription(); // For logging before ID is known
        try {
            // Setup streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true); // Auto-flush enabled

            // 1. Register the client's unique name
            if (!registerClientName()) {
                // Registration failed or client disconnected
                return;
            }
            remoteDesc = getClientDescription(); // Update description now that ID is known

            // 2. Add client to the server's active list and notify others
            server.addClient(this);

            // 3. Process incoming messages until client quits or connection lost
            processMessages();

        } catch (SocketException se) {
            if (running) server.log("SocketException for " + remoteDesc + ": " + se.getMessage());
        } catch (IOException e) {
            if (running) server.log("IOException for " + remoteDesc + ": " + e.getMessage());
        } catch (Exception e) {
            // Catch unexpected errors
            server.log("ERROR in ClientHandler for " + remoteDesc + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
             e.printStackTrace(); // For debugging
        } finally {
            // Cleanup regardless of how the handler exits
            boolean wasRunning = running; // Check if it was an expected shutdown
            running = false;
            String finalClientId = clientId != null ? clientId : "(unregistered)";
            server.log("Client handler cleanup for " + finalClientId);
            // Only remove if registration was successful
            if (clientId != null) {
                server.removeClientFromServer(this, wasRunning ? "disconnected" : "shutdown");
            }
            closeResources(); // Close streams and socket
            server.log("Client handler finished for " + finalClientId);
        }
    }

    /**
     * Handles the initial username registration process.
     * @return true if registration is successful, false otherwise.
     * @throws IOException If an I/O error occurs reading from the client.
     */
    private boolean registerClientName() throws IOException {
        while (running) {
            sendMessage(ProtocolConstants.SUBMIT_NAME); // Ask client for name
            String name = in.readLine();
            if (name == null) { // Client disconnected before sending name
                running = false;
                server.log("Client disconnected before registration.");
                return false;
            }
            lastActivityTime = System.currentTimeMillis(); // Update activity time
            name = name.trim();

            // Validate name
            if (name.isEmpty() || name.contains(" ") || name.startsWith("/") || name.length() > 20 || name.equalsIgnoreCase("System")) {
                sendMessage(ProtocolConstants.ERROR + " Invalid username. Must be <20 chars, no spaces, not start with '/', and not 'System'.");
                continue; // Ask again
            }

            // Check uniqueness with the server
            if (server.isNameInUse(name)) {
                sendMessage(ProtocolConstants.NAME_IN_USE); // Name already taken
            } else {
                // Name is valid and unique
                this.clientId = name;
                sendMessage(ProtocolConstants.NAME_ACCEPTED + " " + this.clientId); // Confirm acceptance
                server.log("Client registered successfully as: " + this.clientId);
                return true; // Registration successful
            }
        }
        return false; // Loop exited without successful registration (e.g., server shutting down)
    }

    /**
     * Main loop for processing messages from the connected client.
     * @throws IOException If an I/O error occurs reading from the client.
     */
    private void processMessages() throws IOException {
        String inputLine;
        while (running && (inputLine = in.readLine()) != null) {
            lastActivityTime = System.currentTimeMillis(); // Update activity on message receipt
            inputLine = inputLine.trim();

            // Log received message with timestamp
            String timestampedMessage = LocalDateTime.now().format(timestampFormatter) + " | Received from " + clientId + ": " + inputLine;
            server.logMessage(timestampedMessage); // Log to server's central log

            if (inputLine.isEmpty()) continue; // Ignore empty lines

            // Handle commands
            if (inputLine.equalsIgnoreCase(ProtocolConstants.CMD_QUIT)) {
                server.log("Client " + clientId + " requested quit.");
                running = false; // Signal to exit loop
                break; // Exit loop immediately
            } else if (inputLine.equalsIgnoreCase(ProtocolConstants.CMD_WHO)) {
                server.sendClientList(this);
            } else if (inputLine.equalsIgnoreCase(ProtocolConstants.CMD_PING)) {
                // Acknowledge ping implicitly by resetting timeout; no response needed.
                 server.log("Ping received from " + clientId);
            } else if (inputLine.toLowerCase().startsWith(ProtocolConstants.CMD_MSG)) {
                handlePrivateMessageCommand(inputLine);
            } else if (inputLine.startsWith("/")) {
                // Unknown command
                sendMessage(MessageFactory.createErrorMessage("Unknown command: " + inputLine).toString());
            } else {
                // Broadcast message
                server.broadcast(MessageFactory.createBroadcastMessage(clientId, inputLine).toString(), this);
            }
        }
        // If loop terminates and still running, it means connection was lost unexpectedly
        if (running) {
            server.log("Client " + clientId + " disconnected unexpectedly (readLine returned null).");
        }
    }

    /**
     * Parses and handles a private message command (/msg).
     * @param commandLine The full command line received from the client.
     */
    private void handlePrivateMessageCommand(String commandLine) {
        Matcher matcher = privateMsgPattern.matcher(commandLine);
        if (matcher.matches()) {
            String recipientId = matcher.group(1);
            String message = matcher.group(2);

            if (recipientId.equalsIgnoreCase(this.clientId)) {
                sendMessage(MessageFactory.createErrorMessage("You cannot send a private message to yourself.").toString());
                return;
            }
            if (message == null || message.trim().isEmpty()) {
                sendMessage(MessageFactory.createErrorMessage("Private message text cannot be empty.").toString());
                return;
            }

            // Ask server to send the private message
            boolean sent = server.sendPrivateMessage(this.clientId, recipientId, message);
            if (!sent) {
                 // Server already sends error if recipient not found, no need to duplicate here
                 server.log("Attempted PM from " + clientId + " to non-existent user " + recipientId);
            }
        } else {
            // Malformed command
            sendMessage(MessageFactory.createErrorMessage("Invalid private message format. Use: " + ProtocolConstants.CMD_MSG + " <recipientId> <message>").toString());
        }
    }

    /**
     * Sends a message string to the connected client.
     * Handles potential errors during sending.
     * @param message The message string to send.
     */
    public void sendMessage(String message) {
        if (running && out != null && !out.checkError()) {
            out.println(message);
             // Optionally log sent messages too, but can be verbose
             // server.log("Sent to " + getClientDescription() + ": " + message);
        } else if (running) {
            // Only log error if we were supposed to be running
            server.log("ERROR: Cannot send message to " + getClientDescription() + " (PrintWriter error or null). Message: " + message);
            // Attempt to close connection if output stream failed
             closeClientSocket("output_stream_error");
        }
    }

    /**
     * Closes the client's connection and associated resources.
     * Called on explicit quit, timeout, server shutdown, or error.
     * @param reason A string indicating why the connection is being closed.
     */
    public synchronized void closeClientSocket(String reason) {
        if (!running) return; // Already closing or closed
        running = false; // Stop the main loop
        server.log("Closing socket for " + getClientDescription() + ". Reason: " + reason);
        closeResources(); // Close streams and socket
    }

    /** Safely closes the PrintWriter, BufferedReader, and Socket. */
    private void closeResources() {
        try { if (out != null) out.close(); } catch (Exception e) { server.log("Error closing PrintWriter for " + getClientDescription() + ": " + e.getMessage());}
        try { if (in != null) in.close(); } catch (IOException e) { server.log("Error closing BufferedReader for " + getClientDescription() + ": " + e.getMessage());}
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException e) { server.log("Error closing socket for " + getClientDescription() + ": " + e.getMessage());}
        out = null;
        in = null;
        // Do not nullify socket immediately, may be needed for description
    }

    // --- Getters ---
    public String getClientId() { return clientId; }
    public String getClientIP() {
        return (socket != null && socket.getInetAddress() != null) ? socket.getInetAddress().getHostAddress() : "?.?.?.?";
    }
    public int getClientPort() { return (socket != null) ? socket.getPort() : -1; }
    public long getLastActivityTime() { return lastActivityTime; }
    public boolean isRunning() { return running; }

    /** Provides a description for logging, using ID if available, otherwise IP/Port. */
    private String getClientDescription() {
        if (clientId != null) {
            return clientId;
        } else if (socket != null && socket.getRemoteSocketAddress() != null) {
            return socket.getRemoteSocketAddress().toString();
        } else {
            return "unknown client";
        }
    }

    // --- Overrides for equals/hashCode based on unique clientId ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientHandler that = (ClientHandler) o;
        // If clientId is set, use it for comparison. Otherwise, rely on object identity.
        return clientId != null ? clientId.equals(that.clientId) : super.equals(o);
    }

    @Override
    public int hashCode() {
        // Use clientId's hashcode if available, otherwise default.
        return clientId != null ? clientId.hashCode() : super.hashCode();
    }
}