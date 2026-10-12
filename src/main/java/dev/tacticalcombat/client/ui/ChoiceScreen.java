package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import java.util.function.*;

/** Local choice of supported catalog values; the eventual mutation still rechecks server permission. */
public final class ChoiceScreen extends ManagementScreen {
    private final List<String> choices;
    private final Function<String,String> label;
    private final Consumer<String> chosen;
    public ChoiceScreen(Screen parent,String title,List<String> choices,Function<String,String> label,Consumer<String> chosen) {super(title,parent);this.choices=List.copyOf(choices);this.label=label;this.chosen=chosen;}
    @Override protected void refresh() {clearRows();fresh=true;for(String value:choices) row(label.apply(value),"Choose this option",true,"",()->returnChoice(parent,()->chosen.accept(value)));}
}
