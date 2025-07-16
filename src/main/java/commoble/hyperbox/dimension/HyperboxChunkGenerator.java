package commoble.hyperbox.dimension;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import commoble.hyperbox.Hyperbox;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.Difficulty;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public class HyperboxChunkGenerator extends ChunkGenerator {
	public static final int BOX_XZ = 480;
	public static final int GROUND_Y = 250;
	public static final ChunkPos CHUNKPOS = new ChunkPos(0,0);
	public static final long CHUNKID = CHUNKPOS.toLong();
	public static final BlockPos CORNER = CHUNKPOS.getWorldPosition();
	public static final BlockPos CENTER = CORNER.offset(7,7,7);
	private final Holder<Biome> biome;
	private final MinecraftServer server;
	private final BlockState bedrockBlock;
	private final BlockState stoneBlock;
	private final BlockState dirtBlock;
	private final BlockState grassBlock;

	private static final int STRUCTURE_MIN_COUNT = 8;
	private static final int STRUCTURE_MAX_TRIES = 64;
	private static final int STRUCTURE_MARGIN_BLOCKS = 16;
	private static final int STRUCTURE_FORCE_RADIUS_BLOCKS = 256;
	private static final int STRUCTURE_SEPARATION_BLOCKS = 96;

	public static Codec<HyperboxChunkGenerator> makeCodec() {
		return Biome.CODEC.fieldOf("biome").xmap(HyperboxChunkGenerator::new,HyperboxChunkGenerator::biome).codec();
	}

	public HyperboxChunkGenerator(MinecraftServer srv) {
		super(new FixedBiomeSource(srv.overworld().getBiome(BlockPos.ZERO)));
		this.server = srv;
		this.biome = srv.overworld().getBiome(BlockPos.ZERO);
		this.bedrockBlock = Hyperbox.INSTANCE.commonConfig.bedrockBlock(srv);
		this.stoneBlock = Hyperbox.INSTANCE.commonConfig.stoneBlock(srv);
		this.dirtBlock = Hyperbox.INSTANCE.commonConfig.dirtBlock(srv);
		this.grassBlock = Hyperbox.INSTANCE.commonConfig.grassBlock(srv);
	}

	public HyperboxChunkGenerator(Holder<Biome> b) {
		super(new FixedBiomeSource(b));
		this.biome = b;
		this.server = ServerLifecycleHooks.getCurrentServer();
		MinecraftServer srv = this.server;
		if (srv==null) {
			this.bedrockBlock = net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState();
			this.stoneBlock = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
			this.dirtBlock = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
			this.grassBlock = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
		} else {
			this.bedrockBlock = Hyperbox.INSTANCE.commonConfig.bedrockBlock(srv);
			this.stoneBlock = Hyperbox.INSTANCE.commonConfig.stoneBlock(srv);
			this.dirtBlock = Hyperbox.INSTANCE.commonConfig.dirtBlock(srv);
			this.grassBlock = Hyperbox.INSTANCE.commonConfig.grassBlock(srv);
		}
	}

	public Holder<Biome> biome() { return this.biome; }

	@Override
	protected Codec<? extends ChunkGenerator> codec() { return Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get(); }

	@Override public void applyCarvers(WorldGenRegion r,long s,RandomState rs,net.minecraft.world.level.biome.BiomeManager bm,StructureManager sm,ChunkAccess c,GenerationStep.Carving g) {}
	@Override public void spawnOriginalMobs(WorldGenRegion r) {}
	@Override public int getGenDepth() { MinecraftServer srv = sampleServer(); return srv==null?384:srv.overworld().getMaxBuildHeight()-srv.overworld().getMinBuildHeight(); }
	@Override public int getSeaLevel() { MinecraftServer srv = sampleServer(); return srv==null?0:srv.overworld().getSeaLevel(); }
	@Override public int getMinY() { MinecraftServer srv = sampleServer(); return srv==null?0:srv.overworld().getMinBuildHeight(); }
	@Override public int getBaseHeight(int x,int z,Heightmap.Types t,LevelHeightAccessor l,RandomState rs) { return GROUND_Y+1; }
	@Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor l,RandomState rs) { return new NoiseColumn(GROUND_Y+1,new BlockState[0]); }
	@Override public void addDebugScreenInfo(List<String> l,RandomState rs,BlockPos p) {}
	@Nullable @Override public Pair<BlockPos,Holder<Structure>> findNearestMapStructure(ServerLevel l,HolderSet<Structure> s,BlockPos p,int r,boolean k) { return null; }
	@Override public void applyBiomeDecoration(WorldGenLevel w,ChunkAccess c,StructureManager sm) {}
	@Override public int getSpawnHeight(LevelHeightAccessor l) { return GROUND_Y+1; }
	@Override public void createReferences(WorldGenLevel w,StructureManager sm,ChunkAccess c) {}
	@Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor e,Blender b,RandomState rs,StructureManager sm,ChunkAccess c) { return CompletableFuture.completedFuture(c); }

	@Override
	public void buildSurface(WorldGenRegion region,StructureManager sm,RandomState rs,ChunkAccess chunk) {
		fillChunkLimitedWorld(region,chunk,this.bedrockBlock,this.stoneBlock,this.dirtBlock,this.grassBlock);
		if (!chunk.getPos().equals(CHUNKPOS)) return;
		HyperboxWorldData d = HyperboxWorldData.getOrCreate(region.getLevel());
		if (!d.isGenerated() && !d.isPending()) d.setPending(true);
	}

	private static void fillChunkLimitedWorld(WorldGenRegion region,ChunkAccess chunk,BlockState bedrockBlock,BlockState stoneBlock,BlockState dirtBlock,BlockState grassBlock) {
		int minY = region.getMinBuildHeight();
		int maxY = region.getMaxBuildHeight()-1;
		int chunkMinX = chunk.getPos().getMinBlockX();
		int chunkMinZ = chunk.getPos().getMinBlockZ();
		int chunkMaxX = chunkMinX+15;
		int chunkMaxZ = chunkMinZ+15;
		int maxX = CORNER.getX()+BOX_XZ-1;
		int maxZ = CORNER.getZ()+BOX_XZ-1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x=chunkMinX;x<=chunkMaxX;x++) {
			for (int z=chunkMinZ;z<=chunkMaxZ;z++) {
				boolean inside = x>=CORNER.getX() && x<=maxX && z>=CORNER.getZ() && z<=maxZ;
				if (!inside) {
					for (int y=minY;y<=maxY;y++) region.setBlock(pos.set(x,y,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
				} else {
					for (int y=minY;y<=maxY;y++) {
						BlockState s;
						if (y==minY) s=bedrockBlock;
						else if (y>minY && y<GROUND_Y-3) s=stoneBlock;
						else if (y>=GROUND_Y-3 && y<GROUND_Y-1) s=dirtBlock;
						else if (y==GROUND_Y-1) s=dirtBlock;
						else if (y==GROUND_Y) s=grassBlock;
						else s=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
						region.setBlock(pos.set(x,y,z),s,2);
					}
				}
			}
		}
	}

	@Mod.EventBusSubscriber(modid=Hyperbox.MODID)
	public static class TickHandler {
		@SubscribeEvent
		public static void onLevelTick(TickEvent.LevelTickEvent e) {
			if (e.phase!=TickEvent.Phase.END) return;
			if (!(e.level instanceof ServerLevel lvl)) return;
			if (lvl.dimensionType()!=HyperboxDimension.getDimensionType(lvl.getServer())) return;
			HyperboxWorldData d = HyperboxWorldData.getOrCreate(lvl);
			if (!d.isPending() || d.isGenerated()) return;
			HyperboxChunkGenerator.generateNow(lvl);
			d.setGenerated(true);
			d.setPending(false);
		}
	}

	public static void generateNow(ServerLevel targetLevel) {
		new HyperboxChunkGenerator(targetLevel.getServer()).spawnConfiguredStructure(targetLevel);
	}

	private void spawnConfiguredStructure(ServerLevel lvl) {
		MinecraftServer srv = lvl.getServer();
		Difficulty diff = lvl.getDifficulty();
		List<ResourceLocation> pool = Hyperbox.INSTANCE.commonConfig.getStructurePoolForDifficulty(diff);
		if (pool.isEmpty()) pool = Hyperbox.INSTANCE.commonConfig.getStructurePoolForDifficulty(net.minecraft.world.Difficulty.NORMAL);
		if (pool.isEmpty()) pool = List.of(new ResourceLocation("dungeoncrawl","dungeon"));
		RandomSource rand = lvl.random;

		int minX = CORNER.getX();
		int minZ = CORNER.getZ();
		int maxX = CORNER.getX() + BOX_XZ - 1;
		int maxZ = CORNER.getZ() + BOX_XZ - 1;

		List<BlockPos> placedCenters = new ArrayList<>();
		int placed = 0;

		BlockPos first = new BlockPos(CORNER.getX()+BOX_XZ/2, GROUND_Y-4, CORNER.getZ()+BOX_XZ/2);
		forceBoxChunks(lvl, first, STRUCTURE_FORCE_RADIUS_BLOCKS);
		placeStructureCommand(lvl, srv, pool.get(rand.nextInt(pool.size())), first);
		placedCenters.add(first);
		placed++;

		while (placed < STRUCTURE_MIN_COUNT) {
			boolean success = false;
			for (int attempt=0; attempt<STRUCTURE_MAX_TRIES; attempt++) {
				int spanX = Math.max(1, BOX_XZ - STRUCTURE_MARGIN_BLOCKS*2);
				int spanZ = Math.max(1, BOX_XZ - STRUCTURE_MARGIN_BLOCKS*2);
				int x = minX + STRUCTURE_MARGIN_BLOCKS + rand.nextInt(spanX);
				int z = minZ + STRUCTURE_MARGIN_BLOCKS + rand.nextInt(spanZ);
				int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos pos = new BlockPos(x, y, z);

				if (!isFarEnough(pos, placedCenters, STRUCTURE_SEPARATION_BLOCKS)) continue;

				forceBoxChunks(lvl, pos, STRUCTURE_FORCE_RADIUS_BLOCKS);
				placeStructureCommand(lvl, srv, pool.get(rand.nextInt(pool.size())), pos);
				placedCenters.add(pos);
				placed++;
				success = true;
				break;
			}
			if (!success) {
				int x = minX + rand.nextInt(Math.max(1, BOX_XZ));
				int z = minZ + rand.nextInt(Math.max(1, BOX_XZ));
				int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockPos pos = new BlockPos(x, y, z);
				forceBoxChunks(lvl, pos, STRUCTURE_FORCE_RADIUS_BLOCKS);
				placeStructureCommand(lvl, srv, pool.get(rand.nextInt(pool.size())), pos);
				placedCenters.add(pos);
				placed++;
			}
		}

		BlockPos spawn = placedCenters.get(0).above();
		lvl.setDefaultSpawnPos(spawn, 0F);
		HyperboxWorldData.getOrCreate(lvl).setSpawnPoint(spawn);
	}


	private static boolean isFarEnough(BlockPos candidate, List<BlockPos> existing, int minDistSqRoot) {
		int minDistSq = minDistSqRoot * minDistSqRoot;
		for (BlockPos p : existing) {
			int dx = candidate.getX() - p.getX();
			int dz = candidate.getZ() - p.getZ();
			int distSq = dx*dx + dz*dz;
			if (distSq < minDistSq) return false;
		}
		return true;
	}

	private static void forceBoxChunks(ServerLevel lvl, BlockPos center, int radiusBlocks) {
		int minX = center.getX() - radiusBlocks;
		int minZ = center.getZ() - radiusBlocks;
		int maxX = center.getX() + radiusBlocks;
		int maxZ = center.getZ() + radiusBlocks;
		int minChunkX = minX >> 4;
		int minChunkZ = minZ >> 4;
		int maxChunkX = maxX >> 4;
		int maxChunkZ = maxZ >> 4;
		for (int cx=minChunkX; cx<=maxChunkX; cx++) {
			for (int cz=minChunkZ; cz<=maxChunkZ; cz++) {
				lvl.setChunkForced(cx, cz, true);
				// ensure FULL status
				lvl.getChunk(cx, cz);
			}
		}
	}

	private static void placeStructureCommand(ServerLevel lvl, MinecraftServer srv, ResourceLocation id, BlockPos pos) {
		String cmd = "place structure " + id + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
		CommandSourceStack css = srv.createCommandSourceStack().withLevel(lvl).withSuppressedOutput().withPermission(4);
		ParseResults<CommandSourceStack> parsed = srv.getCommands().getDispatcher().parse(new StringReader(cmd), css);
		srv.getCommands().performCommand(parsed, cmd);
	}




	private static void forceAreaChunks(ServerLevel lvl,BlockPos center,int boxSizeBlocks) {
		int half = boxSizeBlocks/2;
		int minX = center.getX()-half;
		int minZ = center.getZ()-half;
		int maxX = center.getX()+half;
		int maxZ = center.getZ()+half;
		int minChunkX = minX>>4;
		int minChunkZ = minZ>>4;
		int maxChunkX = maxX>>4;
		int maxChunkZ = maxZ>>4;
		for (int cx=minChunkX;cx<=maxChunkX;cx++) {
			for (int cz=minChunkZ;cz<=maxChunkZ;cz++) {
				lvl.setChunkForced(cx,cz,true);
				lvl.getChunk(cx,cz);
			}
		}
	}

	private static MinecraftServer sampleServer() { return ServerLifecycleHooks.getCurrentServer(); }

	private static ResourceLocation asRoomId(String id) { return new ResourceLocation(Hyperbox.MODID,"rooms/"+id); }

	private void copyFullTemplateWorld(ServerLevel lvl,String folder) {
		MinecraftServer srv = lvl.getServer();
		Path root = srv.getWorldPath(LevelResource.ROOT);
		Path src = Path.of("config/hyperbox_templates").resolve(folder);
		if (!Files.exists(src)) return;
		try {
			Path dst = root.resolve("dimensions").resolve(Hyperbox.MODID).resolve(lvl.dimension().location().getPath());
			Files.createDirectories(dst);
			for (String dir : List.of("region","entities")) {
				Path s = src.resolve(dir);
				if (!Files.exists(s)) continue;
				Path d = dst.resolve(dir);
				Files.createDirectories(d);
				try (Stream<Path> stream = Files.walk(s)) {
					stream.forEach(p->{
						try {
							Path q = d.resolve(s.relativize(p));
							if (Files.isDirectory(p)) Files.createDirectories(q);
							else if (p.toString().endsWith(".mca")) Files.copy(p,q,StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException ignored) {}
					});
				}
			}
		} catch (IOException ignored) {}
	}
}
