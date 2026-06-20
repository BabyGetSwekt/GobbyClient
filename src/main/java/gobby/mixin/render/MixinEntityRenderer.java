package gobby.mixin.render;

import gobby.features.dungeons.StarredMobEsp;
import gobby.features.render.EntityHighlighter;
import gobby.gui.components.hud.InventoryHud;
import gobby.interfaces.EspLayerHidingState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class MixinEntityRenderer {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void gobbyclient$suppressNameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        if (InventoryHud.suppressNameTag) {
            state.nameTag = null;
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void gobbyclient$markHideLayers(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
        EspLayerHidingState hiding = (EspLayerHidingState) state;
        hiding.gobbyclient$setHideLayers(StarredMobEsp.INSTANCE.shouldHideLayers(entity));
        hiding.gobbyclient$setHideBody(EntityHighlighter.isHighlightedByAny(entity));
    }
}
