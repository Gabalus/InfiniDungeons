package commoble.hyperbox.dimension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class DungeonSpawnData extends SavedData {
    public int lastDungeonDay = -1;
    public int spawnCount = 0;
    public int lastX = 0, lastY = 0, lastZ = 0;
    public Component pendingMessage;

    public static DungeonSpawnData load(CompoundTag tag) {
        DungeonSpawnData data = new DungeonSpawnData();
        data.lastDungeonDay = tag.getInt("lastDungeonDay");
        data.spawnCount = tag.getInt("spawnCount");
        data.lastX = tag.getInt("lastX");
        data.lastY = tag.getInt("lastY");
        data.lastZ = tag.getInt("lastZ");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("lastDungeonDay", this.lastDungeonDay);
        tag.putInt("spawnCount", this.spawnCount);
        tag.putInt("lastX", this.lastX);
        tag.putInt("lastY", this.lastY);
        tag.putInt("lastZ", this.lastZ);
        return tag;
    }

    public static DungeonSpawnData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(DungeonSpawnData::load, DungeonSpawnData::new, "hyperbox_dungeon_spawn");
    }
}
