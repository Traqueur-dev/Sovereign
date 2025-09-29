// core/src/main/java/fr/traqueur/sovereign/api/events/EventBus.java
package fr.traqueur.sovereign.api.events;

import fr.traqueur.sovereign.api.listeners.Listener;
import fr.traqueur.sovereign.api.listeners.ListenerRegistration;
import org.slf4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

public class EventBus {

    private final CopyOnWriteArrayList<ListenerEntry> listeners;
    private final Logger logger;
    private final Executor eventExecutor;

    public EventBus(Executor eventExecutor, Logger logger) {
        this.logger = logger;
        this.listeners = new CopyOnWriteArrayList<>();
        this.eventExecutor = eventExecutor;
    }

    public <T extends Event> ListenerRegistration addListener(Listener<T> listener, Class<T> eventType, boolean async) {
        Listener.TypedListenerWrapper<T> wrapper = new Listener.TypedListenerWrapper<>(listener, eventType);
        ListenerEntry entry = new ListenerEntry(wrapper, async);
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    public void publishEvent(Event event) {
        for (ListenerEntry entry : listeners) {
            if (entry.async) {
                eventExecutor.execute(() -> notifyListener(entry.wrapper, event));
            } else {
                notifyListener(entry.wrapper, event);
            }
        }
    }

    private void notifyListener(Listener.TypedListenerWrapper<?> wrapper, Event event) {
        try {
            wrapper.notifyIfMatches(event);
        } catch (Exception e) {
            logger.error("Error while notifying listener", e);
        }
    }

    public void clear() {
        listeners.clear();
    }

    private record ListenerEntry(Listener.TypedListenerWrapper<?> wrapper, boolean async) {}
}