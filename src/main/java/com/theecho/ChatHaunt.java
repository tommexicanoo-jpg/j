package com.theecho;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Random;

/**
 * Mensajes de chat falsos que parecen escritos por el propio jugador (<TuNombre> ...) pero no los escribió él.
 * Empiezan casi inocentes y, con los días del Eco, se vuelven más frecuentes, más amenazantes y más personales.
 */
public final class ChatHaunt {
    private static final Random RNG = new Random();

    // Nivel 0: parecen una broma o una duda tuya
    private static final String[] T0 = {
            "hola?", "alguien más está jugando?", "creo que escuché algo", "qué raro...", "jaja nada",
            "juraría que acá había una antorcha", "oye, ¿tú también oíste eso?", "se siente raro este lugar",
            "ya es tarde, no?", "mmm", "dejé la puerta cerrada, no?"
    };
    // Nivel 1: incomodidad
    private static final String[] T1 = {
            "hay alguien detrás de mí", "no estoy solo", "eso no estaba ahí antes", "quién movió mis cosas",
            "me están mirando", "deja de seguirme", "mi casa se ve diferente", "alguien abrió la puerta",
            "escucho pasos y no hay nadie", "no mires hacia allá", "siento que me copia", "esto no es normal"
    };
    // Nivel 2: amenazas
    private static final String[] T2 = {
            "ya te vi", "no vas a poder esconderte", "voy a atraparte", "sé dónde duermes",
            "te estoy siguiendo desde ayer", "cuando duermas voy a entrar", "deja de mirar atrás",
            "pronto vas a ser como yo", "cada bloque que pones me acerca a ti", "no corras. me cansa",
            "ya no recuerdo cómo era tu cara", "me estás olvidando. yo no"
    };
    // Nivel 3: terror personal
    private static final String[] T3 = {
            "{name}, ya casi te alcanzo", "estoy detrás de ti, {name}", "te voy a arrastrar conmigo",
            "NO VAS A DESPERTAR", "estoy en {x} {z}. ven.", "día {day}. ya no queda nadie más",
            "tu mundo ya no te recuerda", "VOY A ATRAPARTE. VOY A ATRAPARTE.", "no hay a dónde ir",
            "ya estoy dentro de tu casa", "mírame. MÍRAME.", "{name} {name} {name} {name}",
            "no deshiciste nada. sigo aquí", "te veo desde la ventana que ya no existe", "cierra los ojos, {name}"
    };
    private static final String[][] POOLS = {T0, T1, T2, T3};

    private ChatHaunt() {}

    /** Se llama una vez por segundo por jugador activo. */
    public static void tick(MinecraftServer server, ServerPlayerEntity p, EchoState.PlayerData d, int day) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (day < cfg.chatStartDay) return;

        // Mensajes por minuto: sube con los días, el miedo y la paranoia
        double perMin = (0.15 + day * 0.4 + d.dread / 70.0 + d.paranoia / 90.0) * cfg.eventIntensity;
        perMin = Math.min(perMin, 10.0);
        if (RNG.nextDouble() > perMin / 60.0) return;

        int tier = baseTier(day);
        if (RNG.nextInt(4) == 0 && tier > 0) tier--;                // a veces uno más suave: no es predecible
        if (d.dread > 70 || d.paranoia > 75) tier = Math.min(3, tier + 1);

        int count = switch (tier) {
            case 3 -> 1 + RNG.nextInt(4);
            case 2 -> 1 + RNG.nextInt(2);
            default -> 1;
        };

        // Falso "salió del juego" antes de la ráfaga (niveles altos)
        int delay = 0;
        if (tier >= 2 && RNG.nextInt(6) == 0) {
            Text left = Text.translatable("multiplayer.player.left", p.getDisplayName()).formatted(Formatting.YELLOW);
            p.sendMessage(left, false);
            delay = 50;
            Sched.at(server, 45, () -> p.sendMessage(
                    Text.translatable("multiplayer.player.joined", p.getDisplayName()).formatted(Formatting.YELLOW), false));
        }

        for (int i = 0; i < count; i++) {
            final String msg = format(pick(tier), p, day);
            Sched.at(server, delay + i * (22 + RNG.nextInt(40)), () -> send(p, msg));
        }
    }

    public static int baseTier(int day) {
        if (day < 3) return 0;
        if (day < 6) return 1;
        if (day < 10) return 2;
        return 3;
    }

    private static String pick(int tier) {
        String[] pool = POOLS[Math.max(0, Math.min(3, tier))];
        return pool[RNG.nextInt(pool.length)];
    }

    private static String format(String raw, ServerPlayerEntity p, int day) {
        return raw.replace("{name}", p.getName().getString())
                .replace("{x}", String.valueOf(p.getBlockX() + RNG.nextInt(9) - 4))
                .replace("{y}", String.valueOf(p.getBlockY()))
                .replace("{z}", String.valueOf(p.getBlockZ() + RNG.nextInt(9) - 4))
                .replace("{day}", String.valueOf(day + 1));
    }

    private static void send(ServerPlayerEntity p, String msg) {
        if (p.isRemoved()) return;
        p.sendMessage(Text.literal("<" + p.getName().getString() + "> " + msg), false);
    }

    /** Para /echo chat: fuerza un mensaje del nivel dado (0-3). */
    public static void force(MinecraftServer server, ServerPlayerEntity p, int tier, int day) {
        send(p, format(pick(tier), p, day));
    }

    /** Mensaje con la pista de que hay algo en el horizonte (ruinas con nota). */
    public static void hint(ServerPlayerEntity p, String direction) {
        send(p, "hay algo viejo hacia el " + direction + ". no debería ir");
    }
}
