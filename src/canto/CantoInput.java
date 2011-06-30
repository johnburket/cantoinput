package canto;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

/**
 * CantoInput - An Input Method (IME) for Cantonese.
 * Author: John Burket
 *
 * CantoInput is a freely available, Unicode-based Chinese input method (IME)
 * which allows the user to type both traditional and simplified characters
 * using Cantonese romanization.  Both the Yale and Jyutping methods are
 * supported.
 *
 * While there already exist excellent phonetic input methods based on Mandarin
 * Pinyin pronunciation, there is a general lack of support for Cantonese.  As a
 * Cantonese learner, I was frustrated by the difficulty of typing Chinese,
 * especially Cantonese specific colloquial characters.  Most existing Cantonese
 * input methods require a Chinese version of Windows and operate using
 * non-Unicode encodings such as BIG5 or GB, while non-phonetic methods such as
 * Cangjie have a very steep learning curve.
 *
 * I originally wrote this program in 2006 for my own personal use but decided to
 * make it freely available since I felt that other Cantonese speakers and
 * learners might also find it useful.  It's still pretty basic, but hopefully
 * I'll have time to add more features in the future.
 */
public class CantoInput extends KeyAdapter implements ActionListener {

   private static final String APP_NAME = "CantoInput";
   private static final String APP_NAME_AND_VERSION = APP_NAME + " 1.35";
   private static final String COPYRIGHT_MSG =
      "Author: John Burket\n";
   private static final String CREDITS =
      "This program may be freely distributed, and is\n" +
      "provided 'as is' without warranty of any kind.\n\n" +
      "Romanization is based on data from Aaron Chan's\n" +
      "excellent HanConv utility:  http://www.icycloud.tk\n\n" +
      "Chinese character frequency is based on 1993-1994\n" +
      "Usenet statistics compiled by Shih-Kun Huang at\n" +
      "the National Chiao Tung University in Taiwan.\n" +
      "List is located at Chih-Hao Tsai's site:\n" +
      "http://technology.chtsai.org/charfreq/\n\n" +
      "Compound character data derived from CEDICT,\n" +
      "Copyright (C) 1997, 1998 Paul Andrew Denisowski:\n" +
      "http://www.mandarintools.com/cedict.html\n\n";

   private static final String MENU_YALE = "Cantonese/Yale";
   private static final String MENU_JYUTPING = "Cantonese/Jyutping";
   private static final String MENU_PINYIN = "Mandarin/Pinyin";
   private static final String MENU_TRADITIONAL = "Traditional";
   private static final String MENU_SIMPLIFIED = "Simplified";
   private static final String MENU_CHOOSE_FONT = "Select Font";
   private static final String MENU_CUT = "Cut";
   private static final String MENU_COPY = "Copy";
   private static final String MENU_PASTE = "Paste";
   private static final String MENU_SELECT_ALL = "Select All";
   private static final String MENU_TRAD_TO_SIMP = "Convert Traditional to Simplified";
   private static final String MENU_SIMP_TO_TRAD = "Convert Simplified to Traditional";
   private static final String MENU_EXIT = "Exit";
   private static final String MENU_ABOUT = "About";
   private static final String MENU_TOGGLE_INPUT_MODE = "Toggle Chinese/English (Ctrl-Enter)";
   private static final String DATAFILE_YALE = "input-yale.utf-8";
   private static final String DATAFILE_JYUTPING = "input-jyutping.utf-8";
   private static final String DATAFILE_PINYIN = "input-pinyin.utf-8";
   private static final String DATAFILE_PUNCT = "punct.utf-8";
   private static final String DATAFILE_TRADSIMP = "trad-simp.utf-8";
   private static final String DATAFILE_SIMPTRAD = "simp-trad.utf-8";
   private static final String PREF_KEY_METHOD = "method";
   private static final String PREF_KEY_CHARSET = "charset";
   private static final String PREF_KEY_FONT = "font";
   private static final String PREF_KEY_FONT_SIZE = "font_size";
   private static final int DEFAULT_FONT_SIZE = 18;

   // GUI components
   private final JFrame frame = new JFrame(APP_NAME);
   private final JTextField inputTextField = new JTextField();
   private final JTextField matchTextField = new JTextField();
   private final JTextField pageNumTextField = new JTextField();
   private final JTextArea textArea = new JTextArea();

   // State data
   private int currentPageNumber = 0;
   private boolean inInputMode = true;
   private String currentCharacterSet = MENU_TRADITIONAL;
   private Font defaultChineseFont;
   private List<String> availableChineseFonts = new ArrayList<String>();
   private List<String> currentChoiceList = new ArrayList<String>();
   private Map<String,String> currentChoiceMap = new TreeMap<String,String>();
   private Map<String,String> punctuationMap;
   private Map<String,String> tradSimpMap;
   private Map<String,String> simpTradMap;
   private Properties prefs;

   /**
    * Constructor.  Initialize data files.
    */
   public CantoInput() {
      punctuationMap = readDataFile(DATAFILE_PUNCT);
      tradSimpMap = readDataFile(DATAFILE_TRADSIMP);
      simpTradMap = readDataFile(DATAFILE_SIMPTRAD);
   }

   /**
    * Parse CantoInput data files and return a Map of the data.
    * The Map key is the first token in a line from the file (up to the first
    * space) and the Map value is the remainder of the line after the space.
    * If the same key occurs multiple times, the values will be merged.
    * These data files contain Chinese characters/words and the corresponding
    * romanization, traditional to simplified character mappings, and Chinese
    * punctuation symbols.  To facilitate partial data matching, a TreeMap
    * will be created for the input method lookup files.
    *
    * @param filename - Filename containing data to parse
    * @return - Map containing data from file
    */
   private Map<String,String> readDataFile(String filename) {
      Map<String,String> map;

      if (filename.startsWith("input-")) {
         map = new TreeMap<String,String>();
      }
      else {
         map = new HashMap<String,String>();
      }

      try {
         BufferedReader in = new BufferedReader(
            new InputStreamReader(this.getClass().getResourceAsStream("/" + filename), "UTF-8"));

         for (String line; (line = in.readLine()) != null; ) {
            String s[] = line.split("\\s+", 2);
            String key = s[0];
            String val = map.get(key) != null ? map.get(key) + " " + s[1] : s[1];
            map.put(key, val);
         }
      }
      catch (Exception e) {
         e.printStackTrace();
      }

      return map;
   }

   /**
    * Handle keyPressed event.  Note that VK_BACK_SPACE needs to be consumed in
    * both keyPressed() and keyTyped() because of Java version compatibility
    * issues.
    */
   public void keyPressed(KeyEvent e) {
      if (! inInputMode) {
         return;
      }

      char c = Character.toLowerCase(e.getKeyChar());

      if (c == KeyEvent.VK_BACK_SPACE) {
         String s = inputTextField.getText();
         if (s != null && ! s.equals("")) {
            e.consume();
         }
      }
      else if (c == KeyEvent.VK_ENTER) {
         String s = inputTextField.getText();
         if (s != null && ! s.equals("")) {
            e.consume();
         }
      }

      if (currentChoiceList != null) {
         if ((e.getKeyCode() == KeyEvent.VK_PAGE_DOWN || e.getKeyCode() == KeyEvent.VK_RIGHT ||
              e.getKeyCode() == KeyEvent.VK_DOWN || c == '=' || c == '+' || c == '.' ||
              c == '>' || c == ']' || c == '}') && currentChoiceList.size() > ((currentPageNumber + 1) * 9))
         {
            currentPageNumber++;
         }
         else if ((e.getKeyCode() == KeyEvent.VK_PAGE_UP || e.getKeyCode() == KeyEvent.VK_LEFT ||
                   e.getKeyCode() == KeyEvent.VK_UP || c == '-' || c == '_' || c == ',' ||
                   c == '<' || c == '[' || c == '{') && currentPageNumber > 0)
         {
            currentPageNumber--;
         }
         e.consume();
         updateMatches();
      }
   }

   /**
    * Generates character lists and handles most of the work for the input
    * method.  Note that VK_BACK_SPACE needs to be consumed in both keyPressed()
    * and keyTyped() because of Java version compatibility issues.
    */
   public void keyTyped(KeyEvent e) {
      char c = Character.toLowerCase(e.getKeyChar());

      if (c == KeyEvent.VK_ENTER && e.isControlDown()) {
         inInputMode = ! inInputMode;
         if (! inInputMode) {
            resetStateData();
         }
      }

      if (! inInputMode) {
         return;
      }

      if (c >= 'a' && c <= 'z' && ! e.isAltDown()) {
         inputTextField.setText(inputTextField.getText() + c);
         currentPageNumber = 0;
         e.consume();
      }
      else if (c == KeyEvent.VK_BACK_SPACE) {
         String s = inputTextField.getText();
         if (s != null && ! s.equals("")) {
            inputTextField.setText(s.substring(0, s.length() - 1));
            currentPageNumber = 0;
            e.consume();
         }
      }
      else if (currentChoiceList != null && (c == '-' || c == '_' || c == '=' || c == '+' ||
               c == '.' || c == ',' || c == '<' || c == '>' || c == '[' || c == ']' ||
               c == '{' || c == '}'))
      {
         e.consume();
      }
      else if (punctuationMap.containsKey("" + c)) {
         String s = punctuationMap.get("" + c);
         e.setKeyChar(s.charAt(0));
      }
      else if (c == KeyEvent.VK_ESCAPE) {
         String s = inputTextField.getText();
         if (s != null && ! s.equals("")) {
            resetStateData();
            e.consume();
         }
      }
      else if (c == KeyEvent.VK_ENTER) {
         String s = inputTextField.getText();
         if (s != null && ! s.equals("")) {
            e.consume();
         }
      }

      populateCurrentChoiceList(inputTextField.getText());

      if (currentChoiceList != null && currentChoiceList.size() > 0) {
         updateMatches();

         if (c >= '1' && c <= '9') {
            try {
               String s = currentChoiceList.get((currentPageNumber * 9) + Integer.parseInt("" + c) - 1);
               textArea.insert(s, textArea.getCaretPosition());
               e.consume();
               resetStateData();
            }
            catch (Exception ex) {
            }
         }
         else if (c == ' ') {
            String s = currentChoiceList.get(currentPageNumber * 9);
            textArea.insert(s, textArea.getCaretPosition());
            e.consume();
            resetStateData();
         }
      }
      else {
         matchTextField.setText("");
         pageNumTextField.setText("");
      }
   }

   /**
    * Populate currentChoiceList with traditional or simplified characters
    * depending on settings.  All words which start with the input string are
    * placed into the resultant list: exact matches are put at the beginning of
    * the list, followed by partial matches in the order of the closest match.
    */
   private void populateCurrentChoiceList(String input) {
      List<String> choiceList = new ArrayList<String>();
      Set<String> dupeSet = new HashSet<String>();

      if (input == null || input.equals("")) {
         currentChoiceList = null;
         return;
      }

      Map<String,String> map = ((TreeMap) currentChoiceMap).subMap(input, input + "zzz");

      for (String val : map.values()) {
         for (String choice : val.split("\\s+")) {
            if (currentCharacterSet.equals(MENU_SIMPLIFIED)) {
               choice = convertTradSimp(choice, tradSimpMap);
            }
            if (dupeSet.add(choice)) {
               choiceList.add(choice);
            }
         }
      }

      currentChoiceList = choiceList;
   }

   /**
    * Reset input/match/pagenum data.
    */
   private void resetStateData() {
      inputTextField.setText("");
      matchTextField.setText("");
      pageNumTextField.setText("");
      currentChoiceList = null;
      currentPageNumber = 0;
   }

   /**
    * Update matches with respect to page number.
    */
   private void updateMatches() {
      String choices = " ";
      for (int i = 0; i < 9; i++) {
         int j = (currentPageNumber * 9) + i;
         if (j >= currentChoiceList.size()) {
            break;
         }
         choices += (i+1) + ". " + currentChoiceList.get(j) + " ";
      }

      int pageTotal = currentChoiceList.size() / 9;
      if (currentChoiceList.size() % 9 != 0) {
         pageTotal++;
      }
      pageNumTextField.setText((currentPageNumber + 1) + "/" + pageTotal);

      matchTextField.setText(choices);
      matchTextField.setCaretPosition(0);
   }

   /**
    * Find Chinese capable fonts.
    */
   private void setDefaultChineseFont() {
      String goodFont = null;

      for (Font font : GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts()) {
         if (font.canDisplayUpTo("\u4e00") == -1) {
            String fontName = font.getFontName();
            availableChineseFonts.add(fontName);
            if (fontName.toLowerCase().startsWith("simsun")) {
               goodFont = fontName;
            }
         }
      }

      if (! prefs.getProperty(PREF_KEY_FONT, "").equals("")) {
         try {
            defaultChineseFont = new Font(
               prefs.getProperty(PREF_KEY_FONT),
               Font.PLAIN,
               Integer.parseInt(prefs.getProperty(PREF_KEY_FONT_SIZE, "" + DEFAULT_FONT_SIZE)));
         }
         catch (Exception e) {
            defaultChineseFont = new Font("Monospaced", Font.PLAIN, DEFAULT_FONT_SIZE);
            saveUserPrefs(PREF_KEY_FONT, "");
            saveUserPrefs(PREF_KEY_FONT_SIZE, "" + DEFAULT_FONT_SIZE);
         }
      }
      else if (goodFont != null) {
         defaultChineseFont = new Font(goodFont, Font.PLAIN, DEFAULT_FONT_SIZE);
      }
      else if (availableChineseFonts.size() > 0) {
         defaultChineseFont = new Font(availableChineseFonts.get(0), Font.PLAIN, DEFAULT_FONT_SIZE);
      }
      else {
         defaultChineseFont = new Font("Monospaced", Font.PLAIN, DEFAULT_FONT_SIZE);
      }
   }

   /**
    * Load saved user preferences.
    */
   private void loadUserPrefs() {
      prefs = new Properties();
      try {
         String dir = System.getProperty("user.home") + System.getProperty("file.separator") + APP_NAME;
         new File(dir).mkdir();
         File f = new File(dir + System.getProperty("file.separator") + APP_NAME + ".properties");
         if (! f.createNewFile()) {
            prefs.load(new FileInputStream(f));
         }
      }
      catch (Exception e) {
         // Use default settings if we can't read/write the file
      }
   }

   /**
    * Save new user preferences.
    */
   private void saveUserPrefs(String prop, String value) {
      try {
         prefs.setProperty(prop, value);
         String dir = System.getProperty("user.home") + System.getProperty("file.separator") + APP_NAME;
         File f = new File(dir + System.getProperty("file.separator") + APP_NAME + ".properties");
         prefs.store(new FileOutputStream(f), null);
      }
      catch (Exception e) {
         // Use default settings if we can't read/write the file
      }
   }

   /**
    * Action listener for menu items.
    */
   public void actionPerformed(ActionEvent e) {
      String source = ((JMenuItem) e.getSource()).getText();

      if (MENU_YALE.equals(source)) {
         resetStateData();
         currentChoiceMap.clear();
         currentChoiceMap = readDataFile(DATAFILE_YALE);
         saveUserPrefs(PREF_KEY_METHOD, MENU_YALE);
      }
      else if (MENU_JYUTPING.equals(source)) {
         resetStateData();
         currentChoiceMap.clear();
         currentChoiceMap = readDataFile(DATAFILE_JYUTPING);
         saveUserPrefs(PREF_KEY_METHOD, MENU_JYUTPING);
      }
      else if (MENU_PINYIN.equals(source)) {
         resetStateData();
         currentChoiceMap.clear();
         currentChoiceMap = readDataFile(DATAFILE_PINYIN);
         saveUserPrefs(PREF_KEY_METHOD, MENU_PINYIN);
      }
      else if (MENU_TRADITIONAL.equals(source)) {
         resetStateData();
         currentCharacterSet = MENU_TRADITIONAL;
         saveUserPrefs(PREF_KEY_CHARSET, MENU_TRADITIONAL);
      }
      else if (MENU_SIMPLIFIED.equals(source)) {
         resetStateData();
         currentCharacterSet = MENU_SIMPLIFIED;
         saveUserPrefs(PREF_KEY_CHARSET, MENU_SIMPLIFIED);
      }
      else if (MENU_TOGGLE_INPUT_MODE.equals(source)) {
         resetStateData();
         inInputMode = ! inInputMode;
      }
      else if (MENU_CHOOSE_FONT.equals(source)) {
         setFontConfig();
      }
      else if (MENU_EXIT.equals(source)) {
         System.exit(0);
      }
      else if (MENU_CUT.equals(source)) {
         textArea.cut();
      }
      else if (MENU_COPY.equals(source)) {
         textArea.copy();
      }
      else if (MENU_PASTE.equals(source)) {
         textArea.paste();
      }
      else if (MENU_SELECT_ALL.equals(source)) {
         textArea.selectAll();
      }
      else if (MENU_TRAD_TO_SIMP.equals(source)) {
         convertSelected(tradSimpMap);
      }
      else if (MENU_SIMP_TO_TRAD.equals(source)) {
         convertSelected(simpTradMap);
      }
      else if (MENU_ABOUT.equals(source)) {
         JOptionPane.showMessageDialog(null, APP_NAME_AND_VERSION + "\n" + COPYRIGHT_MSG + "\n" + CREDITS);
      }
   }

   /**
    * Present dialog for updating font config settings.
    */
   private void setFontConfig() {
      if (availableChineseFonts == null || availableChineseFonts.size() == 0) {
         JOptionPane.showMessageDialog(
            null,
            "No Chinese fonts installed!",
            "Error",
            JOptionPane.ERROR_MESSAGE);
         return;
      }

      String font = (String) JOptionPane.showInputDialog(
                                null,
                                "Available Chinese Fonts:",
                                "Select Font",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                availableChineseFonts.toArray(),
                                defaultChineseFont.getFontName());

      if (font == null || font.length() == 0) {
         return;
      }

      Object[] sizeChoices = { "8","9","10","11","12","14","16","18",
                               "20","22","24","26","28","36","48" };

      String size = (String) JOptionPane.showInputDialog(
                                null,
                                "Font Size:",
                                "Select Font Size",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                sizeChoices,
                                "" + DEFAULT_FONT_SIZE);

      if (size == null || size.trim().equals("")) {
         return;
      }

      int newFontSize;
      try {
         newFontSize = Integer.parseInt(size);
      }
      catch (Exception ex) {
         newFontSize = DEFAULT_FONT_SIZE;
         JOptionPane.showMessageDialog(null, "Invalid font size - using default");
      }

      defaultChineseFont = new Font(font, Font.PLAIN, newFontSize);
      inputTextField.setFont(defaultChineseFont);
      matchTextField.setFont(defaultChineseFont);
      pageNumTextField.setFont(defaultChineseFont);
      textArea.setFont(defaultChineseFont);
      setPreferredSizes();
      frame.pack();
      saveUserPrefs(PREF_KEY_FONT, font);
      saveUserPrefs(PREF_KEY_FONT_SIZE, "" + newFontSize);
   }

   /**
    * Convert String from traditional to simplified characters or vice-versa.
    *
    * @param str - String to convert
    * @param conversionMap - Map which contains either trad->simp or simp->trad data
    * @return - String containing converted text
    */
   private String convertTradSimp(String str, Map<String,String> conversionMap) {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < str.length(); i++) {
         if (conversionMap.get("" + str.charAt(i)) != null) {
            sb.append(conversionMap.get("" + str.charAt(i)));
         }
         else {
            sb.append(str.charAt(i));
         }
      }

      return sb.toString();
   }

   /**
    * Convert selected text in the GUI from traditional to simplified
    * characters or vice-versa.
    *
    * @param conversionMap - Map which contains either trad->simp or simp->trad data
    */
   private void convertSelected(Map<String,String> conversionMap) {
      String selectedText = textArea.getSelectedText();
      if (selectedText == null || selectedText.equals("")) {
         JOptionPane.showMessageDialog(
            frame,
            "Please select some text and try again.",
            "Convert Characters",
            JOptionPane.ERROR_MESSAGE);
         return;
      }

      int select = JOptionPane.showConfirmDialog(
                      frame,
                      "The selected characters will be\n" +
                      "converted.  This operation cannot\n" +
                      "be undone!  Click OK to continue.",
                      "Convert Characters",
                      JOptionPane.OK_CANCEL_OPTION,
                      JOptionPane.INFORMATION_MESSAGE);

      if (select != JOptionPane.OK_OPTION) {
         return;
      }

      textArea.replaceSelection(convertTradSimp(selectedText, conversionMap));
   }

   /**
    * Add popup menu for right-mouse click (copy/paste/etc).
    */
   private void addPopupMenu() {
      final JPopupMenu menu = new JPopupMenu();

      JMenuItem item1 = new JMenuItem(MENU_CUT);
      JMenuItem item2 = new JMenuItem(MENU_COPY);
      JMenuItem item3 = new JMenuItem(MENU_PASTE);
      JMenuItem item4 = new JMenuItem(MENU_SELECT_ALL);
      JMenuItem item5 = new JMenuItem(MENU_TRAD_TO_SIMP);
      JMenuItem item6 = new JMenuItem(MENU_SIMP_TO_TRAD);

      item1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
      item2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
      item3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
      item4.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
      item5.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));
      item6.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK));

      menu.add(item1);
      menu.add(item2);
      menu.add(item3);
      menu.addSeparator();
      menu.add(item4);
      menu.addSeparator();
      menu.add(item5);
      menu.add(item6);

      item1.addActionListener(this);
      item2.addActionListener(this);
      item3.addActionListener(this);
      item4.addActionListener(this);
      item5.addActionListener(this);
      item6.addActionListener(this);

      textArea.addMouseListener(new MouseAdapter() {
         public void mousePressed(MouseEvent e) {
            checkForTriggerEvent(e);
         }
         public void mouseReleased(MouseEvent e) {
            checkForTriggerEvent(e);
         }
         private void checkForTriggerEvent(MouseEvent e) {
            if (e.isPopupTrigger()) {
               menu.show(e.getComponent(), e.getX(), e.getY());
            }
         }
      });
   }

   /**
    * Create the top menu bar.
    */
   private JMenuBar createMenuBar() {
      JMenuBar menuBar = new JMenuBar();
      JMenu menu = new JMenu("Settings");
      ButtonGroup group = new ButtonGroup();

      menu.setMnemonic(KeyEvent.VK_S);
      menuBar.add(menu);

      JRadioButtonMenuItem rbMenuItem = new JRadioButtonMenuItem(MENU_YALE);
      if (prefs.getProperty(PREF_KEY_METHOD, MENU_YALE).equals(MENU_YALE)) {
         rbMenuItem.setSelected(true);
         currentChoiceMap.clear();
         currentChoiceMap = readDataFile(DATAFILE_YALE);
      }
      rbMenuItem.addActionListener(this);
      group.add(rbMenuItem);
      menu.add(rbMenuItem);

      rbMenuItem = new JRadioButtonMenuItem(MENU_JYUTPING);
      if (prefs.getProperty(PREF_KEY_METHOD, MENU_YALE).equals(MENU_JYUTPING)) {
         rbMenuItem.setSelected(true);
         currentChoiceMap.clear();
         currentChoiceMap = readDataFile(DATAFILE_JYUTPING);
      }
      rbMenuItem.addActionListener(this);
      group.add(rbMenuItem);
      menu.add(rbMenuItem);

      rbMenuItem = new JRadioButtonMenuItem(MENU_PINYIN);
      if (prefs.getProperty(PREF_KEY_METHOD, MENU_YALE).equals(MENU_PINYIN)) {
         rbMenuItem.setSelected(true);
         currentChoiceMap.clear();
         currentChoiceMap = readDataFile(DATAFILE_PINYIN);
      }
      rbMenuItem.addActionListener(this);
      group.add(rbMenuItem);
      menu.add(rbMenuItem);

      menu.addSeparator();
      group = new ButtonGroup();

      rbMenuItem = new JRadioButtonMenuItem(MENU_TRADITIONAL);
      if (prefs.getProperty(PREF_KEY_CHARSET, MENU_TRADITIONAL).equals(MENU_TRADITIONAL)) {
         rbMenuItem.setSelected(true);
         currentCharacterSet = MENU_TRADITIONAL;
      }
      rbMenuItem.addActionListener(this);
      group.add(rbMenuItem);
      menu.add(rbMenuItem);

      rbMenuItem = new JRadioButtonMenuItem(MENU_SIMPLIFIED);
      if (prefs.getProperty(PREF_KEY_CHARSET, MENU_TRADITIONAL).equals(MENU_SIMPLIFIED)) {
         rbMenuItem.setSelected(true);
         currentCharacterSet = MENU_SIMPLIFIED;
      }
      rbMenuItem.addActionListener(this);
      group.add(rbMenuItem);
      menu.add(rbMenuItem);

      menu.addSeparator();
      JMenuItem menuItem = new JMenuItem(MENU_TOGGLE_INPUT_MODE);
      menuItem.addActionListener(this);
      menu.add(menuItem);

      menu.addSeparator();
      menuItem = new JMenuItem(MENU_CHOOSE_FONT);
      menuItem.addActionListener(this);
      menu.add(menuItem);

      menu.addSeparator();
      menuItem = new JMenuItem(MENU_EXIT);
      menuItem.addActionListener(this);
      menu.add(menuItem);

      menu = new JMenu("Edit");
      menu.setMnemonic(KeyEvent.VK_E);
      menuBar.add(menu);
      menuItem = new JMenuItem(MENU_CUT);
      menuItem.addActionListener(this);
      menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
      menu.add(menuItem);
      menuItem = new JMenuItem(MENU_COPY);
      menuItem.addActionListener(this);
      menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
      menu.add(menuItem);
      menuItem = new JMenuItem(MENU_PASTE);
      menuItem.addActionListener(this);
      menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
      menu.add(menuItem);
      menu.addSeparator();
      menuItem = new JMenuItem(MENU_SELECT_ALL);
      menuItem.addActionListener(this);
      menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
      menu.add(menuItem);
      menu.addSeparator();
      menuItem = new JMenuItem(MENU_TRAD_TO_SIMP);
      menuItem.addActionListener(this);
      menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P, ActionEvent.CTRL_MASK));
      menu.add(menuItem);
      menuItem = new JMenuItem(MENU_SIMP_TO_TRAD);
      menuItem.addActionListener(this);
      menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, ActionEvent.CTRL_MASK));
      menu.add(menuItem);

      menu = new JMenu("Help");
      menu.setMnemonic(KeyEvent.VK_H);
      menuBar.add(menu);
      menuItem = new JMenuItem(MENU_ABOUT);
      menuItem.addActionListener(this);
      menu.add(menuItem);

      return menuBar;
   }

   /**
    * Calculate font metrics so that window does not become arbitrarily wide
    * when using certain fonts.
    */
   private void setPreferredSizes() {
      FontMetrics metrics = matchTextField.getFontMetrics(defaultChineseFont);
      int width = metrics.stringWidth("9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00");
      matchTextField.setPreferredSize(new Dimension(width, metrics.getHeight()));

      metrics = inputTextField.getFontMetrics(defaultChineseFont);
      width = metrics.stringWidth("seungseung");
      inputTextField.setPreferredSize(new Dimension(width, metrics.getHeight()));

      metrics = pageNumTextField.getFontMetrics(defaultChineseFont);
      width = metrics.stringWidth(" 99/99 ");
      pageNumTextField.setPreferredSize(new Dimension(width, metrics.getHeight()));
   }

   /**
    * Create the GUI and show it.
    */
   private void createAndShowGUI() {
      loadUserPrefs();
      setDefaultChineseFont();
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      Container pane = frame.getContentPane();
      pane.setLayout(new BorderLayout());

      inputTextField.setEditable(false);
      inputTextField.setFont(defaultChineseFont);
      inputTextField.setBorder(BorderFactory.createLineBorder(Color.gray));

      matchTextField.setEditable(false);
      matchTextField.setFont(defaultChineseFont);
      matchTextField.setBorder(BorderFactory.createLineBorder(Color.gray));

      pageNumTextField.setEditable(false);
      pageNumTextField.setFont(defaultChineseFont);
      pageNumTextField.setHorizontalAlignment(JTextField.CENTER);
      pageNumTextField.setBorder(BorderFactory.createEmptyBorder());

      setPreferredSizes();

      JPanel morePanel = new JPanel();
      morePanel.add(pageNumTextField);
      morePanel.setBorder(BorderFactory.createLineBorder(Color.gray));

      textArea.addKeyListener(this);
      textArea.setFont(defaultChineseFont);
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      JScrollPane areaScrollPane = new JScrollPane(textArea);
      areaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
      areaScrollPane.setPreferredSize(new Dimension(650, 70));
      areaScrollPane.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
      pane.add(areaScrollPane, BorderLayout.CENTER);

      JPanel innerPane = new JPanel(new BorderLayout());
      innerPane.add(inputTextField, BorderLayout.WEST);
      innerPane.add(matchTextField, BorderLayout.CENTER);
      innerPane.add(morePanel, BorderLayout.EAST);
      pane.add(innerPane, BorderLayout.SOUTH);

      frame.setJMenuBar(createMenuBar());
      addPopupMenu();

      frame.pack();
      frame.setVisible(true);
   }

   /**
    * Start the GUI.
    */
   public static void main(String[] args) {
      javax.swing.SwingUtilities.invokeLater(new Runnable() {
         public void run() {
            new CantoInput().createAndShowGUI();
         }
      });
   }
}

