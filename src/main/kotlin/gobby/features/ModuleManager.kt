package gobby.features

import gobby.Gobbyclient.Companion.EVENT_MANAGER
import gobby.commands.*
import gobby.events.core.EventDispatcher
import gobby.events.listener.GlobalKeyHandler
import gobby.features.dungeons.*
import gobby.features.floor7.*
import gobby.features.floor7.devices.*
import gobby.features.galatea.*
import gobby.features.render.*
import gobby.features.skyblock.*
import gobby.utils.LocationUtils
import gobby.utils.render.*
import gobby.utils.skyblock.dungeon.ScanUtils
import gobby.utils.rotation.RotationUtils
import gobby.utils.timer.Executor

object ModuleManager {

    /**
     * This class is responsible for managing all the modules in the client.
     * All modules (annotated with @SubscribeEvent) MUST be registered here in order for it to work.
     */
    fun subscribeEventListeners() {
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
        EVENT_MANAGER.subscribe(Renderer)
        EVENT_MANAGER.subscribe(ScanUtils)
        EVENT_MANAGER.subscribe(StarredMobEsp)
        EVENT_MANAGER.subscribe(MiniBossEsp)
        EVENT_MANAGER.subscribe(CancelInteract)
        EVENT_MANAGER.subscribe(SecretTriggerbot)
        EVENT_MANAGER.subscribe(AutoCloseChest)
        EVENT_MANAGER.subscribe(LastBreathHelper)
        EVENT_MANAGER.subscribe(DebuffAreaRenderer)
        EVENT_MANAGER.subscribe(AutoAlign)
        EVENT_MANAGER.subscribe(AlignHelper)
        EVENT_MANAGER.subscribe(AutoPre4)
        EVENT_MANAGER.subscribe(ShootingDeviceEsp)
        EVENT_MANAGER.subscribe(RotationUtils)

        EVENT_MANAGER.subscribe(EventDispatcher)

    }

}