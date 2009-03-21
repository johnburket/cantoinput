package canto;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.*;

/**
 * CantoInput - A simple Cantonese Input Method (IME)
 * Author: John Burket
 *
 * This file only contains the GUI code -- the interesting part is
 * located in the data sets :-)
 */
public class CantoInput extends KeyAdapter implements ActionListener {
    static private final String APP_NAME = "CantoInput";
    static private final String APP_NAME_AND_VERSION = APP_NAME + " 1.10";
    static private final String COPYRIGHT_MSG = "Copyright (C) 2006\nJohn Burket - jburket@gmail.com\n";
    static private final String CREDITS = "This program may be freely distributed, and is\n" +
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

    static private final String MENU_YALE = "Cantonese/Yale";
    static private final String MENU_JYUTPING = "Cantonese/Jyutping";
    static private final String MENU_PINYIN = "Mandarin/Pinyin";
    static private final String MENU_TRADITIONAL = "Traditional";
    static private final String MENU_SIMPLIFIED = "Simplified";
    static private final String MENU_CHOOSE_FONT = "Select Font";
    static private final String MENU_CUT = "Cut";
    static private final String MENU_COPY = "Copy";
    static private final String MENU_PASTE = "Paste";
    static private final String MENU_SELECT_ALL = "Select All";
    static private final String MENU_EXIT = "Exit";
    static private final String MENU_ABOUT = "About";
    static private final String MENU_TOGGLE_INPUT_MODE = "Toggle Chinese/English (Ctrl-Enter)";
    static private final String DATAFILE_YALE = "input-yale.utf-8";
    static private final String DATAFILE_JYUTPING = "input-jyutping.utf-8";
    static private final String DATAFILE_PINYIN = "input-pinyin.utf-8";
    static private final String DATAFILE_PUNCT = "punct.utf-8";
    static private final String DATAFILE_TRADSIMP = "trad-simp.utf-8";
    static private final String PREF_KEY_METHOD = "method";
    static private final String PREF_KEY_CHARSET = "charset";
    static private final String PREF_KEY_FONT = "font";
    static private final String PREF_KEY_FONT_SIZE = "font_size";
    static private final int DEFAULT_FONT_SIZE = 18;

    static private JFrame frame;
    static private Font defaultChineseFont;
    static private ArrayList availableChineseFonts = new ArrayList();
    static private JTextField inputTextField;
    static private JTextField matchTextField;
    static private JTextField pageNumTextField;
    static private JTextArea textArea;
    static private HashMap currentChoiceMap = new HashMap();
    static private ArrayList currentChoiceList = new ArrayList();
    static private HashMap punctuationMap = new HashMap();
    static private HashMap tradSimpMap = new HashMap();
    static private int currentPageNumber = 0;
    static private boolean inInputMode = true;
    static private String currentCharacterSet = MENU_TRADITIONAL;
    static private Properties prefs = new Properties();

    /**
     * Initialize common data files.
     */
    public CantoInput() {
        punctuationMap = readDataFile(DATAFILE_PUNCT);
        tradSimpMap = readDataFile(DATAFILE_TRADSIMP);
    }

    /**
     * Parse the data files.
     */
    private static HashMap readDataFile(String filename) {
        HashMap choices = new HashMap();

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(CantoInput.class.getResourceAsStream("/" + filename), "UTF-8"));

            for (String line; (line = in.readLine()) != null; ) {
                try {
                    StringTokenizer st = new StringTokenizer(line);
                    String key = st.nextToken();
                    if (st.hasMoreTokens()) {
                        String val = st.nextToken("\r\n").trim();
                        if (choices.get(key) != null) {
                            String lst = (String) choices.get(key);
                            choices.put(key, lst + " " + val);
                        }
                        else {
                            choices.put(key, val);
                        }
                    }
                }
                catch (Exception e) {
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return choices;
    }

    /**
     * Handle keyPressed event.
     * Note that VK_BACK_SPACE needs to be consumed in both keyPressed() and keyTyped()
     * because of Java version compatibility issues.
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
            if ((e.getKeyCode() == KeyEvent.VK_PAGE_DOWN || e.getKeyCode() == KeyEvent.VK_RIGHT || e.getKeyCode() == KeyEvent.VK_DOWN || c == '=' || c == '+' || c == '.' || c == '>' || c == ']' || c == '}') && currentChoiceList.size() > ((currentPageNumber + 1) * 9)) {
                currentPageNumber++;
            }
            else if ((e.getKeyCode() == KeyEvent.VK_PAGE_UP || e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_UP || c == '-' || c == '_' || c == ',' || c == '<' || c == '[' || c == '{') && currentPageNumber > 0) {
                currentPageNumber--;
            }
            e.consume();
            updateMatches();
        }
    }

    /**
     * Generates character lists and handles most of the work for the input method.
     * Note that VK_BACK_SPACE needs to be consumed in both keyPressed() and keyTyped()
     * because of Java version compatibility issues.
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
        else if (currentChoiceList != null && (c == '-' || c == '_' || c == '=' || c == '+' || c == '.' || c == ',' || c == '<' || c == '>' || c == '[' || c == ']' || c == '{' || c == '}')) {
            e.consume();
        }
        else if (punctuationMap.containsKey("" + c)) {
            String s = (String) punctuationMap.get("" + c);
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
        if (currentChoiceList != null) {
            updateMatches();

            if (c >= '1' && c <= '9') {
                try {
                    String s = (String) currentChoiceList.get((currentPageNumber * 9) + Integer.parseInt("" + c) - 1);
                    textArea.insert(s, textArea.getCaretPosition());
                    e.consume();
                    resetStateData();
                }
                catch (Exception ex) {
                }
            }
            else if (c == ' ') {
                String s = (String) currentChoiceList.get(currentPageNumber * 9);
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
     * Populate currentChoiceList with traditional or simplified characters depending on settings.
     */
    private static void populateCurrentChoiceList(String str) {
        String lst1 = (String) currentChoiceMap.get(str);
        ArrayList lst2 = new ArrayList();
        ArrayList lst3 = new ArrayList();
        HashSet hs = new HashSet();

        if (lst1 == null) {
            currentChoiceList = null;
            return;
        }

        for (StringTokenizer st = new StringTokenizer(lst1); st.hasMoreTokens(); ) {
            String s = st.nextToken();
            if (currentCharacterSet.equals(MENU_SIMPLIFIED)) {
                String simp_s = "";
                for (int j = 0; j < s.length(); j++) {
                    if (tradSimpMap.get("" + s.charAt(j)) != null) {
                        simp_s += (String) tradSimpMap.get("" + s.charAt(j));
                    }
                    else {
                        simp_s += s.charAt(j);
                    }
                }
                s = simp_s;
            }
            if (hs.add(s)) {
                // Move multiple character entries to beginning of list while preserving order in data file
                if (s.length() > 1) {
                    lst2.add(s);
                }
                else {
                    lst3.add(s);
                }
            }
        }

        lst2.addAll(lst3);
        currentChoiceList = lst2;
    }

    /**
     * Reset input/match/pagenum data.
     */
    private static void resetStateData() {
        inputTextField.setText("");
        matchTextField.setText("");
        pageNumTextField.setText("");
        currentChoiceList = null;
        currentPageNumber = 0;
    }

    /**
     * Update matches with respect to page number.
     */
    private static void updateMatches() {
        String choices = " ";
        for (int i = 0; i < 9; i++) {
            int j = (currentPageNumber * 9) + i;
            if (j >= currentChoiceList.size()) {
                break;
            }
            choices += (i+1) + ". " + (String) currentChoiceList.get(j) + " ";
        }

        int ptotal = currentChoiceList.size() / 9;
        if (currentChoiceList.size() % 9 != 0) {
            ptotal++;
        }
        pageNumTextField.setText((currentPageNumber + 1) + "/" + ptotal);

        matchTextField.setText(choices);
        matchTextField.setCaretPosition(0);
    }

    /**
     * Find Chinese-capable fonts.
     */
    private static void setDefaultChineseFont() {
        Font[] allFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();
        String chinesesample = "\u4e00";
        String goodFont = null;

        for (int j = 0; j < allFonts.length; j++) {
            if (allFonts[j].canDisplayUpTo(chinesesample) == -1) { 
                String aFontName = allFonts[j].getFontName();
                availableChineseFonts.add(aFontName);
                if (aFontName.toLowerCase().startsWith("simsun")) {
                    goodFont = aFontName;
                }
            }
        }

        if (! prefs.getProperty(PREF_KEY_FONT, "").equals("")) {
            try {
                defaultChineseFont = new Font(prefs.getProperty(PREF_KEY_FONT), Font.PLAIN, Integer.parseInt(prefs.getProperty(PREF_KEY_FONT_SIZE, "" + DEFAULT_FONT_SIZE)));
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
            defaultChineseFont = new Font((String) availableChineseFonts.get(0), Font.PLAIN, DEFAULT_FONT_SIZE);
        }
        else {
            defaultChineseFont = new Font("Monospaced", Font.PLAIN, DEFAULT_FONT_SIZE);
        }
    }

    /**
     * Load saved user preferences.
     */
    private static void loadUserPrefs() {
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
            // Ignore - we'll just use defaults if we can't read/write the file
        }
    }

    /**
     * Save new user preferences.
     */
    private static void saveUserPrefs(String prop, String value) {
        try {
            prefs.setProperty(prop, value);
            String dir = System.getProperty("user.home") + System.getProperty("file.separator") + APP_NAME;
            File f = new File(dir + System.getProperty("file.separator") + APP_NAME + ".properties");
            prefs.store(new FileOutputStream(f), null);
        }
        catch (Exception e) {
            // Ignore - we'll just use defaults if we can't read/write the file
        }
    }

    /**
     * Action listener for menu items.
     */
    public void actionPerformed(ActionEvent e) {
        JMenuItem source = (JMenuItem)(e.getSource());

        if (source.getText().equals(MENU_YALE)) {
            resetStateData();
            currentChoiceMap.clear();
            currentChoiceMap = readDataFile(DATAFILE_YALE);
            saveUserPrefs(PREF_KEY_METHOD, MENU_YALE);
        }
        else if (source.getText().equals(MENU_JYUTPING)) {
            resetStateData();
            currentChoiceMap.clear();
            currentChoiceMap = readDataFile(DATAFILE_JYUTPING);
            saveUserPrefs(PREF_KEY_METHOD, MENU_JYUTPING);
        }
        else if (source.getText().equals(MENU_PINYIN)) {
            resetStateData();
            currentChoiceMap.clear();
            currentChoiceMap = readDataFile(DATAFILE_PINYIN);
            saveUserPrefs(PREF_KEY_METHOD, MENU_PINYIN);
        }
        else if (source.getText().equals(MENU_TRADITIONAL)) {
            resetStateData();
            currentCharacterSet = MENU_TRADITIONAL;
            saveUserPrefs(PREF_KEY_CHARSET, MENU_TRADITIONAL);
        }
        else if (source.getText().equals(MENU_SIMPLIFIED)) {
            resetStateData();
            currentCharacterSet = MENU_SIMPLIFIED;
            saveUserPrefs(PREF_KEY_CHARSET, MENU_SIMPLIFIED);
        }
        else if (source.getText().equals(MENU_TOGGLE_INPUT_MODE)) {
            resetStateData();
            inInputMode = ! inInputMode;
        }
        else if (source.getText().equals(MENU_CHOOSE_FONT)) {
            if (availableChineseFonts == null || availableChineseFonts.size() == 0) {
                JOptionPane.showMessageDialog(null, "No Chinese fonts installed!", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String s = (String) JOptionPane.showInputDialog(
                                null,
                                "Available Chinese Fonts:",
                                "Select Font",
                                JOptionPane.PLAIN_MESSAGE,
                                null,
                                availableChineseFonts.toArray(),
                                defaultChineseFont.getFontName());

            if ((s != null) && (s.length() > 0)) {
                Object[] sz_possibilities = {"8","9","10","11","12","14","16","18","20","22","24","26","28","36","48"};
                String sz = (String) JOptionPane.showInputDialog(
                                     null,
                                     "Font Size:",
                                     "Select Font Size",
                                     JOptionPane.PLAIN_MESSAGE,
                                     null,
                                     sz_possibilities,
                                     "" + DEFAULT_FONT_SIZE);

                if (sz == null || sz.trim().equals("")) {
                    return;
                }

                int newFontSize;
                try {
                    newFontSize = Integer.parseInt(sz);
                }
                catch (Exception ex) {
                    newFontSize = DEFAULT_FONT_SIZE;
                    JOptionPane.showMessageDialog(null, "Invalid font size - using default");
                }

                defaultChineseFont = new Font(s, Font.PLAIN, newFontSize);
                inputTextField.setFont(defaultChineseFont);
                matchTextField.setFont(defaultChineseFont);
                pageNumTextField.setFont(defaultChineseFont);
                textArea.setFont(defaultChineseFont);
                setPreferredSizes();
                frame.pack();
                saveUserPrefs(PREF_KEY_FONT, s);
                saveUserPrefs(PREF_KEY_FONT_SIZE, "" + newFontSize);
            }
        }
        else if (source.getText().equals(MENU_EXIT)) {
            System.exit(0);
        }
        else if (source.getText().equals(MENU_CUT)) {
            textArea.cut();
        }
        else if (source.getText().equals(MENU_COPY)) {
            textArea.copy();
        }
        else if (source.getText().equals(MENU_PASTE)) {
            textArea.paste();
        }
        else if (source.getText().equals(MENU_SELECT_ALL)) {
            textArea.selectAll();
        }
        else if (source.getText().equals(MENU_ABOUT)) {
            JOptionPane.showMessageDialog(null, APP_NAME_AND_VERSION + "\n" + COPYRIGHT_MSG + "\n" + CREDITS);
        }
    }

    /**
     * Copy/paste popup menu for right-mouse click.
     */
    private static void addPopupMenu(CantoInput ci) {
        final JPopupMenu x = new JPopupMenu();

        JMenuItem i1 = new JMenuItem(MENU_CUT);
        JMenuItem i2 = new JMenuItem(MENU_COPY);
        JMenuItem i3 = new JMenuItem(MENU_PASTE);
        JMenuItem i4 = new JMenuItem(MENU_SELECT_ALL);

        i1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        i2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        i3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
        i4.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));

        x.add(i1); x.add(i2); x.add(i3);
        x.addSeparator(); x.add(i4);

        i1.addActionListener(ci);
        i2.addActionListener(ci);
        i3.addActionListener(ci);
        i4.addActionListener(ci);

        textArea.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                checkForTriggerEvent(e);
            }
            public void mouseReleased(MouseEvent e) {
                checkForTriggerEvent(e);
            }
            private void checkForTriggerEvent(MouseEvent e) {
                if (e.isPopupTrigger())
                    x.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }

    /**
     * Create the top menu bar.
     */
    private static JMenuBar createMenuBar(CantoInput ci) {
        JMenuBar menuBar;
        JMenu menu;
        JMenuItem menuItem;
        JRadioButtonMenuItem rbMenuItem;

        menuBar = new JMenuBar();

        menu = new JMenu("Settings");
        menu.setMnemonic(KeyEvent.VK_S);
        menuBar.add(menu);

        ButtonGroup group = new ButtonGroup();

        rbMenuItem = new JRadioButtonMenuItem(MENU_YALE);
        if (prefs.getProperty(PREF_KEY_METHOD, MENU_YALE).equals(MENU_YALE)) {
            rbMenuItem.setSelected(true);
            currentChoiceMap.clear();
            currentChoiceMap = readDataFile(DATAFILE_YALE);
        }
        rbMenuItem.addActionListener(ci);
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        rbMenuItem = new JRadioButtonMenuItem(MENU_JYUTPING);
        if (prefs.getProperty(PREF_KEY_METHOD, MENU_YALE).equals(MENU_JYUTPING)) {
            rbMenuItem.setSelected(true);
            currentChoiceMap.clear();
            currentChoiceMap = readDataFile(DATAFILE_JYUTPING);
        }
        rbMenuItem.addActionListener(ci);
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        rbMenuItem = new JRadioButtonMenuItem(MENU_PINYIN);
        if (prefs.getProperty(PREF_KEY_METHOD, MENU_YALE).equals(MENU_PINYIN)) {
            rbMenuItem.setSelected(true);
            currentChoiceMap.clear();
            currentChoiceMap = readDataFile(DATAFILE_PINYIN);
        }
        rbMenuItem.addActionListener(ci);
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        menu.addSeparator();
        group = new ButtonGroup();

        rbMenuItem = new JRadioButtonMenuItem(MENU_TRADITIONAL);
        if (prefs.getProperty(PREF_KEY_CHARSET, MENU_TRADITIONAL).equals(MENU_TRADITIONAL)) {
            rbMenuItem.setSelected(true);
            currentCharacterSet = MENU_TRADITIONAL;
        }
        rbMenuItem.addActionListener(ci);
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        rbMenuItem = new JRadioButtonMenuItem(MENU_SIMPLIFIED);
        if (prefs.getProperty(PREF_KEY_CHARSET, MENU_TRADITIONAL).equals(MENU_SIMPLIFIED)) {
            rbMenuItem.setSelected(true);
            currentCharacterSet = MENU_SIMPLIFIED;
        }
        rbMenuItem.addActionListener(ci);
        group.add(rbMenuItem);
        menu.add(rbMenuItem);

        menu.addSeparator();
        menuItem = new JMenuItem(MENU_TOGGLE_INPUT_MODE);
        menuItem.addActionListener(ci);
        menu.add(menuItem);

        menu.addSeparator();
        menuItem = new JMenuItem(MENU_CHOOSE_FONT);
        menuItem.addActionListener(ci);
        menu.add(menuItem);

        menu.addSeparator();
        menuItem = new JMenuItem(MENU_EXIT);
        menuItem.addActionListener(ci);
        menu.add(menuItem);

        menu = new JMenu("Edit");
        menu.setMnemonic(KeyEvent.VK_E);
        menuBar.add(menu);
        menuItem = new JMenuItem(MENU_CUT);
        menuItem.addActionListener(ci);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK));
        menu.add(menuItem);
        menuItem = new JMenuItem(MENU_COPY);
        menuItem.addActionListener(ci);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        menu.add(menuItem);
        menuItem = new JMenuItem(MENU_PASTE);
        menuItem.addActionListener(ci);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));
        menu.add(menuItem);
        menu.addSeparator();
        menuItem = new JMenuItem(MENU_SELECT_ALL);
        menuItem.addActionListener(ci);
        menuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_A, ActionEvent.CTRL_MASK));
        menu.add(menuItem);

        menu = new JMenu("Help");
        menu.setMnemonic(KeyEvent.VK_H);
        menuBar.add(menu);
        menuItem = new JMenuItem(MENU_ABOUT);
        menuItem.addActionListener(ci);
        menu.add(menuItem);

        return menuBar;
    }

    /**
     * Calculate font metrics so that window does not become arbitrarily wide
     * when using certain fonts.
     */
    private static void setPreferredSizes() {
        FontMetrics fm;
        int width;

        fm = matchTextField.getFontMetrics(defaultChineseFont);
        width = fm.stringWidth("9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00 9. \u4e00");
        matchTextField.setPreferredSize(new Dimension(width, fm.getHeight()));

        fm = inputTextField.getFontMetrics(defaultChineseFont);
        width = fm.stringWidth("seungseung");
        inputTextField.setPreferredSize(new Dimension(width, fm.getHeight()));

        fm = pageNumTextField.getFontMetrics(defaultChineseFont);
        width = fm.stringWidth(" 99/99 ");
        pageNumTextField.setPreferredSize(new Dimension(width, fm.getHeight()));
    }

    /**
     * Create the GUI and show it.
     */
    private static void createAndShowGUI() {
        // Create and set up the window
        loadUserPrefs();
        setDefaultChineseFont();
        frame = new JFrame(APP_NAME);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Container pane = frame.getContentPane();
        pane.setLayout(new BorderLayout());

        inputTextField = new JTextField();
        inputTextField.setEditable(false);
        inputTextField.setFont(defaultChineseFont);
        inputTextField.setBorder(BorderFactory.createLineBorder(Color.gray));

        matchTextField = new JTextField();
        matchTextField.setEditable(false);
        matchTextField.setFont(defaultChineseFont);
        matchTextField.setBorder(BorderFactory.createLineBorder(Color.gray));

        pageNumTextField = new JTextField();
        pageNumTextField.setEditable(false);
        pageNumTextField.setFont(defaultChineseFont);
        pageNumTextField.setHorizontalAlignment(JTextField.CENTER);
        pageNumTextField.setBorder(BorderFactory.createEmptyBorder());

        // Set realistic width for components
        setPreferredSizes();

        JPanel morePanel = new JPanel();
        morePanel.add(pageNumTextField);
        morePanel.setBorder(BorderFactory.createLineBorder(Color.gray));

        CantoInput ci = new CantoInput();
        textArea = new JTextArea();
        textArea.addKeyListener(ci);
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

        // Create the menu
        frame.setJMenuBar(createMenuBar(ci));
        addPopupMenu(ci);

        // Display the window
        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
    }
}

