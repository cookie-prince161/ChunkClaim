package de.chunkclaim;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Verwaltet alle Claims: Speichern, Laden, Preise, Rechteprüfung, Bezahlung mit Items. */
public final class ClaimManager {

    private final ChunkClaimPlugin plugin;
    private final File file;
    private final Map<ChunkKey, Claim> claims = new HashMap<>();
    private final Set<UUID> bypassOn = new HashSet<>();
    private final Set<UUID> bypassOff = new HashSet<>();

    public ClaimManager(ChunkClaimPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
    }

    // --- Abfragen ----------------------------------------------------------

    public Claim get(ChunkKey key) { return claims.get(key); }
    public Claim get(Block block) { return claims.get(ChunkKey.of(block)); }
    public Claim get(Location location) { return claims.get(ChunkKey.of(location)); }

    public Collection<Claim> all() {
        return Collections.unmodifiableCollection(claims.values());
    }

    public List<Claim> claimsOf(UUID owner) {
        return claims.values().stream()
                .filter(claim -> claim.owner().equals(owner))
                .sorted(Comparator.comparingLong(Claim::claimedAt))
                .toList();
    }

    public int countOf(UUID owner) {
        return (int) claims.values().stream().filter(claim -> claim.owner().equals(owner)).count();
    }

    // --- Einstellungen aus der config.yml ---------------------------------

    public int maxPerPlayer() {
        return plugin.getConfig().getInt("claims.max-per-player", 16);
    }

    public boolean isClaimableWorld(World world) {
        return plugin.getConfig().getStringList("claims.allowed-worlds").contains(world.getName());
    }

    public Material currency() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("prices.currency", "DIAMOND"));
        return material != null ? material : Material.DIAMOND;
    }

    /** Preis für den nächsten Chunk eines Spielers, der schon {@code owned} Chunks besitzt. */
    public int priceForNext(int owned) {
        int n = owned + 1;
        int price = 0;
        for (Map<?, ?> tier : plugin.getConfig().getMapList("prices.tiers")) {
            price = number(tier.get("price"));
            if (n <= number(tier.get("up-to"))) return price;
        }
        return price;
    }

    public int refundFor(Claim claim) {
        return claim.paid() * plugin.getConfig().getInt("prices.refund-percent", 50) / 100;
    }

    public boolean freeForAdmins() {
        return plugin.getConfig().getBoolean("prices.free-for-admins", false);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    // --- Rechte ------------------------------------------------------------

    public boolean hasBypass(Player player) {
        if (!player.hasPermission("chunkclaim.admin")) return false;
        UUID id = player.getUniqueId();
        if (bypassOn.contains(id)) return true;
        if (bypassOff.contains(id)) return false;
        return plugin.getConfig().getBoolean("admin.bypass-by-default", true);
    }

    public void setBypass(Player player, boolean enabled) {
        UUID id = player.getUniqueId();
        if (enabled) {
            bypassOn.add(id);
            bypassOff.remove(id);
        } else {
            bypassOff.add(id);
            bypassOn.remove(id);
        }
    }

    /** Darf der Spieler die Aktion (abbauen/bauen/benutzen) in diesem Claim ausführen? */
    public boolean canAccess(Player player, Claim claim, Flag flag) {
        return claim.isOwner(player)
                || claim.isTrusted(player.getUniqueId())
                || claim.flag(flag)
                || hasBypass(player);
    }

    /** Darf der Spieler Einstellungen und Zugriff dieses Claims ändern? */
    public boolean canManage(Player player, Claim claim) {
        return claim.isOwner(player) || hasBypass(player);
    }

    // --- Ändern ------------------------------------------------------------

    public Claim create(ChunkKey key, Player owner, int paid) {
        Claim claim = new Claim(key, owner.getUniqueId(), owner.getName(), paid, System.currentTimeMillis());
        for (Flag flag : Flag.values()) {
            claim.setFlag(flag, plugin.getConfig().getBoolean("defaults." + flag.id(), false));
        }
        claims.put(key, claim);
        save();
        return claim;
    }

    public void remove(Claim claim) {
        claims.remove(claim.key());
        save();
    }

    // --- Items (Bezahlung) -------------------------------------------------

    public int countItems(Player player, Material material) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
        }
        return total;
    }

    public void removeItems(Player player, Material material, int amount) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            int take = Math.min(stack.getAmount(), remaining);
            remaining -= take;
            if (stack.getAmount() - take <= 0) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
        }
        inventory.setStorageContents(contents);
    }

    /** Gibt Items zurück; was nicht ins Inventar passt, fällt auf den Boden. */
    public void giveItems(Player player, Material material, int amount) {
        int remaining = amount;
        int stackSize = material.getMaxStackSize();
        while (remaining > 0) {
            int part = Math.min(stackSize, remaining);
            remaining -= part;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, part));
            for (ItemStack stack : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            }
        }
    }

    // --- Speichern / Laden -------------------------------------------------

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int index = 0;
        for (Claim claim : claims.values()) {
            String path = "claims.c" + index++;
            yaml.set(path + ".world", claim.key().world());
            yaml.set(path + ".x", claim.key().x());
            yaml.set(path + ".z", claim.key().z());
            yaml.set(path + ".owner", claim.owner().toString());
            yaml.set(path + ".owner-name", claim.ownerName());
            yaml.set(path + ".paid", claim.paid());
            yaml.set(path + ".claimed-at", claim.claimedAt());
            for (Flag flag : Flag.values()) {
                yaml.set(path + ".flags." + flag.id(), claim.flag(flag));
            }
            for (Map.Entry<UUID, String> entry : claim.trusted().entrySet()) {
                yaml.set(path + ".trusted." + entry.getKey(), entry.getValue());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            File temp = new File(plugin.getDataFolder(), "claims.yml.tmp");
            yaml.save(temp);
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().severe("claims.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    public void load() {
        claims.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("claims");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            try {
                ChunkKey key = new ChunkKey(section.getString("world", "world"), section.getInt("x"), section.getInt("z"));
                Claim claim = new Claim(
                        key,
                        UUID.fromString(section.getString("owner", "")),
                        section.getString("owner-name", "?"),
                        section.getInt("paid"),
                        section.getLong("claimed-at"));

                ConfigurationSection flags = section.getConfigurationSection("flags");
                for (Flag flag : Flag.values()) {
                    claim.setFlag(flag, flags != null && flags.getBoolean(flag.id(), false));
                }
                ConfigurationSection trusted = section.getConfigurationSection("trusted");
                if (trusted != null) {
                    for (String uuid : trusted.getKeys(false)) {
                        claim.trust(UUID.fromString(uuid), trusted.getString(uuid, "?"));
                    }
                }
                claims.put(key, claim);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Kaputter Eintrag in claims.yml übersprungen: " + id);
            }
        }
    }
}
