package commoble.hyperbox.discord;

import com.mojang.brigadier.CommandDispatcher;
import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.dimension.HyperboxWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Hyperbox.MODID)
public final class DiscordCommands {
	private DiscordCommands() {}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		register(event.getDispatcher());
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("discord")
			.then(Commands.literal("info")
				.executes(ctx -> info(ctx.getSource())))
			.then(Commands.literal("complete")
				.requires(source -> source.hasPermission(2))
				.executes(ctx -> DiscordRewards.completeDiscord(ctx.getSource().getPlayerOrException())))
			.then(Commands.literal("return")
				.executes(ctx -> returnToParent(ctx.getSource()))));
	}

	private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!DiscordProtectionEvents.isProtectedDiscord(player.serverLevel())) {
			player.sendSystemMessage(Component.literal("You are not inside a Discord.").withStyle(ChatFormatting.RED));
			return 0;
		}

		HyperboxWorldData data = HyperboxWorldData.getOrCreate(player.serverLevel());
		player.sendSystemMessage(Component.literal("Discord").withStyle(ChatFormatting.LIGHT_PURPLE));
		player.sendSystemMessage(Component.literal("Theme: " + data.getDiscordTheme()).withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal("Tier: " + data.getDiscordTier()).withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal("Source: " + data.getDiscordSource()).withStyle(ChatFormatting.GRAY));
		player.sendSystemMessage(Component.literal("Completed: " + data.isDiscordCompleted()).withStyle(data.isDiscordCompleted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		return 1;
	}

	private static int returnToParent(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!DiscordProtectionEvents.isProtectedDiscord(player.serverLevel())) {
			player.sendSystemMessage(Component.literal("You are not inside a Discord.").withStyle(ChatFormatting.RED));
			return 0;
		}

		HyperboxWorldData data = HyperboxWorldData.getOrCreate(player.serverLevel());
		ServerLevel parent = player.getServer().getLevel(data.getParentWorld());
		if (parent == null) {
			parent = player.getServer().getLevel(Level.OVERWORLD);
		}
		if (parent == null) {
			player.sendSystemMessage(Component.literal("Could not find a return dimension.").withStyle(ChatFormatting.RED));
			return 0;
		}

		BlockPos parentPos = data.getParentPos();
		player.teleportTo(parent, parentPos.getX() + 0.5D, parentPos.getY() + 1.0D, parentPos.getZ() + 0.5D, player.getYRot(), player.getXRot());
		player.sendSystemMessage(Component.literal("Returned from Discord.").withStyle(ChatFormatting.LIGHT_PURPLE));
		return 1;
	}
}
