package gobby.features

import gobby.Gobbyclient.Companion.EVENT_MANAGER
import gobby.commands.*
import gobby.commands.developer.*
import gobby.events.core.EventDispatcher
import gobby.features.developer.*
import gobby.features.petrules.*
import gobby.features.dungeons.*
import gobby.features.dungeons.puzzles.*
import gobby.features.floor7.*
import gobby.features.floor7.devices.*
import gobby.features.floor7.terminals.*
import gobby.features.force.*
import gobby.features.mining.*
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
import gobby.pathfinder.etherwarp.EtherwarpServerTickGate
import gobby.utils.skyblock.dungeon.*
import gobby.utils.skyblock.dungeon.map.*
import gobby.utils.timer.Executor
import gobby.utils.MovementPacketSuppressor

object ModuleManager {

    fun subscribeEventListeners() {
        subscribeCommands()
        subscribeManagers()
        subscribePetRules()
        subscribeUtils()
        subscribeModules()
    }

    private fun subscribeCommands() = listOf(
        GobbyCommand,
        DevTestCommand,
        BrushCommand,
        SimulateCommand,
        ClipCommand,
        EtherwarpCommands
    ).forEach(EVENT_MANAGER::subscribe)

    private fun subscribeManagers() = listOf(
        GuiElementManager,
        HudManager,
        AuraManager,
        SwapManager,
        WardrobeManager,
        LoadoutManager,
        LeapManager,
        EquipmentManager,
        PetManager,
        SilentContainerFlow,
        AbilityManager,
        InvincibilityManager,
            PacketOrderManager,
            EtherwarpServerTickGate
    ).forEach(EVENT_MANAGER::subscribe)

    private fun subscribePetRules() = listOf(
        PetRules,
        DungeonStart,
        BossSpawn
    ).forEach(EVENT_MANAGER::subscribe)

    private fun subscribeUtils() = listOf(
            MovementPacketSuppressor,
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
            ArmorTracker,
            DungeonMapSaver,
            DungeonMapSource,
            DungeonMapPlayers,
            DungeonMimic,
            StructureCopier,
            MovementRecorder,
            EventDispatcher,
            KeybindListener,
            TitleUtils,
            RenderBeacon,
            RenderBlock,
            SecretTriggerbot,
            EtherwarpEsp,
            EtherwarpRoutes,
            LastBreathHelper,
            DebuffAreaRenderer,
            ShootingDeviceEsp,
            P3Levers,
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
        CorpseEsp,
        FullBright,
        MobEsp,
        PlayerEsp,
        ChinaHat,
        InventoryHud,
        Keystrokes,
        TerminatorAC,
        Trajectory,
        StarredMobEsp,
        MiniBossEsp,
        DungeonMap,
        RoomPathfinder,
        DoorKeyEsp,
        TrashItems,
        Etherwarp,
        EtherwarpTriggerbot,
        Brush,
        AutoLeap,
        LeapOverlay,
        AutoCloseChest,
        AutoExperiments,
        AutoGFS,
        AutoUlt,
        WitherBossEsp,
        BloodCampHelper,
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
        CreeperBeams,
        CowHatHelper,
        MaskTimers,
        SpringBootsHelper,
        PadTimers,
        FuckDiorite,
        Relics,
        P5DebuffHelper,
        InvincibilityHelper,
        NecronPlatform,
        AutoTerminals,
        NoFire,
        NoBlockOverlay,
        ScoreboardHider,
        SkinChanger,
        NickHider,
        DisableBlockParticles,
        WardrobeSwapper,
        LoadoutSwapper,
        MaskSwapper,
        LagSwitch,
        VelocityBuffer,
        SpeedHud,
        FreeCam,
        TerminalOverlay,
        DevMode,
        DrawSlotNumbers,
        CopyGui,
        CopyItemNbt,
        ArmorStandSaver,
        MobSaver,
        MessageDebugger,
        ParticleDebugger,
        SoundDebugger,
        SystemChatDebugger,
        RenderHealth,
        ModIdHiderModule,
        PetsKeybind,
        HideProfileId,
        Welcome
    ).forEach(EVENT_MANAGER::subscribe)
}
