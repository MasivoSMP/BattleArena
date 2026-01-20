package org.battleplugins.arena.module.pvpmanager;

import me.chancesd.pvpmanager.player.CombatPlayer;
import me.chancesd.pvpmanager.player.UntagReason;
import org.battleplugins.arena.ArenaPlayer;
import org.battleplugins.arena.competition.LiveCompetition;
import org.battleplugins.arena.competition.PlayerDisconnectState;
import org.battleplugins.arena.competition.phase.CompetitionPhaseType;
import org.battleplugins.arena.event.arena.ArenaPhaseStartEvent;
import org.battleplugins.arena.event.arena.ArenaRemoveCompetitionEvent;
import org.battleplugins.arena.event.player.ArenaJoinEvent;
import org.battleplugins.arena.event.player.ArenaLeaveEvent;
import org.battleplugins.arena.event.player.ArenaSpectateEvent;
import org.battleplugins.arena.module.ArenaModule;
import org.battleplugins.arena.module.ArenaModuleInitializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A module that integrates BattleArena competitions with PvPManager combat tagging.
 */
@ArenaModule(
        id = PvPManagerIntegration.ID,
        name = "PvPManager Integration",
        description = "Tags participants when the ingame phase starts and defers disconnect elimination to PvPManager combat-log NPCs.",
        authors = "BattlePlugins"
)
public class PvPManagerIntegration implements ArenaModuleInitializer {
    public static final String ID = "pvpmanager-integration";

    private final Map<UUID, ArenaPlayer> offlineParticipants = new ConcurrentHashMap<>();

    @EventHandler
    public void onPhaseStart(ArenaPhaseStartEvent event) {
        if (!isArenaModuleEnabled(event.getArena())) {
            return;
        }

        if (!(event.getCompetition() instanceof LiveCompetition<?> competition)) {
            return;
        }

        if (CompetitionPhaseType.INGAME.equals(event.getPhase().getType())) {
            if (!isPvPManagerEnabled()) {
                return;
            }

            for (ArenaPlayer player : competition.getPlayers()) {
                applyDisconnectPolicy(player);
                tagPlayer(player.getPlayer());
            }

            return;
        }

        if (event.getPhase().getPreviousPhase() != null
                && CompetitionPhaseType.INGAME.equals(event.getPhase().getPreviousPhase().getType())) {
            clearDisconnectPolicy(competition);
            clearOfflineParticipants(competition);
            untagPlayers(competition);
        }
    }

    @EventHandler
    public void onJoin(ArenaJoinEvent event) {
        ArenaPlayer player = event.getArenaPlayer();
        if (!isArenaModuleEnabled(player.getArena())) {
            return;
        }

        if (!CompetitionPhaseType.INGAME.equals(player.getCompetition().getPhase())) {
            return;
        }

        if (!isPvPManagerEnabled()) {
            return;
        }

        applyDisconnectPolicy(player);
        tagPlayer(player.getPlayer());
    }

    @EventHandler
    public void onLeave(ArenaLeaveEvent event) {
        ArenaPlayer player = event.getArenaPlayer();
        if (!isArenaModuleEnabled(player.getArena())) {
            return;
        }

        clearDisconnectPolicy(player);
        this.offlineParticipants.remove(player.getPlayer().getUniqueId());
        untagPlayer(player.getPlayer());
    }

    @EventHandler
    public void onSpectate(ArenaSpectateEvent event) {
        ArenaPlayer player = event.getArenaPlayer();
        if (!isArenaModuleEnabled(player.getArena())) {
            return;
        }

        clearDisconnectPolicy(player);
        this.offlineParticipants.remove(player.getPlayer().getUniqueId());
        untagPlayer(player.getPlayer());
    }

    @EventHandler
    public void onRemoveCompetition(ArenaRemoveCompetitionEvent event) {
        if (!isArenaModuleEnabled(event.getArena())) {
            return;
        }

        if (event.getCompetition() instanceof LiveCompetition<?> competition) {
            clearOfflineParticipants(competition);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isPvPManagerEnabled()) {
            return;
        }

        ArenaPlayer arenaPlayer = ArenaPlayer.getArenaPlayer(event.getPlayer());
        if (arenaPlayer == null) {
            return;
        }

        if (!arenaPlayer.getArena().isModuleEnabled(ID)) {
            return;
        }

        if (!CompetitionPhaseType.INGAME.equals(arenaPlayer.getCompetition().getPhase())) {
            return;
        }

        this.offlineParticipants.put(event.getPlayer().getUniqueId(), arenaPlayer);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        ArenaPlayer arenaPlayer = this.offlineParticipants.remove(event.getPlayer().getUniqueId());
        if (arenaPlayer == null) {
            return;
        }

        if (!(arenaPlayer.getCompetition() instanceof LiveCompetition<?>)) {
            return;
        }

        LiveCompetition<?> competition = (LiveCompetition<?>) arenaPlayer.getCompetition();

        competition.reconnect(arenaPlayer, event.getPlayer());
        arenaPlayer.getStorage().markReconnected();

        if (!arenaPlayer.getArena().isModuleEnabled(ID)) {
            clearDisconnectPolicy(arenaPlayer);
            return;
        }

        if (!isPvPManagerEnabled()) {
            clearDisconnectPolicy(arenaPlayer);
            return;
        }

        if (CompetitionPhaseType.INGAME.equals(competition.getPhase())) {
            applyDisconnectPolicy(arenaPlayer);
            tagPlayer(event.getPlayer());
        } else {
            clearDisconnectPolicy(arenaPlayer);
        }
    }

    @EventHandler
    public void onCombatLogNpcDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Player npc)) {
            return;
        }

        if (!npc.hasMetadata("combat_log_npc")) {
            return;
        }

        Object metadataValue = npc.getMetadata("combat_log_npc").get(0).value();
        if (!(metadataValue instanceof UUID playerUuid)) {
            return;
        }

        ArenaPlayer arenaPlayer = this.offlineParticipants.remove(playerUuid);
        if (arenaPlayer == null) {
            return;
        }

        if (!isArenaModuleEnabled(arenaPlayer.getArena())) {
            return;
        }

        if (!CompetitionPhaseType.INGAME.equals(arenaPlayer.getCompetition().getPhase())) {
            return;
        }

        clearDisconnectPolicy(arenaPlayer);
        if (arenaPlayer.getCompetition() instanceof LiveCompetition<?>) {
            LiveCompetition<?> competition = (LiveCompetition<?>) arenaPlayer.getCompetition();
            competition.leave(arenaPlayer, ArenaLeaveEvent.Cause.PLUGIN);
        }
    }

    private static boolean isArenaModuleEnabled(org.battleplugins.arena.Arena arena) {
        return arena.isModuleEnabled(ID);
    }

    private static boolean isPvPManagerEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("PvPManager");
    }

    private static void tagPlayer(Player player) {
        if (!isPvPManagerEnabled()) {
            return;
        }

        CombatPlayer combatPlayer = CombatPlayer.get(player);
        if (combatPlayer == null) {
            return;
        }

        combatPlayer.tag(true, combatPlayer);
    }

    private static void untagPlayer(Player player) {
        if (!isPvPManagerEnabled()) {
            return;
        }

        CombatPlayer combatPlayer = CombatPlayer.get(player);
        if (combatPlayer == null) {
            return;
        }

        combatPlayer.untag(UntagReason.PLUGIN_API);
    }

    private static void applyDisconnectPolicy(ArenaPlayer player) {
        player.setMetadata(PlayerDisconnectState.class, PlayerDisconnectState.KEEP_IN_COMPETITION);
    }

    private static void clearDisconnectPolicy(ArenaPlayer player) {
        player.removeMetadata(PlayerDisconnectState.class);
    }

    private void clearDisconnectPolicy(LiveCompetition<?> competition) {
        for (ArenaPlayer player : competition.getPlayers()) {
            clearDisconnectPolicy(player);
        }
    }

    private void clearOfflineParticipants(LiveCompetition<?> competition) {
        this.offlineParticipants.entrySet().removeIf(entry -> entry.getValue().getCompetition().equals(competition));
    }

    private void untagPlayers(LiveCompetition<?> competition) {
        for (ArenaPlayer player : competition.getPlayers()) {
            untagPlayer(player.getPlayer());
        }
    }
}
