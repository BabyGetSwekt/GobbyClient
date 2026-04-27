package gobby.mixin;

import gobby.features.dungeons.BloodBlink;
import gobby.features.skyblock.FreeCam;
import gobby.pathfinder.etherwarp.EtherwarpExecutor;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput extends Input {

    @Inject(method = "tick", at = @At("RETURN"))
    private void gobbyclient$onTickReturn(CallbackInfo ci) {
        if (FreeCam.INSTANCE.getEnabled()) {
            this.playerInput = PlayerInput.DEFAULT;
            this.movementVector = Vec2f.ZERO;
        }

        if (EtherwarpExecutor.INSTANCE.isRunning()) {
            PlayerInput old = this.playerInput;
            this.playerInput = new PlayerInput(
                old.forward(), old.backward(), old.left(), old.right(),
                old.jump(), true, old.sprint()
            );
        }

        if (BloodBlink.INSTANCE.isBlinking()) {
            PlayerInput old = this.playerInput;
            if (old.forward() && old.backward() && old.left() && old.right()) {
                BloodBlink.INSTANCE.cancelBlink();
                return;
            }

            boolean sneak = BloodBlink.INSTANCE.getForceSneak();
            this.playerInput = new PlayerInput(false, false, false, false, false, sneak, false);
            BloodBlink.INSTANCE.consumeForceSneak();
        }
    }
}
