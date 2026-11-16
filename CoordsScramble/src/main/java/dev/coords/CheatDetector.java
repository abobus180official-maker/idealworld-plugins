package dev.coords;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Детектор чит-клиентов. Проверка best-effort:
 *   1) по id установленных Fabric-модов (KNOWN_CHEAT_MOD_IDS);
 *   2) по сигнатурным классам (наличие класслоадера = признак чит-клиента).
 *
 * Возвращает null, если ничего не обнаружено, иначе — имя клиента/мода.
 */
public final class CheatDetector {

    private CheatDetector() {}

    /** Проверка по зарегистрированным под разными id модам (без дублей). */
    public static String detectByModId() {
        try {
            var mods = FabricLoader.getInstance().getAllMods();
            for (var m : mods) {
                String id = m.getMetadata().getId().toLowerCase();
                String name = m.getMetadata().getName();
                for (String known : CoordsScrambleMod.KNOWN_CHEAT_MOD_IDS) {
                    if (id.contains(known) || name.toLowerCase().contains(known)) {
                        return name + " (id=" + id + ")";
                    }
                }
            }
        } catch (Throwable t) {
            CoordsScrambleMod.LOGGER.warn("[CoordsScramble] Ошибка детекта по id: " + t.getMessage());
        }
        return null;
    }

    /** Проверка по наличию сигнатурных классов (рефлексия без загрузки зависимостей). */
    public static String detectByClass() {
        for (String cls : CoordsScrambleMod.KNOWN_CHEAT_CLASSES) {
            try {
                Class.forName(cls, false, CheatDetector.class.getClassLoader());
                return "signal-class:" + cls;
            } catch (ClassNotFoundException ignored) {
                // нет такого класса — ок, идём дальше
            } catch (Throwable t) {
                CoordsScrambleMod.LOGGER.warn("[CoordsScramble] Ошибка проверки класса " + cls + ": " + t.getMessage());
            }
        }
        return null;
    }

    /** Полный детект — по id, затем по классам. null = чисто. */
    public static String detect() {
        String byId = detectByModId();
        if (byId != null) return byId;
        return detectByClass();
    }
}