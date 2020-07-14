package org.popcraft.chunky;

import io.papermc.lib.PaperLib;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class Chunky extends JavaPlugin {

    private final static int CHUNK_SIZE = 16;

    @Override
    public void onEnable() {
        // Plugin startup logic

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private static class ChunkLocation {
        public final int x, z;

        public ChunkLocation(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    final String printUpdateFormat = "Task running for '%s'. Progress: %d chunks (%.2f%%) , ETA: %01d:%02d:%02d, Speed: %.1f ch/s, Last Chunk: %d, %d";
    final String printDoneFormat = "Task finished after %d chunks.";
    final AtomicLong startTime = new AtomicLong();
    final AtomicInteger finishedChunks = new AtomicInteger();
    final AtomicInteger totalChunks = new AtomicInteger();
    final ConcurrentLinkedQueue<Long> chunkUpdateTimes10Sec = new ConcurrentLinkedQueue<>();
    final int FREQ = 50;

    private void printUpdate(Chunk chunk) {
        String world = chunk.getWorld().getName();
        int chunkNum = finishedChunks.addAndGet(1);
        double percentDone = 100f * chunkNum / totalChunks.get();
        long currentTime = System.currentTimeMillis();
        chunkUpdateTimes10Sec.add(currentTime);
        while(currentTime - chunkUpdateTimes10Sec.peek() > 1e4 /* 10 seconds */) chunkUpdateTimes10Sec.poll();
        double speed = chunkUpdateTimes10Sec.size() / 10f; // chunk updates in 1 second
        int chunksLeft = totalChunks.get() - finishedChunks.get();
        int eta = (int) (chunksLeft / speed);
        int etaHours = eta / 3600;
        int etaMinutes = (eta - etaHours * 3600) / 60;
        int etaSeconds = eta - etaHours * 3600 - etaMinutes * 60;
        String message = String.format(printUpdateFormat, world, chunkNum, percentDone, etaHours, etaMinutes, etaSeconds, speed, chunk.getX(), chunk.getZ());
        this.getServer().getConsoleSender().sendMessage(message);
    }

    private void printUpdate(World chunkWorld, ChunkLocation chunk) {
        String world = chunkWorld.getName();
        int chunkNum = finishedChunks.addAndGet(1);
        double percentDone = 100f * chunkNum / totalChunks.get();
        long currentTime = System.currentTimeMillis();
        chunkUpdateTimes10Sec.add(currentTime);
        while(currentTime - chunkUpdateTimes10Sec.peek() > 1e4 /* 10 seconds */) chunkUpdateTimes10Sec.poll();
        double speed = chunkUpdateTimes10Sec.size() / 10f; // chunk updates in 1 second
        int chunksLeft = totalChunks.get() - finishedChunks.get();
        int eta = (int) (chunksLeft / speed);
        int etaHours = eta / 3600;
        int etaMinutes = (eta - etaHours * 3600) / 60;
        int etaSeconds = eta - etaHours * 3600 - etaMinutes * 60;
        String message = String.format(printUpdateFormat, world, chunkNum, percentDone, etaHours, etaMinutes, etaSeconds, speed, chunk.x, chunk.z);
        this.getServer().getConsoleSender().sendMessage(message);
    }

    private void printDone() {
        this.getServer().getConsoleSender().sendMessage(String.format(printDoneFormat, finishedChunks.get()));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final World world = sender instanceof Player ? ((Player) sender).getWorld() : this.getServer().getWorlds().get(0);
        final int radius = Integer.parseInt(args[0]);
        final CompletableFuture<Void> genTask = new CompletableFuture<>();
        genTask.thenRun(this::printDone);
        this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            Queue<ChunkLocation> chunkLocations = new LinkedList<>();
            this.getServer().getConsoleSender().sendMessage("Preparing...");
            for (int x = -radius; x < radius; x += CHUNK_SIZE) {
                for (int z = -radius; z < radius; z += CHUNK_SIZE) {
                    chunkLocations.add(new ChunkLocation(x >> 4, z >> 4));
                }
            }
            finishedChunks.set(0);
            totalChunks.set(chunkLocations.size());
            chunkUpdateTimes10Sec.clear();
            this.getServer().getConsoleSender().sendMessage("Starting...");
            startTime.set(System.currentTimeMillis());
            final AtomicInteger working = new AtomicInteger();
            while (!chunkLocations.isEmpty()) {
                if (working.get() < FREQ) {
                    ChunkLocation chunkLocation = chunkLocations.poll();
                    if (PaperLib.isChunkGenerated(world, chunkLocation.x, chunkLocation.z)) {
                        printUpdate(world, chunkLocation);
                        continue;
                    }
                    working.getAndIncrement();
                    CompletableFuture<Chunk> chunkFuture = PaperLib.getChunkAtAsync(world, chunkLocation.x, chunkLocation.z);
                    chunkFuture.thenAccept(this::printUpdate).thenRun(working::getAndDecrement);
                }
            }
            genTask.complete(null);
        });
        return true;
    }
}
