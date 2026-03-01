package gobby

import com.mojang.brigadier.CommandDispatcher
import gobby.events.CommandRegisterEvent
import gobby.events.core.EventManager
import gobby.features.ModuleManager.subscribeEventListeners
import gobby.features.skyblock.ModIdHider
import gobby.gui.click.ConfigManager

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
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import kotlin.coroutines.EmptyCoroutineContext

class Gobbyclient : ClientModInitializer {

	override fun onInitializeClient() {
		logger.info("Hello Fabric world!")

		FabricLoader.getInstance().getModContainer(MOD_ID).ifPresent { container ->
			ResourceManagerHelper.registerBuiltinResourcePack(
				Identifier.of(MOD_ID, "fonts"),
				container,
				ResourcePackActivationType.ALWAYS_ENABLED
			)
		}

		ModIdHider.applyToLoader()
		initEvents()
		EVENT_MANAGER.initEvents()
		subscribeEventListeners()
		ConfigManager.load()

		// Executors
		// TODO: Put these in an event
		Executor.schedule(20, repeat = true) { LocationUtils.update() }
	}

	private fun initEvents() {
		ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher: CommandDispatcher<FabricClientCommandSource?>?, _: CommandRegistryAccess? ->
			dispatcher?.let {
				EVENT_MANAGER.publish(CommandRegisterEvent(it))
			}
		})

	}

	companion object {
		const val MOD_ID = "gobbyclient"
		const val MOD_VERSION = "1.0.3"
		const val BETA_MODE = "development build"

		val mc =  MinecraftClient.getInstance()
		val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

		@JvmStatic
		val logger = LoggerFactory.getLogger(MOD_ID)

		@JvmField
		val EVENT_MANAGER = EventManager()
	}
}
