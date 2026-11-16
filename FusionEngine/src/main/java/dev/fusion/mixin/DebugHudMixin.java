package dev.fusion.mixin;

import dev.fusion.F3ScrambleClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Подмена координат в F3-экране.
 *
 * Мод затрагивает ТОЛЬКО отображение координат в debug overlay (F3). Каждый кадр
 * отрисовки левого текста DebugHud строка "XYZ:" (текущая позиция игрока) и
 * строка "Block:" (координаты блока) заменяются на случайные значения. Реальное
 * местоположение игрока и все другие строки F3 не меняются.
 *
 * Замена происходит в DebugHud.getLeftText() — методе, собирающем список строк
 * левой панели F3. Подменяем элементы со случайными значениями, которые меняются
 * каждый тик клиента (см. F3ScrambleClient).
 */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void fusion_scrambleCoordinates(CallbackInfoReturnable<List<String>> cir) {
        List<String> text = cir.getReturnValue();
        if (text == null || text.isEmpty()) return;
        String fake = F3ScrambleClient.getFakeCoordinatesLine();
        String fakeBlock = F3ScrambleClient.getFakeBlockLine();
        boolean xyzReplaced = false;
        boolean blockReplaced = false;
        for (int i = 0; i < text.size(); i++) {
            String line = text.get(i);
            if (line == null) continue;
            if (!xyzReplaced && line.startsWith("XYZ:")) {
                text.set(i, fake);
                xyzReplaced = true;
            } else if (!blockReplaced && line.startsWith("Block:")) {
                text.set(i, fakeBlock);
                blockReplaced = true;
            }
            if (xyzReplaced && blockReplaced) return;
        }
    }
}