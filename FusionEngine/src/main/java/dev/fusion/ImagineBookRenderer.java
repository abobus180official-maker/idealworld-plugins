package dev.fusion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Рендер картинок по токенам ImagineBook в интерфейсе книги.
 *
 * Формат токена в тексте страницы: [url,x,y,width%,height%,rotation°]
 *   url   — прямая ссылка на изображение (http(s)://)
 *   x, y  — позиция в пикселях от верхнего левого угла страницы
 *   width%, height% — размер относительно ширины/высоты страницы (0..100)
 *   rotation° — градусы поворота (необязательно)
 *
 * Изображения скачиваются и кэшируются на клиенте. При открытии книги (BookScreen)
 * токены на текущей странице рендерятся поверх текста.
 */
public final class ImagineBookRenderer {

    private ImagineBookRenderer() {}

    // разделитель строк страницы книги (вложенные строки игрока, joined в вершину)
    private static final Pattern TOKEN = Pattern.compile(
            "\\[\\s*(https?://[^\\],]+)\\,\\s*(\\d+(?:\\.\\d+)?)\\,\\s*(\\d+(?:\\.\\d+)?)\\,\\s*(\\d+(?:\\.\\d+)?)\\%\\,\\s*(\\d+(?:\\.\\d+)?)\\%(?:\\,\\s*([-\\d.]+)°?)?\\s*\\]", Pattern.CASE_INSENSITIVE);

    // url -> texture identifier (кэш)
    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static float PAGE_W = 160f;   // ширина книжной страницы в пикселях GUI (~146 при масштабе)
    private static float PAGE_H = 200f;

    /** Конвертирует массив строк книги в один текст (страницы склеиваются). */
    private static String joinPages(List<String> pages) {
        if (pages == null) return "";
        return String.join("\n", pages);
    }

    /**
     * Рендерит токены книжных токенов из текста на экране книги.
     * @param pageText текст текущей страницы (с токенами).
     */
    public static void renderFromText(DrawContext ctx, String pageText, int offX, int offY) {
        if (pageText == null || pageText.isEmpty()) return;
        renderOn(ctx, pageText, offX, offY);
    }

    /** Рендерит токены на текущей странице книги. */
    public static void render(DrawContext ctx, BookScreen screen) {
        // Текущий текст страницы берём из активного экрана через reflection
        String text = BookScreenAccess.getCurrentPageText(screen);
        if (text == null || text.isEmpty()) return;
        renderOn(ctx, text, 25, 25); // смещения страницы под рамку книги
    }

    /** Рендерит токены из заданного текста на координатах смещения страницы. */
    public static void renderOn(DrawContext ctx, String text, int offX, int offY) {
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String url = m.group(1).trim();
            float x = parseF(m.group(2));
            float y = parseF(m.group(3));
            float wPct = parseF(m.group(4));
            float hPct = parseF(m.group(5));
            float rot = m.group(6) != null ? parseF(m.group(6)) : 0f;
            drawImage(ctx, url, offX + x, offY + y, wPct / 100f, hPct / 100f, rot);
        }
    }

    private static void drawImage(DrawContext ctx, String url, float x, float y, float wPct, float hPct, float rot) {
        Identifier id = loadTexture(url);
        if (id == null) return;
        float w = PAGE_W * Math.max(0.02f, Math.min(wPct, 1f));
        float h = PAGE_H * Math.max(0.02f, Math.min(hPct, 1f));
        var matrices = ctx.getMatrices();
        matrices.push();
        matrices.translate(x, y, 0f);
        if (rot != 0f) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot));
        ctx.drawTexture(id, 0, 0, 0, 0, 0, (int) w, (int) h, (int) w, (int) h);
        matrices.pop();
    }

    /** Скачивает и кэширует изображение; возвращает Identifier текстуры или null. */
    private static Identifier loadTexture(String url) {
        if (CACHE.containsKey(url)) return CACHE.get(url);
        try {
            URI uri = URI.create(url.trim());
            InputStream in = uri.toURL().openStream();
            NativeImage img = NativeImage.read(in);
            in.close();
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = MinecraftClient.getInstance().getTextureManager()
                    .registerDynamicTexture("imagine_" + (CACHE.size()), tex);
            CACHE.put(url, id);
            return id;
        } catch (Exception e) {
            CACHE.put(url, null);
            return null;
        }
    }

    private static float parseF(String s) {
        if (s == null) return 0f;
        try { return Float.parseFloat(s); } catch (Exception e) { return 0f; }
    }
}