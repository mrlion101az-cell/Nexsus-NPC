package com.nexusuniverse.npc.trait;

import com.nexusuniverse.npc.NpcConfig;
import com.nexusuniverse.npc.npc.NpcInstance;
import com.nexusuniverse.npc.npc.TraitConfig;
import org.bukkit.entity.Player;

/** Faces the nearest player within {@code npc.look-close-range}, if any; otherwise does nothing. */
public class LookCloseTrait implements Trait {

    @Override
    public String typeName() {
        return "LOOK_CLOSE";
    }

    @Override
    public void tick(NpcInstance npc, TraitConfig config, NpcConfig pluginConfig) {
        double range = pluginConfig.lookCloseRange();
        double rangeSquared = range * range;
        var location = npc.location();
        var world = location.getWorld();
        if (world == null) return;

        Player nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;
        for (Player player : world.getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared <= rangeSquared && distanceSquared < nearestDistanceSquared) {
                nearest = player;
                nearestDistanceSquared = distanceSquared;
            }
        }

        if (nearest != null) {
            npc.lookAt(nearest.getEyeLocation());
        }
    }
}
