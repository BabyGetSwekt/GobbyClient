package gobby.mixin.accessor;

//? if <=1.21.10 {
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderStateShard.class)
public interface RenderStateShardAccessor {

    @Accessor("VIEW_OFFSET_Z_LAYERING")
    static RenderStateShard.LayeringStateShard getViewOffsetZLayering() { throw new AssertionError(); }

    @Accessor("ITEM_ENTITY_TARGET")
    static RenderStateShard.OutputStateShard getItemEntityTarget() { throw new AssertionError(); }
}
//?}

//? if >=1.21.11 {
/*import gobby.Gobbyclient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gobbyclient.class)
public interface RenderStateShardAccessor {}*/
//?}
