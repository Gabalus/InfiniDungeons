package commoble.hyperbox;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.*;

public class CommonConfig
{
	public final ForgeConfigSpec.ConfigValue<Boolean> autoForceHyperboxChunks;

	public CommonConfig(ForgeConfigSpec.Builder b)
	{
		autoForceHyperboxChunks = b
				.comment("Keep interior chunks force-loaded while parent chunk is loaded")
				.define("auto_force_hyperbox_chunks", false);
	}

	public Map<String, Map<String, List<String>>> collectThemePools(MinecraftServer srv)
	{
		ResourceManager rm = srv.getResourceManager();
		Map<String, Map<String, List<String>>> map = new HashMap<>();

		// data/hyperbox/structures/rooms/<theme>/<difficulty>/<room>.nbt
		rm.listResources("structures/rooms", rl -> rl.getPath().endsWith(".nbt"))
				.keySet()
				.forEach(rl -> {
					String rel = rl.getPath()
							.substring("structures/rooms/".length(), rl.getPath().length() - 4); // trim prefix and ".nbt"
					String[] parts = rel.split("/");                         // theme / difficulty / room
					if (parts.length < 3) return;

					String theme = parts[0];
					String diff  = parts[1];

					map.computeIfAbsent(theme, k -> new HashMap<>())
							.computeIfAbsent(diff,  k -> new ArrayList<>())
							.add(rel);
				});

		return map;
	}



	public List<String> collectTemplateFolders(MinecraftServer srv)
	{
		ResourceManager rm = srv.getResourceManager();
		Set<String> names = new HashSet<>();

		// data/hyperbox/structures/world_templates/<template>/<file>.nbt
		rm.listResources("structures/world_templates", rl -> rl.getPath().endsWith(".nbt"))
				.keySet()
				.forEach(rl -> {
					String rel = rl.getPath().substring("structures/world_templates/".length()); // <template>/…
					int slash = rel.indexOf('/');
					if (slash > 0)                    // ensure we have "<template>/something"
						names.add(rel.substring(0, slash));
				});

		return List.copyOf(names);
	}

}
