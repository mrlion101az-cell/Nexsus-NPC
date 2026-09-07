package com.nexusuniverse.npc.npc;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

/**
 * The live, spawned-in-the-world side of an NPC. One concrete subclass per {@link NpcType}:
 * {@link MobNpcHandle} for a real Bukkit entity, {@link PacketNpcHandle} for a packet-only
 * player-skinned NPC. Traits are written against this common surface so a trait like
 * LookCloseTrait doesn't need to know or care which kind of NPC it's ticking.
 */
public abstract class NpcInstance {

    protected final NpcDefinition definition;
    /** Scratch space for traits to keep their own per-NPC runtime state (e.g. PatrolTrait's
     *  current waypoint index) without polluting the persisted {@link TraitConfig}. Keyed by
     *  trait type name. Lost on restart -- that's fine, traits re-derive a sane starting state. */
    private final Map<String, Object> traitState = new HashMap<>();

    protected NpcInstance(NpcDefinition definition) {
        this.definition = definition;
    }

    public NpcDefinition definition() {
        return definition;
    }

    public Map<String, Object> traitState() {
        return traitState;
    }

    public abstract Location location();

    /** Moves the NPC to an exact location (teleport-style, not pathfound). */
    public abstract void moveTo(Location location);

    /** Turns the NPC's head (and body, for a mob) to face a point, without changing position. */
    public abstract void lookAt(Location target);

    /** Applies (or re-applies) this NPC's persisted equipment to the live entity/packet state. */
    public abstract void refreshEquipment();

    /** Applies (or re-applies) this NPC's persisted skin. No-op for MOB-type NPCs. */
    public abstract void refreshSkin();

    /** Removes the NPC from the world/from every client's view. Does not touch the persisted definition. */
    public abstract void despawn();

    /** Called once for every player who's already online when a PLAYER-type NPC spawns, and
     *  again whenever a new player joins afterwards, so they get the spawn/visibility packets
     *  too. No-op for MOB-type NPCs, which are real entities Bukkit already handles this for. */
    public void showTo(org.bukkit.entity.Player player) {
        // default no-op; overridden by PacketNpcHandle
    }
}
