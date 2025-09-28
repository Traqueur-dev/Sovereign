package fr.traqueur.sovereign.api;

/**
 * Represents the state of a node in the leader election algorithm.
 */
public enum State {

    /**
     * The node is a follower.
     */
    FOLLOWER,
    /**
     * The node is a candidate.
     */
    CANDIDATE,
    /**
     * The node is the leader.
     */
    LEADER;

}
