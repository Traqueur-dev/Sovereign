package fr.traqueur.sovereign.api.events;

import fr.traqueur.sovereign.api.listeners.Listener;
import fr.traqueur.sovereign.api.listeners.ListenerRegistration;
import org.slf4j.Logger;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal event bus for managing and dispatching leader election events.
 * <p>
 * This class is responsible for:
 * </p>
 * <ul>
 *   <li>Registering and unregistering event listeners</li>
 *   <li>Publishing events to registered listeners</li>
 *   <li>Supporting both synchronous and asynchronous event delivery</li>
 *   <li>Type-safe event matching based on listener registration</li>
 * </ul>
 * <p>
 * The event bus uses {@link CopyOnWriteArrayList} for thread-safe listener management
 * and provides graceful shutdown capabilities.
 * </p>
 * <p>
 * This class is used internally by leader election implementations and is not
 * intended for direct use by library consumers.
 * </p>
 *
 * @see Event
 * @see Listener
 * @see ListenerRegistration
 */
public class EventBus {

    private final CopyOnWriteArrayList<ListenerEntry> listeners;
    private final Logger logger;
    private final Executor eventExecutor;
    private final AtomicBoolean isRunning;

    /**
     * Constructs a new EventBus.
     *
     * @param eventExecutor the executor for asynchronous event delivery
     * @param logger        the logger for event bus operations
     */
    public EventBus(Executor eventExecutor, Logger logger) {
        this.logger = logger;
        this.listeners = new CopyOnWriteArrayList<>();
        this.eventExecutor = eventExecutor;
        this.isRunning = new AtomicBoolean(true);
    }

    /**
     * Registers a listener for a specific event type.
     *
     * @param <T>       the event type
     * @param listener  the listener to register
     * @param eventType the class of the event type to listen for
     * @param async     if true, events are delivered asynchronously; if false, synchronously
     * @return a registration that can be used to unregister the listener
     */
    public <T extends Event> ListenerRegistration addListener(Listener<T> listener, Class<T> eventType, boolean async) {
        TypedListenerWrapper<T> wrapper = new TypedListenerWrapper<>(listener, eventType);
        ListenerEntry entry = new ListenerEntry(wrapper, async);
        listeners.add(entry);
        return () -> listeners.remove(entry);
    }

    /**
     * Publishes an event to all registered listeners.
     * <p>
     * Listeners registered for the event's type (or a supertype) will be notified.
     * Asynchronous listeners are notified via the event executor, while synchronous
     * listeners are notified immediately on the calling thread.
     * </p>
     * <p>
     * If the event bus is shutting down, the event is ignored.
     * </p>
     *
     * @param event the event to publish
     */
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

    /**
     * Notifies a listener wrapper if the event matches its type.
     * <p>
     * Exceptions thrown by listeners are caught and logged to prevent
     * them from affecting other listeners or the election process.
     * </p>
     *
     * @param wrapper the listener wrapper to notify
     * @param event   the event to deliver
     */
    private void notifyListener(TypedListenerWrapper<?> wrapper, Event event) {
        try {
            wrapper.notifyIfMatches(event);
        } catch (Exception e) {
            logger.error("Error while notifying listener", e);
        }
    }

    /**
     * Shuts down the event bus.
     * <p>
     * After shutdown, no more events will be delivered and all listeners are cleared.
     * This method is idempotent.
     * </p>
     */
    public void shutdown() {
        if (!isRunning.compareAndSet(true, false)) {
            return;
        }
        logger.debug("EventBus shutting down, clearing all listeners");
        listeners.clear();
    }

    /**
     * Internal record for tracking listener entries with their delivery mode.
     *
     * @param wrapper the type-safe listener wrapper
     * @param async   if true, the listener should be notified asynchronously
     */
    private record ListenerEntry(TypedListenerWrapper<?> wrapper, boolean async) {}

    /**
     * Internal wrapper for type-safe event matching and delivery.
     *
     * @param <T>       the event type
     * @param listener  the actual listener
     * @param eventType the class of the event type to match
     */
    private record TypedListenerWrapper<T extends Event>(Listener<T> listener, Class<T> eventType) {

        /**
         * Notifies the listener if the event matches the expected type.
         *
         * @param event the event to potentially deliver
         */
        public void notifyIfMatches(Event event) {
                if (eventType.isInstance(event)) {
                    listener.onEvent(eventType.cast(event));
                }
            }
        }
}