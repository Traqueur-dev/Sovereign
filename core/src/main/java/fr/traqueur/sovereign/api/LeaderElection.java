package fr.traqueur.sovereign.api;

import fr.traqueur.sovereign.api.events.Event;
import fr.traqueur.sovereign.api.events.LeadershipAcquiredEvent;
import fr.traqueur.sovereign.api.events.LeadershipLostEvent;
import fr.traqueur.sovereign.api.listeners.Listener;
import fr.traqueur.sovereign.api.listeners.ListenerRegistration;

import java.util.concurrent.CompletableFuture;

/**
 * Main interface for leader election mechanisms.
 * <p>
 * This interface provides the core operations for participating in distributed leader election,
 * including lifecycle management, state queries, and event listeners.
 * </p>
 * <p>
 * All operations are designed to be non-blocking, using {@link CompletableFuture} for
 * asynchronous execution.
 * </p>
 *
 * <h2>Lifecycle:</h2>
 * <ol>
 *   <li>Create instance via {@link LeaderElectionFactory}</li>
 *   <li>Register event listeners (optional)</li>
 *   <li>Call {@link #start()} to begin election process</li>
 *   <li>Query state via {@link #isLeader()} or {@link #getState()}</li>
 *   <li>Call {@link #stop()} when done</li>
 * </ol>
 *
 * @see LeaderElectionFactory
 * @see State
 * @see Event
 */
public interface LeaderElection {

    /**
     * Starts the leader election process.
     * <p>
     * This method initiates the election algorithm, starting periodic tasks for
     * election cycles and heartbeats. The instance will transition to appropriate
     * states based on the election results.
     * </p>
     *
     * @return a CompletableFuture that completes when the process has started
     */
    CompletableFuture<Void> start();

    /**
     * Stops the leader election process.
     * <p>
     * This method gracefully shuts down the election mechanism, canceling periodic tasks,
     * releasing leadership if held, and cleaning up resources. The event bus is also
     * shut down, preventing further event delivery.
     * </p>
     *
     * @return a CompletableFuture that completes when the process has stopped
     */
    CompletableFuture<Void> stop();

    /**
     * Checks if the current instance is the leader.
     * <p>
     * This is a convenience method equivalent to {@code getState() == State.LEADER}.
     * </p>
     *
     * @return true if the current instance is the leader, false otherwise
     */
    boolean isLeader();

    /**
     * Gets the current state of the leader election.
     *
     * @return the current state (FOLLOWER, CANDIDATE, or LEADER)
     */
    State getState();

    /**
     * Gets the unique identifier of this leader election instance.
     *
     * @return the unique identifier
     */
    String getId();

    /**
     * Registers a listener for a specific event type.
     * <p>
     * Listeners can be registered before or after starting the election process.
     * </p>
     *
     * @param <T>       the event type
     * @param eventType the class of the event type to listen for
     * @param listener  the listener to be notified when events occur
     * @param async     if true, the listener is notified asynchronously; if false, synchronously
     * @return a registration object that can be used to unregister the listener
     *
     * @see Event
     * @see Listener
     * @see ListenerRegistration
     */
    <T extends Event> ListenerRegistration on(Class<T> eventType, Listener<T> listener, boolean async);

    /**
     * Convenience method for registering a listener for leadership acquisition events.
     * <p>
     * This event is fired when the instance transitions to the LEADER state.
     * </p>
     *
     * @param listener the listener to be notified
     * @param async    if true, the listener is notified asynchronously
     * @return a registration object that can be used to unregister the listener
     *
     * @see LeadershipAcquiredEvent
     */
    default ListenerRegistration onLeadershipAcquired(Listener<LeadershipAcquiredEvent> listener, boolean async) {
        return on(LeadershipAcquiredEvent.class, listener, async);
    }

    /**
     * Convenience method for registering a listener for leadership loss events.
     * <p>
     * This event is fired when the instance transitions from LEADER to another state.
     * </p>
     *
     * @param listener the listener to be notified
     * @param async    if true, the listener is notified asynchronously
     * @return a registration object that can be used to unregister the listener
     *
     * @see LeadershipLostEvent
     */
    default ListenerRegistration onLeadershipLost(Listener<LeadershipLostEvent> listener, boolean async) {
        return on(LeadershipLostEvent.class, listener, async);
    }

}
