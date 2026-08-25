package gobby

import com.mojang.brigadier.CommandDispatcher
import gobby.events.CommandRegisterEvent
import gobby.events.core.EventManager
import gobby.features.ModuleManager.subscribeEventListeners
import gobby.features.skyblock.ModIdHider
import gobby.gui.click.ConfigManager
import gobby.pathfinder.etherwarp.DungeonEtherwarpPathfinder
import gobby.pathfinder.etherwarp.DungeonPathPlanningExecutor
import gobby.pathfinder.etherwarp.EtherwarpPathfinder
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor
import gobby.features.dungeons.DungeonMap

import gobby.utils.LocationUtils
import gobby.utils.timer.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.ResourcePackActivationType
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandBuildContext
import net.minecraft.resources.Identifier as ResourceLocation
import org.slf4j.LoggerFactory
import kotlin.coroutines.EmptyCoroutineContext

class Gobbyclient : ClientModInitializer {

	override fun onInitializeClient() {
		logger.info("Hello Fabric world!")

		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent { container ->
			ResourceManagerHelper.registerBuiltinResourcePack(
				ResourceLocation.fromNamespaceAndPath(MOD_ID, "fonts"),
				container,
				ResourcePackActivationType.ALWAYS_ENABLED
			)
		}

		ModIdHider.applyToLoader()
		EtherwarpPathfinder.preload()
		DungeonEtherwarpPathfinder.preload(DungeonMap.grid, DungeonMap.revision)
		DungeonPathPlanningExecutor.preload()
		EtherwarpPathExecutor.preload()
		initEvents()
		EVENT_MANAGER.initEvents()
		subscribeEventListeners()
		ConfigManager.load()

		// Executors
		// TODO: Put these in an event
		Executor.schedule(20, repeat = true) { LocationUtils.update() }
	}

	private fun initEvents() {
		ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher: CommandDispatcher<FabricClientCommandSource>, _: CommandBuildContext ->

			@Suppress("UNCHECKED_CAST")
			EVENT_MANAGER.publish(CommandRegisterEvent(dispatcher as CommandDispatcher<FabricClientCommandSource?>))
		})

	}

	companion object {
		const val MOD_ID = "gobbyclient"
		const val MOD_VERSION = BuildConfig.MOD_VERSION

		val mc =  Minecraft.getInstance()
		val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

		@JvmStatic
		val logger = LoggerFactory.getLogger(MOD_ID)

		@JvmField
		val EVENT_MANAGER = EventManager()
	}
}
