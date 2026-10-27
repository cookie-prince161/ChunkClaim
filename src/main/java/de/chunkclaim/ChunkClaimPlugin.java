package de.chunkclaim;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkClaimPlugin extends JavaPlugin {

    private Messages messages;
    private ClaimManager claims;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        claims = new ClaimManager(this);
        claims.load();

        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);

        PluginCommand command = getCommand("chunk");
        if (command != null) {
            ChunkCommand handler = new ChunkCommand(this);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        getLogger().info(claims.all().size() + " Claims geladen.");
    }

    @Override
    public void onDisable() {
        if (claims != null) claims.save();
    }

    /** Lädt die config.yml neu (Texte, Preise, Sounds werden live gelesen). */
    public void reload() {
        reloadConfig();
    }

    public Messages messages() { return messages; }
    public ClaimManager claims() { return claims; }
}
