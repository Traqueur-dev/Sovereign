package fr.traqueur.sovereign.api.events;

import fr.traqueur.sovereign.api.listeners.Listener;
import fr.traqueur.sovereign.api.listeners.ListenerRegistration;
import org.slf4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventBus {

    private final CopyOnWriteArrayList<ListenerEntry> listeners;
    private final Logger logger;
    private final Executor eventExecutor;
    private final AtomicBoolean isRunning;

    public EventBus(Executor eventExecutor, Logger logger) {
        this.logger = logger;
        this.listeners = new CopyOnWriteArrayList<>();
        this.eventExecutor = eventExecutor;
        this.isRunning = new AtomicBoolean(true);
    }

    public <T extends Event> ListenerRegistration addListener(Listener<T> listener, Class<T> eventType, boolean async) {
        TypedListenerWrapper<T> wrapper = new TypedListenerWrapper<>(listener, eventType);
        ListenerEntry entry = new ListenerEntry(wrapper, async);
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    public void publishEvent(Event event) {
        if (!isRunning.get()) {
            logger.debug("EventBus is shutting down, ignoring event: {}", event.getClass().getSimpleName());
            return;
        }

        for (ListenerEntry entry : listeners) {
            if (entry.async) {
                eventExecutor.execute(() -> notifyListener(entry.wrapper, event));
            } else {
                notifyListener(entry.wrapper, event);
            }
        }
    }

    private void notifyListener(TypedListenerWrapper<?> wrapper, Event event) {
        try {
            wrapper.notifyIfMatches(event);
        } catch (Exception e) {
            logger.error("Error while notifying listener", e);
        }
    }

    public void shutdown() {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }
        logger.debug("EventBus shutting down, clearing all listeners");
        listeners.clear();
    }

    private record ListenerEntry(TypedListenerWrapper<?> wrapper, boolean async) {}

    private record TypedListenerWrapper<T extends Event>(Listener<T> listener, Class<T> eventType) {

        public void notifyIfMatches(Event event) {
                if (eventType.isInstance(event)) {
                    listener.onEvent(eventType.cast(event));
                }
            }
        }
}