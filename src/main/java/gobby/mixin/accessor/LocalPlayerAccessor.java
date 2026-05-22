package gobby.mixin.accessor;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {

    @Accessor("crouching")
    boolean getCrouching();

    @Accessor("crouching")
    void setCrouching(boolean crouching);
}
