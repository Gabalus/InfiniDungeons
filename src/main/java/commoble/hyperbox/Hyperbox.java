package commoble.hyperbox;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;

import commoble.hyperbox.blocks.ApertureBlock;
import commoble.hyperbox.blocks.ApertureBlockEntity;
import commoble.hyperbox.blocks.HyperboxBlock;
import commoble.hyperbox.blocks.HyperboxBlockEntity;
import commoble.hyperbox.blocks.HyperboxBlockItem;
import commoble.hyperbox.blocks.HyperboxMenu;
import commoble.hyperbox.client.ClientProxy;
import commoble.hyperbox.dimension.*;
import commoble.hyperbox.network.C2SSaveHyperboxPacket;
import commoble.hyperbox.network.PacketSerializer;
import commoble.infiniverse.api.InfiniverseAPI;
import commoble.infiniverse.api.UnregisterDimensionEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.LevelTickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig.Type;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Random;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mod(Hyperbox.MODID)
public class Hyperbox
{
	public static final String MODID = "hyperbox";
	public static Hyperbox INSTANCE;
	
	public static final String PROTOCOL_VERSION = "4.0.1";
	public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
		new ResourceLocation(MODID,MODID),
		() -> PROTOCOL_VERSION,
		PROTOCOL_VERSION::equals,
		PROTOCOL_VERSION::equals);
	
	public static final ResourceLocation HYPERBOX_ID = new ResourceLocation(MODID, Names.HYPERBOX);
	public static final ResourceKey<Biome> BIOME_KEY = ResourceKey.create(Registries.BIOME, HYPERBOX_ID);
	public static final ResourceKey<Level> WORLD_KEY = ResourceKey.create(Registries.DIMENSION, HYPERBOX_ID);
	public static final ResourceKey<LevelStem> DIMENSION_KEY = ResourceKey.create(Registries.LEVEL_STEM, HYPERBOX_ID);
	public static final ResourceKey<DimensionType> DIMENSION_TYPE_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, HYPERBOX_ID);
	
	public final CommonConfig commonConfig;
	public final RegistryObject<HyperboxBlock> hyperboxBlock;
	public final RegistryObject<HyperboxBlock> hyperboxPreviewBlock;
	public final RegistryObject<ApertureBlock> apertureBlock;
	public final RegistryObject<BlockItem> hyperboxItem;
	public final RegistryObject<BlockEntityType<HyperboxBlockEntity>> hyperboxBlockEntityType;
	public final RegistryObject<BlockEntityType<ApertureBlockEntity>> apertureBlockEntityType;
	public final RegistryObject<MenuType<HyperboxMenu>> hyperboxMenuType;
	public final RegistryObject<Codec<HyperboxChunkGenerator>> hyperboxChunkGeneratorCodec;
	
	public Hyperbox()
	{
		INSTANCE = this;
		
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		IEventBus forgeBus = MinecraftForge.EVENT_BUS;
		
		this.commonConfig = ConfigHelper.register(Type.COMMON, CommonConfig::new);

		DeferredRegister<SoundEvent> soundEvents = makeVanillaRegister(modBus, Registries.SOUND_EVENT);
		DeferredRegister<Block> blocks = makeRegister(modBus, ForgeRegistries.BLOCKS);
		DeferredRegister<Item> items = makeRegister(modBus, ForgeRegistries.ITEMS);
		DeferredRegister<BlockEntityType<?>> tileEntities = makeRegister(modBus, ForgeRegistries.BLOCK_ENTITY_TYPES);
		DeferredRegister<MenuType<?>> menuTypes = makeRegister(modBus, ForgeRegistries.MENU_TYPES);
		DeferredRegister<Codec<? extends ChunkGenerator>> chunkGeneratorCodecs = makeVanillaRegister(modBus, Registries.CHUNK_GENERATOR);
		
		soundEvents.register("ambience", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "ambience")));
		
		this.hyperboxBlock = blocks.register(Names.HYPERBOX, () -> new HyperboxBlock(BlockBehaviour.Properties.copy(Blocks.PURPUR_BLOCK).strength(2F, 1200F).isRedstoneConductor(HyperboxBlock::getIsNormalCube)));
		this.hyperboxPreviewBlock = blocks.register(Names.HYPERBOX_PREVIEW, () -> new HyperboxBlock(BlockBehaviour.Properties.copy(Blocks.PURPUR_BLOCK).strength(2F, 1200F).isRedstoneConductor(HyperboxBlock::getIsNormalCube)));
		this.hyperboxItem = items.register(Names.HYPERBOX, () -> new HyperboxBlockItem(this.hyperboxBlock.get(), new Item.Properties()));
		this.hyperboxBlockEntityType = tileEntities.register(Names.HYPERBOX, () -> BlockEntityType.Builder.of(HyperboxBlockEntity::create, this.hyperboxBlock.get()).build(null));
		
		this.apertureBlock = blocks.register(Names.APERTURE, () -> new ApertureBlock(BlockBehaviour.Properties.copy(Blocks.BARRIER).lightLevel(state -> 6).isRedstoneConductor(HyperboxBlock::getIsNormalCube)));
		this.apertureBlockEntityType = tileEntities.register(Names.APERTURE, () -> BlockEntityType.Builder.of(ApertureBlockEntity::create, this.apertureBlock.get()).build(null));
		
		this.hyperboxMenuType = menuTypes.register(Names.HYPERBOX, () -> new MenuType<HyperboxMenu>(HyperboxMenu::makeClientMenu, FeatureFlags.VANILLA_SET));
		
		this.hyperboxChunkGeneratorCodec = chunkGeneratorCodecs.register(Names.HYPERBOX, HyperboxChunkGenerator::makeCodec);

		modBus.addListener(this::onRegisterCapabilities);
		modBus.addListener(this::onBuildTabContents);
		forgeBus.addListener(this::onUnregisterDimension);
		forgeBus.addListener(EventPriority.HIGH, this::onHighPriorityWorldTick);
		forgeBus.addGenericListener(Entity.class, this::onAttachEntityCapabilities);

		if (FMLEnvironment.dist == Dist.CLIENT)
		{
			ClientProxy.doClientModInit(modBus, forgeBus);
		}

		int id = 0;
		PacketSerializer.register(id++, CHANNEL, C2SSaveHyperboxPacket.SERIALIZER);

		MinecraftForge.EVENT_BUS.addListener(this::onServerTick);

		MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
			ServerPlayer sp = (ServerPlayer)e.getEntity();
			ServerLevel ovw = sp.server.getLevel(Level.OVERWORLD);
			DungeonSpawnData d = DungeonSpawnData.get(ovw);
			if (d.pendingMessage != null)
			{
				sp.sendSystemMessage(d.pendingMessage);
				d.pendingMessage = null;
				d.setDirty();
			}
		});

	}

	private void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) return;

		ServerLevel overworld = server.overworld();
		DungeonSpawnData data = DungeonSpawnData.get(overworld);

		long day = overworld.getDayTime() / 24000;
		if (day == data.lastDungeonDay) return;

		data.lastDungeonDay = (int) day;
		data.spawnCount++;

		if (data.lastY != 0) {
			BlockPos old = new BlockPos(data.lastX, data.lastY, data.lastZ);
			if (overworld.getBlockState(old).is(Hyperbox.INSTANCE.hyperboxBlock.get()))
				overworld.setBlock(old, Blocks.AIR.defaultBlockState(), 3);
		}

		BlockPos base = overworld.getSharedSpawnPos();
		int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base.getX(), base.getZ()) + 1;
		BlockPos portalPos = new BlockPos(base.getX(), y, base.getZ());

		ChunkPos cp = new ChunkPos(portalPos);
		overworld.getChunk(cp.x, cp.z);

		boolean placed = overworld.setBlockAndUpdate(portalPos,
				Hyperbox.INSTANCE.hyperboxBlock.get().defaultBlockState());

		if (placed) {
			BlockEntity be = overworld.getBlockEntity(portalPos);
			if (be instanceof HyperboxBlockEntity box)
				box.updateDimensionAfterPlacingBlock();

			Component msg = Component.literal("Разлом открыт: X=" + portalPos.getX()
							+ " Y=" + portalPos.getY() + " Z=" + portalPos.getZ())
					.withStyle(ChatFormatting.LIGHT_PURPLE);

			if (server.getPlayerList().getPlayerCount() == 0)
				data.pendingMessage = msg;                 // покажем позже
			else
				server.getPlayerList().broadcastSystemMessage(msg, false);

			LOGGER.info("[Hyperbox] Portal spawned at {}", portalPos);

			data.lastX = portalPos.getX();
			data.lastY = portalPos.getY();
			data.lastZ = portalPos.getZ();
			data.setDirty();
		} else {
			LOGGER.warn("[Hyperbox] Failed to place portal at {}", portalPos);
		}
	}


	private void onRegisterCapabilities(RegisterCapabilitiesEvent event)
	{
		event.register(ReturnPointCapability.class);
	}
	
	private void onBuildTabContents(BuildCreativeModeTabContentsEvent event)
	{
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS)
		{
			event.accept(this.hyperboxItem);			
		}
	}
	
	private void onAttachEntityCapabilities(AttachCapabilitiesEvent<Entity> event)
	{
		Entity entity = event.getObject();
		if (entity instanceof Player)
		{
			event.addCapability(ReturnPointCapability.ID, new ReturnPointCapability());
		}
	}
	
	private void onUnregisterDimension(UnregisterDimensionEvent event)
	{
		ServerLevel level = event.getLevel();
		MinecraftServer server = level.getServer();
		DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
		// if this is a hyperbox dimension
		if (level.dimensionType() == hyperboxDimensionType)
		{
			// send players to their return points (we have specific points we want to return players to)
			// iterate over a copy of the player list as we're modifying the original
			for (ServerPlayer player : Lists.newArrayList(level.players()))
			{
				TeleportHelper.ejectPlayerFromDeadWorld(player);
			}
		}
	}
	
	private void onHighPriorityWorldTick(LevelTickEvent event)
	{
		Level level = event.level;
		if (level instanceof ServerLevel serverLevel)
		{
			if (event.phase == TickEvent.Phase.END)
			{
				this.onPreServerWorldTick(serverLevel);
			}
			else // phase == END
			{
				this.onPostServerWorldTick(serverLevel);
			}
		}
	}
	
	private void onPreServerWorldTick(ServerLevel level)
	{
		if (this.commonConfig.autoForceHyperboxChunks.get() && HyperboxDimension.getDimensionType(level.getServer()) == level.dimensionType())
		{
			boolean isChunkForced = level.getForcedChunks().contains(HyperboxChunkGenerator.CHUNKID);
			boolean shouldChunkBeForced = shouldHyperboxChunkBeForced(level);
			if (isChunkForced != shouldChunkBeForced)
			{
				level.setChunkForced(HyperboxChunkGenerator.CHUNKPOS.x, HyperboxChunkGenerator.CHUNKPOS.z, shouldChunkBeForced);
			}
		}
	}
	
	private void onPostServerWorldTick(@Nonnull ServerLevel level)
	{		
		MinecraftServer server = level.getServer();
		
		// cleanup unused hyperboxes
		if (shouldUnloadDimension(server, level))
		{
			ResourceKey<Level> key = level.dimension();
			// apparently if we unload a dimension then pending changes don't get saved
			// TODO fix this in infiniverse
			level.save(null, true, false);
			InfiniverseAPI.get().markDimensionForUnregistration(server, key);
		}
		
		// handle scheduled teleports
		DelayedTeleportData.tick(level);
	}
	
	public static boolean shouldUnloadDimension(MinecraftServer server, @Nonnull ServerLevel targetLevel)
	{
		// only unload hyperbox dimensions
		DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
		if (hyperboxDimensionType != targetLevel.dimensionType())
			return false;
		
		// only run this once a second as the getBlockEntity call flags the parent chunk to be kept loaded another tick
		// which prevents the hyperbox-chunk-unloader from running
		if ((targetLevel.getGameTime() + targetLevel.hashCode()) % 20 != 0)
			return false;
		
		HyperboxWorldData hyperboxData = HyperboxWorldData.getOrCreate(targetLevel);
		ResourceKey<Level> parentKey = hyperboxData.getParentWorld();
		BlockPos parentPos = hyperboxData.getParentPos();
		
		// if we can't find the parent world, unload the hyperbox dimension
		@Nullable ServerLevel parentLevel = server.getLevel(parentKey);
		if (parentLevel == null)
			return true;
		
		// don't load chunks in the tick event
		// if we can't check the chunk, we can't verify that the dimension should be unloaded
		if (!parentLevel.hasChunk(parentPos.getX()>>4, parentPos.getZ()>>4))
			return false;
		
		// if the te doesn't exist or isn't a hyperbox, return true and unload
		BlockEntity te = parentLevel.getBlockEntity(parentPos);
		if (!(te instanceof HyperboxBlockEntity hyperbox))
			return true;
		
		ResourceKey<Level> key = targetLevel.dimension();
		
		return hyperbox.getLevelKey()
			// if the te points to our dimension, return false and don't unload
			.map(childKey -> !childKey.equals(key))
			// if the te doesn't point anywhere, return true and unload
			.orElse(true);
	}

	@SuppressWarnings("deprecation")
	private static boolean shouldHyperboxChunkBeForced(ServerLevel hyperboxLevel)
	{
		MinecraftServer server = hyperboxLevel.getServer();
		HyperboxWorldData data = HyperboxWorldData.getOrCreate(hyperboxLevel);
		ResourceKey<Level> parentKey = data.getParentWorld();
		ServerLevel parentLevel = server.getLevel(parentKey);
		if (parentLevel == null)
			return false;
		
		BlockPos parentPos = data.getParentPos();	
		return parentLevel.hasChunkAt(parentPos);
	}
	
	// create and subscribe a forge DeferredRegister
	private static <T> DeferredRegister<T> makeRegister(IEventBus modBus, IForgeRegistry<T> registry)
	{
		DeferredRegister<T> register = DeferredRegister.create(registry, MODID);
		register.register(modBus);
		return register;
	}
	
	private static <T> DeferredRegister<T> makeVanillaRegister(IEventBus modBus, ResourceKey<Registry<T>> registryKey)
	{
		DeferredRegister<T> register = DeferredRegister.create(registryKey, MODID);
		register.register(modBus);
		return register;
	}
}
