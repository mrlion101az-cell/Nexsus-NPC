package com.nexusuniverse.npc.trait;

import com.nexusuniverse.npc.NpcConfig;
import com.nexusuniverse.npc.npc.NpcInstance;
import com.nexusuniverse.npc.npc.TraitConfig;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Walks a straight-line loop between waypoints stored as {@code "world,x,y,z"} strings under the
 * trait's {@code waypoints} param. This is deliberately simple (no pathfinding around obstacles
 * for PLAYER-type NPCs, which have no real entity to hand off to Bukkit's navigator) -- good
 * enough for open patrol routes; MOB-type NPCs could be upgraded to use
 * {@link org.bukkit.entity.Mob#getPathfinder()} for obstacle-aware movement in a later version.
 */
public class PatrolTrait implements Trait {

    private static final String STATE_INDEX = "PATROL_INDEX";

    @Override
    public String typeName() {
        return "PATROL";
    }

    @Override
    public void tick(NpcInstance npc, TraitConfig config, NpcConfig pluginConfig) {
        List<String> waypoints = config.stringList("waypoints");
        if (waypoints.isEmpty()) return;

        int index = (int) npc.traitState().getOrDefault(STATE_INDEX, 0);
        if (index < 0 || index >= waypoints.size()) index = 0;

        Location target = parse(waypoints.get(index));
        if (target == null) {
            // a saved waypoint's world isn't loaded (or got deleted) -- skip past it rather than
            // getting the whole patrol stuck on one bad entry
            npc.traitState().put(STATE_INDEX, (index + 1) % waypoints.size());
            return;
        }

        Location current = npc.location();
        if (current.getWorld() == null || !current.getWorld().equals(target.getWorld())) return;

        if (current.distance(target) <= pluginConfig.patrolArrivalRadius()) {
            npc.traitState().put(STATE_INDEX, (index + 1) % waypoints.size());
            return;
        }

        Vector step = target.toVector().subtract(current.toVector()).normalize().multiply(pluginConfig.patrolStepBlocks());
        Location next = current.clone().add(step);
        next.setYaw(yawTowards(current, target));
        next.setPitch(0f);
        npc.moveTo(next);
    }

    private float yawTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private Location parse(String raw) {
        String[] parts = raw.split(",");
        if (parts.length < 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            double x = Double.parseDouble(parts[1]);
            double y = Double.parseDouble(parts[2]);
            double z = Double.parseDouble(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
