package dev.coords;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа (общая, main). Лениво держит логгер и перечень известных чит-клиентов/модов
 * (проверка идёт по id модов Fabric и по сигнатурным классам).
 */
public final class CoordsScrambleMod implements ModInitializer {

    public static final String MOD_ID = "coords-scramble";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Известные чит-клиенты/моды (id модов FabricLoader). */
    public static final String[] KNOWN_CHEAT_MOD_IDS = {
        "meteor-client", "meteor", "wurst", "impact", "liquidbounce", "clickcrystal",
        "forgehax", "salhack", "aristois", "bleachhack", "kamiblue", "baritone",
        "freecam", "xray", "seesbester", "future", "sigma", "phobos", "konas"
    };

    /** Сигнатурные классы чит-клиентов (проверяются рефлексией, наличие класса = признак). */
    public static final String[] KNOWN_CHEAT_CLASSES = {
        "meteor.systems.modules.Modules",
        "wurst.WurstClient",
        "impact.Client",
        "me.impact.impactforge",
        "com.lukflug.panelstudio",
        "net.optifine.http"
    };

    @Override
    public void onInitialize() {
        // Ничего тяжёлого на main-стороне не делаем; логика — в клиентской части.
        LOGGER.info("CoordsScramble initialized.");
    }
}