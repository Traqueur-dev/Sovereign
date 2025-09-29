package fr.traqueur.sovereign.api.events;

/**
 * Base interface for all leader election events.
 * <p>
 * This is a sealed interface that restricts implementations to a known set of event types.
 * Events are published by the leader election mechanism to notify interested parties
 * about state changes and important occurrences.
 * </p>
 * <p>
 * All events carry the instance ID and timestamp of when they occurred.
 * </p>
 *
 * @see ElectionFailedEvent
 * @see LeadershipAcquiredEvent
 * @see LeadershipLostEvent
 * @see StateChangedEvent
 */
public sealed interface Event permits ElectionFailedEvent, LeadershipAcquiredEvent, LeadershipLostEvent, StateChangedEvent {

    /**
     * Returns the unique identifier of the instance that generated this event.
     *
     * @return the instance ID
     */
    String instanceId();

    /**
     * Returns the timestamp when this event occurred.
     *
     * @return the timestamp in milliseconds since epoch
     */
    long timestamp();

}