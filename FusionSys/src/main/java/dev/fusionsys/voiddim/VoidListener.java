package dev.fusionsys.voiddim;

import dev.fusionsys.FusionSysPlugin;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Слушатель измерения Void:
 *  - Если игрок выходит из адского портала в обычный мир за границей барьера —
 *    переносим в void (вместо обычного мира).
 *  - Возвратный портал из void ведёт обратно в ад.
 *  - В void запрещено строить и ложиться в кровать.
 *  - При выходе последнего игрока void пересоздаётся.
 */
public class VoidListener implements Listener {

    private final FusionSysPlugin plugin;
    private final VoidManager vm;

    public VoidListener(FusionSysPlugin plugin) {
        this.plugin = plugin;
        this.vm = plugin.getVoidManager();
    }

    /** Перехватываем перемещение в обычный мир через портал (из ада). */
    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        Player p = e.getPlayer();
        if (!vm.enabled() || vm.getVoidWorld() == null) return;
        Location from = e.getFrom();
        World fromWorld = from.getWorld();
        Location to = e.getTo();

        // Возврат из void в ад: идём через портал внутрь void -> вернуться в исходный ад-портал
        if (vm.isVoidWorld(fromWorld)) {
            Location ret = vm.getNetherReturn(p.getUniqueId());
            if (ret != null && ret.getWorld() != null && ret.getWorld().getEnvironment().equals(World.Environment.NETHER)) {
                e.setCancelled(true);
                p.teleport(ret);
                p.sendMessage("§5Ты вернулся в Нижний мир.");
            }
            return;
        }

        if (to == null || to.getWorld() == null) return;
        String toName = to.getWorld().getName();
        String voidName = plugin.getConfig().getString("void.world-name", "void");
        if (toName.equalsIgnoreCase(voidName)) return;
        // только если переход из ада (нижнего мира) в обычный
        if (fromWorld != null && !fromWorld.getEnvironment().equals(World.Environment.NETHER)) return;
        if (to.getWorld().getEnvironment().equals(World.Environment.NORMAL) && to.getWorld().getName().equalsIgnoreCase("world")) {
            // выходит в обычный мир — проверяем, за границей ли барьера точка
            if (plugin.getWorldBarrier().isOutside(to)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> vm.sendToVoid(p, from));
                e.setCancelled(true);
                p.sendMessage("§5Портал привёл за границу барьера — тебя затянуло в Пустоту.");
            }
        }
    }

    /** Запрет строить и ломать в void. */
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (vm.isVoidWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cВ Пустоте нечего и нельзя строить.");
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (vm.isVoidWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    /** Кровати в void не работают. */
    @EventHandler
    public void onBed(PlayerBedEnterEvent e) {
        if (vm.isVoidWorld(e.getBed().getWorld())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cВ Пустоте нельзя спать.");
        }
    }

    /** Идентификация начальных точек: защита от вейд (урон пустоты). */
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && vm.getVoidWorld() != null
                && vm.isVoidWorld(p.getWorld())) {
            // в void нет ванильного выпадения в пустоту (слой барьеров), но страховка
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID
                    || e.getCause() == EntityDamageEvent.DamageCause.FALL) {
                // возвращаем на слой
                p.teleport(new Location(vm.getVoidWorld(), p.getLocation().getX(), vm.getFloorY() + 2, p.getLocation().getZ()));
                e.setCancelled(true);
            }
        }
    }

    /** Выход игрока — сброс void если никого не осталось. */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BukkitScheduler_run();
    }

    /** Смена мира (включая вход в void) — проверить сброс, если не осталось. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        BukkitScheduler_run();
    }

    private void BukkitScheduler_run() {
        plugin.getServer().getScheduler().runTaskLater(plugin, vm::resetIfLast, 40L);
    }
}