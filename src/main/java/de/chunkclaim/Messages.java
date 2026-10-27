package de.chunkclaim;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Alle Texte, Anzeigearten und Sounds. Die Texte stehen in der config.yml (MiniMessage-Format). */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2000), Duration.ofMillis(500));
    private static final Title.Times CENTER_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500), Duration.ofMillis(400));

    private final ChunkClaimPlugin plugin;

    public Messages(ChunkClaimPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Platzhalter-Helfer ----------------------------------------------

    public static TagResolver num(String name, int value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    public static TagResolver text(String name, String value) {
        return Placeholder.unparsed(name, value);
    }

    // --- Texte -------------------------------------------------------------

    public Component get(String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null) raw = "<red>[Text fehlt: " + key + "]";
        return MM.deserialize(raw, resolvers);
    }

    public List<Component> getList(String key, TagResolver... resolvers) {
        List<Component> result = new ArrayList<>();
        for (String raw : plugin.getConfig().getStringList("messages." + key)) {
            result.add(MM.deserialize(raw, resolvers));
        }
        return result;
    }

    /** Chat-Nachricht mit Präfix. */
    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(get("prefix").append(get(key, resolvers)));
    }

    // --- Anzeige -----------------------------------------------------------

    /** Zeigt eine Meldung so an, wie es in messages.display.<displayKey> steht. */
    public void display(Player player, String displayKey, Component component) {
        String mode = plugin.getConfig().getString("messages.display." + displayKey, "ACTIONBAR")
                .toUpperCase(Locale.ROOT);
        switch (mode) {
            case "TITLE" -> player.showTitle(Title.title(Component.empty(), component, CENTER_TIMES));
            case "CHAT" -> player.sendMessage(component);
            default -> player.sendActionBar(component);
        }
    }

    public void title(Player player, String titleKey, String subtitleKey, TagResolver... resolvers) {
        player.showTitle(Title.title(get(titleKey, resolvers), get(subtitleKey, resolvers), TITLE_TIMES));
    }

    // --- Sounds ------------------------------------------------------------

    public void sound(Player player, String key) {
        String name = plugin.getConfig().getString("sounds." + key, "");
        if (name == null || name.isBlank()) return;
        float volume = (float) plugin.getConfig().getDouble("sounds.volume", 1.0);
        try {
            player.playSound(Sound.sound(Key.key(name.trim()), Sound.Source.MASTER, volume, 1.0f));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Ungültiger Sound in der config: " + name);
        }
    }
}
