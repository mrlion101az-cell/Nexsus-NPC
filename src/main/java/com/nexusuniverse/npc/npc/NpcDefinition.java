package com.nexusuniverse.npc.npc;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/**
 * The persisted definition of one NPC -- everything needed to recreate it on the next server
 * start. {@link NpcInstance} is the live, spawned-in-the-world counterpart built from this.
 */
public class NpcDefinition {

    private final String id;
    /** Own identity for this NPC, independent of any real player -- used as the fake profile's
     *  UUID for PLAYER-type NPCs. Generated once at creation and never changes. */
    private final UUID npcUuid;
    private NpcType type;
    private String displayName;
    private EntityType mobType; // only meaningful for NpcType.MOB
    private String skinSourceName; // the username last used for /npc skin, if any (PLAYER only)
    private String skinValue;      // Mojang texture property value (PLAYER only)
    private String skinSignature;  // Mojang texture property signature (PLAYER only)
    private Location location;
    private final Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
    private final List<TraitConfig> traits = new ArrayList<>();

    public NpcDefinition(String id, UUID npcUuid, NpcType type, String displayName, Location location) {
        this.id = id;
        this.npcUuid = npcUuid;
        this.type = type;
        this.displayName = displayName;
        this.location = location;
    }

    public String id() {
        return id;
    }

    public UUID npcUuid() {
        return npcUuid;
    }

    public NpcType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public EntityType mobType() {
        return mobType;
    }

    public void setMobType(EntityType mobType) {
        this.mobType = mobType;
    }

    public String skinSourceName() {
        return skinSourceName;
    }

    public String skinValue() {
        return skinValue;
    }

    public String skinSignature() {
        return skinSignature;
    }

    public void setSkin(String sourceName, String value, String signature) {
        this.skinSourceName = sourceName;
        this.skinValue = value;
        this.skinSignature = signature;
    }

    public boolean hasSkin() {
        return skinValue != null && skinSignature != null;
    }

    public Location location() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Map<EquipmentSlot, ItemStack> equipment() {
        return equipment;
    }

    public List<TraitConfig> traits() {
        return traits;
    }

    public TraitConfig trait(String type) {
        for (TraitConfig trait : traits) {
            if (trait.type().equalsIgnoreCase(type)) return trait;
        }
        return null;
    }

    public void removeTrait(String type) {
        traits.removeIf(t -> t.type().equalsIgnoreCase(type));
    }
}
