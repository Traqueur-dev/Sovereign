package fr.traqueur.sovereign.api;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for leader election mechanisms.
 */
public interface LeaderElection {

    /**
     * Starts the leader election process.
     *
     * @return A CompletableFuture that completes when the process has started.
     */
    CompletableFuture<Void> start();

    /**
     * Stops the leader election process.
     *
     * @return A CompletableFuture that completes when the process has stopped.
     */
    CompletableFuture<Void> stop();

    /**
     * Checks if the current instance is the leader.
     *
     * @return true if the current instance is the leader, false otherwise.
     */
    boolean isLeader();

    /**
     * Gets the current state of the leader election.
     *
     * @return The current state.
     */
    State getState();

    /**
     * Gets the unique identifier of this leader election instance.
     *
     * @return The unique identifier.
     */
    String getId();

}
