package de.chunkclaim;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Ein geclaimter Chunk mit Besitzer, Vertrauten und Einstellungen. */
public final class Claim {

    private final ChunkKey key;
    private final UUID owner;
    private String ownerName;
    private final int paid;
    private final long claimedAt;
    private final Map<UUID, String> trusted = new LinkedHashMap<>();
    private final EnumMap<Flag, Boolean> flags = new EnumMap<>(Flag.class);

    public Claim(ChunkKey key, UUID owner, String ownerName, int paid, long claimedAt) {
        this.key = key;
        this.owner = owner;
        this.ownerName = ownerName;
        this.paid = paid;
        this.claimedAt = claimedAt;
    }

    public ChunkKey key() { return key; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public void setOwnerName(String name) { this.ownerName = name; }
    public int paid() { return paid; }
    public long claimedAt() { return claimedAt; }

    public boolean isOwner(Player player) {
        return owner.equals(player.getUniqueId());
    }

    // --- Vertraute -------------------------------------------------------

    public boolean isTrusted(UUID id) {
        return trusted.containsKey(id);
    }

    public Map<UUID, String> trusted() {
        return Collections.unmodifiableMap(trusted);
    }

    public void trust(UUID id, String name) {
        trusted.put(id, name);
    }

    public void untrust(UUID id) {
        trusted.remove(id);
    }

    public UUID findTrusted(String name) {
        for (Map.Entry<UUID, String> entry : trusted.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        }
        return null;
    }

    // --- Einstellungen für Fremde ---------------------------------------

    public boolean flag(Flag flag) {
        return flags.getOrDefault(flag, false);
    }

    public void setFlag(Flag flag, boolean value) {
        flags.put(flag, value);
    }
}
