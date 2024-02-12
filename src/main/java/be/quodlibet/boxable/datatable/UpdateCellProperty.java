package be.quodlibet.boxable.datatable;

import be.quodlibet.boxable.Cell;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Allows changing the cell properties, while the CSV documents is written directly into the PDF
 * tables
 *
 * @author Christoph Schemmelmann {@code <CSchemmy@gmx.de>}
 */
public interface UpdateCellProperty {

  void updateCellPropertiesAtColumn(Cell<PDPage> cell, int column, int row);
}
