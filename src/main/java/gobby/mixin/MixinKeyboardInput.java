package gobby.mixin;

import gobby.features.dungeons.BloodBlink;
import gobby.features.skyblock.FreeCam;
import gobby.pathfinder.etherwarp.EtherwarpPathExecutor;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput extends ClientInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void gobbyclient$onTickReturn(CallbackInfo ci) {
        if (FreeCam.INSTANCE.getEnabled()) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        }

        if (BloodBlink.INSTANCE.isBlinking()) {
            Input old = this.keyPresses;
            if (old.forward() && old.backward() && old.left() && old.right()) {
                BloodBlink.INSTANCE.cancelBlink();
                return;
            }

            boolean sneak = BloodBlink.INSTANCE.getForceSneak();
            this.keyPresses = new Input(false, false, false, false, false, sneak, false);
            BloodBlink.INSTANCE.consumeForceSneak();
        }

        if (EtherwarpPathExecutor.INSTANCE.getForceSneak()) {
            Input old = this.keyPresses;
            this.keyPresses = new Input(old.forward(), old.backward(), old.left(), old.right(), old.jump(), true, old.sprint());
        }
    }
}
