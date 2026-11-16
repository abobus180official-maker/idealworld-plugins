package dev.coords;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Клиентская часть coords-scramble:
 *  - подмена координат в F3 (через DebugHudMixin; генерация случайных строк — здесь);
 *  - при входе на сервер: шлём PRESENCE (сигнал о наличии мода) и, если нашёлся чит-клиент,
 *    шлём CHEAT с его именем;
 *  - блокировка ресурспаков с "xray" в URL/имени (через ResourcePackMixin).
 */
public class CoordsScrambleClient implements ClientModInitializer {

    private static String fakeLine = "XYZ: 0.000 / 0.00000 / 0.000";
    private static String fakeBlockLine = "Block: 0 0 0 [0 0 0]";

    @Override
    public void onInitializeClient() {
        // Еже-тиковое обновление фейк-строк.
        ClientTickEvents.END_CLIENT_TICK.register(client -> updateFakeStrings());

        // При установлении соединения — наличие мода + детект чита.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            CoordsNetwork.sendPresence();

            String cheat = CheatDetector.detect();
            if (cheat != null) {
                CoordsScrambleMod.LOGGER.warn("[CoordsScramble] Обнаружен чит-клиент: " + cheat);
                CoordsNetwork.sendCheatDetected(cheat);
            }
        });
    }

    private static void updateFakeStrings() {
        double x = Math.random() * 2000 - 1000;
        double y = Math.random() * 300 - 100;
        double z = Math.random() * 2000 - 1000;
        fakeLine = String.format("XYZ: %.3f / %.5f / %.3f", x, y, z);
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        int dx = (int) Math.floor(Math.random() * 16);
        int dy = (int) Math.floor(Math.random() * 16);
        int dz = (int) Math.floor(Math.random() * 16);
        fakeBlockLine = String.format("Block: %d %d %d [%d %d %d]", bx, by, bz, dx, dy, dz);
    }

    public static String getFakeCoordinatesLine() {
        return fakeLine;
    }

    public static String getFakeBlockLine() {
        return fakeBlockLine;
    }
}