package fr.traqueur.sovereign.api.listeners;

import fr.traqueur.sovereign.api.events.*;

/**
 * Functional interface for listening to leader election events.
 * <p>
 * Listeners are registered via {@link fr.traqueur.sovereign.api.LeaderElection#on(Class, Listener, boolean)}
 * and are notified when events of the specified type occur.
 * </p>
 * <p>
 * Listeners can be invoked either synchronously or asynchronously depending on
 * the registration configuration. Exceptions thrown by listeners are caught and logged
 * but do not affect the election process.
 * </p>
 *
 * @param <T> the type of event this listener handles
 *
 * @see Event
 * @see ListenerRegistration
 * @see fr.traqueur.sovereign.api.LeaderElection#on(Class, Listener, boolean)
 */
@FunctionalInterface
public interface Listener<T extends Event> {

    /**
     * Called when an event of type T occurs.
     * <p>
     * Implementations should be lightweight and non-blocking. Heavy processing
     * should be offloaded to separate threads.
     * </p>
     *
     * @param event the event that occurred
     */
    void onEvent(T event);
}