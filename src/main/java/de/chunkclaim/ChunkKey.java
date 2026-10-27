package de.chunkclaim;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Eindeutige Adresse eines Chunks: Welt + Chunk-Koordinaten. */
public record ChunkKey(String world, int x, int z) {

    public static ChunkKey of(Location loc) {
        return new ChunkKey(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
    }

    public static ChunkKey of(Block block) {
        return new ChunkKey(block.getWorld().getName(), block.getX() >> 4, block.getZ() >> 4);
    }
}
