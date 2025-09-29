package fr.traqueur.sovereign.api.events;

/**
 * Event fired when an election cycle fails due to an error.
 * <p>
 * This event indicates that an election attempt encountered an exception.
 * It does not necessarily mean the instance cannot participate in future elections;
 * the election mechanism will continue to retry.
 * </p>
 * <p>
 * Common causes include network errors, backend connectivity issues, or
 * unexpected exceptions during the election process.
 * </p>
 *
 * @param instanceId the unique identifier of the instance
 * @param timestamp  the timestamp when the failure occurred (milliseconds since epoch)
 * @param cause      the exception that caused the election failure
 *
 * @see Event
 */
public record ElectionFailedEvent(
    String instanceId,
    long timestamp,
    Throwable cause
) implements Event {}