package fr.traqueur.sovereign.api.events;

import fr.traqueur.sovereign.api.State;

/**
 * Event fired when an instance transitions from one state to another.
 * <p>
 * This event is published for all state transitions (FOLLOWER, CANDIDATE, LEADER).
 * It provides both the previous and new states, allowing listeners to track
 * the complete state transition history.
 * </p>
 *
 * @param instanceId    the unique identifier of the instance
 * @param timestamp     the timestamp when the transition occurred (milliseconds since epoch)
 * @param previousState the state before the transition
 * @param newState      the state after the transition
 *
 * @see State
 * @see Event
 */
public record StateChangedEvent(
    String instanceId,
    long timestamp,
    State previousState,
    State newState
) implements Event {}