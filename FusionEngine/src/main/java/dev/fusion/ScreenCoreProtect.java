package dev.fusion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Экран управления CoreProtect (GUI /co).
 *
 * Открывается из главного меню FusionCore. Позволяет:
 *  - Lookup по блоку под прицелом;
 *  - Lookup внутри игр (радиус / ник / время);
 *  - Rollback / Restore действий вокруг игрока (радиус / ник / время).
 *
 * Все действия отправляются на сервер по каналу "fusion:engine" (C2S);
 * результаты приходят обратно S2C (альтернативный канал) и отображаются
 * текстом в чат.
 */
public class ScreenCoreProtect extends Screen {

	private TextFieldWidget radiusField;
	private TextFieldWidget userField;
	private TextFieldWidget timeField;

	public ScreenCoreProtect() {
		super(Text.literal("CoreProtect — GUI"));
	}

	@Override
	public void init() {
		int cx = this.width / 2;
		int x0 = cx - 120;
		int y = 34;

		addDrawableChild(ButtonWidget.builder(Text.literal("Lookup по блоку (прицел)"), b -> {
			FusionNetwork.sendCoLookupBlock(25);
			toast("Запрошен lookup по блоку. Результат в чат.");
		}).dimensions(x0, y, 240, 20).build());

		y += 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("Lookup вокруг игрока"), b -> {
			doLookupNear();
		}).dimensions(x0, y, 240, 20).build());

		y += 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("Rollback (откат)"), b -> {
			FusionNetwork.sendCoRollback(FusionNetwork.CO_ROLLBACK, radius(), user(), time());
			toast("Rollback отправлен на сервер.");
		}).dimensions(x0, y, 118, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Restore"), b -> {
			FusionNetwork.sendCoRollback(FusionNetwork.CO_RESTORE, radius(), user(), time());
			toast("Restore отправлен на сервер.");
		}).dimensions(x0 + 122, y, 118, 20).build());

		y += 32;
		radiusField = new TextFieldWidget(this.textRenderer, x0, y, 118, 18, Text.literal("Радиус"));
		radiusField.setText("10");
		radiusField.setPlaceholder(Text.literal("радиус (блоков)"));
		addDrawableChild(radiusField);

		timeField = new TextFieldWidget(this.textRenderer, x0 + 122, y, 118, 18, Text.literal("Время"));
		timeField.setText("3600");
		timeField.setPlaceholder(Text.literal("время (сек)"));
		addDrawableChild(timeField);

		y += 24;
		userField = new TextFieldWidget(this.textRenderer, x0, y, 240, 18, Text.literal("Игрок"));
		userField.setPlaceholder(Text.literal("ник игрока (пусто = все)"));
		addDrawableChild(userField);
	}

	private void doLookupNear() {
		FusionNetwork.sendCoLookupNear(radius(), user(), time(), 50);
		toast("Запрошен lookup рядом. Результат в чат.");
	}

	private int radius() {
		return parseInt(radiusField, 10, 1);
	}

	private int time() {
		return parseInt(timeField, 3600, 1);
	}

	private String user() {
		return userField == null ? "" : userField.getText().trim();
	}

	private int parseInt(TextFieldWidget f, int def, int min) {
		try {
			return Math.max(min, Integer.parseInt(f.getText().trim()));
		} catch (Exception e) {
			return def;
		}
	}

	private void toast(String s) {
		if (this.client != null && this.client.player != null) {
			this.client.player.sendMessage(Text.literal("[FusionCore-CoreProtect] " + s), false);
		}
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(this.textRenderer, Text.literal("CoreProtect — управление"), this.width / 2 - 80, 12, 0xFFFFFF, false);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void close() {
		this.client.setScreen(null);
	}
}