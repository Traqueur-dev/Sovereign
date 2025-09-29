package fr.traqueur.sovereign.api.events;

public record LeadershipLostEvent(
    String instanceId, 
    long timestamp
) implements Event {}