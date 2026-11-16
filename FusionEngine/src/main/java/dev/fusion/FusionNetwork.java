package dev.fusion;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.UUID;

/**
 * Сетевой мост между клиентским модом и серверным Paper-плагином.
 * Канал plugin-messaging "fusion:engine", совместимый с Bukkit PluginMessageListener.
 *
 * Буфер клиента: байт = subchannel, затем полезная нагрузка.
 * Сервер читает тот же формат (AdminNetworkListener).
 */
public final class FusionNetwork {

	public static final String CHANNEL = "fusion:engine";
	public static final Identifier CHANNEL_ID = Identifier.of("fusion", "engine");

	// subchannels — должны совпадать с плагином
	public static final int SYNC_STAND = 0x01;
	public static final int SWAP_SLOTS = 0x02;
	public static final int RENAME = 0x03;
	public static final int GROUP_UPDATE = 0x04;
	public static final int RUN_SCENARIO = 0x05;
	public static final int STOP_SCENARIO = 0x06;
	public static final int HANDSHAKE = 0x07;
	public static final int SUMMON_METEOR = 0x08;
	public static final int POSSESS_MOVE = 0x09;
	public static final int POSSESS_ACTION = 0x0A;
	public static final int POSSESS_EXIT = 0x0B;
	public static final int POSSESS_BUTTON = 0x0C;

	// CoreProtect GUI — C2S запросы
	public static final int CO_LOOKUP_BLOCK = 0x20;
	public static final int CO_LOOKUP_NEAR = 0x21;
	public static final int CO_ROLLBACK = 0x22;
	public static final int CO_RESTORE = 0x23;
	// S2C результат (сервер → клиент)
	public static final int CO_RESULT = 0x40;

	private static boolean serverCompatible = false;

	@FunctionalInterface
	private interface PacketWriter { void write(java.io.DataOutputStream out) throws Exception; }

	private FusionNetwork() {}

	public static boolean isServerCompatible() { return serverCompatible; }

	public static void setServerCompatible(boolean v) { serverCompatible = v; }

	public static boolean canSend() {
		return ClientPlayNetworking.canSend(CHANNEL_ID);
	}

	/** Сериализует пакет и отправляет как custom payload на канал. */
	private static void send(int sub, PacketWriter writer) {
		if (!canSend()) return;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bos);
			out.writeByte(sub);
			if (writer != null) writer.write(out);
			out.flush();
			byte[] data = bos.toByteArray();
			PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(data));
			ClientPlayNetworking.send(new PluginChannelPayload(data));
		} catch (Exception e) {
			FusionMod.LOGGER.warn("[FusionEngine] Ошибка отправки: " + e.getMessage());
		}
	}

	// --- API для GUI ---

	public static void sendSync(UUID standId, String json) {
		send(SYNC_STAND, out -> {
			out.writeUTF(standId.toString());
			out.writeUTF(json);
		});
	}

	public static void sendSwap(UUID standId, int action) {
		send(SWAP_SLOTS, out -> {
			out.writeUTF(standId.toString());
			out.writeByte(action);
		});
	}

	public static void sendRename(UUID standId, String name) {
		send(RENAME, out -> {
			out.writeUTF(standId.toString());
			out.writeUTF(name);
		});
	}

	public static void sendGroupUpdate(UUID standId, List<String> groups) {
		send(GROUP_UPDATE, out -> {
			out.writeUTF(standId.toString());
			out.writeInt(groups.size());
			for (String g : groups) out.writeUTF(g);
		});
	}

	public static void sendRunScenario(String name) {
		send(RUN_SCENARIO, out -> out.writeUTF(name));
	}

	public static void sendStopScenario(String name) {
		send(STOP_SCENARIO, out -> out.writeUTF(name));
	}

	public static void sendHandshake() {
		send(HANDSHAKE, out -> out.writeInt(1));
	}

	public static void sendSummonMeteor(String coreType) {
		send(SUMMON_METEOR, out -> out.writeUTF(coreType == null ? "magnet" : coreType));
	}

	public static void sendPossessMove(float forward, float strafe, boolean jump) {
		send(POSSESS_MOVE, out -> {
			out.writeFloat(forward);
			out.writeFloat(strafe);
			out.writeBoolean(jump);
		});
	}

	public static void sendPossessAction(int action) {
		send(POSSESS_ACTION, out -> out.writeByte(action));
	}

	public static void sendPossessExit() {
		send(POSSESS_EXIT, out -> {});
	}

	public static void sendPossessButton(int button) {
		send(POSSESS_BUTTON, out -> out.writeByte(button));
	}

	// --- CoreProtect GUI запросы (C2S) ---

	/** Запрос lookup по блоку под прицелом. limit — максимум строк. */
	public static void sendCoLookupBlock(int limit) {
		send(CO_LOOKUP_BLOCK, out -> out.writeInt(limit));
	}

	/** Запрос lookup вокруг игрока. radius, user, timeSec, limit. */
	public static void sendCoLookupNear(int radius, String user, int timeSec, int limit) {
		send(CO_LOOKUP_NEAR, out -> {
			out.writeInt(radius);
			out.writeUTF(user == null ? "" : user);
			out.writeInt(timeSec);
			out.writeInt(limit);
		});
	}

	/** Запрос rollback (restore=false) или restore (true). */
	public static void sendCoRollback(int sub, int radius, String user, int timeSec) {
		send(sub, out -> {
			out.writeInt(radius);
			out.writeUTF(user == null ? "" : user);
			out.writeInt(timeSec);
		});
	}

	// --- пакетный тип (raw custom payload для Bukkit plugin-message) ---

	public record PluginChannelPayload(byte[] data) implements CustomPayload {
		public static final CustomPayload.Id<PluginChannelPayload> ID =
				new CustomPayload.Id<>(CHANNEL_ID);
		public static final PacketCodec<RegistryByteBuf, PluginChannelPayload> CODEC =
				PacketCodec.of(PluginChannelPayload::write, PluginChannelPayload::read);

		private static void write(PluginChannelPayload p, RegistryByteBuf buf) {
			buf.writeBytes(p.data());
		}

		private static PluginChannelPayload read(RegistryByteBuf buf) {
			int len = buf.readableBytes();
			byte[] data = new byte[len];
			buf.readBytes(data);
			return new PluginChannelPayload(data);
		}

		@Override
		public Id<PluginChannelPayload> getId() { return ID; }
	}
}