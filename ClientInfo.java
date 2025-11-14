package coursework;

/**
 * Represents metadata about a connected client.
 */
public class ClientInfo {
    private final String id;
    private final String ipAddress;
    private final int port;
    private final ClientHandler handler; // Reference to the handler managing this client

    public ClientInfo(String id, String ipAddress, int port, ClientHandler handler) {
        this.id = id;
        this.ipAddress = ipAddress;
        this.port = port;
        this.handler = handler;
    }

    public String getId() { return id; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public ClientHandler getHandler() { return handler; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientInfo that = (ClientInfo) o;
        // Assuming ID is unique and the primary identifier
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Assuming ID is unique
        return id.hashCode();
    }

    @Override
    public String toString() {
        // Useful for debugging
        return "ClientInfo{" + "id='" + id + '\'' + ", ipAddress='" + ipAddress + '\'' + ", port=" + port + '}';
    }
}