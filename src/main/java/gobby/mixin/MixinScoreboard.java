package gobby.mixin;

import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Scoreboard.class)
public class MixinScoreboard {

    @Inject(method = "updateScore", at = @At("TAIL"), cancellable = true)
    public void gobby$test(ScoreHolder scoreHolder, ScoreboardObjective objective, ScoreboardScore score, CallbackInfo ci) {
        //System.out.println("[Gobby] Scoreboard updated: " + scoreHolder.getNameForScoreboard() + " " + objective.getName());

    }
}
