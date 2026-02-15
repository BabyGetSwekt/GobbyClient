package gobby

import com.mojang.brigadier.CommandDispatcher
import gobby.config.GobbyConfig
import gobby.events.CommandRegisterEvent
import gobby.events.core.EventManager
import gobby.features.ModuleManager.subscribeEventListeners
import gobby.utils.LocationUtils
import gobby.utils.timer.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.command.CommandRegistryAccess
import org.slf4j.LoggerFactory
import kotlin.coroutines.EmptyCoroutineContext

class Gobbyclient : ClientModInitializer {

	override fun onInitializeClient() {
		logger.info("Hello Fabric world!")
		config.init()
		initEvents()
		EVENT_MANAGER.initEvents()
		subscribeEventListeners()


		// Executors
		Executor.schedule(20, repeat = true) { LocationUtils.update() } // TODO: Put it in an event
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
		const val MOD_VERSION = "1.0.0"
		const val BETA_MODE = "development build"

		val mc =  MinecraftClient.getInstance()
		val scope = CoroutineScope(SupervisorJob() + EmptyCoroutineContext)

		@JvmStatic
		val logger = LoggerFactory.getLogger(MOD_ID)

		@JvmField
		val EVENT_MANAGER = EventManager()

		@JvmField
		val config = GobbyConfig

	}
}