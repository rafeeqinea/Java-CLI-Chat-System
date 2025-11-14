package coursework;

import java.util.Set;
import java.util.TreeSet;

/**
 * Provides coordinator election functionality.
 * Implements a simple strategy: the client with the lexicographically smallest ID becomes the coordinator.
 */
public class FaultToleranceManager {

    /**
     * Assigns a new coordinator from the given set of client IDs.
     * @param clientIds The set of currently active client IDs.
     * @return The ID of the new coordinator, or null if the set is empty.
     */
    public static String assignNewCoordinator(Set<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return null; // No clients left to be coordinator
        }
        // Use TreeSet to automatically sort IDs lexicographically
        TreeSet<String> sortedIds = new TreeSet<>(clientIds);
        // The first element in the sorted set is the new coordinator
        return sortedIds.first();
    }

    // Private constructor to prevent instantiation
    private FaultToleranceManager() {}
}