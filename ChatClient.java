package coursework;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Command-Line Interface (CLI) chat client for the distributed chat system.
 * Connects to the chat server, registers with a username, and facilitates communication.
 * Handles coordinator designation and protocol messages from the server.
 */
public class ChatClient {
    // Protocol constants
    private static final String PROTOCOL_SUBMIT_NAME = "SUBMITNAME";
    private static final String PROTOCOL_NAME_ACCEPTED = "NAMEACCEPTED";
    private static final String PROTOCOL_NAME_IN_USE = "NAMEINUSE";
    private static final String PROTOCOL_COORDINATOR_INFO = "COORDINATOR_INFO";
    private static final String PROTOCOL_SYSTEM = "SYSTEM";
    private static final String PROTOCOL_PRIVATE = "PRIVATE";
    private static final String PROTOCOL_CLIENT_LIST_START = "CLIENTLIST_START";
    private static final String PROTOCOL_CLIENT_LIST_END = "CLIENTLIST_END";

    // Client commands - clearly defined constants for better consistency
    private static final String CMD_QUIT = "/quit";
    private static final String CMD_WHO = "/who";
    private static final String CMD_MSG = "/msg";
    private static final String CMD_HELP = "/help";
    private static final String CMD_PING = "/ping";

    // Client state
    private final String preferredClientId;
    private final String serverAddress;
    private final int port;
    private Socket socket;
    private PrintWriter socketOut;
    private Scanner socketIn;
    private volatile boolean running = false;
    private boolean isCoordinator = false;
    private String coordinatorId = null;

    /**
     * Constructor for ChatClient.
     * @param preferredClientId The desired username for the client.
     * @param serverAddress The server's address.
     * @param port The server's port.
     */
    public ChatClient(String preferredClientId, String serverAddress, int port) {
        this.preferredClientId = preferredClientId;
        this.serverAddress = serverAddress;
        this.port = port;
    }

    /**
     * Starts the chat client, connects to the server, and handles communication.
     */
    public void start() {
        running = true;
        try {
            System.out.println("Connecting to server at " + serverAddress + ":" + port + "...");

            // Establish connection and setup streams
            socket = new Socket(serverAddress, port);
            socketOut = new PrintWriter(socket.getOutputStream(), true);
            socketIn = new Scanner(socket.getInputStream());

            System.out.println("Connection established. Waiting for server response...");

            // Thread to listen for messages from the server
            Thread receiverThread = new Thread(() -> {
                try {
                    while (running && socketIn.hasNextLine()) {
                        String message = socketIn.nextLine();
                        processServerMessage(message);
                    }
                } catch (Exception e) {
                    if (running) System.err.println("\nError in receiver: " + e.getMessage());
                }
            });
            receiverThread.setDaemon(true);
            receiverThread.start();

            // Display command information prominently at startup
            displayCommands();

            // Main input loop: read user input from console and send to server
            Scanner userInput = new Scanner(System.in);
            System.out.print("> ");
            while (running && userInput.hasNextLine()) {
                String input = userInput.nextLine();

                if (input.equals(CMD_HELP)) {
                    displayCommands();
                } else if (input.equalsIgnoreCase(CMD_QUIT)) {
                    socketOut.println(input);
                    System.out.println("Disconnecting from server...");
                    running = false;
                } else {
                    socketOut.println(input);
                }

                if (running) System.out.print("> ");
            }

        } catch (ConnectException e) {
            System.err.println("Error: Cannot connect to server at " + serverAddress + ":" + port);
            System.err.println("Make sure the server is running and the address/port are correct.");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            // Ensure resources are closed properly
            cleanup();
        }
    }

    /**
     * Processes incoming messages from the server.
     * @param message The message received from the server.
     */
    private void processServerMessage(String message) {
        // Handle protocol messages
        if (message.equals(PROTOCOL_SUBMIT_NAME)) {
            socketOut.println(preferredClientId);
            return;
        }

        // Print a newline before the message for better readability
        System.out.println();

        if (message.startsWith(PROTOCOL_NAME_ACCEPTED)) {
            System.out.println("Connected as: " + preferredClientId);
            // Display commands again after successful connection
            displayCommands();
        } else if (message.startsWith(PROTOCOL_NAME_IN_USE)) {
            System.out.println("Username already in use. Please try a different name.");
            running = false;
        } else if (message.startsWith(PROTOCOL_COORDINATOR_INFO)) {
            coordinatorId = message.substring(PROTOCOL_COORDINATOR_INFO.length()).trim();
            System.out.println(message);
            if (coordinatorId.equals(preferredClientId)) {
                isCoordinator = true;
                System.out.println("You are now the coordinator.");
            } else {
                isCoordinator = false;
                System.out.println("Current coordinator is: " + coordinatorId);
            }
        } else if (message.startsWith(PROTOCOL_SYSTEM) && message.contains("You are the first client and the coordinator")) {
            isCoordinator = true;
            System.out.println(message);
        } else if (message.equals(PROTOCOL_CLIENT_LIST_START)) {
            System.out.println("Connected Users:");
            System.out.println("---------------");
        } else if (message.equals(PROTOCOL_CLIENT_LIST_END)) {
            System.out.println("---------------");
        } else {
            // Regular message or other protocol message
            System.out.println(message);
        }

        // Reprint prompt after receiving message
        if (running) System.out.print("> ");
    }

    /**
     * Displays all available commands in a simple format.
     */
    private void displayCommands() {
        System.out.println("\n--- AVAILABLE COMMANDS ---");
        System.out.println("/msg <username> <message> - Send a private message");
        System.out.println("/who                     - List all connected users");
        System.out.println("/ping                    - Send ping to server");
        System.out.println("/help                    - Show these commands again");
        System.out.println("/quit                    - Disconnect from server");
        System.out.println("To send a message to everyone, just type and press Enter\n");
    }

    /**
     * Cleans up resources when the client is shutting down.
     */
    private void cleanup() {
        running = false;
        try {
            if (socket != null) socket.close();
            if (socketIn != null) socketIn.close();
            if (socketOut != null) socketOut.close();
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
        System.out.println("Disconnected from server.");
    }

    /**
     * Main method to launch the chat client.
     * @param args Command line arguments: <username> <server_address> <port>
     */
    public static void main(String[] args) {
        // Default values
        String username = "User" + (int)(Math.random() * 1000);
        String server = "127.0.0.1";
        int port = 59001;

        // Parse command-line arguments if provided
        if (args.length >= 1) username = args[0];
        if (args.length >= 2) server = args[1];
        if (args.length >= 3) {
            try {
                port = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + port);
            }
        }

        if (args.length < 3) {
            System.out.println("Usage: java coursework.ChatClient <username> <server_address> <port>");
            System.out.println("Using defaults: " + username + "@" + server + ":" + port);
        }

        // Create and start the client
        ChatClient client = new ChatClient(username, server, port);
        client.start();
    }
}