package de.sciss.lucre.swing.graph.impl;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

/** Work around the problem that from Scala's perspective `ListCellRenderer` and `DefaultListCellRenderer`
  * diverge.
  */
public abstract class ListCellRendererDelegate extends DefaultListCellRenderer {
    public ListCellRendererDelegate() {
        super();
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        final Object value1 = rendererDelegate(list, value, index, isSelected, cellHasFocus);
        return super.getListCellRendererComponent(list, value1, index, isSelected, cellHasFocus);
    }

    abstract protected Object rendererDelegate(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus);
}