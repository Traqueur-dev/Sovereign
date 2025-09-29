package fr.traqueur.sovereign.api.events;

public sealed interface Event permits ElectionFailedEvent, LeadershipAcquiredEvent, LeadershipLostEvent, StateChangedEvent {
    
    String instanceId();
    long timestamp();

}