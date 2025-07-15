package commoble.hyperbox.dimension;

import commoble.hyperbox.Hyperbox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class HyperboxWorldData extends SavedData {
	private static final String PENDING = "pending";
	private static final String GENERATED = "generated";
	private static final String PARENT_WORLD = "parent_world";
	private static final String PARENT_POS = "parent_pos";
	private static final String SPAWN_X = "spawn_x";
	private static final String SPAWN_Y = "spawn_y";
	private static final String SPAWN_Z = "spawn_z";
	private boolean pending;
	private boolean generated;
	private ResourceKey<Level> parentWorld = Level.OVERWORLD;
	private BlockPos parentPos = BlockPos.ZERO;
	private BlockPos spawnPoint = HyperboxChunkGenerator.CENTER;

	public static HyperboxWorldData getOrCreate(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(HyperboxWorldData::load, HyperboxWorldData::new, Hyperbox.MODID);
	}

	private HyperboxWorldData() {}

	public static HyperboxWorldData load(CompoundTag tag) {
		HyperboxWorldData d = new HyperboxWorldData();
		d.pending = tag.getBoolean(PENDING);
		d.generated = tag.getBoolean(GENERATED);
		if (tag.contains(PARENT_WORLD))
			d.parentWorld = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString(PARENT_WORLD)));
		if (tag.contains(PARENT_POS))
			d.parentPos = BlockPos.of(tag.getLong(PARENT_POS));
		if (tag.contains(SPAWN_X))
			d.spawnPoint = new BlockPos(tag.getInt(SPAWN_X), tag.getInt(SPAWN_Y), tag.getInt(SPAWN_Z));
		return d;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		tag.putBoolean(PENDING, pending);
		tag.putBoolean(GENERATED, generated);
		tag.putString(PARENT_WORLD, parentWorld.location().toString());
		tag.putLong(PARENT_POS, parentPos.asLong());
		tag.putInt(SPAWN_X, spawnPoint.getX());
		tag.putInt(SPAWN_Y, spawnPoint.getY());
		tag.putInt(SPAWN_Z, spawnPoint.getZ());
		return tag;
	}

	public boolean isPending() { return pending; }
	public boolean isGenerated() { return generated; }
	public void setPending(boolean b) { pending = b; setDirty(); }
	public void setGenerated(boolean b) { generated = b; setDirty(); }
	public ResourceKey<Level> getParentWorld() { return parentWorld; }
	public BlockPos getParentPos() { return parentPos; }

	public void setWorldPos(MinecraftServer srv, ServerLevel thisWorld, ResourceKey<Level> thisKey, ResourceKey<Level> parentKey, BlockPos parentPosIn, int color) {
		parentWorld = parentKey;
		parentPos = parentPosIn;
		setDirty();
	}

	public void setSpawnPoint(BlockPos pos) {
		spawnPoint = pos.immutable();
		setDirty();
	}

	public java.util.Optional<BlockPos> getSpawnPoint() {
		return java.util.Optional.ofNullable(spawnPoint);
	}
}
