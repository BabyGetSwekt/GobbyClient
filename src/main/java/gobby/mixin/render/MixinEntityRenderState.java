package gobby.mixin.render;

import gobby.interfaces.EspLayerHidingState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class MixinEntityRenderState implements EspLayerHidingState {

    @Unique
    private boolean gobbyclient$hideLayers;

    @Unique
    private boolean gobbyclient$hideBody;

    @Override
    public void gobbyclient$setHideLayers(boolean hide) {
        this.gobbyclient$hideLayers = hide;
    }

    @Override
    public boolean gobbyclient$shouldHideLayers() {
        return this.gobbyclient$hideLayers;
    }

    @Override
    public void gobbyclient$setHideBody(boolean hide) {
        this.gobbyclient$hideBody = hide;
    }

    @Override
    public boolean gobbyclient$shouldHideBody() {
        return this.gobbyclient$hideBody;
    }
}
