package dev.fusionsys;

import dev.fusionsys.race.RaceManager;
import dev.fusionsys.potion.SizePotionListener;
import dev.fusionsys.barrier.WorldBarrier;
import dev.fusionsys.voiddim.VoidManager;
import dev.fusionsys.voiddim.VoidListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * FusionSys — дополнение к FusionEngine.
 *
 * ВНИМАНИЕ: кастомные блоки (магнит, метеорит, гематит), предметы (пластинки,
 * картины, инструменты, книги), зачарования и их серверные GUI (заметки,
 * справочник, кисть) ПЕРЕНЕСЕНЫ в плагин FusionEngine (пакет dev.fusion.sys).
 * Здесь остались только системы, тесно связанные с миром/измерениями:
 *   - расы (масштаб, баффы/дебаффы, % металла),
 *   - зелья уменьшения/увеличения,
 *   - динамический барьер,
 *   - измерение Void.
 */
public final class FusionSysPlugin extends JavaPlugin {

    public static FusionSysPlugin INSTANCE;

    private RaceManager raceManager;
    private SizePotionListener potionListener;
    private WorldBarrier worldBarrier;
    private VoidManager voidManager;
    private VoidListener voidListener;

    @Override
    public void onEnable() {
        INSTANCE = this;
        saveDefaultConfig();
        mergeMissingConfig();

        this.raceManager = new RaceManager(this);
        this.potionListener = new SizePotionListener(this);
        this.worldBarrier = new WorldBarrier(this);
        this.voidManager = new VoidManager(this);
        this.voidListener = new VoidListener(this);

        raceManager.reload();
        worldBarrier.reload();
        voidManager.reload();

        getServer().getPluginManager().registerEvents(potionListener, this);
        getServer().getPluginManager().registerEvents(raceManager, this);
        getServer().getPluginManager().registerEvents(worldBarrier, this);
        getServer().getPluginManager().registerEvents(voidListener, this);

        getCommand("fusionsys").setExecutor((sender, cmd, label, args) -> {
            if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                mergeMissingConfig();
                reloadConfig();
                raceManager.reload();
                worldBarrier.reload();
                voidManager.reload();
                sender.sendMessage("§aFusionSys перезагружен.");
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("race") && args.length >= 2) {
                String raceName = args[1];
                String targetName = args.length >= 3 ? args[2] : (sender instanceof org.bukkit.entity.Player sp ? sp.getName() : null);
                if (raceManager.getRace(raceName) == null) {
                    sender.sendMessage("§cРаса не найдена: " + raceName + ". Доступны: " + String.join(", ", raceManager.getRaceNames()));
                    return true;
                }
                if (targetName == null) {
                    sender.sendMessage("§cУкажите никнейм: /fusionsys race <раса> <ник>");
                    return true;
                }
                raceManager.assignPlayer(targetName, raceName);
                sender.sendMessage("§aИгрок " + targetName + " назначен в расу " + raceName + ".");
                return true;
            }
            sender.sendMessage("§cИспользование: /fusionsys <reload|race>");
            return true;
        });

        getServer().getScheduler().runTaskTimer(this, this::tick, 0L, 1L);
        getServer().getScheduler().runTaskTimer(this, this::tickSlow, 0L, 20L);

        getLogger().info("FusionSys включён (расы/барьер/void/зелья). Блоки, предметы и зачарования — в FusionEngine.");
    }

    @Override
    public void onDisable() {
        getLogger().info("FusionSys выключен.");
    }

    private void tick() {
        raceManager.tick();
        potionListener.tick(getServer().getCurrentTick());
    }

    private void tickSlow() {
        raceManager.tickSlow();
        worldBarrier.tickSlow();
    }

    /**
     * Гарантирует наличие всех секций конфига. Если в config.yml нет секции barrier
     * (старый конфиг) — пересоздаём файл из ресурса.
     */
    public void mergeMissingConfig() {
        File cfgFile = new File(getDataFolder(), "config.yml");
        try {
            org.bukkit.configuration.file.YamlConfiguration loaded =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);
            if (!loaded.contains("barrier")) {
                File bak = new File(getDataFolder(), "config.yml.bak");
                if (cfgFile.exists()) {
                    java.nio.file.Files.copy(cfgFile.toPath(), bak.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                saveResource("config.yml", true);
                getLogger().info("FusionSys: config.yml обновлён. Старый сохранён в config.yml.bak");
            }
        } catch (Exception ex) {
            getLogger().warning("Не удалось обновить config.yml: " + ex.getMessage());
        }
    }

    public RaceManager getRaceManager() { return raceManager; }
    public WorldBarrier getWorldBarrier() { return worldBarrier; }
    public VoidManager getVoidManager() { return voidManager; }
}