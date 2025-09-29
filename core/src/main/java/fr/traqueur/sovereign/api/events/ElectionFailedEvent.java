package fr.traqueur.sovereign.api.events;

public record ElectionFailedEvent(
    String instanceId, 
    long timestamp, 
    Throwable cause
) implements Event {}