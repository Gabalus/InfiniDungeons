package commoble.hyperbox.api.discord;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public record DiscordMetadata(
	ResourceKey<Level> dimension,
	ResourceKey<Level> parentDimension,
	BlockPos parentPos,
	BlockPos spawnPos,
	String theme,
	int tier,
	String source,
	boolean createdByPlayer,
	boolean completed
) {
	public static DiscordMetadata of(ServerLevel level, DiscordWorldDataView data) {
		return new DiscordMetadata(
			level.dimension(),
			data.getParentWorld(),
			data.getParentPos(),
			data.getSpawnPoint().orElse(BlockPos.ZERO),
			data.getDiscordTheme(),
			data.getDiscordTier(),
			data.getDiscordSource(),
			data.isDiscordCreatedByPlayer(),
			data.isDiscordCompleted()
		);
	}
}
