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

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public class HyperboxChunkGenerator extends ChunkGenerator
{
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
	@Override public int  getGenDepth(){ return 16; }
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

	private void generateDungeonLayout(ServerLevel srvLevel)
	{
		MinecraftServer srv = srvLevel.getServer();

		List<? extends String> templates = Hyperbox.INSTANCE.commonConfig.worldTemplates.get();
		if (!templates.isEmpty())
		{
			String folder = templates.get(srvLevel.random.nextInt(templates.size()));
			Path root = srv.getWorldPath(LevelResource.ROOT);
			Path src  = Paths.get("config/hyperbox_templates").resolve(folder);
			if (Files.exists(src))
			{
				try
				{
					Path dst = root.resolve("dimensions").resolve(Hyperbox.MODID)
							.resolve(srvLevel.dimension().location().getPath());
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
									if (Files.isDirectory(p)) Files.createDirectories(q);
									else if (p.toString().endsWith(".mca"))
										Files.copy(p,q,StandardCopyOption.REPLACE_EXISTING);
								}
								catch (IOException ignore){}
							});
						}
					}
				}
				catch (IOException ignore){}
			}
			return;
		}

		Map<String,Map<String,List<String>>> themes = Hyperbox.INSTANCE.commonConfig.collectThemePools();
		if (themes.isEmpty()) return;

		List<String> themeNames = new ArrayList<>(themes.keySet());
		Collections.shuffle(themeNames, new java.util.Random());
		String theme = themeNames.get(0);

		DungeonSpawnData ds = DungeonSpawnData.get(srv.getLevel(Level.OVERWORLD));
		int idx = ds.spawnCount;
		int diff = idx<=3?1:idx<=6?2:3;
		String diffStr = diff==1?"easy":diff==2?"medium":"hard";

		Map<String,List<String>> byDiff = themes.get(theme);
		List<String> pool = new ArrayList<>(byDiff.getOrDefault(diffStr, List.of()));
		if (pool.isEmpty()) byDiff.values().forEach(pool::addAll);
		if (pool.isEmpty()) return;

		int target = diff==1?5:diff==2?8:12;

		StructureTemplateManager tm = srvLevel.getStructureManager();
		RandomSource rand = RandomSource.create();
		Set<ChunkPos> occ = new HashSet<>();
		class Conn{BlockPos p;Direction d;Conn(BlockPos p,Direction d){this.p=p;this.d=d;}}
		List<Conn> open = new ArrayList<>();

		String start = pool.get(rand.nextInt(pool.size()));
		tm.get(new ResourceLocation(Hyperbox.MODID,start)).ifPresent(t -> {
			BlockPos origin = BlockPos.ZERO;
			StructurePlaceSettings s = new StructurePlaceSettings().setIgnoreEntities(false)
					.setRotation(Rotation.NONE).setMirror(Mirror.NONE).setRandom(rand);
			loadChunksForTemplate(srvLevel,origin,t.getSize());
			t.placeInWorld(srvLevel,origin,origin,s,rand,2);
			Vec3i sz = t.getSize();
			int minCX=origin.getX()>>4,minCZ=origin.getZ()>>4;
			int maxCX=(origin.getX()+sz.getX()-1)>>4,maxCZ=(origin.getZ()+sz.getZ()-1)>>4;
			for(int cx=minCX;cx<=maxCX;cx++)for(int cz=minCZ;cz<=maxCZ;cz++)occ.add(new ChunkPos(cx,cz));
			for (StructureTemplate.StructureBlockInfo b : t.filterBlocks(origin,s,Blocks.JIGSAW))
			{
				Direction d=b.state().getValue(JigsawBlock.ORIENTATION).front();
				BlockPos  p=b.pos();
				srvLevel.setBlock(p,Blocks.AIR.defaultBlockState(),3);
				open.add(new Conn(p,d));
			}
		});

		int placed = 1;
		java.util.Random jrand = new java.util.Random();

		while(!open.isEmpty() && placed<target)
		{
			Conn c = open.remove(0);
			Direction need = c.d;
			boolean done = false;
			Collections.shuffle(pool,jrand);

			for(String name : pool)
			{
				Optional<StructureTemplate> opt = tm.get(new ResourceLocation(Hyperbox.MODID,name));
				if(opt.isEmpty()) continue;
				StructureTemplate t = opt.get();
				Direction opp = need.getOpposite();

				for(Rotation rot:Rotation.values())
				{
					StructurePlaceSettings probe = new StructurePlaceSettings().setRotation(rot);
					List<StructureTemplate.StructureBlockInfo> jigs = t.filterBlocks(BlockPos.ZERO,probe,Blocks.JIGSAW);
					StructureTemplate.StructureBlockInfo exit = jigs.stream()
							.filter(j->j.state().getValue(JigsawBlock.ORIENTATION).front()==opp)
							.findFirst().orElse(null);
					if(exit==null) continue;

					BlockPos newOrg = c.p.subtract(exit.pos());
					Vec3i sz = t.getSize(rot);
					int miCX=newOrg.getX()>>4,miCZ=newOrg.getZ()>>4;
					int maCX=(newOrg.getX()+sz.getX()-1)>>4,maCZ=(newOrg.getZ()+sz.getZ()-1)>>4;
					boolean clash=false;
					for(int cx=miCX;cx<=maCX&&!clash;cx++)
						for(int cz=miCZ;cz<=maCZ&&!clash;cz++)
							if(occ.contains(new ChunkPos(cx,cz))) clash=true;
					if(clash) continue;

					loadChunksForTemplate(srvLevel,newOrg,sz);
					StructurePlaceSettings place = new StructurePlaceSettings().setRotation(rot)
							.setIgnoreEntities(false).setMirror(Mirror.NONE).setRandom(rand);
					t.placeInWorld(srvLevel,newOrg,newOrg,place,rand,2);
					placed++;

					for(int cx=miCX;cx<=maCX;cx++)for(int cz=miCZ;cz<=maCZ;cz++)occ.add(new ChunkPos(cx,cz));
					for (StructureTemplate.StructureBlockInfo j : t.filterBlocks(newOrg,place,Blocks.JIGSAW))
					{
						Direction d=j.state().getValue(JigsawBlock.ORIENTATION).front();
						BlockPos  pp=j.pos();
						srvLevel.setBlock(pp,Blocks.AIR.defaultBlockState(),3);
						if(!pp.equals(c.p)) open.add(new Conn(pp,d));
					}
					done=true;
					break;
				}
				if(done) break;
			}
			if(!done) srvLevel.setBlock(c.p,Blocks.STONE_BRICKS.defaultBlockState(),3);
		}

		int minCX=Integer.MAX_VALUE,minCZ=Integer.MAX_VALUE,maxCX=Integer.MIN_VALUE,maxCZ=Integer.MIN_VALUE;
		for(ChunkPos cp:occ){minCX=Math.min(minCX,cp.x);minCZ=Math.min(minCZ,cp.z);maxCX=Math.max(maxCX,cp.x);maxCZ=Math.max(maxCZ,cp.z);}
		int minX=minCX*16,minZ=minCZ*16,maxX=maxCX*16+15,maxZ=maxCZ*16+15;
		int minY=srvLevel.getMinBuildHeight(),maxY=srvLevel.getMaxBuildHeight()-1;

		for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){
			srvLevel.setBlock(new BlockPos(x,minY,z),Blocks.BEDROCK.defaultBlockState(),3);
			srvLevel.setBlock(new BlockPos(x,maxY,z),Blocks.BEDROCK.defaultBlockState(),3);}
		for(int y=minY;y<=maxY;y++){
			for(int x=minX;x<=maxX;x++){
				srvLevel.setBlock(new BlockPos(x,y,minZ),Blocks.BEDROCK.defaultBlockState(),3);
				srvLevel.setBlock(new BlockPos(x,y,maxZ),Blocks.BEDROCK.defaultBlockState(),3);}
			for(int z=minZ;z<=maxZ;z++){
				srvLevel.setBlock(new BlockPos(minX,y,z),Blocks.BEDROCK.defaultBlockState(),3);
				srvLevel.setBlock(new BlockPos(maxX,y,z),Blocks.BEDROCK.defaultBlockState(),3);}
		}
	}

	private static void loadChunksForTemplate(ServerLevel lvl, BlockPos origin, Vec3i size)
	{
		int minCX=origin.getX()>>4,minCZ=origin.getZ()>>4;
		int maxCX=(origin.getX()+size.getX()-1)>>4,maxCZ=(origin.getZ()+size.getZ()-1)>>4;
		for(int cx=minCX;cx<=maxCX;cx++)
			for(int cz=minCZ;cz<=maxCZ;cz++)
				lvl.getChunk(cx,cz);
	}
}
