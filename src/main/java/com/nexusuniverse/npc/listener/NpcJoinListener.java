package com.nexusuniverse.npc.listener;

import com.nexusuniverse.npc.npc.NpcManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Player-type NPCs are pure packets -- a client that wasn't online yet when one spawned has never
 * been sent it, so every newly-joining player needs a catch-up copy of all the spawn packets.
 * Mob-type NPCs need nothing here; they're real entities Bukkit already sends to everyone.
 */
public class NpcJoinListener implements Listener {

    private final NpcManager npcs;

    public NpcJoinListener(JavaPlugin plugin, NpcManager npcs) {
        this.npcs = npcs;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        npcs.showAllTo(event.getPlayer());
    }
}
