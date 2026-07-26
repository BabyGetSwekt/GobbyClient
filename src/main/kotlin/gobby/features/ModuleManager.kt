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
import gobby.features.mining.structurescanner.*
import gobby.features.render.*
import gobby.features.skyblock.*
import gobby.gui.GuiElementManager
import gobby.gui.click.KeybindListener
import gobby.gui.click.Module
import gobby.gui.hud.HudManager
import gobby.pathfinder.PathExecutor
import gobby.pathfinder.world.BlockCache
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
            BlockCache,
            PathExecutor,
            RotationUtils,
//            AutoUpdater,
            NotificationRenderer,
            HotbarTracker,
            DungeonMapSaver,
            StructureCopier,
            MovementRecorder,
            EventDispatcher,
            KeybindListener,
            TitleUtils,
            RenderBeacon,
            RenderBlock,
            SecretTriggerbot,
            EtherwarpEsp,
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
            TerminalAura,
            PathRender
        ).forEach(EVENT_MANAGER::subscribe)

    private fun subscribeModules() = listOf<Module>(
        PartyCommands,
        RenderTurtles,
        StructureScanner,
        FullBright,
        MobEsp,
        ChinaHat,
        InventoryHud,
        Keystrokes,
        TerminatorAC,
        Trajectory,
        StarredMobEsp,
        MiniBossEsp,
        DungeonMap,
        DoorKeyEsp,
        Etherwarp,
        AutoLeap,
        LeapOverlay,
        AutoCloseChest,
        AutoExperiments,
        AutoGFS,
        AutoJax,
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
        MaskTimers,
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
        SystemChatDebugger,
        RenderHealth,
        ModIdHiderModule,
        Welcome
    ).forEach(EVENT_MANAGER::subscribe)
}
