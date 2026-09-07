package com.nexusuniverse.npc.npc;

/**
 * MOB is a real Bukkit entity (any {@link org.bukkit.entity.EntityType} that's a
 * {@link org.bukkit.entity.Mob}, e.g. a Villager) -- simple, uses normal Bukkit AI/pathfinding,
 * no external dependency.
 * <p>
 * PLAYER is a packet-only "fake player" -- there is no real underlying entity at all. Bukkit's
 * public API has no way to spawn an actual player-type entity (Paper's maintainers deliberately
 * decided not to add one -- see PacketEvents' PLAYER handle for details), so a player-skinned NPC
 * only exists as packets sent to nearby clients, via the PacketEvents plugin.
 */
public enum NpcType {
    MOB,
    PLAYER
}
