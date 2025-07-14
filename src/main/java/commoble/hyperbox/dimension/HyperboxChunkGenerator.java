package commoble.hyperbox.dimension;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import commoble.hyperbox.Hyperbox;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jline.utils.Log;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public class HyperboxChunkGenerator extends ChunkGenerator
{
	public static final int BOX_Y  = 384;
	public static final int BOX_XZ = 240;
	final int BASE_Y = 1;

	public static final ChunkPos CHUNKPOS = new ChunkPos(0,0);
	public static final BlockPos CORNER   = CHUNKPOS.getWorldPosition();
	public static final BlockPos CENTER   = CORNER.offset(7,7,7);
	public static final BlockPos MIN_SPAWN_CORNER = CORNER.offset(1,1,1);
	public static final BlockPos MAX_SPAWN_CORNER = CORNER.offset(13,12,13);
	public static final long     CHUNKID = CHUNKPOS.toLong();

	private final Holder<Biome>  biome;
	private final MinecraftServer server;

	public static Codec<HyperboxChunkGenerator> makeCodec()
	{
		return Biome.CODEC.fieldOf("biome").xmap(HyperboxChunkGenerator::new, HyperboxChunkGenerator::biome).codec();
	}

	public HyperboxChunkGenerator(MinecraftServer srv)
	{
		super(new FixedBiomeSource(srv.overworld().getBiome(BlockPos.ZERO)));
		server = srv;
		biome  = srv.overworld().getBiome(BlockPos.ZERO);
	}

	public HyperboxChunkGenerator(Holder<Biome> b)
	{
		super(new FixedBiomeSource(b));
		biome  = b;
		server = null;
	}

	public Holder<Biome> biome(){ return biome; }

	@Override protected Codec<? extends ChunkGenerator> codec(){ return Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get(); }
	@Override public void applyCarvers(WorldGenRegion r,long s,RandomState rs,net.minecraft.world.level.biome.BiomeManager bm,StructureManager sm,ChunkAccess c,GenerationStep.Carving g){}
	@Override public void spawnOriginalMobs(WorldGenRegion r){}
	@Override public int  getGenDepth(){ return BOX_Y; }
	@Override public int  getSeaLevel(){ return 0; }
	@Override public int  getMinY(){ return 0; }
	@Override public int  getBaseHeight(int x,int z,Types t,LevelHeightAccessor l,RandomState rs){ return 0; }
	@Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor l,RandomState rs){ return new NoiseColumn(0,new BlockState[0]); }
	@Override public void addDebugScreenInfo(List<String> l,RandomState rs,BlockPos p){}
	@Nullable @Override public Pair<BlockPos,Holder<Structure>> findNearestMapStructure(ServerLevel l,HolderSet<Structure> s,BlockPos p,int r,boolean k){ return null; }
	@Override public void applyBiomeDecoration(WorldGenLevel w,ChunkAccess c,StructureManager s){}
	@Override public int  getSpawnHeight(LevelHeightAccessor l){ return 1; }
	@Override public void createReferences(WorldGenLevel w,StructureManager sm,ChunkAccess c){}
	@Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor e,Blender b,RandomState rs,StructureManager sm,ChunkAccess c){ return CompletableFuture.completedFuture(c); }

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager sm, RandomState rs, ChunkAccess chunk)
	{
		if (!chunk.getPos().equals(CHUNKPOS)) return;
		HyperboxWorldData d = HyperboxWorldData.getOrCreate(region.getLevel());
		if (!d.isGenerated() && !d.isPending()) d.setPending(true);
	}

	@Mod.EventBusSubscriber(modid = Hyperbox.MODID)
	public static class TickHandler
	{
		@SubscribeEvent
		public static void onLevelTick(LevelTickEvent e)
		{
			if (e.phase != LevelTickEvent.Phase.END) return;
			if (!(e.level instanceof ServerLevel lvl)) return;
			if (lvl.dimensionType() != HyperboxDimension.getDimensionType(lvl.getServer())) return;

			HyperboxWorldData d = HyperboxWorldData.getOrCreate(lvl);
			if (!d.isPending() || d.isGenerated()) return;

			HyperboxChunkGenerator.generateDungeonNow(lvl);

			d.setGenerated(true);
			d.setPending(false);
		}
	}

	public static void generateDungeonNow(ServerLevel level)
	{
		new HyperboxChunkGenerator(level.getServer()).generateDungeonLayout(level);
	}

	private void generateDungeonLayout(ServerLevel lvl)
	{
		MinecraftServer srv = lvl.getServer();

		List<String> templates = Hyperbox.INSTANCE.commonConfig.collectTemplateFolders(srv);
		if (!templates.isEmpty())
		{
			copyFullTemplateWorld(lvl, templates.get(lvl.random.nextInt(templates.size())));
			return;
		}

		Map<String,Map<String,List<String>>> themes = Hyperbox.INSTANCE.commonConfig.collectThemePools(srv);
		if (themes.isEmpty()) return;

		List<String> themeNames = new ArrayList<>(themes.keySet());
		Collections.shuffle(themeNames, new java.util.Random());
		String theme = themeNames.get(0);

		DungeonSpawnData ds = DungeonSpawnData.get(srv.getLevel(Level.OVERWORLD));
		int diff = /*ds.spawnCount<=3?1:ds.spawnCount<=6?2:3*/1;
		String diffKey = diff==1?"easy":diff==2?"medium":"hard";

		Map<String,List<String>> byDiff = themes.get(theme);
		List<String> pool = new ArrayList<>(byDiff.getOrDefault(diffKey, List.of()));
		if (pool.isEmpty()) byDiff.values().forEach(pool::addAll);
		if (pool.isEmpty()) return;

		StructureTemplateManager tm = lvl.getStructureManager();
		pool.removeIf(id ->
				tm.get(asRoomId(id))
						.map(t -> t.getSize().getY() > BOX_Y) //только высота
						.orElse(true)
		);
		if (pool.isEmpty()) return;

		int target = diff==1?5:diff==2?8:12;

		RandomSource rand = RandomSource.create();
		Set<ChunkPos> occ = new HashSet<>();
		class Conn{BlockPos p;Direction d;Conn(BlockPos p,Direction d){this.p=p;this.d=d;}}
		List<Conn> open = new ArrayList<>();

		String startId = pool.get(rand.nextInt(pool.size()));
		tm.get(asRoomId(startId)).ifPresent(t -> {
			Vec3i s = t.getSize();
			int dx = Math.max(0,(s.getX()-BOX_XZ)/2);
			int dz = Math.max(0,(s.getZ()-BOX_XZ)/2);
			BlockPos origin = new BlockPos(CORNER.getX()+1-dx, BASE_Y, CORNER.getZ()+1-dz);
			StructurePlaceSettings ps = new StructurePlaceSettings().setIgnoreEntities(false).setRandom(rand);
			loadChunksForTemplate(lvl, origin, s);
			t.placeInWorld(lvl, origin, origin, ps, rand, 2);
			markChunks(occ, origin, s);
			t.filterBlocks(origin, ps, Blocks.JIGSAW).forEach(b -> {
				lvl.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 3);
				open.add(new Conn(b.pos(), b.state().getValue(JigsawBlock.ORIENTATION).front()));
			});
		});

		java.util.Random jrand = new java.util.Random();
		int placed = 1;

		while (!open.isEmpty() && placed < target)
		{
			Conn c = open.remove(0);
			Direction need = c.d;
			Collections.shuffle(pool, jrand);

			for (String id : pool)
			{
				Optional<StructureTemplate> opt = tm.get(asRoomId(id));
				if (opt.isEmpty()) continue;
				StructureTemplate t = opt.get();

				for (Rotation rot : Rotation.values())
				{
					StructurePlaceSettings probe = new StructurePlaceSettings().setRotation(rot);
					List<StructureTemplate.StructureBlockInfo> jigs = t.filterBlocks(BlockPos.ZERO, probe, Blocks.JIGSAW);
					StructureTemplate.StructureBlockInfo exit = jigs.stream()
							.filter(j -> j.state().getValue(JigsawBlock.ORIENTATION).front() == need.getOpposite())
							.findFirst().orElse(null);
					if (exit == null) continue;

					BlockPos newOrg = c.p.subtract(exit.pos());
					Vec3i s = t.getSize(rot);
					if (s.getX()>BOX_XZ || s.getZ()>BOX_XZ || s.getY()>BOX_Y) continue;

					if (intersects(occ, newOrg, s)) continue;

					loadChunksForTemplate(lvl, newOrg, s);
					StructurePlaceSettings place = new StructurePlaceSettings().setRotation(rot).setRandom(rand);
					t.placeInWorld(lvl, newOrg, newOrg, place, rand, 2);
					placed++;
					markChunks(occ, newOrg, s);
					t.filterBlocks(newOrg, place, Blocks.JIGSAW).forEach(b -> {
						lvl.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 3);
						if (!b.pos().equals(c.p))
							open.add(new Conn(b.pos(), b.state().getValue(JigsawBlock.ORIENTATION).front()));
					});
					break;
				}
				if (placed >= target) break;
			}
		}
		occ.forEach(cp -> lvl.setChunkForced(cp.x, cp.z, false));
		sealBox(lvl, occ);
	}

	private static ResourceLocation asRoomId(String id)
	{
		return new ResourceLocation(Hyperbox.MODID, "rooms/"+id);
	}


	private void copyFullTemplateWorld(ServerLevel lvl, String folder)
	{
		MinecraftServer srv = lvl.getServer();
		Path root = srv.getWorldPath(LevelResource.ROOT);
		Path src  = Paths.get("config/hyperbox_templates").resolve(folder);
		if (!Files.exists(src)) return;

		try
		{
			Path dst = root.resolve("dimensions")
					.resolve(Hyperbox.MODID)
					.resolve(lvl.dimension().location().getPath());
			Files.createDirectories(dst);
			for (String dir : List.of("region","entities"))
			{
				Path s = src.resolve(dir);
				if (!Files.exists(s)) continue;
				Path d = dst.resolve(dir);
				Files.createDirectories(d);
				try (Stream<Path> st = Files.walk(s))
				{
					st.forEach(p -> {
						try
						{
							Path q = d.resolve(s.relativize(p));
							if (Files.isDirectory(p))
								Files.createDirectories(q);
							else if (p.toString().endsWith(".mca"))
								Files.copy(p, q, StandardCopyOption.REPLACE_EXISTING);
						}
						catch (IOException ignore){}
					});
				}
			}
		}
		catch (IOException ignore){}
	}

	private static void markChunks(Set<ChunkPos> set, BlockPos org, Vec3i size)
	{
		int minCX = org.getX() >> 4,                minCZ = org.getZ() >> 4;
		int maxCX = (org.getX()+size.getX()-1) >> 4, maxCZ = (org.getZ()+size.getZ()-1) >> 4;
		for (int cx=minCX; cx<=maxCX; cx++)
			for (int cz=minCZ; cz<=maxCZ; cz++)
				set.add(new ChunkPos(cx,cz));
	}

	private static boolean intersects(Set<ChunkPos> set, BlockPos org, Vec3i size)
	{
		int minCX = org.getX() >> 4,                minCZ = org.getZ() >> 4;
		int maxCX = (org.getX()+size.getX()-1) >> 4, maxCZ = (org.getZ()+size.getZ()-1) >> 4;
		for (int cx=minCX; cx<=maxCX; cx++)
			for (int cz=minCZ; cz<=maxCZ; cz++)
				if (set.contains(new ChunkPos(cx,cz))) return true;
		return false;
	}

	private static void sealBox(ServerLevel l, Set<ChunkPos> occ)
	{
		int minCX=Integer.MAX_VALUE,minCZ=Integer.MAX_VALUE,maxCX=Integer.MIN_VALUE,maxCZ=Integer.MIN_VALUE;
		for (ChunkPos cp : occ)
		{
			if (cp.x < minCX) minCX = cp.x;
			if (cp.z < minCZ) minCZ = cp.z;
			if (cp.x > maxCX) maxCX = cp.x;
			if (cp.z > maxCZ) maxCZ = cp.z;
		}
		int minX=minCX*16, minZ=minCZ*16, maxX=maxCX*16+15, maxZ=maxCZ*16+15;
		int minY=l.getMinBuildHeight(), maxY=l.getMaxBuildHeight()-1;

		for (int x=minX; x<=maxX; x++)
			for (int z=minZ; z<=maxZ; z++)
			{
				l.setBlock(new BlockPos(x,minY,z), Blocks.BEDROCK.defaultBlockState(), 3);
				l.setBlock(new BlockPos(x,maxY,z), Blocks.BEDROCK.defaultBlockState(), 3);
			}

		for (int y=minY; y<=maxY; y++)
		{
			for (int x=minX; x<=maxX; x++)
			{
				l.setBlock(new BlockPos(x,y,minZ), Blocks.BEDROCK.defaultBlockState(), 3);
				l.setBlock(new BlockPos(x,y,maxZ), Blocks.BEDROCK.defaultBlockState(), 3);
			}
			for (int z=minZ; z<=maxZ; z++)
			{
				l.setBlock(new BlockPos(minX,y,z), Blocks.BEDROCK.defaultBlockState(), 3);
				l.setBlock(new BlockPos(maxX,y,z), Blocks.BEDROCK.defaultBlockState(), 3);
			}
		}
	}




	private static void loadChunksForTemplate(ServerLevel lvl,
											  BlockPos origin,
											  Vec3i size)
	{
		int minCX = (origin.getX() - 16)        >> 4;
		int minCZ = (origin.getZ() - 16)        >> 4;
		int maxCX = (origin.getX() + size.getX()+15) >> 4;
		int maxCZ = (origin.getZ() + size.getZ()+15) >> 4;

		for (int cx = minCX; cx <= maxCX; cx++)
			for (int cz = minCZ; cz <= maxCZ; cz++)
			{
				lvl.setChunkForced(cx, cz, true);
				lvl.getChunk(cx, cz);
			}
	}

}
