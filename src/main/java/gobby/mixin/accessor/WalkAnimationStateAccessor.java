package gobby.mixin.accessor;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {

    @Accessor("speedOld")
    float getLastSpeed();

    @Accessor("speedOld")
    void setLastSpeed(float lastSpeed);

    @Accessor("position")
    void setWalkPosition(float position);
}
