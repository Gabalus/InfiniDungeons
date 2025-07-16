package commoble.hyperbox;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Difficulty;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

public class CommonConfig {
	public final ForgeConfigSpec.ConfigValue<Boolean> autoForceHyperboxChunks;
	public final ForgeConfigSpec.ConfigValue<String> bedrockBlockId;
	public final ForgeConfigSpec.ConfigValue<String> stoneBlockId;
	public final ForgeConfigSpec.ConfigValue<String> dirtBlockId;
	public final ForgeConfigSpec.ConfigValue<String> grassBlockId;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> peacefulStructIds;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> easyStructIds;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> normalStructIds;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> hardStructIds;

	public CommonConfig(ForgeConfigSpec.Builder b) {
		autoForceHyperboxChunks = b.comment("Keep interior chunks force-loaded while parent chunk is loaded").define("auto_force_hyperbox_chunks", false);
		b.push("blocks");
		bedrockBlockId = b.define("bedrock_block", "minecraft:bedrock");
		stoneBlockId = b.define("stone_block", "minecraft:stone");
		dirtBlockId = b.define("dirt_block", "minecraft:dirt");
		grassBlockId = b.define("grass_block", "minecraft:grass_block");
		b.pop();
		b.push("structures");
		peacefulStructIds = b.defineList("peaceful", ()->List.of("minecraft:village_plains"), o->o instanceof String);
		easyStructIds = b.defineList("easy", ()->List.of("dungeoncrawl:dungeon"), o->o instanceof String);
		normalStructIds = b.defineList("normal", ()->List.of("overhauledstructures:overhauleddungeonspiders","dungeoncrawl:dungeon"), o->o instanceof String);
		hardStructIds = b.defineList("hard", ()->List.of("minecraft:ancient_city","minecraft:fortress"), o->o instanceof String);
		b.pop();
	}

	private static ResourceLocation rl(String s) {
		try { return ResourceLocation.tryParse(s); } catch (Exception e) { return new ResourceLocation("minecraft","air"); }
	}

	private static BlockState getBlock(MinecraftServer srv, String id, BlockState def) {
		if (srv==null) return def;
		Block b = srv.registryAccess().registryOrThrow(Registries.BLOCK).getOptional(rl(id)).orElse(def.getBlock());
		return b.defaultBlockState();
	}

	public BlockState bedrockBlock(MinecraftServer srv) { return getBlock(srv, bedrockBlockId.get(), Blocks.BEDROCK.defaultBlockState()); }
	public BlockState stoneBlock(MinecraftServer srv) { return getBlock(srv, stoneBlockId.get(), Blocks.STONE.defaultBlockState()); }
	public BlockState dirtBlock(MinecraftServer srv) { return getBlock(srv, dirtBlockId.get(), Blocks.DIRT.defaultBlockState()); }
	public BlockState grassBlock(MinecraftServer srv) { return getBlock(srv, grassBlockId.get(), Blocks.GRASS_BLOCK.defaultBlockState()); }

	public List<ResourceLocation> getStructurePoolForDifficulty(Difficulty d) {
		return switch(d) {
			case PEACEFUL -> toRls(peacefulStructIds.get());
			case EASY -> toRls(easyStructIds.get());
			case NORMAL -> toRls(normalStructIds.get());
			case HARD -> toRls(hardStructIds.get());
		};
	}

	private static List<ResourceLocation> toRls(List<? extends String> in) {
		if (in==null || in.isEmpty()) return List.of();
		List<ResourceLocation> out = new ArrayList<>(in.size());
		for (String s : in) out.add(rl(s));
		return out;
	}

	@Deprecated
	public Map<String, Map<String, List<String>>> collectThemePools(MinecraftServer srv) { return Map.of(); }
	@Deprecated
	public List<String> collectTemplateFolders(MinecraftServer srv) { return List.of(); }
}
