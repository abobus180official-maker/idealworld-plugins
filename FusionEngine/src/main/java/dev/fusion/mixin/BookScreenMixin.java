package dev.fusion.mixin;

import dev.fusion.ImagineBookRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Рендер картинок (токенов ImagineBook) в интерфейсе чтения книги (BookScreen).
 * Перехватывает отрисовку книги и рисует изображения из текущей страницы поверх.
 */
@Mixin(BookScreen.class)
public abstract class BookScreenMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void fusion_renderImages(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BookScreen self = (BookScreen) (Object) this;
        ImagineBookRenderer.render(ctx, self);
    }
}