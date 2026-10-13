package dev.tacticalcombat.mixin.client;

import net.minecraft.client.gui.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The native selection record is protected; read its anchor for GUI resize recovery. */
@Mixin(EditBox.class)
public interface NarrativeSelectionAccessor {
    @Accessor("selectionEnd") int cubetable$anchor();
}
