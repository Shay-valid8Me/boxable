package be.quodlibet.boxable;

/**
 * Data container for HTML ordered list elements.
 *
 * @author hstimac
 */
public class HTMLListNode {

  /** Element's current ordering number (e.g third element in the current list) */
  private int orderingNumber;

  /** Element's whole ordering number value (e.g 1.1.2.1) */
  private String value;

  public int getOrderingNumber() {
    return orderingNumber;
  }

  public void setOrderingNumber(int orderingNumber) {
    this.orderingNumber = orderingNumber;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public HTMLListNode(int orderingNumber, String value) {
    this.orderingNumber = orderingNumber;
    this.value = value;
  }
}
