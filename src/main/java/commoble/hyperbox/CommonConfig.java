package commoble.hyperbox;

import net.minecraftforge.common.ForgeConfigSpec;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class CommonConfig
{
	public final ForgeConfigSpec.ConfigValue<Boolean> autoForceHyperboxChunks;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> worldTemplates;
	public final ForgeConfigSpec.ConfigValue<List<? extends String>> roomFolders;


	public CommonConfig(ForgeConfigSpec.Builder b)
	{
		b.push("general").push("world_management");

		autoForceHyperboxChunks = b.define("auto_force_hyperbox_chunks", false);

		worldTemplates = b.defineList("world_templates", List.of(), o -> o instanceof String);

		roomFolders = b.defineList("room_folders", List.of(), o -> o instanceof String);

		b.pop(2);
	}

	public record Entry(String theme,String difficulty,String room) {}

	public Map<String,Map<String,List<String>>> collectThemePools()
	{
		Map<String,Map<String,List<String>>> map = new HashMap<>();
		Path base = Paths.get("config/hyperbox/rooms");
		for (String folder : roomFolders.get())
		{
			Path themeDir = base.resolve(folder);
			if (!Files.isDirectory(themeDir)) continue;
			try (Stream<Path> st = Files.walk(themeDir,3))
			{
				st.filter(p -> p.toString().endsWith(".nbt")).forEach(p ->
				{
					Path rel = themeDir.relativize(p);
					if (rel.getNameCount() < 2) return;
					String diff = rel.getName(0).toString();
					String name = p.getFileName().toString().replace(".nbt","");
					map.computeIfAbsent(folder,k->new HashMap<>())
							.computeIfAbsent(diff,k->new ArrayList<>())
							.add(name);
				});
			}
			catch (Exception ignore) {}
		}
		return map;
	}

	public List<String> collectAllRooms()
	{
		Map<String,Map<String,List<String>>> pools = collectThemePools();
		List<String> list = new ArrayList<>();
		pools.forEach((t,dmap)-> dmap.forEach((d,rooms)-> rooms.forEach(r-> list.add(t+"/"+d+"/"+r))));
		return list;
	}
}
