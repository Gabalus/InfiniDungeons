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

		// hyperbox:rooms/<theme>/<difficulty>/<room>.nbt
		rm.listResources("rooms", rl -> rl.getPath().endsWith(".nbt"))
				.keySet().forEach(rl ->
				{
					String[] parts = rl.getPath().split("/");
					if (parts.length != 4) return;           // rooms/theme/diff/room.nbt
					String theme = parts[1];
					String diff  = parts[2];
					String room  = parts[3].replace(".nbt", "");
					map.computeIfAbsent(theme, k -> new HashMap<>())
							.computeIfAbsent(diff,  k -> new ArrayList<>())
							.add(room);
				});
		return map;
	}

	public List<String> collectTemplateFolders(MinecraftServer srv)
	{
		ResourceManager rm = srv.getResourceManager();
		Set<String> out = new HashSet<>();

		// hyperbox:world_templates/<template>/region/…
		rm.listResources("world_templates", rl -> rl.getPath().endsWith(".mca"))
				.keySet().forEach(rl ->
				{
					String[] parts = rl.getPath().split("/");
					if (parts.length < 3) return;            // world_templates/template/…
					out.add(parts[1]);                       // имя шаблона
				});
		return List.copyOf(out);
	}
}
