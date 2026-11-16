package dev.fusionsys.potion;

import dev.fusionsys.FusionSysPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Color;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Зелья уменьшения и увеличения.
 * Реализуются как особые зелье, помеченные в PersistentData плагина:
 *  - fusion_shrink_level / fusion_grow_level — признак зелья.
 *  - Варка: зелье увеличения варится из мембраны фантома (potion grow), а зелье
 *    уменьшения — из зелья увеличения и паучьего глаза (квестовый крафт-обмен в listener).
 *  - При употреблении: меняет масштаб модели через атрибут GENERIC_SCALE;
 *    блокирует булаву на время эффекта.
 *
 *  Внимание: сам ванильный атрибут GENERIC_SCALE в 1.21.1 доступен как SCALE (generic.scale).
 *
 *  Эффект хранится как длительность в данных игрока и обрабатывается в тике.
 */
public class SizePotionListener implements Listener {

    private final FusionSysPlugin plugin;

    // теги зелья
    public static final String SHRINK_KEY = "fusion_shrink";
    public static final String GROW_KEY = "fusion_grow";

    // состояние размеров игроков (пока активен эффект): uuid -> [scale, endTick]
    private final Map<UUID, double[]> activeScale = new HashMap<>();

    public SizePotionListener(FusionSysPlugin plugin) {
        this.plugin = plugin;
    }

    /** Текущий активный масштаб игрока (1.0 = обычный). */
    public double getScale(UUID id) {
        double[] v = activeScale.get(id);
        return v != null ? v[0] : 1.0;
    }

    public boolean isShrunk(UUID id) { return activeScale.containsKey(id); }

    /** Наторение времени эффекта. Вызывается в тике плагина. */
    public void tick(long currentTick) {
        if (activeScale.isEmpty()) return;
        boolean changed = false;
        for (java.util.Iterator<Map.Entry<UUID, double[]>> it = activeScale.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, double[]> e = it.next();
            double[] v = e.getValue();
            if (currentTick >= v[1]) {
                it.remove();
                changed = true;
                org.bukkit.entity.Player p = plugin.getServer().getPlayer(e.getKey());
                if (p != null && p.isOnline()) {
                    var attr = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
                    if (attr != null) attr.setBaseValue(1.0);
                }
            }
        }
    }

    /**
     * Создаёт зелье-изменение размера.
     * @param grow true = увеличение, false = уменьшение
     */
    public ItemStack createPotion(boolean grow, int level) {
        ItemStack pot = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) pot.getItemMeta();
        meta.setColor(grow ? Color.fromRGB(0x33FF00) : Color.fromRGB(0x33CCCC));
        meta.addCustomEffect(new PotionEffect(grow ? PotionEffectType.STRENGTH : PotionEffectType.WEAKNESS, 1, 0), true);
        String k = grow ? GROW_KEY : SHRINK_KEY;
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, k), PersistentDataType.INTEGER, level);
        meta.setDisplayName(grow ? "Зелье увеличения" : "Зелье уменьшения");
        pot.setItemMeta(meta);
        return pot;
    }

    // -- варка (brewing) --

    /**
     * Варка зелий через варочную стойку:
     *  - Зелье увеличения: бутылка воды + мембрана фантома (phantom_membrane) -> grow-зелье.
     *  - Зелье уменьшения: зелье увеличения + паучий глаз (spider_eye) -> shrink-зелье.
     */
    @EventHandler
    public void onBrew(BrewEvent e) {
        BrewerInventory inv = e.getContents();
        ItemStack ingredient = inv.getIngredient();
        if (ingredient == null) return;

        // ингредиент: мембрана фантома -> зелье увеличения
        if (ingredient.getType() == Material.PHANTOM_MEMBRANE) {
            boolean any = false;
            for (int i = 0; i < 3; i++) {
                ItemStack slot = inv.getItem(i);
                if (slot == null || slot.getType() != Material.POTION) continue;
                inv.setItem(i, createPotion(true, 1));
                any = true;
            }
            if (any) {
                if (ingredient.getAmount() > 1) ingredient.setAmount(ingredient.getAmount() - 1);
                else inv.setIngredient(null);
                e.setCancelled(true);
            }
            return;
        }

        // ингредиент: паучий глаз -> зелье уменьшения (из зелья увеличения)
        if (ingredient.getType() == Material.SPIDER_EYE) {
            boolean any = false;
            for (int i = 0; i < 3; i++) {
                ItemStack slot = inv.getItem(i);
                if (slot == null || slot.getType() != Material.POTION) continue;
                ItemMeta m = slot.getItemMeta();
                if (m == null) continue;
                Integer g = m.getPersistentDataContainer().get(new NamespacedKey(plugin, GROW_KEY), PersistentDataType.INTEGER);
                if (g == null) continue; // это не наше grow-зелье
                inv.setItem(i, createPotion(false, 1));
                any = true;
            }
            if (any) {
                if (ingredient.getAmount() > 1) ingredient.setAmount(ingredient.getAmount() - 1);
                else inv.setIngredient(null);
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var pdc = meta.getPersistentDataContainer();
        Integer shrink = pdc.get(new NamespacedKey(plugin, SHRINK_KEY), PersistentDataType.INTEGER);
        Integer grow = pdc.get(new NamespacedKey(plugin, GROW_KEY), PersistentDataType.INTEGER);
        if (shrink == null && grow == null) return;

        Player p = e.getPlayer();
        double base = 1.0;
        double factor = 1.0;
        if (shrink != null) {
            factor = plugin.getConfig().getDouble("potions.shrink-scale-factor", 0.6);
        } else if (grow != null) {
            factor = plugin.getConfig().getDouble("potions.grow-scale-factor", 3.0);
        }
        double target = base * factor;

        setPlayerScale(p, target);
        // эффект длится время из конфига; храним [scale, endTick]
        int dur = Math.max(1, plugin.getConfig().getInt(grow != null ? "potions.grow-duration" : "potions.shrink-duration", 30));
        long endTick = plugin.getServer().getCurrentTick() + dur;
        activeScale.put(p.getUniqueId(), new double[]{target, endTick});

        // превращаем в обычное пустое зелье после выпивания
        e.setReplacement(new ItemStack(Material.GLASS_BOTTLE));

        e.setCancelled(true); // чтобы не дать ванильный эффект
    }

    public void setPlayerScale(Player p, double scale) {
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
        if (attr == null) return;
        attr.setBaseValue(scale);
        p.setGlowing(false);
        // сбрасываем экранные пересечения коллизий (1.21.1 client scale is visual; server scale = hitbox)
    }

    public void clearPlayerScale(Player p) {
        activeScale.remove(p.getUniqueId());
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.GENERIC_SCALE);
        if (attr != null) attr.setBaseValue(1.0);
    }

    /** true, если игрок не может использовать булаву (под эффектом уменьшения). */
    public boolean cannotUseMace(Player p) {
        return activeScale.containsKey(p.getUniqueId()) || isShrunk(p.getUniqueId());
    }

    /** Урон, наносимый уменьшенным игроком булавой, отменяется (блокировка булавы). */
    @EventHandler
    public void onMaceAttack(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        if (!cannotUseMace(p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() == Material.MACE) {
            e.setCancelled(true);
            p.sendActionBar("§cУменьшенный игрок не может использовать булаву!");
        }
    }
}