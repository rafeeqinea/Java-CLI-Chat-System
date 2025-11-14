# Java CLI Chat System

This project is a distributed, command-line based chat application built in Java. It features a robust, multi-threaded central server that manages multiple clients, handles message broadcasting, private messaging, and ensures system resilience through a coordinator election mechanism.

## Features

- **Multi-Client Architecture**: Supports multiple clients connecting concurrently to a central server.
- **Public and Private Messaging**: Users can broadcast messages to all connected clients or send private messages to specific users.
- **User Presence**: Real-time notifications when users join or leave the chat.
- **List Connected Users**: The `/who` command lists all currently connected users.
- **Coordinator Election**: A simple fault-tolerance mechanism. If the designated "coordinator" client disconnects, the server automatically assigns a new coordinator based on lexicographical order of usernames.
- **Client Inactivity Timeout**: The server periodically checks for inactive clients and disconnects them to free up resources.
- **Simple Command-Line Interface**: Easy-to-use commands for interacting with the chat system.

## Architecture

The system is designed around a classic client-server model:

- **`ChatServer`**: A singleton server that listens for incoming client connections. It uses a thread pool to manage a dedicated `ClientHandler` for each connected client. The server is responsible for routing messages, managing the list of active clients, and overseeing the coordinator status.

- **`ChatClient`**: A command-line client that connects to the server. It has a main thread for user input and a separate listener thread for receiving messages from the server, ensuring a non-blocking user experience.

- **`ClientHandler`**: A runnable class on the server side that manages all communication for a single client. It handles the initial user registration, processes incoming messages, and cleans up resources upon disconnection.

- **`FaultToleranceManager`**: A utility class that implements the coordinator election strategy. When the current coordinator leaves, it selects the client with the lexicographically smallest ID as the new coordinator.

- **Protocol**: Communication is handled via a simple, text-based protocol defined in `ProtocolConstants.java`. Messages are prefixed with keywords like `MESSAGE`, `PRIVATE`, `SYSTEM`, etc., to allow the client and server to interpret them correctly.

## How to Run

### Prerequisites
- Java Development Kit (JDK) 8 or higher.

### 1. Compile the Source Code

Navigate to the root directory of the project and compile all Java files:

```sh
javac coursework/*.java
```

### 2. Start the Server

Run the `ChatServer`. You can optionally specify a port number. If not provided, it defaults to `59001`.

```sh
java coursework.ChatServer [port]
```

Example:
```sh
java coursework.ChatServer 59001
```

### 3. Start a Client

Open a new terminal window to run the `ChatClient`. You must provide a username, the server address, and the port.

```sh
java coursework.ChatClient <username> <server_address> <port>
```

Example:
```sh
java coursework.ChatClient Alice 127.0.0.1 59001
```

You can open multiple terminal windows and run the client with different usernames to simulate a multi-user chat.

## Client Commands

Once connected, you can use the following commands:

- **Send a public message**: Just type your message and press Enter.
- `/msg <username> <message>`: Send a private message to a specific user.
- `/who`: List all users currently connected to the chat.
- `/ping`: Send a keep-alive signal to the server (resets inactivity timer).
- `/help`: Display the list of available commands.
- `/quit`: Disconnect from the server.

## Testing

The project includes a suite of JUnit tests to verify the server's functionality, including client registration, coordinator election, private messaging, and inactivity checks.

The `MockClientHandler` class is used to simulate client behavior and capture server responses without requiring actual network connections, allowing for reliable and fast unit testing.
