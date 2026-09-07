package com.nexusuniverse.npc.npc;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;

/**
 * A MOB-type NPC: a real Bukkit entity. Vanilla AI (wandering, random look-around) is switched
 * off on spawn -- movement/looking is entirely trait-driven, same design goal as the packet-based
 * PLAYER-type NPCs, so both kinds behave predictably rather than a Villager NPC also doing its
 * own vanilla wandering on top of whatever a Patrol trait tells it to do.
 */
public class MobNpcHandle extends NpcInstance {

    private final LivingEntity entity;

    public MobNpcHandle(NpcDefinition definition, LivingEntity entity) {
        super(definition);
        this.entity = entity;
        entity.setInvulnerable(true);
        entity.setRemoveWhenFarAway(false);
        entity.setCanPickupItems(false);
        entity.customName(net.kyori.adventure.text.Component.text(definition.displayName()));
        entity.setCustomNameVisible(true);
        if (entity instanceof Mob mob) {
            mob.setAware(false); // no vanilla wandering/target-seeking -- traits own all movement
        }
    }

    public LivingEntity entity() {
        return entity;
    }

    @Override
    public Location location() {
        return entity.getLocation();
    }

    @Override
    public void moveTo(Location location) {
        entity.teleport(location);
    }

    @Override
    public void lookAt(Location target) {
        Location eye = entity.getEyeLocation();
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDistance));
        entity.setRotation(yaw, pitch);
    }

    @Override
    public void refreshEquipment() {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        definition.equipment().forEach(equipment::setItem);
    }

    @Override
    public void refreshSkin() {
        // no-op -- mob-type NPCs don't have a player skin
    }

    @Override
    public void despawn() {
        entity.remove();
    }
}
