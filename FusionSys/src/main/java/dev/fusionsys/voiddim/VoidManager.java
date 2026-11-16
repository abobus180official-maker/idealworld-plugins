package dev.fusionsys.voiddim;

import dev.fusionsys.FusionSysPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Измерение Void.
 *
 *  - Мир "void": 1 слой барьеров на высоте floor-y, остальное пустота.
 *  - Масштаб между void и адом 1:1 (координаты переносятся напрямую).
 *  - Игрок попадает в void, если выходит из адского портала в обычный мир
 *    за границей барьера.
 *  - При перемещении в void у входа создаётся возвратный портал обратно в ад.
 *  - Кровати в void не работают, строить нельзя (обрабатывается в listener).
 *  - При выходе последнего игрока из void мир пересоздаётся (сброс построек).
 */
public class VoidManager {

    private final FusionSysPlugin plugin;
    private World voidWorld;
    private boolean enabled;
    private String voidName;
    private double scale;
    private int floorY;
    private final Map<UUID, Location> returnPortals = new HashMap<>(); // uuid -> ад-точка куда вернуться

    public VoidManager(FusionSysPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        var c = plugin.getConfig().getConfigurationSection("void");
        if (c == null) { enabled = false; return; }
        enabled = c.getBoolean("enabled", true);
        voidName = c.getString("world-name", "void");
        scale = c.getDouble("scale-from-nether", 1.0);
        floorY = c.getInt("floor-y", 64);
        if (!enabled) return;
        ensureWorld();
    }

    private void ensureWorld() {
        voidWorld = plugin.getServer().getWorld(voidName);
        if (voidWorld == null) {
            WorldCreator wc = WorldCreator.name(voidName);
            // Пустой мир через собственный генератор: НЕ WorldType.FLAT (падает с
            // `No key layers in MapLike[{}]` и вешает сервер) и НЕ generator("void")
            // (это имя плагина-генератора, а не встроенный void).
            wc.environment(World.Environment.THE_END);
            wc.generator(new VoidChunkGenerator());
            wc.generateStructures(false);
            voidWorld = plugin.getServer().createWorld(wc);
            if (voidWorld != null) {
                // небольшая стартовая площадка из барьеров (не весь радиус — иначе
                // сервер виснет на заполнении 131k блоков синхронно при загрузке)
                plugin.getServer().getScheduler().runTask(plugin, this::buildSpawnPlatform);
                plugin.getLogger().info("FusionSys: мир Void создан: " + voidName);
            }
        }
    }

    /** Строит платформу барьеров 32x32 вокруг спауна мира. */
    private void buildSpawnPlatform() {
        if (voidWorld == null) return;
        int half = 16;
        int sx = voidWorld.getSpawnLocation().getBlockX();
        int sz = voidWorld.getSpawnLocation().getBlockZ();
        for (int x = sx - half; x <= sx + half; x++) {
            for (int z = sz - half; z <= sz + half; z++) {
                voidWorld.getBlockAt(x, floorY, z).setType(Material.BARRIER, false);
            }
        }
    }

    public World getVoidWorld() { return voidWorld; }
    public boolean isVoidWorld(World w) { return voidWorld != null && voidWorld.equals(w); }
    public boolean enabled() { return enabled; }
    public int getFloorY() { return floorY; }

    /** Истина, если ад-точка попадает за границу барьера (надо в void). */
    public boolean isOverworldExport(Location overworldLoc) {
        return plugin.getWorldBarrier().isOutside(overworldLoc);
    }

    /** Перемещает игрока из ала в void, сохраняя придыхание возвратного портала. */
    public void sendToVoid(Player p, Location netherExit) {
        if (voidWorld == null) { ensureWorld(); }
        if (voidWorld == null) return;
        // сохраняем где был ад-портал, чтобы вернуть
        saveNetherReturnFor(p, netherExit);
        // позиция в void: отражаем координаты ad->void (масштаб 1:1), на слой барьера
        Location target = new Location(voidWorld,
                netherExit.getX() * scale,
                floorY + 2.0,
                netherExit.getZ() * scale);
        p.teleport(target);
        // создаём возвратный портал в void (нижний портал обратно в ад)
        createReturnPortal(target, p.getUniqueId());
        p.sendMessage("§5Ты провалился в Пустоту (Void). Найди портал, чтобы вернуться в ад.");
    }

    /** Возвращает ад-точку, откуда игрок пришёл в void (или null). */
    public Location getNetherReturn(UUID id) {
        return returnPortals.get(id);
    }

    private void saveNetherReturnFor(Player p, Location netherExit) {
        returnPortals.put(p.getUniqueId(), netherExit.clone());
    }

    private void createReturnPortal(Location at, UUID uid) {
        // Строим стандартную рамку портала Нижнего мира 3x3 (внутри 1 блок)
        at = at.clone();
        int bx = at.getBlockX();
        int by = at.getBlockY();
        int bz = at.getBlockZ();
        // рамка 4x4 (стены)
        for (int x = bx - 1; x <= bx + 2; x++) {
            for (int z = bz - 1; z <= bz + 2; z++) {
                if (x == bx - 1 || x == bx + 2 || z == bz - 1 || z == bz + 2) {
                    for (int y = by; y <= by + 2; y++) {
                        voidWorld.getBlockAt(x, y, z).setType(Material.OBSIDIAN, false);
                    }
                }
            }
        }
        // активируем внутренность рамки (портал) — нижний ряд
        for (int x = bx; x <= bx + 1; x++) {
            voidWorld.getBlockAt(x, by, bz).setType(Material.FIRE, true);
        }
    }

    /** Сброс мира при выходе последнего игрока. */
    public void resetIfLast() {
        if (voidWorld == null) return;
        int players = voidWorld.getPlayers().size();
        if (players <= 0) {
            // мир пуст — пересоздаём (сбрасывает построенные базы)
            Bukkit.getLogger().info("FusionSys: Void пуст, пересоздаю мир.");
            unloadAndReset();
        }
    }

    private void unloadAndReset() {
        String name = voidName;
        Bukkit.unloadWorld(voidWorld, false);
        voidWorld = null;
        returnPortals.clear();
        // удалить папку мира
        java.io.File dir = new java.io.File(Bukkit.getWorldContainer(), name);
        if (dir.exists()) {
            deleteRecursive(dir);
        }
        ensureWorld();
        plugin.getLogger().info("FusionSys: Void сброшен заново.");
    }

    private void deleteRecursive(java.io.File f) {
        if (f.isDirectory()) {
            java.io.File[] children = f.listFiles();
            if (children != null) for (java.io.File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}