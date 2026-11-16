package dev.fusionsys.race;

import dev.fusionsys.FusionSysPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Система рас. Каждая раса — отдельный файл plugins/FusionSys/races/<name>.yml.
 * В файле перечисляются никнеймы игроков, принадлежащих расе. Игрок получает
 * расу при входе по своему никнейму (сам выбрать не может).
 *
 * Структура файла расы:
 *   display-name: "&eЧеловек"
 *   base-scale: 1.0
 *   grow-scale-multiplier: 1.0
 *   shrink-scale-multiplier: 1.0
 *   metal-percent: 0
 *   buffs: ["SPEED:0:999999"]
 *   debuffs: []
 *   players: ["ZergLord", "Alex"]
 */
public class RaceManager implements Listener {

    private final FusionSysPlugin plugin;
    private final Map<String, Race> races = new LinkedHashMap<>();
    private final Map<UUID, String> activeRace = new HashMap<>();
    private String defaultRace = "human";

    public RaceManager(FusionSysPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        races.clear();
        activeRace.clear();
        defaultRace = plugin.getConfig().getString("races.default-race", "human");

        File dir = new File(plugin.getDataFolder(), "races");
        if (!dir.exists()) {
            dir.mkdirs();
            // создаём пример расы
            saveSampleRace(dir);
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml") || n.endsWith(".yaml"));
        if (files == null) return;
        for (File f : files) {
            String baseName = f.getName().substring(0, f.getName().lastIndexOf('.'));
            FileConfiguration yml = YamlConfiguration.loadConfiguration(f);
            Race r = parseRace(baseName, yml);
            if (r != null) races.put(r.name, r);
        }
        plugin.getLogger().info("FusionSys: загружено рас: " + races.size());
    }

    private Race parseRace(String name, FileConfiguration yml) {
        Race r = new Race(name);
        r.displayName = yml.getString("display-name", name);
        r.baseScale = yml.getDouble("base-scale", 1.0);
        r.growScaleMultiplier = yml.getDouble("grow-scale-multiplier", 1.0);
        r.shrinkScaleMultiplier = yml.getDouble("shrink-scale-multiplier", 1.0);
        r.metalPercent = yml.getInt("metal-percent", 0);
        r.buffs = yml.getStringList("buffs");
        r.debuffs = yml.getStringList("debuffs");
        r.players = yml.getStringList("players");
        return r;
    }

    private void saveSampleRace(File dir) {
        File f = new File(dir, "human.yml");
        if (!f.exists()) {
            YamlConfiguration yml = new YamlConfiguration();
            yml.set("display-name", "&eЧеловек");
            yml.set("base-scale", 1.0);
            yml.set("metal-percent", 0);
            yml.set("buffs", List.of("SPEED:0:999999"));
            yml.set("debuffs", List.of());
            yml.set("players", List.of("Steve"));
            try { yml.save(f); } catch (Exception ignored) {}
        }
    }

    public Race getRace(String name) { return races.get(name); }

    public java.util.Set<String> getRaceNames() { return races.keySet(); }

    public Race getDefaultRace() {
        return races.getOrDefault(defaultRace, races.values().stream().findFirst().orElse(null));
    }

    /** Раса игрока, определённая по его никнейму (без учёта регистра). */
    public Race raceOf(Player p) {
        String n = p.getName();
        for (Race r : races.values()) {
            if (r.hasPlayer(n)) return r;
        }
        // если игрок не прописан ни в одной расе — дефолтная
        return getDefaultRace();
    }

    /** true, если игрок прописан в какой-то расе. */
    public boolean isAssigned(Player p) {
        String n = p.getName();
        for (Race r : races.values()) if (r.hasPlayer(n)) return true;
        return false;
    }

    public String raceName(Player p) {
        Race r = raceOf(p);
        return r != null ? r.name : "";
    }

    /** % металла игрока (0-100). */
    public double getMetalPercent(Player p) {
        Race r = raceOf(p);
        return r != null ? r.metalPercent : 0;
    }

    /** Назначить расу по никнейму (управление). Добавляет ник в players расы. */
    public void assignPlayer(String playerName, String raceName) {
        Race target = races.get(raceName);
        if (target == null) return;
        // убрать из остальных рас
        for (Race r : races.values()) {
            if (r != target) r.players.removeIf(x -> x != null && x.equalsIgnoreCase(playerName));
        }
        if (!target.hasPlayer(playerName)) {
            target.players.add(playerName);
        }
        saveRace(target);
    }

    private void saveRace(Race r) {
        File f = new File(new File(plugin.getDataFolder(), "races"), r.name + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("display-name", r.displayName);
        yml.set("base-scale", r.baseScale);
        yml.set("grow-scale-multiplier", r.growScaleMultiplier);
        yml.set("shrink-scale-multiplier", r.shrinkScaleMultiplier);
        yml.set("metal-percent", r.metalPercent);
        yml.set("buffs", r.buffs);
        yml.set("debuffs", r.debuffs);
        yml.set("players", r.players);
        try { yml.save(f); } catch (Exception ignored) {}
    }

    /** Применяет масштаб игрока (настоящий рост расы). */
    public void applyRaceScale(Player p) {
        Race r = raceOf(p);
        if (r == null) return;
        var attr = p.getAttribute(Attribute.GENERIC_SCALE);
        if (attr != null) attr.setBaseValue(r.baseScale);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        activeRace.put(p.getUniqueId(), raceName(p));
        Bukkit.getScheduler().runTaskLater(plugin, () -> applyRaceScale(p), 20L);
    }

    // --- быстрый тик: чистка офлайн игроков ---
    public void tick() {
        activeRace.entrySet().removeIf(e -> {
            Player pl = plugin.getServer().getPlayer(e.getKey());
            return pl == null || !pl.isOnline();
        });
    }

    /** Медленный тик: выдаём скрытые баффы/дебаффы и эффекты сознания. */
    public void tickSlow() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Race r = raceOf(p);
            if (r == null || !isAssigned(p)) continue; // баффы только прописанным
            for (String b : r.buffs) applyHiddenEffect(p, b);
            for (String b : r.debuffs) applyHiddenEffect(p, b);
            applyMindEffects(p, r.metalPercent);
        }
    }

    /** Применяет скрытый эффект вида "NAME:level:duration" (тики). */
    private void applyHiddenEffect(Player p, String spec) {
        try {
            String[] parts = spec.split(":");
            PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase());
            if (type == null) return;
            int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int dur = parts.length > 2 ? Integer.parseInt(parts[2]) : 999999;
            if (dur > 30000) dur = 30000;
            PotionEffect eff = new PotionEffect(type, dur, level, true, false);
            if (!p.hasPotionEffect(type) || p.getPotionEffect(type).getAmplifier() <= level) {
                p.addPotionEffect(eff, true);
            }
        } catch (Exception ignored) {}
    }

    /** Эффекты металла на сознание: темнота/туман/помехи, пропорционально %. */
    private void applyMindEffects(Player p, int metal) {
        if (metal <= 0) return;
        if (metal >= 90) {
            if (Math.random() < 0.3) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false), true);
        } else if (metal >= 60) {
            if (Math.random() < 0.2) p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, true, false), true);
        } else if (metal >= 30) {
            if (Math.random() < 0.1) p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20, 0, true, false), true);
        }
    }
}