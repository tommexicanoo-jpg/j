package com.theecho;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Genera un laberinto nuevo (aleatorio) de deepslate dentro de the_echo:mirror_realm.
 * Los pasillos miden 3 bloques de ancho (celdas de 3x3) y los muros 1 bloque; el techo está a 4 de altura.
 */
public final class RealmBuilder {

    public static final int CELL = 3;        // ancho de cada celda / pasillo
    public static final int PITCH = CELL + 1; // celda + muro
    public static final int HEIGHT = 4;      // altura libre

    public record Result(BlockPos start, BlockPos original, List<BlockPos> traps, List<BlockPos> cells) {}

    private RealmBuilder() {}

    public static Result build(ServerWorld dst, BlockPos origin, Random rand) {
        final int n = Math.max(5, Math.min(11, EchoConfig.INSTANCE.mazeSize));
        final int size = n * PITCH + 1;
        final int floorY = origin.getY() - 1;
        final int ceilY = origin.getY() + HEIGHT;
        final int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

        boolean[][] solid = new boolean[size][size];
        for (boolean[] row : solid) Arrays.fill(row, true);

        // --- laberinto por backtracking recursivo ---
        boolean[][] seen = new boolean[n][n];
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        seen[0][0] = true;
        carveCell(solid, 0, 0);
        stack.push(new int[]{0, 0});
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            List<int[]> opts = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < n && nz < n && !seen[nx][nz]) opts.add(d);
            }
            if (opts.isEmpty()) { stack.pop(); continue; }
            int[] d = opts.get(rand.nextInt(opts.size()));
            int nx = c[0] + d[0], nz = c[1] + d[1];
            seen[nx][nz] = true;
            carveCell(solid, nx, nz);
            carvePassage(solid, c[0], c[1], d[0], d[1]);
            stack.push(new int[]{nx, nz});
        }
        // Algunos bucles extra para que los Ecos puedan rodearte
        for (int i = 0; i < n; i++) {
            int cx = rand.nextInt(n), cz = rand.nextInt(n);
            int[] d = dirs[rand.nextInt(4)];
            int nx = cx + d[0], nz = cz + d[1];
            if (nx >= 0 && nz >= 0 && nx < n && nz < n) carvePassage(solid, cx, cz, d[0], d[1]);
        }

        // --- el Original: la casilla más lejana del inicio (BFS) ---
        int[][] dist = new int[size][size];
        for (int[] row : dist) Arrays.fill(row, -1);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        dist[2][2] = 0;
        q.add(new int[]{2, 2});
        int ox = 2, oz = 2, best = 0;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx < 0 || nz < 0 || nx >= size || nz >= size) continue;
                if (solid[nx][nz] || dist[nx][nz] >= 0) continue;
                dist[nx][nz] = dist[c[0]][c[1]] + 1;
                if (dist[nx][nz] > best) { best = dist[nx][nz]; ox = nx; oz = nz; }
                q.add(new int[]{nx, nz});
            }
        }

        // --- escribir bloques ---
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int wx = origin.getX() + x, wz = origin.getZ() + z;
                put(dst, m.set(wx, floorY, wz), floor(rand), flags);
                put(dst, m.set(wx, ceilY, wz), Blocks.DEEPSLATE_BRICKS.getDefaultState(), flags);
                for (int y = origin.getY(); y < ceilY; y++) {
                    BlockState out;
                    if (solid[x][z]) {
                        out = wall(rand);
                    } else {
                        out = air;
                        boolean nearStart = x <= 4 && z <= 4;
                        if (!nearStart) {
                            if (y == origin.getY() + HEIGHT - 1 && rand.nextInt(40) == 0) out = Blocks.COBWEB.getDefaultState();
                            if (y == origin.getY() && rand.nextInt(90) == 0) out = Blocks.SOUL_LANTERN.getDefaultState();
                        }
                    }
                    put(dst, m.set(wx, y, wz), out, flags);
                }
            }
        }

        BlockPos start = new BlockPos(origin.getX() + 2, origin.getY(), origin.getZ() + 2);
        BlockPos original = new BlockPos(origin.getX() + ox, origin.getY(), origin.getZ() + oz);
        dst.setBlockState(original, ModBlocks.ECHO_ORIGINAL.getDefaultState(), Block.NOTIFY_ALL);

        // --- celdas y callejones sin salida (trampas de susto) ---
        List<BlockPos> cells = new ArrayList<>();
        List<BlockPos> deadEnds = new ArrayList<>();
        for (int cx = 0; cx < n; cx++) {
            for (int cz = 0; cz < n; cz++) {
                if (cx == 0 && cz == 0) continue;
                int x0 = 1 + cx * PITCH, z0 = 1 + cz * PITCH;
                BlockPos cp = new BlockPos(origin.getX() + x0 + 1, origin.getY(), origin.getZ() + z0 + 1);
                if (cp.equals(original)) continue;
                cells.add(cp);
                int open = 0;
                if (!solid[x0 + CELL][z0 + 1]) open++;
                if (!solid[x0 - 1][z0 + 1]) open++;
                if (!solid[x0 + 1][z0 + CELL]) open++;
                if (!solid[x0 + 1][z0 - 1]) open++;
                if (open == 1) deadEnds.add(cp);
            }
        }
        Collections.shuffle(deadEnds, rand);
        List<BlockPos> traps = new ArrayList<>(deadEnds.subList(0, Math.min(6, deadEnds.size())));
        List<BlockPos> shuffled = new ArrayList<>(cells);
        Collections.shuffle(shuffled, rand);
        for (BlockPos c : shuffled) {
            if (traps.size() >= 5) break;
            if (!traps.contains(c)) traps.add(c);
        }
        return new Result(start, original, traps, cells);
    }

    /** Una celda ocupa 3x3 casillas, empezando en (1 + cx*4, 1 + cz*4). */
    private static void carveCell(boolean[][] solid, int cx, int cz) {
        int x0 = 1 + cx * PITCH, z0 = 1 + cz * PITCH;
        for (int dx = 0; dx < CELL; dx++) for (int dz = 0; dz < CELL; dz++) solid[x0 + dx][z0 + dz] = false;
    }

    /** Un pasillo entre celdas también mide 3 de ancho: se abre el muro de 1 bloque en las 3 casillas. */
    private static void carvePassage(boolean[][] solid, int cx, int cz, int dx, int dz) {
        int x0 = 1 + cx * PITCH, z0 = 1 + cz * PITCH;
        for (int i = 0; i < CELL; i++) {
            if (dx == 1) solid[x0 + CELL][z0 + i] = false;
            else if (dx == -1) solid[x0 - 1][z0 + i] = false;
            else if (dz == 1) solid[x0 + i][z0 + CELL] = false;
            else solid[x0 + i][z0 - 1] = false;
        }
    }

    private static BlockState floor(Random r) {
        return (r.nextInt(5) == 0 ? Blocks.DEEPSLATE_TILES : Blocks.POLISHED_DEEPSLATE).getDefaultState();
    }

    private static BlockState wall(Random r) {
        int k = r.nextInt(100);
        if (k < 55) return Blocks.DEEPSLATE_BRICKS.getDefaultState();
        if (k < 75) return Blocks.DEEPSLATE_TILES.getDefaultState();
        if (k < 88) return Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState();
        if (k < 96) return Blocks.COBBLED_DEEPSLATE.getDefaultState();
        if (k < 98) return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        return Blocks.SCULK.getDefaultState();
    }

    /** Solo escribe si el bloque cambia (menos lag y permite reutilizar la zona). */
    private static void put(ServerWorld w, BlockPos pos, BlockState state, int flags) {
        if (w.getBlockState(pos) == state) return;
        w.setBlockState(pos, state, flags);
    }
}
