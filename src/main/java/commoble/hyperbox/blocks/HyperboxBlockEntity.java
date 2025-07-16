package commoble.hyperbox.blocks;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.dimension.DelayedTeleportData;
import commoble.hyperbox.dimension.DungeonSpawnData;
import commoble.hyperbox.dimension.HyperboxDimension;
import commoble.hyperbox.dimension.HyperboxWorldData;
import commoble.hyperbox.dimension.ReturnPointCapability;
import commoble.hyperbox.dimension.TeleportHelper;
import commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Optional;

public class HyperboxBlockEntity extends BlockEntity implements Nameable {
	public static final String WORLD_KEY = "world_key";
	public static final String NAME = "CustomName";
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String COLOR = "color";
	private Optional<ResourceKey<Level>> levelKey = Optional.empty();
	private Optional<Component> name = Optional.empty();
	private int color = HyperboxBlockItem.DEFAULT_COLOR;
	private int[] weakPowerDUNSWE = {0,0,0,0,0,0};
	private int[] strongPowerDUNSWE = {0,0,0,0,0,0};

	public static HyperboxBlockEntity create(BlockPos pos, BlockState state) {
		return new HyperboxBlockEntity(Hyperbox.INSTANCE.hyperboxBlockEntityType.get(), pos, state);
	}

	public HyperboxBlockEntity(BlockEntityType<? extends HyperboxBlockEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public void updateDimensionAfterPlacingBlock() {
		if (this.level instanceof ServerLevel thisServerLevel) {
			MinecraftServer server = thisServerLevel.getServer();
			ServerLevel childLevel = this.getLevelIfKeySet(server);
			if (childLevel == null) return;
			if (Hyperbox.INSTANCE.commonConfig.autoForceHyperboxChunks.get()) {
				childLevel.getChunk(commoble.hyperbox.dimension.HyperboxChunkGenerator.CHUNKPOS.x, commoble.hyperbox.dimension.HyperboxChunkGenerator.CHUNKPOS.z);
				childLevel.setChunkForced(commoble.hyperbox.dimension.HyperboxChunkGenerator.CHUNKPOS.x, commoble.hyperbox.dimension.HyperboxChunkGenerator.CHUNKPOS.z, true);
				childLevel.getChunkSource().updateChunkForced(commoble.hyperbox.dimension.HyperboxChunkGenerator.CHUNKPOS, true);
			}
			BlockState thisState = this.getBlockState();
			for (Direction dir : Direction.values()) thisState.onNeighborChange(this.level, this.worldPosition, this.worldPosition.relative(dir));
			this.level.updateNeighbourForOutputSignal(this.worldPosition, thisState.getBlock());
			HyperboxBlock.notifyNeighborsOfStrongSignalChange(thisState, childLevel, this.worldPosition);
			for (Direction sideOfChildLevel : Direction.values()) this.getAperture(server, sideOfChildLevel).ifPresent(aperture -> {
				BlockPos aperturePos = aperture.getBlockPos();
				aperture.getBlockState().onNeighborChange(aperture.getLevel(), aperturePos, aperturePos.relative(sideOfChildLevel.getOpposite()));
			});
		}
	}

	public void setColor(int color) {
		if (this.color != color) {
			this.color = color;
			this.setChanged();
			BlockState state = this.getBlockState();
			this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
			this.level.setBlocksDirty(this.worldPosition, state, state);
		}
	}

	public int getColor() {
		return this.color;
	}

	public Optional<ResourceKey<Level>> getLevelKey() {
		return this.levelKey;
	}

	public void setLevelKey(ResourceKey<Level> key) {
		this.levelKey = Optional.ofNullable(key);
		if (this.level instanceof ServerLevel level) this.getLevelIfKeySet(level.getServer());
		this.setChanged();
	}

	@Override
	public Component getName() {
		return this.name.orElse(Component.translatable("block.hyperbox.hyperbox"));
	}

	@Override
	@Nullable
	public Component getCustomName() {
		return this.name.orElse(null);
	}

	public void setName(@Nullable Component name) {
		this.name = Optional.ofNullable(name);
		this.setChanged();
		this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
	}

	@Nullable
	public ServerLevel getLevelIfKeySet(MinecraftServer server) {
		return this.levelKey.map(key -> {
			ServerLevel targetWorld = this.getChildWorld(server, key);
			HyperboxWorldData.getOrCreate(targetWorld).setWorldPos(server, targetWorld, targetWorld.dimension(), this.level.dimension(), this.worldPosition, this.getColor());
			return targetWorld;
		}).orElse(null);
	}

	public ServerLevel getChildWorld(MinecraftServer server, ResourceKey<Level> key) {
		return InfiniverseAPI.get().getOrCreateLevel(server, key, () -> HyperboxDimension.createDimension(server));
	}

	public int getPower(boolean strong, Direction originalFace) {
		int output = (strong ? this.strongPowerDUNSWE : this.weakPowerDUNSWE)[originalFace.get3DDataValue()] - 1;
		return Mth.clamp(output, 0, 15);
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction worldSpaceFace) {
		if (worldSpaceFace != null) {
			BlockState thisState = this.getBlockState();
			Block thisBlock = thisState.getBlock();
			if (thisBlock instanceof HyperboxBlock hyperboxBlock && this.level instanceof ServerLevel serverLevel) {
				ServerLevel targetLevel = this.getLevelIfKeySet(serverLevel.getServer());
				if (targetLevel != null) {
					BlockPos targetPos = hyperboxBlock.getPosAdjacentToAperture(thisState, worldSpaceFace);
					BlockEntity delegateBlockEntity = targetLevel.getBlockEntity(targetPos);
					if (delegateBlockEntity != null) {
						Direction rotatedDirection = hyperboxBlock.getOriginalFace(thisState, worldSpaceFace);
						return delegateBlockEntity.getCapability(cap, rotatedDirection);
					}
				}
			}
		}
		return super.getCapability(cap, worldSpaceFace);
	}

	public Optional<ApertureBlockEntity> getAperture(MinecraftServer server, Direction sideOfChildLevel) {
		BlockPos aperturePos = commoble.hyperbox.dimension.HyperboxChunkGenerator.CENTER.relative(sideOfChildLevel, 7);
		ServerLevel targetLevel = this.getLevelIfKeySet(server);
		return targetLevel == null ? Optional.empty() : targetLevel.getBlockEntity(aperturePos) instanceof ApertureBlockEntity aperture ? Optional.of(aperture) : Optional.empty();
	}

	public void updatePower(int weakPower, int strongPower, Direction originalFace) {
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		if (thisBlock instanceof HyperboxBlock hyperboxBlock) {
			Direction worldSpaceFace = hyperboxBlock.getCurrentFacing(thisState, originalFace);
			int originalFaceIndex = originalFace.get3DDataValue();
			int oldWeakPower = this.weakPowerDUNSWE[originalFaceIndex];
			int oldStrongPower = this.strongPowerDUNSWE[originalFaceIndex];
			if (oldWeakPower != weakPower || oldStrongPower != strongPower) {
				this.weakPowerDUNSWE[originalFaceIndex] = weakPower;
				this.strongPowerDUNSWE[originalFaceIndex] = strongPower;
				this.setChanged();
				this.level.sendBlockUpdated(this.worldPosition, thisState, thisState, 3);
				if (net.minecraftforge.event.ForgeEventFactory.onNeighborNotify(this.level, this.worldPosition, thisState, EnumSet.of(originalFace), true).isCanceled()) return;
				BlockPos adjacentPos = this.worldPosition.relative(worldSpaceFace);
				this.level.neighborChanged(adjacentPos, thisBlock, this.worldPosition);
				this.level.updateNeighborsAtExceptFromFacing(adjacentPos, thisBlock, worldSpaceFace.getOpposite());
			}
		}
	}

	public void teleportPlayerOrOpenMenu(ServerPlayer player, Direction face) {
		ServerLevel overworld = player.serverLevel();
		MinecraftServer server = overworld.getServer();
		boolean justCreated = false;
		if (levelKey.isEmpty()) {
			DungeonSpawnData data = DungeonSpawnData.get(server.getLevel(Level.OVERWORLD));
			ResourceKey<Level> newKey = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Hyperbox.MODID, "dungeon_" + data.spawnCount));
			setLevelKey(newKey);
			updateDimensionAfterPlacingBlock();
			justCreated = true;
		}
		ServerLevel dungeon = getLevelIfKeySet(server);
		if (dungeon == null) return;
		HyperboxWorldData wd = HyperboxWorldData.getOrCreate(dungeon);
		if (justCreated || wd.isPending() || !wd.isGenerated()) {
			player.sendSystemMessage(Component.literal("Dungeon is still generating – please try again in a few seconds"));
			return;
		}
		BlockPos base = wd.getSpawnPoint().orElse(dungeon.getSharedSpawnPos());
		BlockPos safe = findSafeSpawn(dungeon, base, 64, 2);
		if (HyperboxDimension.getDimensionType(server) != overworld.dimensionType())
			player.getCapability(ReturnPointCapability.INSTANCE).ifPresent(c -> c.setReturnPoint(overworld.dimension(), getBlockPos()));
		ChunkPos cp = new ChunkPos(safe);
		dungeon.getChunk(cp.x, cp.z);
		DelayedTeleportData.getOrCreate(overworld).schedulePlayerTeleport(player, dungeon.dimension(), Vec3.atCenterOf(safe));
	}

	private static BlockPos findSafeSpawn(ServerLevel lvl, BlockPos start, int horizRadius, int vertPad) {
		ChunkPos scp = new ChunkPos(start);
		lvl.getChunk(scp.x, scp.z);
		BlockPos primary = heightmapPos(lvl, start.getX(), start.getZ());
		if (isSafeSpawnBlock(lvl, primary, vertPad)) return primary;
		int sx = start.getX();
		int sz = start.getZ();
		int r = 1;
		while (r <= horizRadius) {
			for (int dx = -r; dx <= r; dx++) {
				if (Math.abs(dx) != r) continue;
				BlockPos p1 = heightmapPos(lvl, sx + dx, sz + r);
				if (isSafeSpawnBlock(lvl, p1, vertPad)) return p1.immutable();
				BlockPos p2 = heightmapPos(lvl, sx + dx, sz - r);
				if (isSafeSpawnBlock(lvl, p2, vertPad)) return p2.immutable();
			}
			for (int dz = -r+1; dz <= r-1; dz++) {
				BlockPos p3 = heightmapPos(lvl, sx + r, sz + dz);
				if (isSafeSpawnBlock(lvl, p3, vertPad)) return p3.immutable();
				BlockPos p4 = heightmapPos(lvl, sx - r, sz + dz);
				if (isSafeSpawnBlock(lvl, p4, vertPad)) return p4.immutable();
			}
			r++;
		}
		int top = lvl.getMaxBuildHeight();
		int bottom = lvl.getMinBuildHeight();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(start.getX(), top, start.getZ());
		for (int y = top; y >= bottom; --y) {
			m.setY(y);
			if (isSafeSpawnBlock(lvl, m, vertPad)) return m.immutable();
		}
		return start;
	}

	private static BlockPos heightmapPos(ServerLevel lvl, int x, int z) {
		int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		return new BlockPos(x, y, z);
	}

	private static boolean isSafeSpawnBlock(ServerLevel lvl, BlockPos pos, int vertPad) {
		ChunkPos cp = new ChunkPos(pos);
		if (!lvl.hasChunk(cp.x, cp.z)) lvl.getChunk(cp.x, cp.z);
		BlockPos below = pos.below();
		BlockState belowState = lvl.getBlockState(below);
		boolean sturdy = belowState.isFaceSturdy(lvl, below, Direction.UP) || !belowState.getCollisionShape(lvl, below).isEmpty();
		if (!sturdy) lvl.setBlock(below, Blocks.STONE.defaultBlockState(), 3);
		for (int i = 0; i < vertPad; i++) {
			BlockPos p = pos.above(i);
			if (!lvl.isEmptyBlock(p)) return false;
		}
		return true;
	}

	@Override
	public void saveAdditional(CompoundTag compound) {
		super.saveAdditional(compound);
		this.levelKey.ifPresent(key -> compound.putString(WORLD_KEY, key.location().toString()));
		this.writeClientSensitiveData(compound);
	}

	@Override
	public void load(CompoundTag nbt) {
		super.load(nbt);
		this.levelKey = nbt.contains(WORLD_KEY) ? Optional.of(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(nbt.getString(WORLD_KEY)))) : Optional.empty();
		this.readClientSensitiveData(nbt);
	}

	protected CompoundTag writeClientSensitiveData(CompoundTag nbt) {
		this.name.ifPresent(theName -> nbt.putString(NAME, Component.Serializer.toJson(theName)));
		if (this.color != HyperboxBlockItem.DEFAULT_COLOR) nbt.putInt(COLOR, this.color);
		nbt.putIntArray(WEAK_POWER, this.weakPowerDUNSWE);
		nbt.putIntArray(STRONG_POWER, this.strongPowerDUNSWE);
		return nbt;
	}

	protected void readClientSensitiveData(CompoundTag nbt) {
		this.name = nbt.contains(NAME) ? Optional.ofNullable(Component.Serializer.fromJson(nbt.getString(NAME))) : Optional.empty();
		this.color = nbt.contains(COLOR) ? nbt.getInt(COLOR) : HyperboxBlockItem.DEFAULT_COLOR;
		this.weakPowerDUNSWE = nbt.getIntArray(WEAK_POWER);
		this.strongPowerDUNSWE = nbt.getIntArray(STRONG_POWER);
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag nbt = super.getUpdateTag();
		this.writeClientSensitiveData(nbt);
		return nbt;
	}

	@Override
	public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt) {
		this.readClientSensitiveData(pkt.getTag());
	}

	@Override
	public void handleUpdateTag(CompoundTag nbt) {
		this.readClientSensitiveData(nbt);
	}
}
