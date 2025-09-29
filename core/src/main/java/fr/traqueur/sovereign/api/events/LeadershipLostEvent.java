package fr.traqueur.sovereign.api.events;

/**
 * Event fired when an instance loses leadership.
 * <p>
 * This event is published when an instance transitions from the LEADER state
 * to another state (typically FOLLOWER). This can occur when the instance
 * voluntarily steps down, loses connectivity, or fails to maintain its lease.
 * </p>
 *
 * @param instanceId the unique identifier of the instance that lost leadership
 * @param timestamp  the timestamp when leadership was lost (milliseconds since epoch)
 *
 * @see Event
 * @see fr.traqueur.sovereign.api.State#LEADER
 */
public record LeadershipLostEvent(
    String instanceId,
    long timestamp
) implements Event {}