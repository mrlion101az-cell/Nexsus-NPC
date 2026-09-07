package com.nexusuniverse.npc;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Thin typed accessor over config.yml -- see that file's comments for what each key does. */
public class NpcConfig {

    private final JavaPlugin plugin;

    public NpcConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public int tickIntervalTicks() {
        return cfg().getInt("npc.tick-interval-ticks", 10);
    }

    public int hideFromTablistAfterTicks() {
        return cfg().getInt("npc.hide-from-tablist-after-ticks", 40);
    }

    public double lookCloseRange() {
        return cfg().getDouble("npc.look-close-range", 8.0);
    }

    public double patrolStepBlocks() {
        return cfg().getDouble("npc.patrol-step-blocks", 0.2);
    }

    public double patrolArrivalRadius() {
        return cfg().getDouble("npc.patrol-arrival-radius", 0.5);
    }

    public int skinCacheMinutes() {
        return cfg().getInt("skin.cache-minutes", 60);
    }
}
