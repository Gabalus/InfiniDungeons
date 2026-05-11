package commoble.hyperbox.api.discord;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

public class DiscordCompletedEvent extends Event {
	private final ServerPlayer player;
	private final DiscordMetadata metadata;

	public DiscordCompletedEvent(ServerPlayer player, DiscordMetadata metadata) {
		this.player = player;
		this.metadata = metadata;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	public DiscordMetadata getMetadata() {
		return metadata;
	}
}
