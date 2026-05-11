package commoble.hyperbox.discord;

import commoble.hyperbox.api.discord.DiscordCompletedEvent;
import commoble.hyperbox.api.discord.DiscordMetadata;
import commoble.hyperbox.dimension.HyperboxWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;

public final class DiscordRewards {
	private DiscordRewards() {}

	public static int completeDiscord(ServerPlayer player) {
		if (!DiscordProtectionEvents.isProtectedDiscord(player.serverLevel())) {
			player.sendSystemMessage(Component.literal("You are not inside a Discord.").withStyle(ChatFormatting.RED));
			return 0;
		}

		HyperboxWorldData data = HyperboxWorldData.getOrCreate(player.serverLevel());
		if (data.isDiscordCompleted()) {
			player.sendSystemMessage(Component.literal("This Discord has already been completed.").withStyle(ChatFormatting.YELLOW));
			return 0;
		}

		data.setDiscordCompleted(true);
		MinecraftForge.EVENT_BUS.post(new DiscordCompletedEvent(player, DiscordMetadata.of(player.serverLevel(), data)));
		giveMvpRewards(player, data);
		player.sendSystemMessage(Component.literal("Discord stabilized. Rewards granted.").withStyle(ChatFormatting.LIGHT_PURPLE));
		return 1;
	}

	private static void giveMvpRewards(ServerPlayer player, HyperboxWorldData data) {
		int tier = Math.max(1, data.getDiscordTier());

		ItemStack echoes = new ItemStack(Items.AMETHYST_SHARD, 4 + tier * 4);
		echoes.setHoverName(Component.literal("Echo Shard").withStyle(ChatFormatting.LIGHT_PURPLE));
		giveOrDrop(player, echoes);

		ItemStack scroll = new ItemStack(Items.PAPER);
		scroll.setHoverName(Component.literal("Sealed Recipe Scroll").withStyle(ChatFormatting.GOLD));
		giveOrDrop(player, scroll);

		player.giveExperiencePoints(20 * tier);
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
