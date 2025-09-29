package fr.traqueur.sovereign.api.events;

import fr.traqueur.sovereign.api.State;

public record StateChangedEvent(
    String instanceId, 
    long timestamp, 
    State previousState,
    State newState
) implements Event {}