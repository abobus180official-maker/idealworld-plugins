package dev.fusion;

import net.minecraft.client.gui.screen.ingame.BookScreen;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Безопасный доступ к внутренним полям BookScreen (страницы/текущий индекс) без
 * знания точных имён полей по yarn-маппингам — через reflection по известным типам.
 */
public final class BookScreenAccess {

    private BookScreenAccess() {}

    /**
     * Возвращает текст текущей страницы открытой книги (для чтения/редактирования).
     * Ищет поля: список страниц (List<String> или List of formatted Text) и текущий
     * индекс страницы, затем склеивает/выбирает нужную страницу.
     */
    public static String getCurrentPageText(BookScreen screen) {
        try {
            if (screen == null) return "";

            // 1) Текущий индекс страницы: поле типа int (page index)
            int pageIndex = 0;
            for (Field f : allFields(screen.getClass())) {
                if (f.getType() == int.class) {
                    f.setAccessible(true);
                    Object v = f.get(screen);
                    int iv = v instanceof Integer ? (Integer) v : 0;
                    if (iv >= 0 && iv < 1000 && isPageIndexField(iv, f)) { pageIndex = iv; break; }
                }
            }

            // 2) Страницы: первое поле типа List (страницы книги)
            for (Field f : allFields(screen.getClass())) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(screen);
                    if (!(v instanceof List<?> list) || list.isEmpty()) continue;
                    // элементы могут быть String или FormattedText/Text
                    Object el = pageIndex < list.size() ? list.get(pageIndex) : list.get(0);
                    if (el == null) return "";
                    String s = el.toString();
                    if (el instanceof net.minecraft.text.OrderedText) return stripFormatting(s);
                    return s;
                }
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Устраняет управляющие коды (§) и переносы (из подчёркивания книги). */
    private static String stripFormatting(String s) {
        if (s == null) return "";
        return s.replace("\u00A7", "").replaceAll("\\s+", " ").trim();
    }

    /** Эвристика: считаем поле int потенциальным индексом страницы, если оно небольшое. */
    private static boolean isPageIndexField(int v, Field f) {
        String n = f.getName().toLowerCase();
        return n.contains("page") || n.contains("index") || n.contains("current") || (v >= 0 && v <= 5);
    }

    private static Field[] allFields(Class<?> c) {
        java.util.List<Field> all = new java.util.ArrayList<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) all.add(f);
        }
        return all.toArray(new Field[0]);
    }
}