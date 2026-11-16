package dev.coords.mixin;

import dev.coords.CoordsScrambleMod;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Блокировка серверных ресурспаков, содержащих "xray" в URL или имени пакета.
 * Сервер призывает ресурспак (клиент грузит из сети); если он потенциально xray —
 * отменяем обработку, чтобы пак не применялся. Такой пак удаляется сразу после запуска,
 * т.е. не даём ему загрузиться на клиенте вовсе.
 *
 * Срабатывает в ClientCommonNetworkHandler.onResourcePackSend — точке приёма S2C-пакета
 * ResourcePackSendS2CPacket на клиенте (1.21.1, Yarn-имя).
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ResourcePackMixin {

    @Inject(method = "onResourcePackSend", at = @At("HEAD"), cancellable = true)
    private void coords_blockXrayResourcePack(ResourcePackSendS2CPacket packet, CallbackInfo ci) {
        try {
            String url = packet.url().toLowerCase();
            String name = String.valueOf(packet.hash()).toLowerCase();
            if (url.contains("xray") || name.contains("xray")
                    || url.contains("esp") || url.contains("tracers")) {
                CoordsScrambleMod.LOGGER.warn("[CoordsScramble] Блокируем ресурспак с " +
                        "подозрительным именем (xray): " + packet.url());
                ci.cancel();
            }
        } catch (Throwable t) {
            // Никогда не роняем сетевой обработчик из-за проверки.
            CoordsScrambleMod.LOGGER.warn("[CoordsScramble] Ошибка блокировки ресурспака: " + t.getMessage());
        }
    }
}