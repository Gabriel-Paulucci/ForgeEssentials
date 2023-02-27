package com.forgeessentials.mapper;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.imageio.ImageIO;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.IntArrayNBT;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent.WorldTickEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import com.forgeessentials.core.ForgeEssentials;
import com.forgeessentials.core.misc.FECommandManager;
import com.forgeessentials.core.moduleLauncher.FEModule;
import com.forgeessentials.mapper.command.CommandMapper;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleCommonSetupEvent;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleRegisterCommandsEvent;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleServerStartingEvent;
import com.forgeessentials.util.events.FEModuleEvent.FEModuleServerStoppingEvent;
import com.forgeessentials.util.output.LoggingHandler;
import com.google.common.collect.Iterables;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

@FEModule(name = "mapper", parentMod = ForgeEssentials.class, canDisable = true, defaultModule = false)
public class ModuleMapper
{

    public static final String TAG_MODIFIED = "lastModified";

    public static final int MAX_UPDATE_INTERVAL = 1000 * 5;
    public static final int MAX_REGION_UPDATE_INTERVAL = 1000 * 10;
    public static final long MAX_CACHE_SAVE_INTERVAL = 1000 * 60;

    public final String CACHE_FILE = "cache.dat";

    @FEModule.Instance
    protected static ModuleMapper instance;

    protected File dataDirectory;

    @FEModule.ModuleDir
    private static File mapperDirectory;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private CompoundNBT cacheStorage = new CompoundNBT();

    private long lastCacheSave;

    private Set<Chunk> modifiedChunks = Collections.newSetFromMap(new WeakHashMap<Chunk, Boolean>());

    protected Map<Long, Future<BufferedImage>> regionRenderers = new ConcurrentHashMap<>();

    protected Map<Long, Future<BufferedImage>> chunkRenderers = new ConcurrentHashMap<>();

    public static ModuleMapper getInstance()
    {
        return instance;
    }

    /* ------------------------------------------------------------ */

    public ModuleMapper()
    {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void load(FEModuleCommonSetupEvent event)
    {
        InputStream is = Object.class.getResourceAsStream("/mapper_colorscheme.txt");
        if (is != null)
            MapperUtil.loadColorScheme(is);
    }

    @SubscribeEvent
    private void registerCommands(FEModuleRegisterCommandsEvent event)
    {
        FECommandManager.registerCommand(new CommandMapper(true));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void serverStarting(FEModuleServerStartingEvent event)
    {
        dataDirectory = new File(mapperDirectory, ServerLifecycleHooks.getCurrentServer().getServerDirectory().getAbsolutePath());
        dataDirectory.mkdirs();
        loadCache();
    }

    @SubscribeEvent
    public void serverStopping(FEModuleServerStoppingEvent event)
    {
        saveCache(true);
    }

    /* ------------------------------------------------------------ */

    @SubscribeEvent
    public void chunkUnloadEvent(ChunkEvent.Unload event)
    {
        if (event.getWorld().isClientSide())
            return;
        Chunk chunk = (Chunk) event.getChunk();
        if (!chunk.isUnsaved() && !modifiedChunks.contains(chunk))
        {
            setChunkModified(chunk, (ServerWorld) chunk.getLevel());
            setRegionModified((ServerWorld) chunk.getLevel(), MapperUtil.chunkToRegion(chunk.getPos().x), MapperUtil.chunkToRegion(chunk.getPos().z));
        }
    }

    @SubscribeEvent
    public synchronized void worldTickEvent(WorldTickEvent event)
    {
        if (event.side.isClient())
            return;
        ServerWorld world = (ServerWorld) event.world;
        ServerChunkProvider cSource = world.getChunkSource();
        Long2ObjectLinkedOpenHashMap<ChunkHolder> map = ObfuscationReflectionHelper.getPrivateValue(ChunkManager.class, cSource.chunkMap, "visibleChunkMap");
        Iterable<ChunkHolder> list = Iterables.unmodifiableIterable(map.values());
        for (ChunkHolder chunkH : list) {
            Chunk chunk = chunkH.getTickingChunk();
            if (chunk != null && !chunk.isUnsaved() && !modifiedChunks.contains(chunk))
            {
                setChunkModified(chunk, world);
                setRegionModified(world, MapperUtil.chunkToRegion(chunk.getPos().x), MapperUtil.chunkToRegion(chunk.getPos().z));
            }
        }
    }

    // @SubscribeEvent
    // public void serverTickEvent(ServerTickEvent event)
    // {
    // if (event.phase == Phase.START || ServerUtil.getPlayerList().isEmpty())
    // return;
    // ServerPlayerEntity player = ServerUtil.getPlayerList().get(0);
    // int x = (int) Math.floor(player.posX);
    // int z = (int) Math.floor(player.posZ);
    // WorldServer world = (WorldServer) player.world;
    // getRegionImageAsync(world, MapperUtil.worldToRegion(x), MapperUtil.worldToRegion(z));
    // getChunkImageAsync(world, MapperUtil.worldToChunk(x), MapperUtil.worldToChunk(z));
    // }

    /* ------------------------------------------------------------ */

    /* ------------------------------------------------------------ */

    public synchronized void setChunkModified(Chunk chunk, ServerWorld world)
    {
        modifiedChunks.add(chunk);
        setChunkModified((ServerWorld) world, chunk.getPos().x, chunk.getPos().z);
    }

    public synchronized void unsetChunkModified(Chunk chunk)
    {
        modifiedChunks.remove(chunk);
        unsetChunkModified((ServerWorld) chunk.getLevel(), chunk.getPos().x, chunk.getPos().z);
    }

    public synchronized void setChunkModified(ServerWorld world, int chunkX, int chunkZ)
    {
        int regionX = MapperUtil.chunkToRegion(chunkX);
        int regionZ = MapperUtil.chunkToRegion(chunkZ);
        chunkX -= regionX * MapperUtil.REGION_CHUNKS;
        chunkZ -= regionZ * MapperUtil.REGION_CHUNKS;
        int[] cache = getRegionCache(world, regionX, regionZ);
        int index = chunkX + chunkZ * MapperUtil.REGION_CHUNKS;
        if (cache[index] == 0)
            cache[index] = getCurrentMillisInt();
        saveCache(false);
    }

    public synchronized void setRegionModified(ServerWorld world, int regionX, int regionZ)
    {
        int[] cache = getRegionCache(world, regionX, regionZ);
        if (cache[MapperUtil.REGION_CHUNK_COUNT] == 0)
            cache[MapperUtil.REGION_CHUNK_COUNT] = getCurrentMillisInt();
        saveCache(false);
    }

    public synchronized void unsetChunkModified(ServerWorld world, int chunkX, int chunkZ)
    {
        int regionX = MapperUtil.chunkToRegion(chunkX);
        int regionZ = MapperUtil.chunkToRegion(chunkZ);
        chunkX -= regionX * MapperUtil.REGION_CHUNKS;
        chunkZ -= regionZ * MapperUtil.REGION_CHUNKS;
        int[] cache = getRegionCache(world, regionX, regionZ);
        int index = chunkX + chunkZ * MapperUtil.REGION_CHUNKS;
        cache[index] = 0;
        saveCache(false);
    }

    public synchronized void unsetRegionModified(ServerWorld world, int regionX, int regionZ)
    {
        int[] cache = getRegionCache(world, regionX, regionZ);
        cache[MapperUtil.REGION_CHUNK_COUNT] = 0;
        saveCache(false);
    }

    public boolean shouldUpdateChunk(ServerWorld world, int chunkX, int chunkZ)
    {
        int regionX = MapperUtil.chunkToRegion(chunkX);
        int regionZ = MapperUtil.chunkToRegion(chunkZ);
        chunkX -= regionX * MapperUtil.REGION_CHUNKS;
        chunkZ -= regionZ * MapperUtil.REGION_CHUNKS;
        int[] cache = getRegionCache(world, regionX, regionZ);
        int index = chunkX + chunkZ * MapperUtil.REGION_CHUNKS;
        int flag = cache[index];
        return flag > 0 && flag < getCurrentMillisInt() - MAX_UPDATE_INTERVAL;
    }

    public boolean shouldUpdateRegion(ServerWorld world, int regionX, int regionZ)
    {
        int[] cache = getRegionCache(world, regionX, regionZ);
        int flag = cache[MapperUtil.REGION_CHUNK_COUNT];
        return flag > 0 && flag < getCurrentMillisInt() - MAX_REGION_UPDATE_INTERVAL;
    }

    public synchronized int[] getRegionCache(ServerWorld world, int regionX, int regionZ)
    {
        String regionId = String.format("%d-%d.%d", world.dimension(), regionX, regionZ);
        INBT tag = cacheStorage.get(regionId);
        if (!(tag instanceof IntArrayNBT) || ((IntArrayNBT) tag).getAsIntArray().length != MapperUtil.REGION_CHUNK_COUNT + 1)
        {
            tag = new IntArrayNBT(new int[MapperUtil.REGION_CHUNK_COUNT + 1]);
            cacheStorage.put(regionId, tag);
        }
        return ((IntArrayNBT) tag).getAsIntArray();
    }

    /* ------------------------------------------------------------ */

    private int getCurrentMillisInt()
    {
        return (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
    }

    public void loadCache()
    {
        try
        {
            FileInputStream is = new FileInputStream(new File(dataDirectory, CACHE_FILE));
            cacheStorage = CompressedStreamTools.readCompressed(is);
        }
        catch (IOException e)
        {
            cacheStorage = new CompoundNBT();
        }
    }

    public void saveCache(boolean force)
    {
        if (!force && lastCacheSave > System.currentTimeMillis() - MAX_CACHE_SAVE_INTERVAL)
            return;
        try
        {
            FileOutputStream os = new FileOutputStream(new File(dataDirectory, CACHE_FILE));
            CompressedStreamTools.writeCompressed(cacheStorage, os);
            lastCacheSave = System.currentTimeMillis();
        }
        catch (IOException e)
        {
            LoggingHandler.felog.error("Error saving mapping cache");
        }
    }

    /* ------------------------------------------------------------ */

    public File getChunkCacheFile(final ServerWorld world, final int chunkX, final int chunkZ)
    {
        return new File(dataDirectory, String.format("%d.c.%d.%d.png", world.dimension(), chunkX, chunkZ));
    }

    public BufferedImage renderChunk(final ServerWorld world, final int chunkX, final int chunkZ)
    {
        if (!MapperUtil.chunkExists(world, chunkX, chunkZ))
            return null;
        File cacheFile = getChunkCacheFile(world, chunkX, chunkZ);
        LoggingHandler.felog.warn(String.format("Rendering chunk %d.%d...", chunkX, chunkZ));
        Chunk chunk = MapperUtil.loadOrCreateChunk(world, chunkX, chunkZ);
        BufferedImage image = MapperUtil.renderChunk(chunk);
        try
        {
            ImageIO.write(image, "png", cacheFile);
            unsetChunkModified(chunk);
            saveCache(false);
        }
        catch (IOException e)
        {
            LoggingHandler.felog.warn(String.format("Error writing mapper cache file %s: %s", cacheFile, e.getMessage()));
        }
        return image;
    }

    public BufferedImage getChunkImage(final ServerWorld world, final int chunkX, final int chunkZ)
    {
        File cacheFile = getChunkCacheFile(world, chunkX, chunkZ);
        if (cacheFile.exists() && !shouldUpdateChunk(world, chunkX, chunkZ))
        {
            try
            {
                return ImageIO.read(cacheFile);
            }
            catch (IOException e)
            {
                LoggingHandler.felog.warn(String.format("Error reading mapper cache file %s", cacheFile));
            }
        }
        return renderChunk(world, chunkX, chunkZ);
    }

    public synchronized Future<BufferedImage> getChunkImageAsync(final ServerWorld world, final int chunkX, final int chunkZ)
    {
        final long id = ChunkPos.asLong(chunkX, chunkZ);
        Future<BufferedImage> result = chunkRenderers.get(id);
        if (result != null)
            return result;
        result = executor.submit(new Callable<BufferedImage>() {
            @Override
            public BufferedImage call()
            {
                BufferedImage result = getChunkImage(world, chunkX, chunkZ);
                chunkRenderers.remove(id);
                return result;
            }
        });
        chunkRenderers.put(id, result);
        return result;
    }

    public Future<File> getChunkFileAsync(final ServerWorld world, final int chunkX, final int chunkZ)
    {
        final Future<BufferedImage> future = getChunkImageAsync(world, chunkX, chunkZ);
        return executor.submit(new Callable<File>() {
            @Override
            public File call()
            {
                try
                {
                    if (future.get() == null)
                        return null;
                    return getChunkCacheFile(world, chunkX, chunkZ);
                }
                catch (InterruptedException | ExecutionException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }
        });
    }

    /* ------------------------------------------------------------ */

    public File getRegionCacheFile(final ServerWorld world, final int regionX, final int regionZ)
    {
        return new File(dataDirectory, String.format("%d.%d.%d.png", world.dimension(), regionX, regionZ));
    }

    public BufferedImage renderRegion(ServerWorld world, int regionX, int regionZ)
    {
        LoggingHandler.felog.warn(String.format("Rendering region %d.%d...", regionX, regionZ));
        // image = MapperUtil.renderRegion(world, regionX, regionZ);
        BufferedImage image = MapperUtil.renderRegion(world, regionX, regionZ);
        LoggingHandler.felog.warn("Finished!");
        File cacheFile = getRegionCacheFile(world, regionX, regionZ);
        try
        {
            ImageIO.write(image, "png", cacheFile);
            unsetRegionModified(world, regionX, regionZ);
            saveCache(false);
        }
        catch (IOException e)
        {
            LoggingHandler.felog.warn(String.format("Error writing mapper cache file %s: %s", cacheFile, e.getMessage()));
        }
        return image;
    }

    public BufferedImage getRegionImage(ServerWorld world, int regionX, int regionZ)
    {
        File cacheFile = getRegionCacheFile(world, regionX, regionZ);
        if (cacheFile.exists() && !shouldUpdateRegion(world, regionX, regionZ))
        {
            try
            {
                return ImageIO.read(cacheFile);
            }
            catch (IOException e)
            {
                LoggingHandler.felog.warn(String.format("Error reading mapper cache file %s", cacheFile));
            }
        }
        return renderRegion(world, regionX, regionZ);
    }

    public synchronized Future<BufferedImage> getRegionImageAsync(final ServerWorld world, final int regionX, final int regionZ)
    {
        final long id = ChunkPos.asLong(regionX, regionZ);
        Future<BufferedImage> result = regionRenderers.get(id);
        if (result != null)
            return result;
        result = executor.submit(new Callable<BufferedImage>() {
            @Override
            public BufferedImage call()
            {
                BufferedImage result = getRegionImage(world, regionX, regionZ);
                regionRenderers.remove(id);
                return result;
            }
        });
        regionRenderers.put(id, result);
        return result;
    }

    public Future<File> getRegionFileAsync(final ServerWorld world, final int regionX, final int regionZ)
    {
        final Future<BufferedImage> future = getRegionImageAsync(world, regionX, regionZ);
        return executor.submit(new Callable<File>() {
            @Override
            public File call()
            {
                try
                {
                    if (future.get() == null)
                        return null;
                    return getRegionCacheFile(world, regionX, regionZ);
                }
                catch (InterruptedException | ExecutionException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }
        });
    }

    /* ------------------------------------------------------------ */

}
