package fr.traqueur.sovereign.api.listeners;

/**
 * Handle for managing a registered event listener.
 * <p>
 * This interface provides a way to unregister a previously registered listener.
 * Once removed, the listener will no longer receive events.
 * </p>
 * <p>
 * Instances are returned when registering listeners via
 * {@link fr.traqueur.sovereign.api.LeaderElection#on(Class, Listener, boolean)}.
 * </p>
 *
 * @see Listener
 * @see fr.traqueur.sovereign.api.LeaderElection#on(Class, Listener, boolean)
 */
@FunctionalInterface
public interface ListenerRegistration {

    /**
     * Removes the registered listener.
     * <p>
     * After calling this method, the listener will no longer receive events.
     * This method is idempotent - calling it multiple times has no additional effect.
     * </p>
     */
    void remove();
}