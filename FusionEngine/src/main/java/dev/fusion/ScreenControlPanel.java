package dev.fusion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Главное меню управления fusioncore (открывается по бинду, по умолчанию Правый Shift).
 *
 * Содержит:
 *  - «События» — подменю быстрых событий (призыв метеорита и т.п.).
 *  - Сценарии: поле ввода имени + Запустить/Остановить (шлются на сервер через канал).
 *  - «Редактор стойки» — переходит к редактору стойки для брони (по взгляду).
 *  - Подсказка о смене бинда в Настройки → Управление.
 */
public class ScreenControlPanel extends Screen {

	private TextFieldWidget scenarioField;

	public ScreenControlPanel() {
		super(Text.literal("FusionCore — управление"));
	}

	@Override
	public void init() {
		int cx = this.width / 2;
		int x0 = cx - 120;
		int y = 34;

		addDrawableChild(ButtonWidget.builder(Text.literal("События…"), b -> {
			this.client.setScreen(new ScreenEvents());
		}).dimensions(x0, y, 240, 20).build());

		y += 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("CoreProtect /co…"), b -> {
			this.client.setScreen(new ScreenCoreProtect());
		}).dimensions(x0, y, 240, 20).build());

		y += 28;
		addDrawableChild(ButtonWidget.builder(Text.literal("Редактор стойки…"), b -> {
			openStandEditor();
		}).dimensions(x0, y, 240, 20).build());

		y += 34;
		scenarioField = new TextFieldWidget(this.textRenderer, x0, y, 240, 18, Text.literal("Имя сценария"));
		scenarioField.setText("");
		scenarioField.setPlaceholder(Text.literal("имя сценария"));
		addDrawableChild(scenarioField);

		y += 24;
		addDrawableChild(ButtonWidget.builder(Text.literal("Запустить сценарий"), b -> {
			String name = scenarioField.getText().trim();
			if (name.isEmpty()) return;
			FusionNetwork.sendRunScenario(name);
			toast("Запущен сценарий: " + name);
		}).dimensions(x0, y, 118, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Остановить"), b -> {
			String name = scenarioField.getText().trim();
			if (name.isEmpty()) return;
			FusionNetwork.sendStopScenario(name);
			toast("Остановлен сценарий: " + name);
		}).dimensions(x0 + 122, y, 118, 20).build());
	}

	private void openStandEditor() {
		if (this.client == null || this.client.player == null) return;
		var stand = ArmorStandHelper.findTarget(this.client.player);
		if (stand == null) {
			this.client.player.sendMessage(
					Text.literal("[FusionCore] Наведите взгляд на стойку для брони в радиусе 20 блоков."), false);
			return;
		}
		this.client.setScreen(new ScreenArmorStandEditor(stand));
	}

	private void toast(String s) {
		if (this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("[FusionCore] " + s), false);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(this.textRenderer, Text.literal("FusionCore — управление"), this.width / 2 - 75, 12, 0xFFFFFF, false);
		ctx.drawText(this.textRenderer,
				Text.literal("Бинд можно сменить в Настройки → Управление."), this.width / 2 - 100, this.height - 20, 0xAAAAAA, false);
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void close() {
		this.client.setScreen(null);
	}
}