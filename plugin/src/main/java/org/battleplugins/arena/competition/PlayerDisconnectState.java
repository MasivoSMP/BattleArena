package org.battleplugins.arena.competition;

/**
 * Metadata for determining how a player should be handled on disconnect.
 */
public final class PlayerDisconnectState {
    public static final PlayerDisconnectState KEEP_IN_COMPETITION = new PlayerDisconnectState(true);

    private final boolean keepInCompetition;

    public PlayerDisconnectState(boolean keepInCompetition) {
        this.keepInCompetition = keepInCompetition;
    }

    public boolean keepInCompetition() {
        return this.keepInCompetition;
    }
}
