package org.battleplugins.arena.competition.phase.phases;

import org.battleplugins.arena.competition.CompetitionType;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.phase.LiveCompetitionPhase;

/**
 * A phase that stops a running event and removes the competition.
 */
public class EndEventPhase<T extends LiveCompetition<T>> extends LiveCompetitionPhase<T> {

    @Override
    public void onStart() {
        if (this.competition.getArena().getType() == CompetitionType.EVENT) {
            this.competition.getArena().getPlugin().getEventScheduler().stopEvent(this.competition.getArena());
            return;
        }

        this.competition.getArena().getPlugin().removeCompetition(this.competition.getArena(), this.competition);
    }

    @Override
    public void onComplete() {

    }
}
