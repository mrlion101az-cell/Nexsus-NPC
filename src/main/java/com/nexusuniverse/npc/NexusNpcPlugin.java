package com.nexusuniverse.npc;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import com.nexusuniverse.npc.command.NpcCommand;
import com.nexusuniverse.npc.listener.NpcJoinListener;
import com.nexusuniverse.npc.npc.NpcManager;
import com.nexusuniverse.npc.skin.SkinFetcher;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusNpcPlugin extends JavaPlugin {

    private NpcManager npcManager;

    @Override
    public void onLoad() {
        // Must happen in onLoad, before PacketEvents' own onLoad/onEnable have necessarily run,
        // per PacketEvents' documented plugin-lifecycle contract -- this is what lets it inject
        // into every player's network channel once it's enabled.
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();

        NpcConfig config = new NpcConfig(this);
        this.npcManager = new NpcManager(this, config);
        SkinFetcher skinFetcher = new SkinFetcher(this, config.skinCacheMinutes());

        // Spawn on the next tick rather than synchronously here -- worlds are guaranteed loaded
        // by then, whereas onEnable can in rare cases run before every world has finished loading.
        Bukkit.getScheduler().runTask(this, npcManager::spawnAll);
        Bukkit.getScheduler().runTaskTimer(this, npcManager::tickAll, config.tickIntervalTicks(), config.tickIntervalTicks());

        getCommand("npc").setExecutor(new NpcCommand(this, npcManager, skinFetcher));
        Bukkit.getPluginManager().registerEvents(new NpcJoinListener(this, npcManager), this);

        getLogger().info("NexusNPC v0.1.0 enabled -- " + npcManager.all().size() + " NPC(s) loaded.");
    }

    @Override
    public void onDisable() {
        if (npcManager != null) npcManager.despawnAll();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    }
}
