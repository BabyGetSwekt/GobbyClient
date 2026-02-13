package gobby

import com.mojang.brigadier.CommandDispatcher
import gobby.commands.*
import gobby.commands.developer.TestPearl
import gobby.config.GobbyConfig
import gobby.events.CommandRegisterEvent
import gobby.events.core.EventManager
import gobby.events.listener.GlobalKeyHandler
import gobby.features.dungeons.*
import gobby.features.galatea.*
import gobby.features.render.*
import gobby.features.skyblock.*
import gobby.utils.LocationUtils
import gobby.utils.render.RenderBeacon
import gobby.utils.render.RenderBlock
import gobby.utils.render.Renderer
import gobby.utils.skyblock.dungeon.ScanUtils
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
		Executor.schedule(20, repeat = true) { LocationUtils.update() }
	}

	private fun initEvents() {
		ClientCommandRegistrationCallback.EVENT.register(ClientCommandRegistrationCallback { dispatcher: CommandDispatcher<FabricClientCommandSource?>?, _: CommandRegistryAccess? ->
			dispatcher?.let {
				EVENT_MANAGER.publish(CommandRegisterEvent(it))
			}
		})

	}

	// TODO: Move this into module manager
	private fun subscribeEventListeners() {
		//DevTest.init()
		EVENT_MANAGER.subscribe(GobbyCommand)
		EVENT_MANAGER.subscribe(RotateCommand)
		EVENT_MANAGER.subscribe(DevTestCommand)
		EVENT_MANAGER.subscribe(RenderMobs)
		EVENT_MANAGER.subscribe(Test)
		EVENT_MANAGER.subscribe(FullBright)
		EVENT_MANAGER.subscribe(GlobalKeyHandler)
		EVENT_MANAGER.subscribe(RenderSeaPickle)
		EVENT_MANAGER.subscribe(CoordWaypoints)
		EVENT_MANAGER.subscribe(Executor)
		EVENT_MANAGER.subscribe(TerminatorAC)
		EVENT_MANAGER.subscribe(PartyCommands)
		EVENT_MANAGER.subscribe(RenderBeacon)
		EVENT_MANAGER.subscribe(RenderBlock)
		EVENT_MANAGER.subscribe(LocationUtils)
		EVENT_MANAGER.subscribe(TestPearl)
		EVENT_MANAGER.subscribe(Renderer)
		EVENT_MANAGER.subscribe(ScanUtils)
		EVENT_MANAGER.subscribe(StarredMobEsp)
		EVENT_MANAGER.subscribe(MiniBossEsp)
		EVENT_MANAGER.subscribe(Pearls)
		EVENT_MANAGER.subscribe(SecretTriggerbot)
		EVENT_MANAGER.subscribe(AutoCloseChest)
		//EVENT_MANAGER.subscribe(BowCharger)
		//EVENT_MANAGER.subscribe(PacketDebug)



		//EVENT_MANAGER.subscribe(DevTestCommand.init())
		//KeybindHandler.register()
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