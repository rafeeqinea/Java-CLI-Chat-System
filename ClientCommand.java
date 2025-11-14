package coursework;

/**
 * Represents a command to be executed on the server, triggered by client input.
 * (Command Pattern Interface)
 */
@FunctionalInterface // Optional: Indicates this is intended as a functional interface
public interface ClientCommand {
    /**
     * Executes the command.
     * @param handler The ClientHandler initiating the command.
     * @param argument The arguments provided with the command (if any).
     * @param server The ChatServer instance.
     */
    void execute(ClientHandler handler, String argument, ChatServer server);
}