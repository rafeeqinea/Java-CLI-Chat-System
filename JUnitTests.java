package coursework;

import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class JUnitTests {
    private ChatServer server;

    @Before
    public void setup() {
        server = ChatServer.getInstance();
        server.resetServerStateForTest();
    }

    @After
    public void teardown() {
        server.resetServerStateForTest();
    }

    @Test
    public void testClientRegistrationAndFirstCoordinator() {
        MockClientHandler aliceHandler = new MockClientHandler("Alice", server);
        server.addClient(aliceHandler);
        assertEquals("Alice should be the first coordinator", "Alice", server.getCoordinatorIdForTest());
        List<String> messages = aliceHandler.getAllMessagesSent();
        // Updated expectation based on revised server logic
        assertTrue("Alice should receive SYSTEM coordinator message", messages.stream().anyMatch(m -> m.contains("SYSTEM You are the first client and the coordinator.")));
        assertTrue("Alice should receive COORDINATOR_INFO broadcast", messages.stream().anyMatch(m -> m.contains("COORDINATOR_INFO Alice")));
    }

    @Test
    public void testLoggingWorks() {
        server.clearLogForTest();
        MockClientHandler aliceHandler = new MockClientHandler("Alice", server);
        server.addClient(aliceHandler);
        server.broadcast("Test Broadcast", null);
        List<String> logs = server.getLogMessagesForTest();
        // Check for the specific server log message related to broadcasting
        assertTrue("Log should contain broadcast initiating message", logs.stream().anyMatch(l -> l.contains("[Server] Broadcasting message (excluding nobody): Test Broadcast")));
    }

    @Test
    public void testPrivateMessageUserNotFound() {
        MockClientHandler aliceHandler = new MockClientHandler("Alice", server);
        server.addClient(aliceHandler);
        server.sendPrivateMessage("Alice", "Ghost", "Hello");
        List<String> msgLog = aliceHandler.getAllMessagesSent();
        // Use startsWith for more precise matching of the error message structure
        assertTrue("Alice should receive user not found error", msgLog.stream().anyMatch(msg -> msg.startsWith(ProtocolConstants.ERROR + " User 'Ghost' not found")));
    }

    @Test
    public void testClientTimeout() {
        server.clearLogForTest();
        MockClientHandler lazyHandler = new MockClientHandler("LazyClient", server);
        server.addClient(lazyHandler);
        // Simulate inactivity
        lazyHandler.setLastActivityTime(System.currentTimeMillis() - ProtocolConstants.CLIENT_TIMEOUT_MS - 2000); // 2 seconds past timeout
        server.performActivityCheck(); // Trigger the check

        // Allow time for async removal if needed (though mock + server logic might be synchronous enough)
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        List<String> logs = server.getLogMessagesForTest();
        boolean timeoutLogged = logs.stream().anyMatch(l -> l.contains("Disconnecting client LazyClient due to inactivity (timeout)"));
        assertTrue("Log should show timeout detection and disconnection intent", timeoutLogged);

        // Check if client received disconnect message
         assertTrue("LazyClient should receive inactivity disconnection message",
                    lazyHandler.getAllMessagesSent().stream().anyMatch(m -> m.contains(ProtocolConstants.SYSTEM + " You have been disconnected due to inactivity")));

         // Check if client handler is marked closed by mock
         assertFalse("MockClientHandler should be marked as not running", lazyHandler.isRunning());

         // Check if server removed the client (Mock calls removeClientFromServer now)
         assertNull("LazyClient should be removed from active clients map", server.getActiveClientsForTest().get("LazyClient"));
    }

    @Test
    public void testPrivateMessageToSelf() {
        // This test reflects the original expectation that the *server* allows PM to self,
        // even if the ClientHandler might prevent it earlier in a real flow.
        MockClientHandler aliceHandler = new MockClientHandler("Alice", server);
        server.addClient(aliceHandler);

        boolean result = server.sendPrivateMessage("Alice", "Alice", "Hi me!");
        assertTrue("Server's sendPrivateMessage should allow sending message to self", result);

        List<String> messages = aliceHandler.getAllMessagesSent();
        assertTrue("Should contain private message to self", messages.stream().anyMatch(m -> m.contains("PRIVATE from Alice: Hi me!")));
        assertTrue("Should contain confirmation message", messages.stream().anyMatch(m -> m.contains("INFO Private message sent to Alice.")));
    }

    @Test
    public void testAllClientsLeave() {
        MockClientHandler aliceHandler = new MockClientHandler("Alice", server);
        server.addClient(aliceHandler);
        server.removeClientFromServer(aliceHandler, "test_remove"); // Simulate removal
        assertTrue("Active client map should be empty", server.getActiveClientsForTest().isEmpty());
        assertNull("Coordinator ID should be null", server.getCoordinatorIdForTest());
    }

    @Test
    public void testSecondClientJoins() {
        MockClientHandler alice = new MockClientHandler("Alice", server);
        server.addClient(alice); // Alice is first, becomes coord
        MockClientHandler bob = new MockClientHandler("Bob", server);
        server.addClient(bob); // Bob joins second

        assertEquals("Alice should remain coordinator", "Alice", server.getCoordinatorIdForTest());
        // Check Bob's received messages
        assertTrue("Bob should receive coordinator info about Alice", bob.getAllMessagesSent().stream().anyMatch(m -> m.contains("COORDINATOR_INFO Alice")));
         // Check Alice's received messages
         assertTrue("Alice should receive system message about Bob joining", alice.getAllMessagesSent().stream().anyMatch(m -> m.contains("SYSTEM Bob joined the chat.")));
    }

    @Test
    public void testBroadcastMessage() {
        MockClientHandler alice = new MockClientHandler("Alice", server);
        server.addClient(alice);
        MockClientHandler bob = new MockClientHandler("Bob", server);
        server.addClient(bob);
        MockClientHandler charlie = new MockClientHandler("Charlie", server);
        server.addClient(charlie);

        String broadcastText = "Hello everyone!";
        // Simulate server broadcasting Alice's message (excluding Alice)
        server.broadcast(MessageFactory.createBroadcastMessage("Alice", broadcastText).toString(), alice);

        // Verify Bob received it
        assertTrue("Bob should receive the broadcast", bob.getAllMessagesSent().stream().anyMatch(m -> m.contains("MESSAGE Alice: " + broadcastText)));
        // Verify Charlie received it
        assertTrue("Charlie should receive the broadcast", charlie.getAllMessagesSent().stream().anyMatch(m -> m.contains("MESSAGE Alice: " + broadcastText)));
        // Verify Alice did NOT receive it (important check for exclusion)
        assertFalse("Alice should NOT receive her own broadcast message", alice.getAllMessagesSent().stream().anyMatch(m -> m.contains("MESSAGE Alice: " + broadcastText)));
    }

    @Test
    public void testCoordinatorLeaves() {
        MockClientHandler alice = new MockClientHandler("Alice", server);
        server.addClient(alice); // Alice is coord
        MockClientHandler bob = new MockClientHandler("Bob", server);
        server.addClient(bob);
        MockClientHandler charlie = new MockClientHandler("Charlie", server);
        server.addClient(charlie); // Order: A, B, C

        server.removeClientFromServer(alice, "test_remove"); // Alice leaves

        assertEquals("Bob should become the new coordinator (alphabetical)", "Bob", server.getCoordinatorIdForTest());
         // Verify Bob was notified
         assertTrue("Bob should be notified he is the new coordinator", bob.getAllMessagesSent().stream().anyMatch(m -> m.contains("SYSTEM You are now the coordinator.")));
         // Verify Charlie was notified about the new coordinator
         assertTrue("Charlie should be notified Bob is the new coordinator", charlie.getAllMessagesSent().stream().anyMatch(m -> m.contains("COORDINATOR_INFO Bob")));
         // Verify both Bob and Charlie were notified Alice left
         assertTrue("Bob should be notified Alice left", bob.getAllMessagesSent().stream().anyMatch(m -> m.contains("SYSTEM Alice left the chat (test_remove).")));
         assertTrue("Charlie should be notified Alice left", charlie.getAllMessagesSent().stream().anyMatch(m -> m.contains("SYSTEM Alice left the chat (test_remove).")));
    }

    @Test
    public void testNonCoordinatorLeaves() {
        MockClientHandler alice = new MockClientHandler("Alice", server);
        server.addClient(alice); // Alice is coord
        MockClientHandler bob = new MockClientHandler("Bob", server);
        server.addClient(bob); // Bob joins

        server.removeClientFromServer(bob, "test_remove"); // Bob leaves

        assertEquals("Alice should remain the coordinator", "Alice", server.getCoordinatorIdForTest());
        assertEquals("Only 1 client (Alice) should remain", 1, server.getActiveClientsForTest().size());
         assertTrue("Alice should be the only remaining client", server.getActiveClientsForTest().containsKey("Alice"));
         // Verify Alice was notified Bob left
         assertTrue("Alice should be notified Bob left", alice.getAllMessagesSent().stream().anyMatch(m -> m.contains("SYSTEM Bob left the chat (test_remove).")));
    }

    @Test
    public void testClientListCommandWho() {
        MockClientHandler alice = new MockClientHandler("Alice", server);
        server.addClient(alice); // Alice is coord
        MockClientHandler bob = new MockClientHandler("Bob", server);
        server.addClient(bob);

        // Simulate Alice requesting the list
        server.sendClientList(alice);
        List<String> messages = alice.getAllMessagesSent();

        // Find list boundaries
        int startIdx = messages.indexOf(ProtocolConstants.CLIENT_LIST_START);
        int endIdx = messages.indexOf(ProtocolConstants.CLIENT_LIST_END);
        assertTrue("Should contain CLIENTLIST_START", startIdx != -1);
        assertTrue("Should contain CLIENTLIST_END", endIdx != -1);
        assertTrue("Start should come before End", startIdx < endIdx);

        // Extract lines between boundaries
        List<String> listContent = messages.subList(startIdx + 1, endIdx);

        // Check content (sorted alphabetically)
        assertEquals("List should contain 2 entries", 2, listContent.size());
        assertTrue("First entry should be Alice (Coordinator)", listContent.get(0).contains("- ID: Alice") && listContent.get(0).contains("[Coordinator]"));
        assertTrue("Second entry should be Bob", listContent.get(1).contains("- ID: Bob") && !listContent.get(1).contains("[Coordinator]"));
    }

    @Test
    public void testPrivateMessageSuccess() {
        MockClientHandler alice = new MockClientHandler("Alice", server);
        server.addClient(alice);
        MockClientHandler bob = new MockClientHandler("Bob", server);
        server.addClient(bob);

        boolean success = server.sendPrivateMessage("Alice", "Bob", "Hi Bob!");
        assertTrue("sendPrivateMessage should return true", success);

        // Verify Bob received it
        assertTrue("Bob should receive the PM from Alice", bob.getAllMessagesSent().stream().anyMatch(m -> m.equals("PRIVATE from Alice: Hi Bob!")));
        // Verify Alice received confirmation
        assertTrue("Alice should receive PM sent confirmation", alice.getAllMessagesSent().stream().anyMatch(m -> m.equals("INFO Private message sent to Bob.")));
    }

    @Test
    public void testNameValidationServerCheck() {
        MockClientHandler alice = new MockClientHandler("Alice", server);
        server.addClient(alice);
        assertTrue("isNameInUse should return true for existing 'Alice'", server.isNameInUse("Alice"));
        assertFalse("isNameInUse should return false for non-existent 'Bob'", server.isNameInUse("Bob"));
         // Assuming case-sensitive check based on HashMap default behavior
         assertFalse("isNameInUse should return false for different case 'alice'", server.isNameInUse("alice"));
    }
}