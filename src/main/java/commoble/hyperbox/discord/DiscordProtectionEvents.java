package commoble.hyperbox.discord;

import commoble.hyperbox.Hyperbox;
import commoble.hyperbox.dimension.HyperboxDimension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Hyperbox.MODID)
public final class DiscordProtectionEvents {
	private DiscordProtectionEvents() {}

	public static boolean isProtectedDiscord(ServerLevel level) {
		return Hyperbox.INSTANCE != null
			&& Hyperbox.INSTANCE.commonConfig.protectDiscordBlocks.get()
			&& level.dimensionType() == HyperboxDimension.getDimensionType(level.getServer());
	}

	@SubscribeEvent
	public static void onBreakBlock(BlockEvent.BreakEvent event) {
		if (event.getLevel() instanceof ServerLevel level && isProtectedDiscord(level)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
		if (event.getLevel() instanceof ServerLevel level && isProtectedDiscord(level)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onLivingDestroyBlock(LivingDestroyBlockEvent event) {
		if (event.getEntity().level() instanceof ServerLevel level && isProtectedDiscord(level)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
		if (event.getLevel() instanceof ServerLevel level && isProtectedDiscord(level)) {
			event.getAffectedBlocks().clear();
		}
	}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		if (!(event.getLevel() instanceof ServerLevel level) || !isProtectedDiscord(level)) {
			return;
		}

		if (event.getItemStack().getItem() instanceof BucketItem) {
			event.setCanceled(true);
			event.setCancellationResult(InteractionResult.FAIL);
		}
	}
}
