package dev.coords;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

/**
 * Сетевой мост отдельного мода coords-scramble.
 *
 * Канал "coords:presence":
 *   sub 0x00 PRESENCE — клиент сообщает серверу, что нужный мод установлен (при входе).
 *   sub 0x01 CHEAT    — клиент сообщает имя обнаруженного чит-клиента.
 *
 * Только C2S (клиент → сервер). Серверная проверка на отсутствие PRESENCE → кик;
 * на CHEAT → бан на 5 минут.
 */
public final class CoordsNetwork {

    public static final String CHANNEL = "coords:presence";
    public static final Identifier CHANNEL_ID = Identifier.of("coords", "presence");

    public static final int PRESENCE = 0x00;
    public static final int CHEAT = 0x01;

    public static final CustomPayload.Id<CoordsPayload> PAYLOAD_ID = CoordsPayload.ID;

    public static final PacketCodec<RegistryByteBuf, CoordsPayload> CODEC = CoordsPayload.CODEC;

    public record CoordsPayload(byte[] data) implements CustomPayload {
        public static final Id<CoordsPayload> ID = new Id<>(CHANNEL_ID);
        public static final PacketCodec<RegistryByteBuf, CoordsPayload> CODEC =
                PacketCodec.of(CoordsPayload::write, CoordsPayload::read);

        private static void write(CoordsPayload p, RegistryByteBuf buf) {
            buf.writeBytes(p.data());
        }

        private static CoordsPayload read(RegistryByteBuf buf) {
            int len = buf.readableBytes();
            byte[] data = new byte[len];
            buf.readBytes(data);
            return new CoordsPayload(data);
        }

        @Override
        public Id<CoordsPayload> getId() {
            return ID;
        }
    }

    private CoordsNetwork() {}

    private static boolean canSend() {
        return ClientPlayNetworking.canSend(CHANNEL_ID);
    }

    private static void send(int sub, PayloadWriter writer) {
        if (!canSend()) return;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            out.writeByte(sub);
            if (writer != null) writer.write(out);
            out.flush();
            ClientPlayNetworking.send(new CoordsPayload(bos.toByteArray()));
        } catch (Exception e) {
            CoordsScrambleMod.LOGGER.warn("[CoordsScramble] Ошибка отправки: " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface PayloadWriter {
        void write(DataOutputStream out) throws Exception;
    }

    /** Сообщаем серверу о наличии мода координат. */
    public static void sendPresence() {
        send(PRESENCE, out -> out.writeUTF(CoordsScrambleMod.MOD_ID + ":" + "installed"));
    }

    /** Сообщаем серверу имя обнаруженного чит-клиента. */
    public static void sendCheatDetected(String cheatName) {
        send(CHEAT, out -> out.writeUTF(cheatName));
    }
}