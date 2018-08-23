package nildumu.ui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.CompletionProvider;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SquiggleUnderlineHighlightPainter;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.ChangeableHighlightPainter;
import org.fife.ui.rtextarea.RTextScrollPane;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListDataListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.BadLocationException;

import nildumu.Context;
import nildumu.LeakageCalculation;
import nildumu.NildumuError;
import nildumu.Parser;
import nildumu.Processor;
import nildumu.PythonCaller;
import nildumu.ResponsiveTimer;
import swp.LocatedSWPException;
import swp.lexer.Location;
import swp.util.ParserError;

import static nildumu.Lattices.*;
import static nildumu.Util.p;

public class BasicUI {
    private JPanel panel1;
    private RSyntaxTextArea inputArea;
    private JungPanel jungPanel;
    private JComboBox attackerSecLevelInput;
    private JButton resetButton;
    private JTextArea outputArea;
    private JTable leakageTable;
    private JLabel parserErrorLabel;
    private JCheckBox automaticRedrawCheckBox;
    private JButton redrawButton;
    private RTextScrollPane inputScrollArea;
    private RSyntaxTextArea ssaArea;
    private JTable nodeValueTable;
    private JScrollPane nodeValueScrollPane;
    private JComboBox storeSelectComboBox;
    private JButton storeButton;
    private JScrollPane variableValueScrollPane;
    private JTable variableValueTable;
    private JButton storeJsonButton;
    private JComboBox modeComboBox;
    private JCheckBox pruneCheckBox;
    private Context context = null;
    private DocumentListener documentListener;
    private ResponsiveTimer graphViewRefreshTimer;
    private ResponsiveTimer processRefreshTimer;
    private Object syntaxErrorHighlightTag = null;
    private Object nodeSelectHighlightTag = null;
    private boolean inComboxBoxHandler = false;

    public BasicUI() {
        documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (processRefreshTimer != null) {
                    processRefreshTimer.request();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                insertUpdate(e);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                insertUpdate(e);
            }
        };
        inputArea.getDocument().addDocumentListener(documentListener);
        resetButton.addActionListener(e -> jungPanel.reset());
        resetButton.addActionListener(e -> jungPanel.reset());
        attackerSecLevelInput.addActionListener(e -> updateGraphView());
        inputArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        inputArea.setCodeFoldingEnabled(true);
        inputArea.setMarkOccurrences(true);
        inputArea.setAutoIndentEnabled(true);
        inputArea.setCloseCurlyBraces(true);
        inputArea.setCodeFoldingEnabled(true);
        inputScrollArea.setLineNumbersEnabled(true);
        inputScrollArea.setFoldIndicatorEnabled(true);
        ssaArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        ssaArea.setMarkOccurrences(true);
        CompletionProvider provider = createCompletionProvider();
        AutoCompletion ac = new AutoCompletion(provider);
        ac.install(inputArea);

        for (Context.Mode mode : Context.Mode.values()) {
            modeComboBox.addItem(mode);
        }
        modeComboBox.setSelectedItem(Context.Mode.valueOf(getVarContent("examples/lastMode", Context.Mode.BASIC.name())));
        modeComboBox.addActionListener(a -> {
            Context.Mode mode = (Context.Mode) modeComboBox.getSelectedItem();
            storeVarInFile("examples/lastMode", mode.name());
            processRefreshTimer.request();
        });
        graphViewRefreshTimer = new ResponsiveTimer(() -> updateGraphView());
        graphViewRefreshTimer.start();
        processRefreshTimer = new ResponsiveTimer(() -> processAndUpdate(inputArea.getText()));
        processRefreshTimer.start();
        automaticRedrawCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (automaticRedrawCheckBox.isSelected()) {
                    graphViewRefreshTimer.restart();
                } else {
                    graphViewRefreshTimer.stop();
                }
            }
        });
        redrawButton.addActionListener(e -> updateGraphView());
        jungPanel.addNodeClickedHandler(this::updateNodeSelection);
        storeSelectComboBox.addActionListener(a -> {
            if (inComboxBoxHandler) {
                return;
            }
            String name = (String) storeSelectComboBox.getSelectedItem();
            storeSelectComboBox.removeAllItems();
            for (String n : getExampleNames()) {
                storeSelectComboBox.addItem(n);
            }
            storeLastName(name);
            load(name);
            inComboxBoxHandler = true;
            storeSelectComboBox.setSelectedItem(getLastName());
            inComboxBoxHandler = false;
        });
        String lastContent = getLastContent();
        inComboxBoxHandler = true;
        storeSelectComboBox.setSelectedItem(getLastName());
        if (getLastContent() != null) {
            setProgram(lastContent);
        } else {
            load(getLastName());
        }
        inComboxBoxHandler = false;
        storeButton.addActionListener(a -> store((String) storeSelectComboBox.getSelectedItem()));
        storeJsonButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setCurrentDirectory(Paths.get(getVarContent("examples/lastJSONDir", ".")).toFile());
            fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
            if (fileChooser.showDialog(panel1, "Choose file to export json") == JFileChooser.APPROVE_OPTION) {
                Path path = fileChooser.getSelectedFile().toPath();
                storeGraphJSON(path);
                storeVarInFile("examples/lastJSONDir", path.getParent().toString());
            }
        });
        pruneCheckBox.addActionListener(e -> {
            graphViewRefreshTimer.request();
        });
    }

    public void processAndUpdate(String program) {
        if (program.isEmpty()) {
            return;
        }
        storeLastContent(program);
        clearOutput();
        parserErrorLabel.setText("");
        if (syntaxErrorHighlightTag != null) {
            inputArea.getHighlighter().removeHighlight(syntaxErrorHighlightTag);
        }
        if (nodeSelectHighlightTag != null) {
            inputArea.getHighlighter().removeHighlight(nodeSelectHighlightTag);
        }
        try {
            Context.Mode mode = (Context.Mode) modeComboBox.getSelectedItem();
            Context c = Processor.process(program, mode);
            if (context == null || c.sl != context.sl) {
                context = c;
                updateSecLattice();
            }
            context = c;
            updateLeakageTable(context);
            updateNodeValueTable(context);
            updateVariableValueTable(context);
            ssaArea.setText(Parser.process(program).toPrettyString());
            graphViewRefreshTimer.request();
            context.checkInvariants();
        } catch (LocatedSWPException e) {
            parserErrorLabel.setText(e.getMessage());
            Location errorLocation = e.errorToken.location;
            int startOffset = 0;
            try {
                startOffset = inputArea.getLineStartOffset(errorLocation.line - 1) + errorLocation.column;
                syntaxErrorHighlightTag = inputArea.getHighlighter().addHighlight(startOffset, startOffset + e.errorToken.value.length(), new SquiggleUnderlineHighlightPainter(Color.red));
                inputArea.invalidate();
                inputArea.repaint();
            } catch (BadLocationException e1) {
                e1.printStackTrace();
            }
        } catch (Parser.MJError e) {
            parserErrorLabel.setText(e.getMessage());
        } catch (NildumuError e) {
            addOutput(e.getMessage());
            parserErrorLabel.setText(e.getMessage());
        } catch (RuntimeException e) {
            parserErrorLabel.setText(e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateNodeSelection(LeakageCalculation.VisuNode selectedNode) {
        if (nodeSelectHighlightTag != null) {
            inputArea.getHighlighter().removeHighlight(nodeSelectHighlightTag);
        }
        if (selectedNode.node() != null) {
            Location location = selectedNode.node().location;
            try {
                int startOffset = inputArea.getLineStartOffset(location.line - 1) + location.column;
                nodeSelectHighlightTag = inputArea.getHighlighter().addHighlight(startOffset, startOffset + 1, new ChangeableHighlightPainter(new Color(71, 239, 133)));
                inputArea.invalidate();
                inputArea.repaint();
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateGraphView() {
        if (context != null) {
            try {
                if (attackerSecLevelInput.getSelectedItem() == null) {
                    return;
                }
                jungPanel.update(context.getJungGraphForVisu(((SecWrapper) attackerSecLevelInput.getSelectedItem()).sec, pruneCheckBox.isSelected()));
            } catch (RuntimeException error) {
                parserErrorLabel.setText(error.getMessage());
                error.printStackTrace();
            }
        }
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR
     * call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        final JSplitPane splitPane1 = new JSplitPane();
        panel1.add(splitPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JSplitPane splitPane2 = new JSplitPane();
        splitPane2.setOrientation(0);
        splitPane1.setLeftComponent(splitPane2);
        final JTabbedPane tabbedPane1 = new JTabbedPane();
        tabbedPane1.setEnabled(true);
        splitPane2.setRightComponent(tabbedPane1);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("Leakage", panel2);
        final JScrollPane scrollPane1 = new JScrollPane();
        panel2.add(scrollPane1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        leakageTable = new JTable();
        scrollPane1.setViewportView(leakageTable);
        final RTextScrollPane rTextScrollPane1 = new RTextScrollPane();
        tabbedPane1.addTab("Preprocessed", rTextScrollPane1);
        ssaArea = new RSyntaxTextArea();
        ssaArea.setEditable(false);
        ssaArea.setText("");
        rTextScrollPane1.setViewportView(ssaArea);
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1));
        tabbedPane1.addTab("Output", panel3);
        final JScrollPane scrollPane2 = new JScrollPane();
        panel3.add(scrollPane2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setEnabled(true);
        outputArea.setText("");
        scrollPane2.setViewportView(outputArea);
        nodeValueScrollPane = new JScrollPane();
        nodeValueScrollPane.setHorizontalScrollBarPolicy(32);
        tabbedPane1.addTab("Node values", nodeValueScrollPane);
        nodeValueTable = new JTable();
        nodeValueTable.setAutoCreateRowSorter(true);
        nodeValueTable.setAutoResizeMode(4);
        nodeValueScrollPane.setViewportView(nodeValueTable);
        variableValueScrollPane = new JScrollPane();
        variableValueScrollPane.setHorizontalScrollBarPolicy(32);
        tabbedPane1.addTab("Variable values", variableValueScrollPane);
        variableValueTable = new JTable();
        variableValueTable.setAutoCreateRowSorter(true);
        variableValueTable.setAutoResizeMode(2);
        variableValueScrollPane.setViewportView(variableValueTable);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        splitPane2.setLeftComponent(panel4);
        inputScrollArea = new RTextScrollPane();
        inputScrollArea.setMinimumSize(new Dimension(200, 200));
        inputScrollArea.setPreferredSize(new Dimension(400, 200));
        panel4.add(inputScrollArea, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        inputArea = new RSyntaxTextArea();
        inputScrollArea.setViewportView(inputArea);
        storeSelectComboBox = new JComboBox();
        storeSelectComboBox.setEditable(true);
        final DefaultComboBoxModel defaultComboBoxModel1 = new DefaultComboBoxModel();
        storeSelectComboBox.setModel(defaultComboBoxModel1);
        panel4.add(storeSelectComboBox, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        storeButton = new JButton();
        storeButton.setText("store");
        panel4.add(storeButton, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(2, 7, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setRightComponent(panel5);
        jungPanel = new JungPanel();
        panel5.add(jungPanel, new GridConstraints(0, 0, 1, 7, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(2000, 2000), null, 0, false));
        attackerSecLevelInput = new JComboBox();
        panel5.add(attackerSecLevelInput, new GridConstraints(1, 6, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, 1, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(50, -1), null, 0, false));
        resetButton = new JButton();
        resetButton.setText("reset");
        panel5.add(resetButton, new GridConstraints(1, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        automaticRedrawCheckBox = new JCheckBox();
        automaticRedrawCheckBox.setSelected(true);
        automaticRedrawCheckBox.setText("Redraw automatically");
        panel5.add(automaticRedrawCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        redrawButton = new JButton();
        redrawButton.setText("Redraw");
        panel5.add(redrawButton, new GridConstraints(1, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        storeJsonButton = new JButton();
        storeJsonButton.setText("Store json");
        panel5.add(storeJsonButton, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        modeComboBox = new JComboBox();
        panel5.add(modeComboBox, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        pruneCheckBox = new JCheckBox();
        pruneCheckBox.setText("Prune");
        panel5.add(pruneCheckBox, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        parserErrorLabel = new JLabel();
        parserErrorLabel.setText("Label");
        panel1.add(parserErrorLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return panel1;
    }

    private static class SecWrapper {
        final Sec<?> sec;

        private SecWrapper(Sec<?> sec) {
            this.sec = sec;
        }

        @Override
        public String toString() {
            return "Attacker level: " + sec;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == this.getClass() && toString().equals(obj.toString());
        }
    }

    private void updateSecLattice() {
        attackerSecLevelInput.removeAllItems();
        for (Sec<?> sec : context.sl.elements()) {
            attackerSecLevelInput.addItem(new SecWrapper(sec));
        }
        attackerSecLevelInput.setSelectedItem(new SecWrapper(context.sl.bot()));
    }

    private void clearOutput() {
        outputArea.setText("");
    }

    private void addOutput(String str) {
        outputArea.append("\n" + str);
    }

    private void updateLeakageTable(Context context) {
        leakageTable.setTableHeader(new JTableHeader());
        leakageTable.setModel(new AbstractTableModel() {

            List<Sec<?>> secLevels = new ArrayList<>((Set<Sec<?>>) context.sl.elements());

            @Override
            public int getRowCount() {
                return secLevels.size();
            }

            @Override
            public int getColumnCount() {
                return 2;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                if (columnIndex == 0) {
                    return secLevels.get(rowIndex);
                }
                return context.getLeakageGraph().leakage(secLevels.get(rowIndex));
            }

            @Override
            public String getColumnName(int column) {
                switch (column) {
                    case 0:
                        return "Attacker level";
                    case 1:
                        return "Leakage in bits";
                }
                return "";
            }
        });
    }

    private void updateNodeValueTable(Context context) {
        nodeValueTable.setModel(new AbstractTableModel() {

            final String[] headers = new String[]{"node", "literal", "basic", "repr"};

            List<Parser.MJNode> nodes = new ArrayList<>(context.nodes());

            @Override
            public int getRowCount() {
                return nodes.size();
            }

            @Override
            public int getColumnCount() {
                return 4;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                switch (columnIndex) {
                    case 0:
                        return nodes.get(rowIndex).getTextualId();
                    case 1:
                        return context.nodeValue(nodes.get(rowIndex)).toLiteralString();
                    case 2:
                        return context.nodeValue(nodes.get(rowIndex)).toString();
                    case 3:
                        return context.nodeValue(nodes.get(rowIndex)).repr();
                }
                return "";
            }

            @Override
            public String getColumnName(int column) {
                return headers[column];
            }
        });
        resizeTable(nodeValueTable);
    }

    private void updateVariableValueTable(Context context) {
        variableValueTable.setModel(new AbstractTableModel() {

            final String[] headers = new String[]{"variable", "literal", "basic", "repr"};

            List<String> variableNames = new ArrayList<>(context.variableNames());

            @Override
            public int getRowCount() {
                return variableNames.size();
            }

            @Override
            public int getColumnCount() {
                return 4;
            }

            @Override
            public Object getValueAt(int rowIndex, int columnIndex) {
                Value val = context.getVariableValue(variableNames.get(rowIndex));
                switch (columnIndex) {
                    case 0:
                        return variableNames.get(rowIndex);
                    case 1:
                        return val.toLiteralString();
                    case 2:
                        return val.toString();
                    case 3:
                        return val.repr();
                }
                return "";
            }

            @Override
            public String getColumnName(int column) {
                return headers[column];
            }
        });
        resizeTable(variableValueTable);
    }

    private void setProgram(String program) {
        inputArea.getDocument().removeDocumentListener(documentListener);
        inputArea.setText(program);
        processRefreshTimer.request();
        inputArea.getDocument().addDocumentListener(documentListener);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        BasicUI ui = new BasicUI();
        frame.getContentPane().add(ui.panel1);
        frame.pack();
        frame.setSize(1500, 800);
        frame.setVisible(true);
        //ui.setProgram("h input int l = 0b0u; l output int o = l;");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        while (frame.isVisible()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private CompletionProvider createCompletionProvider() {

        DefaultCompletionProvider provider = new DefaultCompletionProvider();
        Arrays.asList(
                "output",
                "input",
                "int",
                "use_sec",
                "diamond",
                "basic"
        ).forEach(a -> provider.addCompletion(new BasicCompletion(provider, a)));

        Arrays.asList(p("hi", "h input int"), p("lo", "l output int"), p("diamond", "use_sec diamond;")).forEach(p -> provider.addCompletion(new ShorthandCompletion(provider, p.first, p.second)));

        return provider;
    }

    private Path pathForName(String name) {
        return Paths.get("examples", name + ".java");
    }

    private String nameForPath(Path path) {
        return path.getName(path.getNameCount() - 1).toString().split(".java")[0];
    }

    private List<String> getExampleNames() {
        try {
            return Files.list(Paths.get("examples")).filter(p -> p.toString().endsWith(".java")).map(this::nameForPath).collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    private void load(String name) {
        try {
            setProgram(String.join("\n", Files.readAllLines(pathForName(name))));
        } catch (IOException e) {
            e.printStackTrace();
            addOutput(e.getMessage());
        }
    }

    private void store(String name) {
        try {
            Files.write(pathForName(name), Arrays.asList(inputArea.getText().split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
            addOutput(e.getMessage());
        }
    }

    private void storeLastName(String name) {
        storeVarInFile("examples/last.var", name);
    }

    private String getLastName() {
        return getVarContent("examples/last.var", "basic");
    }

    private String getLastContent() {
        return getVarContent("examples/lastContent.var", null);
    }

    private void storeLastContent(String content) {
        storeVarInFile("examples/lastContent.var", content);
    }

    private void storeVarInFile(String var, String content) {
        if (content.isEmpty()) {
            return;
        }
        try {
            Files.write(Paths.get(var), Arrays.asList(content.split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
            addOutput(e.getMessage());
        }
    }

    private String getVarContent(String var, String defaultContent) {
        try {
            return String.join("\n", Files.readAllLines(Paths.get(var)));
        } catch (IOException e) {
        }
        return defaultContent;
    }

    /**
     * Source: https://tips4java.wordpress.com/2008/11/10/table-column-adjuster/
     */
    private void resizeTable(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        for (int column = 0; column < table.getColumnCount(); column++) {
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            int preferredWidth = tableColumn.getMinWidth();
            int maxWidth = tableColumn.getMaxWidth();

            for (int row = 0; row < table.getRowCount(); row++) {
                TableCellRenderer cellRenderer = table.getCellRenderer(row, column);
                Component c = table.prepareRenderer(cellRenderer, row, column);
                int width = c.getPreferredSize().width + table.getIntercellSpacing().width;
                preferredWidth = Math.max(preferredWidth, width);

                //  We've exceeded the maximum width, no need to check other rows

                if (preferredWidth >= maxWidth) {
                    preferredWidth = maxWidth;
                    break;
                }
            }

            tableColumn.setPreferredWidth(preferredWidth);
        }
    }

    private void storeGraphJSON(Path path) {
        try {
            PythonCaller.createJSONFile(path, context, ((SecWrapper) attackerSecLevelInput.getSelectedItem()).sec);
        } catch (RuntimeException exp) {
            parserErrorLabel.setText(exp.getMessage());
            exp.printStackTrace();
        }
    }
}
