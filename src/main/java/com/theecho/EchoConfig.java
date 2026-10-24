package com.theecho;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Se guarda en config/the_echo.json.
 * Todos los "Day" son DÍAS DEL ECO (el contador de la esquina superior derecha, empezando en 0),
 * no días del mundo: así un mundo viejo no empieza con el terror ya a tope, y las ofrendas al espejo los reducen.
 */
public final class EchoConfig {
    public int configVersion = 2;

    // --- fases (día del Eco en el que se desbloquea cada cosa) ---
    public int chatStartDay = 1;       // mensajes falsos en el chat (muy suaves al principio)
    public int audioStartDay = 1;      // pasos / puertas / cofres falsos
    public int echoStartDay = 2;       // primeras apariciones de Ecos (miran de lejos, de reojo)
    public int registrarDay = 3;       // aparece El Registrador
    public int decayStartDay = 3;      // la Decadencia Silenciosa empieza a revertir tus bloques
    public int walkDay = 3;            // Ecos que caminan hacia ti
    public int rushDay = 4;            // Ecos que corren hacia ti y desaparecen antes de tocarte
    public int fakeBlockDay = 4;       // "aldeano" que en realidad es lana, cofres falsos
    public int stareAnimalsDay = 4;    // aldeanos/animales que te miran fijamente
    public int shrineDay = 4;          // aparecen ruinas con notas abandonadas
    public int screamDay = 5;          // Ecos que gritan
    public int flyDay = 5;             // Ecos que vuelan
    public int mimicChopDay = 5;       // un Eco imita que tala un árbol
    public int shadowDay = 6;          // Ecos de skin negra
    public int interfaceDay = 6;       // carteles que mutan, antorchas que parpadean
    public int hijackDay = 9;          // el Eco puede llevarte al laberinto
    public int corruptionDay = 14;     // si sigues construyendo: cielo sepia, animales mudos
    public int endingDay = 20;         // el final: el mundo te echa y debes borrarlo tú

    // --- sistema de terreno ---
    public boolean enableDecay = true;
    public double decayGraceDays = 1.0;   // días del mundo antes de que un bloque tuyo pueda revertirse
    public int riteTolerance = 6;         // bloques "tuyos" que pueden quedar y aun así completar el Rito
    public boolean corruptionEnding = true; // false = nunca te echa del mundo

    // --- miedo / espejo ---
    public double eventIntensity = 1.0;   // multiplicador global de frecuencia de eventos
    public double dreadPerMinute = 3.0;
    public double hijackDread = 100.0;
    public double offeringDreadReduction = 35.0;
    public double offeringParanoiaReduction = 35.0;
    public int offeringDayRelief = 1;     // cuántos días baja el contador al ofrendar
    public int offeringBanishDays = 1;
    public int maxBanishDays = 5;
    public int mirrorRadius = 24;
    public int mazeSize = 7;              // celdas por lado del laberinto (5 a 11); los pasillos miden 3 de ancho
    public boolean enableRealm = true;

    public static EchoConfig INSTANCE = new EchoConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("the_echo.json");
        try {
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path)) {
                    EchoConfig c = GSON.fromJson(r, EchoConfig.class);
                    // Una config vieja (v1) usaba otros días: se descarta para usar los nuevos valores
                    if (c != null && c.configVersion >= 2) INSTANCE = c;
                }
            }
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (IOException | RuntimeException e) {
            TheEcho.LOGGER.error("No se pudo leer/escribir la config del Eco", e);
        }
    }
}
