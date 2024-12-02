package commoble.hyperbox.dimension;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.blocks.ApertureBlock;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class HyperboxChunkGenerator extends ChunkGenerator
{
	public static final ChunkPos CHUNKPOS = new ChunkPos(0,0);
	public static final long CHUNKID = CHUNKPOS.toLong();
	public static final BlockPos CORNER = CHUNKPOS.getWorldPosition();
	public static final BlockPos CENTER = CORNER.offset(7, 7, 7);
	public static final BlockPos MIN_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(1,1,1);
	// don't want to spawn with head in the bedrock ceiling
	public static final BlockPos MAX_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(13,12,13);

	private final Holder<Biome> biome; public Holder<Biome> biome() { return biome; }
	
	/** get from Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get(); **/
	public static Codec<HyperboxChunkGenerator> makeCodec()
	{
		return Biome.CODEC.fieldOf("biome")
			.xmap(HyperboxChunkGenerator::new, HyperboxChunkGenerator::biome)
			.codec();
	}
	
	// hardcoding this for now, may reconsider later
	public int getHeight() { return 15; }
	
	// create chunk generator at runtime when dynamic dimension is created
	public HyperboxChunkGenerator(MinecraftServer server)
	{
		this(server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Hyperbox.BIOME_KEY));
	}

	// create chunk generator when dimension is loaded from the dimension registry on server init
	public HyperboxChunkGenerator(Holder<Biome> biome)
	{
		super(new FixedBiomeSource(biome));
		this.biome = biome;
	}

	// get codec
	@Override
	protected Codec<? extends ChunkGenerator> codec()
	{
		return Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get();
	}
	
	// apply carvers
	@Override
	public void applyCarvers(WorldGenRegion world, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess, GenerationStep.Carving carvingStep)
	{
		// noop
	}

	@Override
	public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureFeatureManager, RandomState random, ChunkAccess chunk) {
		ChunkPos chunkPos = chunk.getPos();
		if (chunkPos.equals(CHUNKPOS)) {
			// Get the StructureTemplateManager from the server level
			StructureTemplateManager templateManager = worldGenRegion.getLevel().getServer().getStructureManager();

			// Define the location of your structure NBT file
			ResourceLocation structureLocation = new ResourceLocation("hyperbox", "room3");
			ResourceLocation structureLocation1 = new ResourceLocation("hyperbox", "room2");
			Random randomSource = new Random();
			// Load the structure template
			Optional<StructureTemplate> optionalTemplate = templateManager.get(structureLocation);
			Optional<StructureTemplate> optionalTemplate1 = templateManager.get(structureLocation1);
			int nextRandom = randomSource.nextInt()%2;

			if (optionalTemplate.isPresent() && optionalTemplate1.isPresent()) {
				if(nextRandom==1) {
					StructureTemplate template = optionalTemplate.get();

					// Get the size of the structure
					Vec3i structureSize = template.getSize();


					BlockPos placementPos = chunkPos.getWorldPosition();

					// Create placement settings
					StructurePlaceSettings placementSettings = new StructurePlaceSettings()
							.setIgnoreEntities(false)
							.setMirror(Mirror.NONE)
							.setRotation(Rotation.NONE)
							.setRandom(worldGenRegion.getRandom())
							.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR); // Ignore structure void blocks

					// Place the structure in the world
					template.placeInWorld(worldGenRegion, placementPos, placementPos, placementSettings, worldGenRegion.getRandom(), 2);
				}
				else{
					StructureTemplate template = optionalTemplate1.get();

					// Get the size of the structure
					Vec3i structureSize = template.getSize();


					BlockPos placementPos = chunkPos.getWorldPosition();

					// Create placement settings
					StructurePlaceSettings placementSettings = new StructurePlaceSettings()
							.setIgnoreEntities(false)
							.setMirror(Mirror.NONE)
							.setRotation(Rotation.NONE)
							.setRandom(worldGenRegion.getRandom())
							.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR); // Ignore structure void blocks

					// Place the structure in the world
					template.placeInWorld(worldGenRegion, placementPos, placementPos, placementSettings, worldGenRegion.getRandom(), 2);
				}
			} else {
				// Handle the case where the structure is not found
				System.err.println("Structure not found: " + structureLocation);
			}
		}
	}




	@Override
	public void spawnOriginalMobs(WorldGenRegion region)
	{
		// NOOP
	}

	@Override
	public int getGenDepth() // total number of available y-levels (between bottom and top)
	{
		return 16;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk)
	{
		// this is where the flat chunk generator generates flat chunks
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public int getSeaLevel()
	{
		// only used by features' generate methods
		return 0;
	}

	@Override
	public int getMinY()
	{
		// the lowest y-level in the dimension
		// debug -> 0
		// flat -> 0
		// noise -> NoiseSettings#minY
			// overworld -> -64
			// nether -> 0
		return 0;
	}

	@Override
	public int getBaseHeight(int x, int z, Types heightmapType, LevelHeightAccessor level, RandomState random)
	{
		// flat chunk generator counts the solid blockstates in its list
		// debug chunk generator returns 0
		// the "normal" chunk generator generates a height via noise
		// we can assume that this is what is used to define the "initial" heightmap
		return 0;
	}

	// get base column
	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random)
	{
		// flat chunk generator returns a reader over its blockstate list
		// debug chunk generator returns a reader over an empty array
		// normal chunk generator returns a column whose contents are either default block, default fluid, or air
		
		return new NoiseColumn(0, new BlockState[0]);
	}

	@Override
	public void addDebugScreenInfo(List<String> stringsToRender, RandomState random, BlockPos pos)
	{
		// no info to add
	}
	
	// let's make sure some of the default chunk generator methods aren't doing
	// anything we don't want them to either

	// get structure position
	@Nullable
	@Override
	public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> structures, BlockPos pos, int range, boolean skipKnownStructures)
	{
		return null;
	}
	
	// decorate biomes with features
	@Override
	public void applyBiomeDecoration(WorldGenLevel world, ChunkAccess chunkAccess, StructureManager structures)
	{
		// noop
	}
	
	@Override
	public int getSpawnHeight(LevelHeightAccessor level)
	{
		return 1;
	}
	
	// create structure references
	@Override
	public void createReferences(WorldGenLevel world, StructureManager structures, ChunkAccess chunk)
	{
		// no structures
	}	
}
