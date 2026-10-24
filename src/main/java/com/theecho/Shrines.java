package com.theecho;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * El Desenlace. Pequeñas ruinas vacías (sin aldeanos, sin nada) con una nota abandonada sobre un atril.
 * La nota pide deshacer todo lo que has construido. Si no lo haces, el mundo se corrompe.
 */
public final class Shrines {
    public static final String NOTE_TITLE = "Nota abandonada";
    private static final Random RNG = new Random();

    private static final String[][] NOTES = {
            {
                    "No eres el primero\nque llega aquí.\n\nCada bloque que pones\nqueda grabado en algo\nque no duerme.",
                    "Yo también construí.\nEl mundo me recordó\nhasta que dejó de hacerlo.\n\nAhora solo soy\nlo que quedó."
            },
            {
                    "Si lees esto,\nya te está copiando.\n\nNo corras.\nNo sirve.\n\nSolo hay una salida.",
                    "Deshaz lo que hiciste.\nRompe tus paredes.\nCierra tus pozos.\nDevuelve el suelo\na como estaba.\n\nTodo.\nHasta el último bloque."
            },
            {
                    "Los espejos retrasan.\nNo curan.\n\nLo único que lo calma\nes que ya no haya\nnada tuyo\nque recordar.",
                    "Cuando el mundo\nvuelva a ser el que era,\nse olvidará de ti.\n\nY eso\nes lo mejor\nque puede pasarte."
            },
            {
                    "Yo me quedé.\nSeguí construyendo.\n\nEl cielo se puso gris.\nLos animales dejaron\nde hacer ruido.\n\nNadie vino a buscarme.",
                    "Al final\nel juego me pidió\nque lo borrara\nyo mismo.\n\nNo lo hagas\ncomo yo.\nDeshaz todo\nmientras puedas."
            }
    };

    private Shrines() {}

    /** Una vez cada ~30 s por jugador activo. */
    public static void tick(ServerWorld w, ServerPlayerEntity p, EchoState.PlayerData d, int day, EchoState st) {
        EchoConfig cfg = EchoConfig.INSTANCE;
        if (day < cfg.shrineDay || d.freed) return;
        int max = Math.min(5, 1 + (day - cfg.shrineDay) / 3);
        if (d.shrines >= max) return;
        if (RNG.nextInt(4) != 0) return;

        for (int i = 0; i < 16; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double dist = 55 + RNG.nextDouble() * 45;
            int x = (int) Math.floor(p.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(p.getZ() + Math.sin(ang) * dist);
            BlockPos origin = tryPlace(w, x, z, d.shrines);
            if (origin != null) {
                d.shrines++;
                st.markDirty();
                double dx = origin.getX() - p.getX(), dz = origin.getZ() - p.getZ();
                String dir = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "este" : "oeste") : (dz > 0 ? "sur" : "norte");
                final String fd = dir;
                Sched.at(w.getServer(), 100 + RNG.nextInt(400), () -> ChatHaunt.hint(p, fd));
                return;
            }
        }
    }

    /** Construye una ruina de 7x7 en (x, z) si el terreno es llano y seco. Devuelve la esquina o null. */
    public static BlockPos tryPlace(ServerWorld w, int x, int z, int noteIndex) {
        for (int dx = 0; dx <= 6; dx += 6) {
            for (int dz = 0; dz <= 6; dz += 6) {
                if (!w.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return null;
            }
        }
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 6; dx += 3) {
            for (int dz = 0; dz <= 6; dz += 3) {
                int y = w.getTopY(Heightmap.Type.WORLD_SURFACE_WG, x + dx, z + dz);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                BlockState top = w.getBlockState(new BlockPos(x + dx, y - 1, z + dz));
                if (!top.getFluidState().isEmpty() || top.isOf(Blocks.ICE) || top.isOf(Blocks.LAVA)) return null;
            }
        }
        if (maxY - minY > 3) return null;

        int base = maxY; // el suelo de la ruina queda a la altura del punto más alto
        BlockPos o = new BlockPos(x, base, z);
        build(w, o, noteIndex);
        return o;
    }

    private static void build(ServerWorld w, BlockPos o, int noteIndex) {
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                BlockPos floor = o.add(dx, -1, dz);
                // cimientos hacia abajo para que no flote
                for (int k = 0; k < 4; k++) {
                    BlockPos fp = floor.down(k);
                    if (!w.getBlockState(fp).isAir() && !w.getBlockState(fp).isReplaceable() && k > 0) break;
                    w.setBlockState(fp, RNG.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState(), flags);
                }
                boolean edge = dx == 0 || dz == 0 || dx == 6 || dz == 6;
                // limpiar el interior y el aire sobre la ruina
                for (int h = 0; h < 5; h++) {
                    BlockPos p = o.add(dx, h, dz);
                    if (!w.getBlockState(p).isAir()) w.setBlockState(p, Blocks.AIR.getDefaultState(), flags);
                }
                if (edge) {
                    boolean door = (dx == 3 && dz == 0);
                    int hgt = door ? 0 : 1 + RNG.nextInt(3); // muros rotos, de altura irregular
                    if (RNG.nextInt(7) == 0) hgt = 0;
                    for (int h = 0; h < hgt; h++) {
                        BlockState s = switch (RNG.nextInt(4)) {
                            case 0 -> Blocks.MOSSY_STONE_BRICKS.getDefaultState();
                            case 1 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
                            case 2 -> Blocks.CRACKED_STONE_BRICKS.getDefaultState();
                            default -> Blocks.COBBLESTONE.getDefaultState();
                        };
                        w.setBlockState(o.add(dx, h, dz), s, flags);
                    }
                } else if (RNG.nextInt(9) == 0) {
                    w.setBlockState(o.add(dx, 0, dz), Blocks.COBWEB.getDefaultState(), flags);
                }
            }
        }
        // El atril con la nota en el centro
        BlockPos lec = o.add(3, 0, 3);
        Direction face = Direction.Type.HORIZONTAL.random(w.getRandom());
        BlockState lecState = Blocks.LECTERN.getDefaultState().with(LecternBlock.FACING, face).with(LecternBlock.HAS_BOOK, true);
        w.setBlockState(lec, lecState, Block.NOTIFY_ALL);
        if (w.getBlockEntity(lec) instanceof LecternBlockEntity le) {
            le.setBook(makeNote(noteIndex));
        }
        // Una vela apagada a cada lado, solo para dar mal rollo
        w.setBlockState(o.add(2, 0, 3), Blocks.CANDLE.getDefaultState(), flags);
        w.setBlockState(o.add(4, 0, 3), Blocks.CANDLE.getDefaultState(), flags);
    }

    public static ItemStack makeNote(int index) {
        String[] pages = NOTES[Math.floorMod(index, NOTES.length)];
        List<RawFilteredPair<Text>> list = new ArrayList<>();
        for (String s : pages) list.add(RawFilteredPair.of(Text.literal(s)));
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new WrittenBookContentComponent(RawFilteredPair.of(NOTE_TITLE), "?", 0, list, true));
        return book;
    }

    /** ¿Lleva el jugador una nota abandonada en el inventario? */
    public static boolean hasNote(ServerPlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(Items.WRITTEN_BOOK)) continue;
            WrittenBookContentComponent c = s.get(DataComponentTypes.WRITTEN_BOOK_CONTENT);
            if (c != null && NOTE_TITLE.equals(c.title().raw())) return true;
        }
        return false;
    }
}
