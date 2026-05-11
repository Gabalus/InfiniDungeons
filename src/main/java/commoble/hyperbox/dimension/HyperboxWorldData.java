package commoble.hyperbox.dimension;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.api.discord.DiscordWorldDataView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

public class HyperboxWorldData extends SavedData implements DiscordWorldDataView {
	private static final String PENDING = "pending";
	private static final String GENERATED = "generated";
	private static final String PARENT_WORLD = "parent_world";
	private static final String PARENT_POS = "parent_pos";
	private static final String SPAWN_X = "spawn_x";
	private static final String SPAWN_Y = "spawn_y";
	private static final String SPAWN_Z = "spawn_z";
	private static final String DISCORD_THEME = "discord_theme";
	private static final String DISCORD_TIER = "discord_tier";
	private static final String DISCORD_COMPLETED = "discord_completed";
	private static final String DISCORD_CREATED_BY_PLAYER = "discord_created_by_player";
	private static final String DISCORD_SOURCE = "discord_source";

	private boolean pending;
	private boolean generated;
	private ResourceKey<Level> parentWorld = Level.OVERWORLD;
	private BlockPos parentPos = BlockPos.ZERO;
	private BlockPos spawnPoint = HyperboxChunkGenerator.CENTER;
	private String discordTheme = "random";
	private int discordTier = 1;
	private boolean discordCompleted;
	private boolean discordCreatedByPlayer;
	private String discordSource = "natural";

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
		if (tag.contains(DISCORD_THEME))
			d.discordTheme = tag.getString(DISCORD_THEME);
		if (tag.contains(DISCORD_TIER))
			d.discordTier = Math.max(1, tag.getInt(DISCORD_TIER));
		d.discordCompleted = tag.getBoolean(DISCORD_COMPLETED);
		d.discordCreatedByPlayer = tag.getBoolean(DISCORD_CREATED_BY_PLAYER);
		if (tag.contains(DISCORD_SOURCE))
			d.discordSource = tag.getString(DISCORD_SOURCE);
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
		tag.putString(DISCORD_THEME, discordTheme);
		tag.putInt(DISCORD_TIER, discordTier);
		tag.putBoolean(DISCORD_COMPLETED, discordCompleted);
		tag.putBoolean(DISCORD_CREATED_BY_PLAYER, discordCreatedByPlayer);
		tag.putString(DISCORD_SOURCE, discordSource);
		return tag;
	}

	public boolean isPending() { return pending; }
	public boolean isGenerated() { return generated; }
	public void setPending(boolean b) { pending = b; setDirty(); }
	public void setGenerated(boolean b) { generated = b; setDirty(); }
	@Override public ResourceKey<Level> getParentWorld() { return parentWorld; }
	@Override public BlockPos getParentPos() { return parentPos; }

	public void setWorldPos(MinecraftServer srv, ServerLevel thisWorld, ResourceKey<Level> thisKey, ResourceKey<Level> parentKey, BlockPos parentPosIn, int color) {
		parentWorld = parentKey;
		parentPos = parentPosIn;
		setDirty();
	}

	public void setSpawnPoint(BlockPos pos) {
		spawnPoint = pos.immutable();
		setDirty();
	}

	@Override
	public java.util.Optional<BlockPos> getSpawnPoint() {
		return java.util.Optional.ofNullable(spawnPoint);
	}

	@Override
	public String getDiscordTheme() {
		return discordTheme;
	}

	public void setDiscordTheme(String theme) {
		discordTheme = theme == null || theme.isBlank() ? "random" : theme;
		setDirty();
	}

	@Override
	public int getDiscordTier() {
		return discordTier;
	}

	public void setDiscordTier(int tier) {
		discordTier = Math.max(1, tier);
		setDirty();
	}

	@Override
	public boolean isDiscordCompleted() {
		return discordCompleted;
	}

	public void setDiscordCompleted(boolean completed) {
		discordCompleted = completed;
		setDirty();
	}

	@Override
	public boolean isDiscordCreatedByPlayer() {
		return discordCreatedByPlayer;
	}

	public void setDiscordCreatedByPlayer(boolean createdByPlayer) {
		discordCreatedByPlayer = createdByPlayer;
		setDirty();
	}

	@Override
	public String getDiscordSource() {
		return discordSource;
	}

	public void setDiscordSource(String source) {
		discordSource = source == null || source.isBlank() ? "natural" : source;
		setDirty();
	}
}
