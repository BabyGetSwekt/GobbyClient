package gobby.features

import gobby.Gobbyclient.Companion.EVENT_MANAGER
import gobby.commands.*
import gobby.commands.developer.ClipCommand
import gobby.commands.developer.SimulateCommand
import gobby.events.core.EventDispatcher
import gobby.features.developer.*
import gobby.features.dungeons.*
import gobby.features.dungeons.puzzles.*
import gobby.features.floor7.*
import gobby.features.floor7.devices.*
import gobby.features.floor7.terminals.*
import gobby.features.force.*
import gobby.features.galatea.*
import gobby.features.render.*
import gobby.features.skyblock.*
import gobby.gui.GuiElementManager
import gobby.gui.click.KeybindListener
import gobby.gui.click.Module
import gobby.gui.hud.HudManager
import gobby.pathfinder.PathExecutor
import gobby.utils.*
import gobby.utils.managers.*
import gobby.utils.render.*
import gobby.utils.rotation.RotationUtils
import gobby.utils.skyblock.dungeon.*
import gobby.utils.timer.Executor

object ModuleManager {

    fun subscribeEventListeners() {
        subscribeCommands()
        subscribeManagers()
        subscribeUtils()
        subscribeModules()
    }

    private fun subscribeCommands() = listOf(
        GobbyCommand,
        DevTestCommand,
        BrushCommand,
        SimulateCommand,
        ClipCommand
    ).forEach(EVENT_MANAGER::subscribe)

    private fun subscribeManagers() = listOf(
        GuiElementManager,
        HudManager,
        AuraManager,
        SwapManager,
        WardrobeManager,
        LeapManager,
        EquipmentManager,
        AbilityManager,
        InvincibilityManager,
        PacketOrderManager
    ).forEach(EVENT_MANAGER::subscribe)

    private fun subscribeUtils() = listOf(
            Executor,
            LocationUtils,
            DungeonListener,
            ScanUtils,
            PathExecutor,
            RotationUtils,
            AutoUpdater,
            NotificationRenderer,
            HotbarTracker,
            DungeonMapSaver,
            StructureCopier,
            EventDispatcher,
            KeybindListener,
            TitleUtils,
            RenderBeacon,
            RenderBlock,
            SecretTriggerbot,
            EtherwarpTriggerbot,
            EtherwarpEsp,
            EtherwarpHighlighter,
            LastBreathHelper,
            DebuffAreaRenderer,
            ShootingDeviceEsp,
            P3Levers,
            Brush,
            YouAreAKingGG,
            TerminalUtils,
            NumbersTerminal,
            ColorsTerminal,
            StartsWithTerminal,
            RubixTerminal,
            RedGreenTerminal,
            MelodyTerminal,
            TerminalAura
        ).forEach(EVENT_MANAGER::subscribe)

    private fun subscribeModules() = listOf<Module>(
        PartyCommands,
        RenderTurtles,
        FullBright,
        CoordWaypoints,
        TerminatorAC,
        Trajectory,
        StarredMobEsp,
        MiniBossEsp,
        DungeonMap,
        BloodBlink,
        DoorKeyEsp,
        AutoLeap,
        LeapOverlay,
        AutoCloseChest,
        AutoGFS,
        LividHelper,
        CancelInteract,
        SecretHitbox,
        AutoAlign,
        SimonSays,
        AlignHelper,
        AutoPre4,
        IceFill,
        Blaze,
        CowHatReminder,
        PadTimers,
        FuckDiorite,
        Relics,
        P5DebuffHelper,
        AutoTerminals,
        NoFire,
        NoBlockOverlay,
        DisableBlockParticles,
        WardrobeSwapper,
        MaskSwapper,
        LagSwitch,
        VelocityBuffer,
        SpeedHud,
        FreeCam,
        TerminalOverlay,
        DevMode,
        DrawSlotNumbers,
        CopyGui,
        ArmorStandSaver,
        MessageDebugger,
        ParticleDebugger,
        SoundDebugger,
        RenderHealth,
        ModIdHiderModule,
        Welcome
    ).forEach(EVENT_MANAGER::subscribe)
}
