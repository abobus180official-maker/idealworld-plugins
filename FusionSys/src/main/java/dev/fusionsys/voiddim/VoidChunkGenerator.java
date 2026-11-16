package dev.fusionsys.voiddim;

import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Чисто-пустой генератор для мира Void: не создаёт ни одного блока (кроме
 * платформы, которую плагин ставит отдельно). Не использует WorldType.FLAT,
 * поэтому не падает с `No key layers in MapLike[{}]` и не вешает сервер.
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    @NotNull
    public ChunkData generateChunkData(@NotNull World world, @NotNull Random random,
                                        int chunkX, int chunkZ, @NotNull BiomeGrid biome) {
        // пустой чанк — вообще ничего не генерируем
        return createChunkData(world);
    }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return true;
    }

    @Override
    @Nullable
    public List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return Collections.emptyList();
    }

    @Override
    public boolean shouldGenerateStructures() { return false; }
    @Override
    public boolean shouldGenerateMobs() { return false; }
    @Override
    public boolean shouldGenerateNoise() { return false; }
    @Override
    public boolean shouldGenerateSurface() { return false; }
    @Override
    public boolean shouldGenerateBedrock() { return false; }
    @Override
    public boolean shouldGenerateCaves() { return false; }
    @Override
    public boolean shouldGenerateDecorations() { return false; }
}