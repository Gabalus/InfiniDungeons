package commoble.hyperbox.api.discord;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public interface DiscordWorldDataView {
	ResourceKey<Level> getParentWorld();
	BlockPos getParentPos();
	Optional<BlockPos> getSpawnPoint();
	String getDiscordTheme();
	int getDiscordTier();
	String getDiscordSource();
	boolean isDiscordCreatedByPlayer();
	boolean isDiscordCompleted();
}
