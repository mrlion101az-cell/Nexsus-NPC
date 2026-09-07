package com.nexusuniverse.npc.trait;

import com.nexusuniverse.npc.NpcConfig;
import com.nexusuniverse.npc.npc.NpcInstance;
import com.nexusuniverse.npc.npc.TraitConfig;

/**
 * One attachable behavior. An NPC can carry any number of traits (see
 * {@link com.nexusuniverse.npc.npc.NpcDefinition#traits()}); {@code NpcManager} calls
 * {@link #tick} on every trait a spawned NPC has, once per {@code npc.tick-interval-ticks}.
 */
public interface Trait {

    /** The persisted {@code type} string this trait answers to, e.g. "LOOK_CLOSE". */
    String typeName();

    void tick(NpcInstance npc, TraitConfig config, NpcConfig pluginConfig);
}
