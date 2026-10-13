package dev.tacticalcombat.mixin.client;

import net.minecraft.client.gui.EditBox;
import net.minecraft.client.gui.widget.EditBoxWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GUI-only access for restoring a multiline caret when wrapping width changes. */
@Mixin(EditBoxWidget.class)
public interface NarrativeEditBoxAccessor {
    @Accessor("editBox") EditBox cubetable$editBox();
}
