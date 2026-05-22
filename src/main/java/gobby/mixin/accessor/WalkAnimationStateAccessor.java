package gobby.mixin.accessor;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WalkAnimationState.class)
public interface WalkAnimationStateAccessor {

    @Accessor("speed")
    void setSpeed(float speed);

    @Accessor("speed")
    float getSpeed();

    @Accessor("speedOld")
    void setLastSpeed(float lastSpeed);

    @Accessor("speedOld")
    float getLastSpeed();

    @Accessor("position")
    void setPosition(float position);

    @Accessor("position")
    float getPosition();
}
