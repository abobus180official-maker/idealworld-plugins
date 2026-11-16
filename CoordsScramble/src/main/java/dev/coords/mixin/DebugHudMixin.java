package dev.coords.mixin;

import dev.coords.CoordsScrambleClient;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Подмена координат (XYZ: и Block:) в F3-экране на случайные значения.
 * Меняется только отображение debug overlay; реальная позиция игрока не трогается.
 */
@Mixin(DebugHud.class)
public abstract class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"), cancellable = true)
    private void coords_scrambleCoordinates(CallbackInfoReturnable<List<String>> cir) {
        List<String> text = cir.getReturnValue();
        if (text == null || text.isEmpty()) return;
        String fake = CoordsScrambleClient.getFakeCoordinatesLine();
        String fakeBlock = CoordsScrambleClient.getFakeBlockLine();
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