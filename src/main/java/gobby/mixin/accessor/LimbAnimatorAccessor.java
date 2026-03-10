package gobby.mixin.accessor;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor {

    @Accessor("speed")
    void setSpeed(float speed);

    @Accessor("lastSpeed")
    void setLastSpeed(float lastSpeed);
}
