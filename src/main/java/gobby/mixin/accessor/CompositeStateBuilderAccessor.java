package gobby.mixin.accessor;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.CompositeState.CompositeStateBuilder.class)
public interface CompositeStateBuilderAccessor {

    @Invoker("setLayeringState")
    RenderType.CompositeState.CompositeStateBuilder invokeSetLayeringState(RenderStateShard.LayeringStateShard state);

    @Invoker("setOutputState")
    RenderType.CompositeState.CompositeStateBuilder invokeSetOutputState(RenderStateShard.OutputStateShard state);

    @Invoker("setLineState")
    RenderType.CompositeState.CompositeStateBuilder invokeSetLineState(RenderStateShard.LineStateShard state);

    @Invoker("createCompositeState")
    RenderType.CompositeState invokeCreateCompositeState(boolean affectsOutline);
}
