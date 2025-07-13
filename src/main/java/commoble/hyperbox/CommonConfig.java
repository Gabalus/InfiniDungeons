package commoble.hyperbox;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import java.util.List;
import java.util.Arrays;

public class CommonConfig {
	public final ConfigValue<Boolean> autoForceHyperboxChunks;
	public final ConfigValue<List<String>> roomList;

	public CommonConfig(ForgeConfigSpec.Builder builder) {
		builder.push("world_management");

		this.autoForceHyperboxChunks = builder
				.comment(
						"Enable automatic forceloading of hyperbox chunks.",
						"While this is enabled, the primary chunks of hyperbox worlds will be kept loaded while the",
						"parent hyperbox's chunk is loaded, and will be kept unloaded while the parent hyperbox's chunk",
						"is not loaded.",
						"If this is disabled, no automatic enabling or disabling of forceloading will be done. In this case,",
						"hyperbox's interiors will only tick while occupied by a player, or while forceloaded through",
						"other means.",
						"Be aware that if this option is changed from true to false while any hyperbox chunks are currently",
						"forceloaded, they will continue to be forceloaded until those chunks are manually un-forceloaded.")
				.define("auto_force_hyperbox_chunks", true);

		this.roomList = builder
				.comment(
						"List of room structures available for random selection.",
						"Add the structure names (without the namespace) of your custom rooms here.")
				.define("room_list", Arrays.asList("room1", "room2", "room3"));

		builder.pop();
	}
}
