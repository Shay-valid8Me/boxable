package be.quodlibet.boxable;

import be.quodlibet.boxable.text.PipelineLayer;
import be.quodlibet.boxable.text.Token;
import be.quodlibet.boxable.text.TokenType;
import be.quodlibet.boxable.text.Tokenizer;
import be.quodlibet.boxable.text.WrappingFunction;
import be.quodlibet.boxable.utils.FontUtils;
import be.quodlibet.boxable.utils.PDStreamUtils;
import be.quodlibet.boxable.utils.PageContentStreamOptimized;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

public class Paragraph {

  private float width;
  private final String text;
  private float fontSize;
  private PDFont font;
  private final PDFont fontBold;
  private final PDFont fontItalic;
  private final PDFont fontBoldItalic;
  private final WrappingFunction wrappingFunction;
  private HorizontalAlignment align;
  private TextType textType;
  private Color color;
  private float lineSpacing;

  private static final int DEFAULT_TAB = 4;
  private static final int DEFAULT_TAB_AND_BULLET = 6;
  private static final int BULLET_SPACE = 2;

  private boolean drawDebug;
  private final Map<Integer, Float> lineWidths = new HashMap<>();
  private Map<Integer, List<Token>> mapLineTokens = new LinkedHashMap<>();
  private float maxLineWidth = Integer.MIN_VALUE;
  private List<Token> tokens;
  private List<String> lines;
  private Float spaceWidth;

  public Paragraph(
      String text, PDFont font, float fontSize, float width, final HorizontalAlignment align) {
    this(text, font, fontSize, width, align, null);
  }

  // This function exists only to preserve backwards compatibility for
  // the getWrappingFunction() method; it has been replaced with a faster implementation in the
  // Tokenizer
  private static final WrappingFunction DEFAULT_WRAP_FUNC =
      new WrappingFunction() {
        @Override
        public String[] getLines(String t) {
          return t.split("(?<=\\s|-|@|,|\\.|:|;)");
        }
      };

  public Paragraph(String text, PDFont font, int fontSize, int width) {
    this(text, font, fontSize, width, HorizontalAlignment.LEFT, null);
  }

  public Paragraph(
      String text,
      PDFont font,
      float fontSize,
      float width,
      final HorizontalAlignment align,
      WrappingFunction wrappingFunction) {
    this(text, font, fontSize, width, align, Color.BLACK, (TextType) null, wrappingFunction);
  }

  public Paragraph(
      String text,
      PDFont font,
      float fontSize,
      float width,
      final HorizontalAlignment align,
      final Color color,
      final TextType textType,
      WrappingFunction wrappingFunction) {
    this(text, font, fontSize, width, align, color, textType, wrappingFunction, 1);
  }

  public Paragraph(
      String text,
      PDFont font,
      float fontSize,
      float width,
      final HorizontalAlignment align,
      final Color color,
      final TextType textType,
      WrappingFunction wrappingFunction,
      float lineSpacing) {
    this.color = color;
    this.text = text;
    this.font = font;
    // check if we have different default font for italic and bold text
    if (FontUtils.getDefaultfonts().isEmpty()) {
      fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
      fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
      fontBoldItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD_OBLIQUE);
    } else {
      fontBold = FontUtils.getDefaultfonts().get("fontBold");
      fontBoldItalic = FontUtils.getDefaultfonts().get("fontBoldItalic");
      fontItalic = FontUtils.getDefaultfonts().get("fontItalic");
    }
    this.fontSize = fontSize;
    this.width = width;
    this.textType = textType;
    this.setAlign(align);
    this.wrappingFunction = wrappingFunction;
    this.lineSpacing = lineSpacing;
  }

  public List<String> getLines() {
    // memoize this function because it is very expensive
    if (lines != null) {
      return lines;
    }

    final List<String> result = new ArrayList<>();

    // text and wrappingFunction are immutable, so we only ever need to compute tokens once
    if (tokens == null) {
      tokens = Tokenizer.tokenize(text, wrappingFunction);
    }

    int lineCounter = 0;
    boolean italic = false;
    boolean bold = false;
    boolean listElement = false;
    PDFont currentFont = font;
    int orderListElement = 1;
    int numberOfOrderedLists = 0;
    int listLevel = 0;
    Stack<HTMLListNode> stack = new Stack<>();

    final PipelineLayer textInLine = new PipelineLayer();
    final PipelineLayer sinceLastWrapPoint = new PipelineLayer();

    for (final Token token : tokens) {
      switch (token.getType()) {
        case OPEN_TAG:
          if (isBold(token)) {
            bold = true;
            currentFont = getFont(bold, italic);
          } else if (isItalic(token)) {
            italic = true;
            currentFont = getFont(bold, italic);
          } else if (isList(token)) {
            listLevel++;
            if (token.getData().equals("ol")) {
              numberOfOrderedLists++;
              if (listLevel > 1) {
                stack.add(
                    new HTMLListNode(
                        orderListElement - 1,
                        stack.isEmpty()
                            ? String.valueOf(orderListElement - 1) + "."
                            : stack.peek().getValue()
                                + String.valueOf(orderListElement - 1)
                                + "."));
              }
              orderListElement = 1;

              textInLine.push(sinceLastWrapPoint);
              // check if you have some text before this list, if you don't then you really don't
              // need extra line break for that
              if (textInLine.trimmedWidth() > 0) {
                // this is our line
                result.add(textInLine.trimmedText());
                lineWidths.put(lineCounter, textInLine.trimmedWidth());
                mapLineTokens.put(lineCounter, textInLine.tokens());
                maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
                textInLine.reset();
                lineCounter++;
              }
            } else if (token.getData().equals("ul")) {
              textInLine.push(sinceLastWrapPoint);
              // check if you have some text before this list, if you don't then you really don't
              // need extra line break for that
              if (textInLine.trimmedWidth() > 0) {
                // this is our line
                result.add(textInLine.trimmedText());
                lineWidths.put(lineCounter, textInLine.trimmedWidth());
                mapLineTokens.put(lineCounter, textInLine.tokens());
                maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
                textInLine.reset();
                lineCounter++;
              }
            }
          }
          sinceLastWrapPoint.push(token);
          break;
        case CLOSE_TAG:
          if (isBold(token)) {
            bold = false;
            currentFont = getFont(bold, italic);
            sinceLastWrapPoint.push(token);
          } else if (isItalic(token)) {
            italic = false;
            currentFont = getFont(bold, italic);
            sinceLastWrapPoint.push(token);
          } else if (isList(token)) {
            listLevel--;
            if (token.getData().equals("ol")) {
              numberOfOrderedLists--;
              // reset elements
              if (numberOfOrderedLists > 0) {
                orderListElement = stack.peek().getOrderingNumber() + 1;
                stack.pop();
              }
            }
            // ensure extra space after each lists
            // no need to worry about current line text because last closing <li> tag already done
            // that
            if (listLevel == 0) {
              result.add(" ");
              lineWidths.put(lineCounter, 0.0f);
              mapLineTokens.put(lineCounter, new ArrayList<Token>());
              lineCounter++;
            }
          } else if (isListElement(token)) {
            // wrap at last wrap point?
            if (textInLine.width() + sinceLastWrapPoint.trimmedWidth() > width) {
              // this is our line
              result.add(textInLine.trimmedText());
              lineWidths.put(lineCounter, textInLine.trimmedWidth());
              mapLineTokens.put(lineCounter, textInLine.tokens());
              maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
              textInLine.reset();
              lineCounter++;
              // wrapping at last wrap point
              if (numberOfOrderedLists > 0) {
                String orderingNumber =
                    stack.isEmpty()
                        ? String.valueOf(orderListElement) + "."
                        : stack.pop().getValue() + ".";
                stack.add(new HTMLListNode(orderListElement, orderingNumber));
                try {
                  float tab = indentLevel(DEFAULT_TAB);
                  float orderingNumberAndTab = font.getStringWidth(orderingNumber) + tab;
                  textInLine.push(
                      currentFont,
                      fontSize,
                      new Token(
                          TokenType.PADDING,
                          String.valueOf(orderingNumberAndTab / 1000 * getFontSize())));
                } catch (IOException e) {
                  e.printStackTrace();
                }
                orderListElement++;
              } else {
                try {
                  // if it's not left aligned then ignore list and list element and deal with it as
                  // normal text where <li> mimic <br> behaviour
                  float tabBullet =
                      getAlign().equals(HorizontalAlignment.LEFT)
                          ? indentLevel(
                              DEFAULT_TAB * Math.max(listLevel - 1, 0) + DEFAULT_TAB_AND_BULLET)
                          : indentLevel(DEFAULT_TAB);
                  textInLine.push(
                      currentFont,
                      fontSize,
                      new Token(
                          TokenType.PADDING, String.valueOf(tabBullet / 1000 * getFontSize())));
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
              textInLine.push(sinceLastWrapPoint);
            }
            // wrapping at this must-have wrap point
            textInLine.push(sinceLastWrapPoint);
            // this is our line
            result.add(textInLine.trimmedText());
            lineWidths.put(lineCounter, textInLine.trimmedWidth());
            mapLineTokens.put(lineCounter, textInLine.tokens());
            maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
            textInLine.reset();
            lineCounter++;
            listElement = false;
          }
          if (isParagraph(token)) {
            if (textInLine.width() + sinceLastWrapPoint.trimmedWidth() > width) {
              // this is our line
              result.add(textInLine.trimmedText());
              lineWidths.put(lineCounter, textInLine.trimmedWidth());
              maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
              mapLineTokens.put(lineCounter, textInLine.tokens());
              lineCounter++;
              textInLine.reset();
            }
            // wrapping at this must-have wrap point
            textInLine.push(sinceLastWrapPoint);
            // this is our line
            result.add(textInLine.trimmedText());
            lineWidths.put(lineCounter, textInLine.trimmedWidth());
            mapLineTokens.put(lineCounter, textInLine.tokens());
            maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
            textInLine.reset();
            lineCounter++;

            // extra spacing because it's a paragraph
            result.add(" ");
            lineWidths.put(lineCounter, 0.0f);
            mapLineTokens.put(lineCounter, new ArrayList<Token>());
            lineCounter++;
          }
          break;
        case POSSIBLE_WRAP_POINT:
          if (textInLine.width() + sinceLastWrapPoint.trimmedWidth() > width) {
            // this is our line
            if (!textInLine.isEmpty()) {
              result.add(textInLine.trimmedText());
              lineWidths.put(lineCounter, textInLine.trimmedWidth());
              maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
              mapLineTokens.put(lineCounter, textInLine.tokens());
              lineCounter++;
              textInLine.reset();
            }
            // wrapping at last wrap point
            if (listElement) {
              if (numberOfOrderedLists > 0) {
                try {
                  float tab =
                      getAlign().equals(HorizontalAlignment.LEFT)
                          ? indentLevel(DEFAULT_TAB * Math.max(listLevel - 1, 0) + DEFAULT_TAB)
                          : indentLevel(DEFAULT_TAB);
                  String orderingNumber =
                      stack.isEmpty()
                          ? String.valueOf(orderListElement) + "."
                          : stack.peek().getValue()
                              + "."
                              + String.valueOf(orderListElement - 1)
                              + ".";
                  textInLine.push(
                      currentFont,
                      fontSize,
                      new Token(
                          TokenType.PADDING,
                          String.valueOf(
                              (tab + font.getStringWidth(orderingNumber)) / 1000 * getFontSize())));
                } catch (IOException e) {
                  e.printStackTrace();
                }
              } else {
                try {
                  // if it's not left aligned then ignore list and list element and deal with it as
                  // normal text where <li> mimic <br> behavior
                  float tabBullet =
                      getAlign().equals(HorizontalAlignment.LEFT)
                          ? indentLevel(
                              DEFAULT_TAB * Math.max(listLevel - 1, 0) + DEFAULT_TAB_AND_BULLET)
                          : indentLevel(DEFAULT_TAB);
                  textInLine.push(
                      currentFont,
                      fontSize,
                      new Token(
                          TokenType.PADDING, String.valueOf(tabBullet / 1000 * getFontSize())));
                } catch (IOException e) {
                  // TODO Auto-generated catch block
                  e.printStackTrace();
                }
              }
            }
            textInLine.push(sinceLastWrapPoint);
          } else {
            textInLine.push(sinceLastWrapPoint);
          }
          break;
        case WRAP_POINT:
          // wrap at last wrap point?
          if (textInLine.width() + sinceLastWrapPoint.trimmedWidth() > width) {
            // this is our line
            result.add(textInLine.trimmedText());
            lineWidths.put(lineCounter, textInLine.trimmedWidth());
            mapLineTokens.put(lineCounter, textInLine.tokens());
            maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
            textInLine.reset();
            lineCounter++;
            // wrapping at last wrap point
            if (listElement) {
              if (!getAlign().equals(HorizontalAlignment.LEFT)) {
                listLevel = 0;
              }
              if (numberOfOrderedLists > 0) {
                //							String orderingNumber = String.valueOf(orderListElement) + ". ";
                String orderingNumber =
                    stack.isEmpty() ? String.valueOf("1") + "." : stack.pop().getValue() + ". ";
                try {
                  float tab = indentLevel(DEFAULT_TAB);
                  float orderingNumberAndTab = font.getStringWidth(orderingNumber) + tab;
                  textInLine.push(
                      currentFont,
                      fontSize,
                      new Token(
                          TokenType.PADDING,
                          String.valueOf(orderingNumberAndTab / 1000 * getFontSize())));
                } catch (IOException e) {
                  e.printStackTrace();
                }
              } else {
                try {
                  // if it's not left aligned then ignore list and list element and deal with it as
                  // normal text where <li> mimic <br> behaviour
                  float tabBullet =
                      getAlign().equals(HorizontalAlignment.LEFT)
                          ? indentLevel(
                              DEFAULT_TAB * Math.max(listLevel - 1, 0) + DEFAULT_TAB_AND_BULLET)
                          : indentLevel(DEFAULT_TAB);
                  textInLine.push(
                      currentFont,
                      fontSize,
                      new Token(
                          TokenType.PADDING, String.valueOf(tabBullet / 1000 * getFontSize())));
                } catch (IOException e) {
                  e.printStackTrace();
                }
              }
            }
            textInLine.push(sinceLastWrapPoint);
          }
          if (isParagraph(token)) {
            // check if you have some text before this paragraph, if you don't then you really don't
            // need extra line break for that
            if (textInLine.trimmedWidth() > 0) {
              // extra spacing because it's a paragraph
              result.add(" ");
              lineWidths.put(lineCounter, 0.0f);
              mapLineTokens.put(lineCounter, new ArrayList<Token>());
              lineCounter++;
            }
          } else if (isListElement(token)) {
            listElement = true;
            // token padding, token bullet
            try {
              // if it's not left aligned then ignore list and list element and deal with it as
              // normal text where <li> mimic <br> behaviour
              float tab =
                  getAlign().equals(HorizontalAlignment.LEFT)
                      ? indentLevel(DEFAULT_TAB * Math.max(listLevel - 1, 0) + DEFAULT_TAB)
                      : indentLevel(DEFAULT_TAB);
              textInLine.push(
                  currentFont,
                  fontSize,
                  new Token(TokenType.PADDING, String.valueOf(tab / 1000 * getFontSize())));
              if (numberOfOrderedLists > 0) {
                // if it's ordering list then move depending on your: ordering number + ". "
                String orderingNumber;
                if (listLevel > 1) {
                  orderingNumber =
                      stack.peek().getValue() + String.valueOf(orderListElement) + ". ";
                } else {
                  orderingNumber = String.valueOf(orderListElement) + ". ";
                }
                textInLine.push(
                    currentFont, fontSize, Token.text(TokenType.ORDERING, orderingNumber));
                orderListElement++;
              } else {
                // if it's unordered list then just move by bullet character (take care of
                // alignment!)
                textInLine.push(currentFont, fontSize, Token.text(TokenType.BULLET, " "));
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          } else {
            // wrapping at this must-have wrap point
            textInLine.push(sinceLastWrapPoint);
            result.add(textInLine.trimmedText());
            lineWidths.put(lineCounter, textInLine.trimmedWidth());
            mapLineTokens.put(lineCounter, textInLine.tokens());
            maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
            textInLine.reset();
            lineCounter++;
            if (listLevel > 0) {
              // preserve current indent
              try {
                if (numberOfOrderedLists > 0) {
                  float tab =
                      getAlign().equals(HorizontalAlignment.LEFT)
                          ? indentLevel(DEFAULT_TAB * Math.max(listLevel - 1, 0))
                          : indentLevel(DEFAULT_TAB);
                  // if it's ordering list then move depending on your: ordering number + ". "
                  String orderingNumber;
                  if (listLevel > 1) {
                    orderingNumber =
                        stack.peek().getValue() + String.valueOf(orderListElement) + ". ";
                  } else {
                    orderingNumber = String.valueOf(orderListElement) + ". ";
                  }
                  float tabAndOrderingNumber = tab + font.getStringWidth(orderingNumber);
                  textInLine.push(
                      currentFont,
                      fontSize,
                      new Token(
                          TokenType.PADDING,
                          String.valueOf(tabAndOrderingNumber / 1000 * getFontSize())));
                  orderListElement++;
                } else {
                  if (getAlign().equals(HorizontalAlignment.LEFT)) {
                    float tab =
                        indentLevel(
                            DEFAULT_TAB * Math.max(listLevel - 1, 0) + DEFAULT_TAB + BULLET_SPACE);
                    textInLine.push(
                        currentFont,
                        fontSize,
                        new Token(TokenType.PADDING, String.valueOf(tab / 1000 * getFontSize())));
                  }
                }
              } catch (IOException e) {
                e.printStackTrace();
              }
            }
          }
          break;
        case TEXT:
          try {
            String word = token.getData();
            float wordWidth = token.getWidth(currentFont);
            if (wordWidth / 1000f * fontSize > width
                && width > font.getAverageFontWidth() / 1000f * fontSize) {
              // you need to check if you have already something in your line
              boolean alreadyTextInLine = false;
              if (textInLine.trimmedWidth() > 0) {
                alreadyTextInLine = true;
              }
              while (wordWidth / 1000f * fontSize > width) {
                float width = 0;
                float firstPartWordWidth = 0;
                float restOfTheWordWidth = 0;
                String lastTextToken = word;
                StringBuilder firstPartOfWord = new StringBuilder();
                StringBuilder restOfTheWord = new StringBuilder();
                for (int i = 0; i < lastTextToken.length(); i++) {
                  char c = lastTextToken.charAt(i);
                  try {
                    width += (currentFont.getStringWidth(String.valueOf(c)) / 1000f * fontSize);
                  } catch (IOException e) {
                    e.printStackTrace();
                  }
                  if (alreadyTextInLine) {
                    if (width < this.width - textInLine.trimmedWidth()) {
                      firstPartOfWord.append(c);
                      firstPartWordWidth = Math.max(width, firstPartWordWidth);
                    } else {
                      restOfTheWord.append(c);
                      restOfTheWordWidth = Math.max(width, restOfTheWordWidth);
                    }
                  } else {
                    if (width < this.width) {
                      firstPartOfWord.append(c);
                      firstPartWordWidth = Math.max(width, firstPartWordWidth);
                    } else {
                      if (i == 0) {
                        firstPartOfWord.append(c);
                        for (int j = 1; j < lastTextToken.length(); j++) {
                          restOfTheWord.append(lastTextToken.charAt(j));
                        }
                        break;
                      } else {
                        restOfTheWord.append(c);
                        restOfTheWordWidth = Math.max(width, restOfTheWordWidth);
                      }
                    }
                  }
                }
                // reset
                alreadyTextInLine = false;
                sinceLastWrapPoint.push(
                    currentFont, fontSize, Token.text(TokenType.TEXT, firstPartOfWord.toString()));
                textInLine.push(sinceLastWrapPoint);
                // this is our line
                result.add(textInLine.trimmedText());
                lineWidths.put(lineCounter, textInLine.trimmedWidth());
                mapLineTokens.put(lineCounter, textInLine.tokens());
                maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
                textInLine.reset();
                lineCounter++;
                word = restOfTheWord.toString();
                wordWidth = currentFont.getStringWidth(word);
              }
              sinceLastWrapPoint.push(currentFont, fontSize, Token.text(TokenType.TEXT, word));
            } else {
              sinceLastWrapPoint.push(currentFont, fontSize, token);
            }

          } catch (IOException e) {
            e.printStackTrace();
          }
          break;
      }
    }
    if (sinceLastWrapPoint.trimmedWidth() + textInLine.trimmedWidth() > 0) {

      textInLine.push(sinceLastWrapPoint);
      result.add(textInLine.trimmedText());
      lineWidths.put(lineCounter, textInLine.trimmedWidth());
      mapLineTokens.put(lineCounter, textInLine.tokens());
      maxLineWidth = Math.max(maxLineWidth, textInLine.trimmedWidth());
    }

    lines = result;
    return result;
  }

  private static boolean isItalic(final Token token) {
    return "i".equals(token.getData());
  }

  private static boolean isBold(final Token token) {
    return "b".equals(token.getData());
  }

  private static boolean isParagraph(final Token token) {
    return "p".equals(token.getData());
  }

  private static boolean isListElement(final Token token) {
    return "li".equals(token.getData());
  }

  private static boolean isList(final Token token) {
    return "ul".equals(token.getData()) || "ol".equals(token.getData());
  }

  private float indentLevel(int numberOfSpaces) throws IOException {
    if (spaceWidth == null) {
      spaceWidth = font.getSpaceWidth();
    }
    return numberOfSpaces * spaceWidth;
  }

  public PDFont getFont(boolean isBold, boolean isItalic) {
    if (isBold) {
      if (isItalic) {
        return fontBoldItalic;
      } else {
        return fontBold;
      }
    } else if (isItalic) {
      return fontItalic;
    } else {
      return font;
    }
  }

  public float write(final PageContentStreamOptimized stream, float cursorX, float cursorY) {
    if (drawDebug) {
      PDStreamUtils.rectFontMetrics(stream, cursorX, cursorY, font, fontSize);

      // width
      PDStreamUtils.rect(stream, cursorX, cursorY, width, 1, Color.RED);
    }

    for (String line : getLines()) {
      line = line.trim();

      float textX = cursorX;
      switch (align) {
        case CENTER:
          textX += getHorizontalFreeSpace(line) / 2;
          break;
        case LEFT:
          break;
        case RIGHT:
          textX += getHorizontalFreeSpace(line);
          break;
      }

      PDStreamUtils.write(stream, line, font, fontSize, textX, cursorY, color);

      if (textType != null) {
        switch (textType) {
          case HIGHLIGHT:
          case SQUIGGLY:
          case STRIKEOUT:
            throw new UnsupportedOperationException("Not implemented.");
          case UNDERLINE:
            float y =
                (float)
                    (cursorY
                        - FontUtils.getHeight(font, fontSize)
                        - FontUtils.getDescent(font, fontSize)
                        - 1.5);
            try {
              float titleWidth = font.getStringWidth(line) / 1000 * fontSize;
              stream.moveTo(textX, y);
              stream.lineTo(textX + titleWidth, y);
              stream.stroke();
            } catch (final IOException e) {
              throw new IllegalStateException("Unable to underline text", e);
            }
            break;
          default:
            break;
        }
      }

      // move one "line" down
      cursorY -= getFontHeight();
    }

    return cursorY;
  }

  public float getHeight() {
    if (getLines().size() == 0) {
      return 0;
    } else {
      return (getLines().size() - 1) * getLineSpacing() * getFontHeight() + getFontHeight();
    }
  }

  public float getFontHeight() {
    return FontUtils.getHeight(font, fontSize);
  }

  /**
   * @deprecated This method will be removed in a future release
   * @return current font width
   */
  @Deprecated
  public float getFontWidth() {
    return font.getFontDescriptor().getFontBoundingBox().getWidth() / 1000 * fontSize;
  }

  /**
   * @deprecated This method will be removed in a future release
   * @param width Paragraph's width
   * @return {@link Paragraph} with designated width
   */
  @Deprecated
  public Paragraph withWidth(int width) {
    invalidateLineWrapping();
    this.width = width;
    return this;
  }

  /**
   * @deprecated This method will be removed in a future release
   * @param font {@link PDFont} for {@link Paragraph}
   * @param fontSize font size for {@link Paragraph}
   * @return {@link Paragraph} with designated font and font size
   */
  @Deprecated
  public Paragraph withFont(PDFont font, int fontSize) {
    invalidateLineWrapping();
    this.spaceWidth = null;
    this.font = font;
    this.fontSize = fontSize;
    return this;
  }

  // font, fontSize, width, and align are non-final and used in getLines(),
  // so if they are mutated, getLines() needs to be recomputed
  private void invalidateLineWrapping() {
    lines = null;
  }

  /**
   * /**
   *
   * @deprecated This method will be removed in a future release
   * @param color {@code int} rgb value for color
   * @return Paragraph's {@link Color}
   */
  @Deprecated
  public Paragraph withColor(int color) {
    this.color = new Color(color);
    return this;
  }

  /**
   * @deprecated This method will be replaced by {@code public Color getColor()} in a future release
   * @return Paragraph's {@link Color}
   */
  @Deprecated
  public int getColor() {
    return color.getRGB();
  }

  private float getHorizontalFreeSpace(final String text) {
    try {
      final float tw = font.getStringWidth(text.trim()) / 1000 * fontSize;
      return width - tw;
    } catch (IOException e) {
      throw new IllegalStateException("Unable to calculate text width", e);
    }
  }

  public float getWidth() {
    return width;
  }

  public String getText() {
    return text;
  }

  public float getFontSize() {
    return fontSize;
  }

  public PDFont getFont() {
    return font;
  }

  public HorizontalAlignment getAlign() {
    return align;
  }

  public void setAlign(HorizontalAlignment align) {
    invalidateLineWrapping();
    this.align = align;
  }

  public boolean isDrawDebug() {
    return drawDebug;
  }

  public void setDrawDebug(boolean drawDebug) {
    this.drawDebug = drawDebug;
  }

  public WrappingFunction getWrappingFunction() {
    return wrappingFunction == null ? DEFAULT_WRAP_FUNC : wrappingFunction;
  }

  public float getMaxLineWidth() {
    return maxLineWidth;
  }

  public float getLineWidth(int key) {
    return lineWidths.get(key);
  }

  public Map<Integer, List<Token>> getMapLineTokens() {
    return mapLineTokens;
  }

  public float getLineSpacing() {
    return lineSpacing;
  }

  public void setLineSpacing(float lineSpacing) {
    this.lineSpacing = lineSpacing;
  }
}
