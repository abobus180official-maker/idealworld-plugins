package dev.fusionsys.race;

import java.util.List;

/**
 * Модель расы. Загружается из отдельного файла plugins/FusionSys/races/<name>.yml.
 * В конфиге перечисляются никнеймы игроков, принадлежащих расе.
 */
public class Race {
    public String name;
    public String displayName;
    public double baseScale = 1.0;
    public double growScaleMultiplier = 1.0;
    public double shrinkScaleMultiplier = 1.0;
    public int metalPercent = 0;
    public List<String> buffs = List.of();
    public List<String> debuffs = List.of();
    /** Никнеймы игроков этой расы (без учёта регистра). */
    public List<String> players = List.of();

    public Race(String name) {
        this.name = name;
    }

    /** true, если игрок с данным никнеймом принадлежит этой расе. */
    public boolean hasPlayer(String playerName) {
        if (playerName == null || players == null) return false;
        for (String p : players) {
            if (p != null && p.equalsIgnoreCase(playerName)) return true;
        }
        return false;
    }
}