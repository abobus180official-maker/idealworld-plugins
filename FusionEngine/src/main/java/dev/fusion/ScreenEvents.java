package dev.fusion;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Меню «События» для fusioncore. Доступно из главного меню управления по клавише.
 * Содержит кнопки быстрых событий, доступных администратору:
 *  - «Призвать метеорит» — шлёт на сервер команду суммон метеорита (реализует FusionSys).
 * Список можно расширять (события/сценарии/мистика).
 */
public class ScreenEvents extends Screen {

	public ScreenEvents() {
		super(Text.literal("FusionCore — события"));
	}

	@Override
	public void init() {
		int cx = this.width / 2;
		int x0 = cx - 100;
		int y = 40;

		addDrawableChild(ButtonWidget.builder(Text.literal("Призвать метеорит (магнит)"), b -> {
			FusionNetwork.sendSummonMeteor("magnet");
			if (this.client != null && this.client.player != null) {
				this.client.player.sendMessage(
						Text.literal("[FusionCore] Призыв метеорита (магнит) отправлен на сервер."), false);
			}
		}).dimensions(x0, y, 200, 20).build());

		y += 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("Призвать метеорит (гематит)"), b -> {
			FusionNetwork.sendSummonMeteor("hematite");
			if (this.client != null && this.client.player != null) {
				this.client.player.sendMessage(
						Text.literal("[FusionCore] Призыв метеорита (гематит) отправлен на сервер."), false);
			}
		}).dimensions(x0, y, 200, 20).build());

		y += 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("Сценарии… (скоро)"), b -> {
			if (this.client != null && this.client.player != null) {
				this.client.player.sendMessage(
						Text.literal("[FusionCore] Меню управления сценариями появится в следующем обновлении."), false);
			}
		}).dimensions(x0, y, 200, 20).build());

		y += 26;
		addDrawableChild(ButtonWidget.builder(Text.literal("Назад"), b -> {
			if (this.client != null) this.client.setScreen(null);
		}).dimensions(x0, y, 200, 20).build());
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(this.textRenderer, Text.literal("FusionCore — события"), this.width / 2 - 60, 15, 0xFFFFFF, false);
	}

	@Override
	public boolean shouldPause() { return false; }

	@Override
	public void close() {
		this.client.setScreen(null);
	}
}