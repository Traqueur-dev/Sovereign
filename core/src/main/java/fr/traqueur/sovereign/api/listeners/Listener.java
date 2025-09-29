package fr.traqueur.sovereign.api.listeners;

import fr.traqueur.sovereign.api.events.*;

@FunctionalInterface
public interface Listener<T extends Event> {
    void onEvent(T event);
}