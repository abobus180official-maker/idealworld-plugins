package dev.fusion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Клиентская инициализация: клавиша-открытие GUI редактора, отправка handshake
 * при входе на сервер и приём ответа о версии протокола.
 */
public class FusionModClient implements ClientModInitializer {

private static KeyBinding controlPanelKey;
	private static KeyBinding possessKey;

	@Override
	public void onInitializeClient() {
		// Бинд для меню управления fusioncore. По умолчанию — Правый Shift (GLFW_KEY_RIGHT_SHIFT).
		controlPanelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fusion.control_panel",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_RIGHT_SHIFT,
				"category.fusion.gui"
		));

		// Бинд вселения в стойку / выхода из неё. По умолчанию — F6 (GLFW_KEY_F6).
		possessKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.fusion.possess",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_F6,
				"category.fusion.gui"
		));

		// Принимаем ответы плагина на нашем канале (handshake).
		ClientPlayNetworking.registerGlobalReceiver(FusionNetwork.PluginChannelPayload.ID,
				(payload, responseSender) -> {
					byte[] data = payload.data();
					try {
						java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(data));
						int sub = in.readUnsignedByte();
						if (sub == FusionNetwork.HANDSHAKE) {
							in.readUTF(); // "server"
							int ver = in.readInt();
							boolean compat = in.readBoolean();
							FusionNetwork.setServerCompatible(compat);
							FusionMod.LOGGER.info("[FusionEngine] Handshake: version=" + ver + " compatible=" + compat);
						} else if (sub == FusionNetwork.CO_RESULT) {
							handleCoreProtectResult(in);
						}
					} catch (Exception e) {
						FusionMod.LOGGER.warn("[FusionEngine] Ответ: " + e.getMessage());
					}
				});

		// При установлении соединения — шлём handshake.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			FusionNetwork.sendHandshake();
			// Заглушка совместимости: мод сам подменяет координаты в F3, поэтому
			// легально отвечает на проверку сервера на канале coords:presence.
			CoordsPresenceStub.sendPresence();
		});

		// Бинд — открыть меню управления при нажатии.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (controlPanelKey.wasPressed()) {
				openControlPanel(client);
			}
			// Вселение: включение (как админ через /fe possess) и выход — отсылаем о входе не может (вход только через команду на сервере),
			// поэтому клавиша здесь = выход при вселении; вход админ делает командой /fe possess.
			if (possessKey.wasPressed()) {
				FusionNetwork.sendPossessExit();
			}
			sendPossessMovement(client);
		});

		// HUD: показываем слоты инвентаря стойки, когда игрок во вселении (camera = ArmorStand).
		HudRenderCallback.EVENT.register((ctx, tick) -> renderStandInventoryHud(ctx, tick));

		FusionMod.LOGGER.info("[FusionEngine] Client ready. Control panel key: Right Shift");
	}

	/** Рендер двух слотов инвентаря стойки (главная + офф-рука) в нижней части экрана. */
	private static void renderStandInventoryHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;
		if (!(client.getCameraEntity() instanceof ArmorStandEntity stand)) return;

		var stack = contextStackOwnerInventory(stand);
		int sw = client.getWindow().getScaledWidth();
		int sh = client.getWindow().getScaledHeight();
		int cx = sw / 2;
		int baseY = sh - 30;

		// фоновая подложка под два слота
		ctx.fill(cx - 70, baseY - 4, cx + 70, baseY + 26, 0x88000000);

		// слот главной руки (слева)
		ctx.getMatrices().push();
		ctx.drawCenteredTextWithShadow(client.textRenderer, stack.mainName, cx - 35, baseY + 5, 0xFFFFFF);
		ctx.getMatrices().pop();

		// слот офф-руки (справа)
		ctx.getMatrices().push();
		ctx.drawCenteredTextWithShadow(client.textRenderer, stack.offName, cx + 35, baseY + 5, 0xFFFFFF);
		ctx.getMatrices().pop();

		// подписи
		ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Рука"), cx - 35, baseY - 12, 0xAAAAAA);
		ctx.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Офф-рука"), cx + 35, baseY - 12, 0xAAAAAA);
	}

	private static StandSlots contextStackOwnerInventory(ArmorStandEntity stand) {
		String mainName = "";
		String offName = "";
		var main = stand.getMainHandStack();
		var off = stand.getOffHandStack();
		if (main != null && !main.isEmpty()) {
			int c = main.getCount();
			mainName = main.getItem().getName(main).getString() + (c > 1 ? " x" + c : "");
		}
		if (off != null && !off.isEmpty()) {
			int c = off.getCount();
			offName = off.getItem().getName(off).getString() + (c > 1 ? " x" + c : "");
		}
		return new StandSlots(mainName, offName);
	}

	private record StandSlots(String mainName, String offName) {}

	private static final int COOLDOWN = 3;
	private static int possessTick = 0;

	private static void sendPossessMovement(MinecraftClient client) {
		if (!FusionNetwork.canSend()) return;
		if (client.player == null) return;
		// Шлём движение WASD постоянно; сервер игнорирует, если игрок не во вселении.
		boolean f = client.options.forwardKey.isPressed();
		boolean b = client.options.backKey.isPressed();
		boolean l = client.options.leftKey.isPressed();
		boolean r = client.options.rightKey.isPressed();
		float forward = (f ? 1f : 0f) - (b ? 1f : 0f);
		float strafe = (r ? 1f : 0f) - (l ? 1f : 0f);
		boolean jump = client.options.jumpKey.isPressed();
		if (possessTick++ % COOLDOWN == 0) {
			FusionNetwork.sendPossessMove(forward, strafe, jump);
		}
		// E-действие: сервер сам решает, что делает стойка (поднять/выброс) по настройкам.
		if (client.options.useKey.wasPressed()) {
			FusionNetwork.sendPossessAction(0);
		}
		if (client.options.dropKey.wasPressed()) {
			FusionNetwork.sendPossessAction(1);
		}
		// Состояние кнопок мыши: 0=нет, 1=ЛКМ (ломка), 2=ПКМ (ставка). Шлём каждый тик.
		int btn = 0;
		if (client.options.attackKey.isPressed()) btn = 1;
		else if (client.options.useKey.isPressed()) btn = 2;
		if (btn != lastMouseButton) {
			lastMouseButton = btn;
			FusionNetwork.sendPossessButton(btn);
		}
	}

	private static int lastMouseButton = -1;

	/** Обрабатывает S2C-результат CoreProtect (lookup/rollback/restore) и печатает в чат клиента. */
	private static void handleCoreProtectResult(java.io.DataInputStream in) throws Exception {
		String title = in.readUTF();
		int n = in.readInt();
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return;
		client.player.sendMessage(Text.literal("§6[CoreProtect] §f" + title), false);
		for (int i = 0; i < n; i++) {
			String line = in.readUTF();
			client.player.sendMessage(Text.literal(line), false);
		}
	}

	private static void openControlPanel(MinecraftClient client) {
		if (client.player == null) return;
		if (!FusionNetwork.canSend()) {
			client.player.sendMessage(net.minecraft.text.Text.literal("[FusionEngine] Сервер не имеет плагина или канал недоступен."), false);
			return;
		}
		client.setScreen(new ScreenControlPanel());
	}
}