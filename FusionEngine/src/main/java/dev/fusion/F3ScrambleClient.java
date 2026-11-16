package dev.fusion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Клиентская часть подмены координат в F3.
 *
 * Каждый тик клиента генерирует две фейковые строки:
 *  - "XYZ: f / f / f" — координаты игрока;
 *  - "Block: x y z [.. .. ..]" — координаты блока (блока, на который смотрит
 *    игрок / текущего блока) — чтобы через F3 нельзя было определить позицию
 *    и по этой строке тоже.
 *
 * DebugHudMixin подставляет эти строки в левую панель F3 вместо настоящих.
 * Реальное положение игрока не меняется.
 */
public class F3ScrambleClient implements ClientModInitializer {

    private static String fakeLine = "XYZ: 0.000 / 0.00000 / 0.000";
    private static String fakeBlockLine = "Block: 0 0 0 [0 0 0]";

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            double x = Math.random() * 2000 - 1000;
            double y = Math.random() * 300 - 100;
            double z = Math.random() * 2000 - 1000;
            fakeLine = String.format("XYZ: %.3f / %.5f / %.3f", x, y, z);
            int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
            // подпозиция внутри блока 0..15 (как "Block: x y z [dx dy dz]")
            int dx = (int) Math.floor(Math.random() * 16);
            int dy = (int) Math.floor(Math.random() * 16);
            int dz = (int) Math.floor(Math.random() * 16);
            fakeBlockLine = String.format("Block: %d %d %d [%d %d %d]", bx, by, bz, dx, dy, dz);
        });
    }

    /** Текущая строка фейковых координат игрока для F3. */
    public static String getFakeCoordinatesLine() {
        return fakeLine;
    }

    /** Текущая строка фейковых координат блока для F3. */
    public static String getFakeBlockLine() {
        return fakeBlockLine;
    }
}