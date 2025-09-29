package fr.traqueur.sovereign.api.events;

/**
 * Event fired when an instance successfully acquires leadership.
 * <p>
 * This event is published when an instance transitions to the LEADER state.
 * It indicates that the instance has won the election and is now responsible
 * for leader duties.
 * </p>
 *
 * @param instanceId the unique identifier of the instance that became leader
 * @param timestamp  the timestamp when leadership was acquired (milliseconds since epoch)
 *
 * @see Event
 * @see fr.traqueur.sovereign.api.State#LEADER
 */
public record LeadershipAcquiredEvent(
    String instanceId,
    long timestamp
) implements Event {}