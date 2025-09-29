package fr.traqueur.sovereign.api.listeners;

import fr.traqueur.sovereign.api.events.*;

@FunctionalInterface
public interface Listener<T extends Event> {
    void onEvent(T event);


    class TypedListenerWrapper<T extends Event> {
        private final Listener<T> listener;
        private final Class<T> eventType;

        public TypedListenerWrapper(Listener<T> listener, Class<T> eventType) {
            this.listener = listener;
            this.eventType = eventType;
        }

        public void notifyIfMatches(Event event) {
            if (eventType.isInstance(event)) {
                listener.onEvent(eventType.cast(event));
            }
        }
    }

}