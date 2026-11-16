package dev.fusionsys.barrier;

import dev.fusionsys.FusionSysPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Динамический барьер — зона разрешённой территории.
 *
 *  - Первые 30 блоков от границы (вглубь): искажение картинки (эффект Nausea/вьюзер),
 *    элитра не работает (подъём отключается), внутренняя стена не пропускает наружу.
 *  - Глубже за барьером (на death-zone блоков): на игрока накладывается темнота,
 *    затем он умирает «по неизвестной причине» (без источника урона).
 *
 * Граница: окружность вокруг центра (center-x/z, radius). Барьер не даёт выйти
 * за пределы разрешённой территории.
 */
public class WorldBarrier implements Listener {

    private final FusionSysPlugin plugin;
    private final Map<UUID, Integer> deathPunish = new HashMap<>(); // мёртвые игроки: тик до смерти

    private int centerX, centerZ, radius, distortion, deathZone;
    private String worldName;

    public WorldBarrier(FusionSysPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        var c = plugin.getConfig().getConfigurationSection("barrier");
        if (c == null) return;
        centerX = c.getInt("center-x", 0);
        centerZ = c.getInt("center-z", 0);
        radius = c.getInt("radius", 256);
        distortion = c.getInt("distortion-zone", 30);
        deathZone = c.getInt("death-zone", 8);
        worldName = c.getString("world", "world");
    }

    public boolean isBarrierWorld(World w) {
        if (worldName == null || worldName.isBlank()) return true;
        return w.getName().equalsIgnoreCase(worldName);
    }

    private double inCircleZ(Location loc) {
        double dx = loc.getX() - centerX;
        double dz = loc.getZ() - centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Открытый доступ: проверка, находится ли точка за границей барьера. */
    public boolean isOutside(Location loc) {
        return inCircleZ(loc) > radius;
    }

    public int getRadius() { return radius; }
    public int getCenterX() { return centerX; }
    public int getCenterZ() { return centerZ; }

    private boolean outside(Location loc) {
        return isOutside(loc);
    }

    /** Смещение за барьер = насколько глубже границы находится игрок. */
    private double beyondDist(Location loc) {
        return inCircleZ(loc) - radius;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (!isBarrierWorld(p.getWorld())) return;
        // Движение за барьером НЕ ограничивается: игрок свободно проходит наружу.
        // Наказание (искажение, элитра, темнота, смерть) обрабатывает tickSlow.
        // Здесь — только искажение картинки при приближении к границе изнутри.
        Location to = e.getTo();
        double beyond = beyondDist(to);
        if (beyond >= -distortion && beyond < 0) {
            applyDistortion(p);
        } else {
            deathPunish.remove(p.getUniqueId());
        }
    }

    /** Искажение картинки (шейдероэффект): лёгкая тошнота/вибрация + сообщение. */
    private void applyDistortion(Player p) {
        // искажение реализуется эффектом NAUSEA (вибрация экрана), как шейдер
        if (!p.hasPotionEffect(PotionEffectType.NAUSEA)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, true, false), true);
            p.sendMessage(plugin.getConfig().getString("barrier.distort-message", "&7...пространство вокруг изгибается...").replace('&', '§'));
        }
    }

    /** Медленный тик: отключение элитры и темнота+смерть глубже за барьером. */
    public void tickSlow() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (!isBarrierWorld(p.getWorld())) continue;
            Location loc = p.getLocation();
            double beyond = beyondDist(loc);

            if (outside(loc)) {
                // за барьером
                if (beyond <= deathZone) {
                    // ещё в слепой зоне смерти: темнота
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false), true);
                    // убиваем по «неизвестной причине»
                    deathPunish.put(p.getUniqueId(), 20); // 20 тиков до смерти
                } else {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false), true);
                }
                // элитра за барьером не работает — отключаем глидинг
                if (p.isGliding()) p.setGliding(false);
            } else if (beyond >= -distortion) {
                // в зоне искажения (внутри, 30 блоков): элитра отключается
                if (p.isGliding()) p.setGliding(false);
            }

            // обрабатываем «неизвестную смерть»
            Integer cnt = deathPunish.get(p.getUniqueId());
            if (cnt != null) {
                deathPunish.put(p.getUniqueId(), cnt - 1);
                if (cnt <= 0) {
                    p.damage(10000.0); // защита от причины — количество не отображается
                    deathPunish.remove(p.getUniqueId());
                }
            }
        }
    }
}