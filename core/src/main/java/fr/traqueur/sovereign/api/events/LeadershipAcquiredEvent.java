package fr.traqueur.sovereign.api.events;

public record LeadershipAcquiredEvent(
    String instanceId, 
    long timestamp
) implements Event {}