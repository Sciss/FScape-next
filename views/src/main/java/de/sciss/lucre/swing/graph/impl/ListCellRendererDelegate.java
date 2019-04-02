package de.sciss.lucre.swing.graph.impl;

import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Component;

/** Work around the problem that from Scala's perspective `ListCellRenderer` and `DefaultListCellRenderer`
  * diverge. What we do is map the display value and then hand it into a peer renderer.
  * This way one can use the "default" renderer of a `ComboBox`, for example, which helps with custom
  * look-and-feels like WebLaF, which would screw up with custom renderers.
  */
public abstract class ListCellRendererDelegate<A, B> implements ListCellRenderer<A> {
    private final ListCellRenderer<B> peer;

    public ListCellRendererDelegate(ListCellRenderer<B> peer) {
        super();
        this.peer = peer;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends A> list, A value, int index, boolean isSelected, boolean cellHasFocus) {
        final B value1 = rendererDelegate(list, value, index, isSelected, cellHasFocus);
        return peer.getListCellRendererComponent((JList<? extends B>) list, value1, index, isSelected, cellHasFocus);
    }

    abstract protected B rendererDelegate(JList<? extends A>  list, A value, int index, boolean isSelected, boolean cellHasFocus);
}