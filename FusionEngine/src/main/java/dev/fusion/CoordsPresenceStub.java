package dev.fusion;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Заглушка совместимости с проверкой сервера на наличие мода координат.
 *
 * Канал "coords:presence" использует отдельный мод coords-scramble, а сервер (плагин
 * FusionEngine, CoordsPresenceListener) кикает игрока, не приславшего PRESENCE.
 * FusionEngine САМ подменяет координаты в F3 (F3ScrambleClient + DebugHudMixin), поэтому
 * он легально отвечает на проверку: при входе шлёт PRESENCE на канале coords:presence.
 *
 * Это совместимость, а не обход проверки: мод действительно выполняет подмену координат.
 */
public final class CoordsPresenceStub {

    public static final String CHANNEL = "coords:presence";
    public static final Identifier CHANNEL_ID = Identifier.of("coords", "presence");
    public static final int PRESENCE = 0x00;
    public static final CustomPayload.Id<PresencePayload> PAYLOAD_ID = PresencePayload.ID;

    public record PresencePayload(byte[] data) implements CustomPayload {
        public static final Id<PresencePayload> ID = new Id<>(CHANNEL_ID);
        public static final PacketCodec<RegistryByteBuf, PresencePayload> CODEC =
                PacketCodec.of(PresencePayload::write, PresencePayload::read);

        private static void write(PresencePayload p, RegistryByteBuf buf) {
            buf.writeBytes(p.data());
        }

        private static PresencePayload read(RegistryByteBuf buf) {
            int len = buf.readableBytes();
            byte[] data = new byte[len];
            buf.readBytes(data);
            return new PresencePayload(data);
        }

        @Override
        public Id<PresencePayload> getId() {
            return ID;
        }
    }

    private CoordsPresenceStub() {}

    /** Шлёт PRESENCE (мод на месте) на канал coords:presence. Вызывать при входе на сервер. */
    public static void sendPresence() {
        if (!ClientPlayNetworking.canSend(CHANNEL_ID)) return;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(PRESENCE);
            out.writeUTF("fusion-engine:installed");
            out.flush();
            ClientPlayNetworking.send(new PresencePayload(bos.toByteArray()));
        } catch (Exception e) {
            FusionMod.LOGGER.warn("[FusionEngine] Ошибка отправки presence-заглушки: " + e.getMessage());
        }
    }
}