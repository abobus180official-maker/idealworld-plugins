package dev.fusion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран редактора стойки для брони (администратор).
 * Содержит: выбор части тела, ползунки X/Y/Z позы, переключатели флагов,
 * кнопки действий (все слоты, свап, переименование, удаление).
 * Все изменения отправляются на сервер через FusionNetwork.applySync.
 */
public class ScreenArmorStandEditor extends Screen {

	private final ArmorStandEntity stand;
	private int partIndex = 0;            // 0..5 (PART_NAMES)
	private float[][][] pose;              // выполняется в init через readPoses
	private float[][] livePose;

	// переключатели (начальные значения считываются со стойки)
	private boolean small;
	private boolean invisible;
	private boolean arms;
	private boolean basePlate;

	private SliderWidget sliderX, sliderY, sliderZ;
	private ButtonWidget partLabel;
	private TextFieldWidget nameField;

	public ScreenArmorStandEditor(ArmorStandEntity stand) {
		super(Text.literal("FusionEngine — стойка"));
		this.stand = stand;
		snapshotFromStand();
	}

	private void snapshotFromStand() {
		float[][] p = ArmorStandHelper.readPoses(stand);
		livePose = p;
		small = stand.isSmall();
		invisible = stand.isInvisible();
		arms = stand.shouldShowArms();
		basePlate = !stand.shouldHideBasePlate();
	}

	private int selectedPart() { return partIndex; }

	private void firePoseUpdate() {
		// строим JSON изменений только для выбранной части
		var sb = new StringBuilder();
		sb.append("{");
		sb.append("\"pose\":{");
		sb.append("\"").append(ArmorStandHelper.PART_NAMES[partIndex]).append("\":[");
		float[] v = livePose[partIndex];
		sb.append(v[0]).append(",").append(v[1]).append(",").append(v[2]).append("]}");
		sb.append("}");
		FusionNetwork.sendSync(stand.getUuid(), sb.toString());
	}

	private void fireFlagsUpdate() {
		var sb = new StringBuilder();
		sb.append("{\"small\":").append(small)
			.append(",\"invisible\":").append(invisible)
			.append(",\"arms\":").append(arms)
			.append(",\"baseplate\":").append(basePlate)
			.append("}");
		FusionNetwork.sendSync(stand.getUuid(), sb.toString());
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int y = 30;
		int x0 = cx - 130;

		// --- выбор части тела ---
		partLabel = ButtonWidget.builder(Text.literal("Часть: " + ArmorStandHelper.PART_NAMES[partIndex]),
				b -> { partIndex = (partIndex + 1) % ArmorStandHelper.PART_NAMES.length; updatePartUI(); })
				.dimensions(x0, y, 100, 20).build();
		addDrawableChild(partLabel);

		y += 26;
		// Ползунки X/Y/Z
		sliderX = new SliderWidget(x0, y, 260, 20, Text.literal("X"), 0.5) {
			@Override protected void updateMessage() { setMessage(Text.literal("X: " + livePose[partIndex][0])); }
			@Override protected void applyValue() {
				livePose[partIndex][0] = (float) ((value - 0.5) * 360);
				firePoseUpdate();
			}
		};
		addDrawableChild(sliderX);

		y += 22;
		sliderY = new SliderWidget(x0, y, 260, 20, Text.literal("Y"), 0.5) {
			@Override protected void updateMessage() { setMessage(Text.literal("Y: " + livePose[partIndex][1])); }
			@Override protected void applyValue() {
				livePose[partIndex][1] = (float) ((value - 0.5) * 360);
				firePoseUpdate();
			}
		};
		addDrawableChild(sliderY);

		y += 22;
		sliderZ = new SliderWidget(x0, y, 260, 20, Text.literal("Z"), 0.5) {
			@Override protected void updateMessage() { setMessage(Text.literal("Z: " + livePose[partIndex][2])); }
			@Override protected void applyValue() {
				livePose[partIndex][2] = (float) ((value - 0.5) * 360);
				firePoseUpdate();
			}
		};
		addDrawableChild(sliderZ);

		updatePartUI();

		// --- флаги ---
		y += 30;
		addDrawableChild(ButtonWidget.builder(Text.literal("Маленькая: " + small), b -> { small = !small; fireFlagsUpdate(); refresh(); })
				.dimensions(x0, y, 130, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Невидимая: " + invisible), b -> { invisible = !invisible; fireFlagsUpdate(); refresh(); })
				.dimensions(x0 + 135, y, 130, 20).build());
		y += 22;
		addDrawableChild(ButtonWidget.builder(Text.literal("Руки: " + arms), b -> { arms = !arms; fireFlagsUpdate(); refresh(); })
				.dimensions(x0, y, 130, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Плита: " + basePlate), b -> { basePlate = !basePlate; fireFlagsUpdate(); refresh(); })
				.dimensions(x0 + 135, y, 130, 20).build());

		// --- действия ---
		y += 30;
		addDrawableChild(ButtonWidget.builder(Text.literal("Свап руки<->голова"), b -> FusionNetwork.sendSwap(stand.getUuid(), 0))
				.dimensions(x0, y, 130, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Свап руки влево/вправо"), b -> FusionNetwork.sendSwap(stand.getUuid(), 1))
				.dimensions(x0 + 135, y, 130, 20).build());

		y += 26;
		nameField = new TextFieldWidget(this.textRenderer, x0, y, 200, 18, Text.literal("Имя"));
		nameField.setText(stand.hasCustomName() ? stand.getCustomName().getString() : "");
		addDrawableChild(nameField);
		addDrawableChild(ButtonWidget.builder(Text.literal("OK"), b -> FusionNetwork.sendRename(stand.getUuid(), nameField.getText()))
				.dimensions(x0 + 205, y, 55, 20).build());
	}

	private void updatePartUI() {
		if (partLabel != null)
			partLabel.setMessage(Text.literal("Часть: " + ArmorStandHelper.PART_NAMES[partIndex]));
		firePoseUpdate();
	}

	private void refresh() {
		// пересоздаём экран, чтобы обновить метки
		this.clearChildren();
		init();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		this.renderBackground(ctx, mouseX, mouseY, delta);
		super.render(ctx, mouseX, mouseY, delta);
		ctx.drawText(this.textRenderer, Text.literal("Стойка: " + stand.getUuid()), 10, 10, 0xFFFFFF, false);
	}

	@Override
	public boolean shouldPause() { return false; }
}