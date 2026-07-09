package gobby.mixin;

import gobby.gui.font.StyledFontHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public class MixinFont {

    @Inject(
        method = "prepareText(Ljava/lang/String;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void gobbyclient$styledPrepareText(String text, float x, float y, int color, boolean shadow, int packedLight, CallbackInfoReturnable<Font.PreparedText> cir) {
        Style style = StyledFontHolder.current();
        if (style != null) {
            FormattedCharSequence sequence = Component.literal(text).setStyle(style).getVisualOrderText();
            cir.setReturnValue(((Font) (Object) this).prepareText(sequence, x, y, color, shadow, false, packedLight));
        }
    }
}
