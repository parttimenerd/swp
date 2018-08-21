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
import org.fife.ui.rtextarea.RTextScrollPane;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.SimpleAttributeSet;

import nildumu.Context;
import nildumu.NildumuError;
import nildumu.Parser;
import nildumu.Processor;
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
    private Context context = null;
    private DocumentListener documentListener;
    private boolean requestedGraphViewUpdate = false;
    private Timer timer;
    private long refreshInterval = 10;
    private long refreshMsCounter = 10;
    private Object syntaxErrorHighlightTag = null;

    public BasicUI() {
        documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                processAndUpdate(inputArea.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                processAndUpdate(inputArea.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                processAndUpdate(inputArea.getText());
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
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (refreshMsCounter > 0) {
                    refreshMsCounter -= 10;
                    return;
                }
                if (requestedGraphViewUpdate) {
                    requestedGraphViewUpdate = false;
                    long start = System.currentTimeMillis();
                    updateGraphView();
                    System.out.println(refreshInterval);
                    refreshInterval = System.currentTimeMillis() - start;
                }
                refreshMsCounter = refreshInterval;
            }
        }, 10, 10);
        automaticRedrawCheckBox.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                if (automaticRedrawCheckBox.isSelected()) {
                    refreshMsCounter = 10;
                } else {
                    refreshMsCounter = Long.MAX_VALUE;
                }
            }
        });
        redrawButton.addActionListener(e -> updateGraphView());
    }

    public void processAndUpdate(String program) {
        clearOutput();
        parserErrorLabel.setText("");
        if (syntaxErrorHighlightTag != null) {
            inputArea.getHighlighter().removeHighlight(syntaxErrorHighlightTag);
        }
        try {
            Context c = Processor.process(program);
            if (context == null || c.sl != context.sl) {
                context = c;
                updateSecLattice();
            }
            context = c;
            updateLeakageTable(context);
            ssaArea.setText(Parser.process(program).toPrettyString());
            requestedGraphViewUpdate = true;
        } catch (ParserError e) {
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
        }
    }

    private void updateGraphView() {
        if (context != null) {
            try {
                if (attackerSecLevelInput.getSelectedItem() == null) {
                    return;
                }
                jungPanel.update(context.getJungGraphForVisu(((SecWrapper) attackerSecLevelInput.getSelectedItem()).sec));
            } catch (NildumuError error) {
                addOutput(error.getMessage());
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
        inputScrollArea = new RTextScrollPane();
        inputScrollArea.setMinimumSize(new Dimension(200, 200));
        inputScrollArea.setPreferredSize(new Dimension(400, 200));
        splitPane2.setLeftComponent(inputScrollArea);
        inputArea = new RSyntaxTextArea();
        inputScrollArea.setViewportView(inputArea);
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 4, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setRightComponent(panel4);
        jungPanel = new JungPanel();
        panel4.add(jungPanel, new GridConstraints(0, 0, 1, 4, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(2000, 2000), null, 0, false));
        attackerSecLevelInput = new JComboBox();
        panel4.add(attackerSecLevelInput, new GridConstraints(1, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, 1, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(50, -1), null, 0, false));
        resetButton = new JButton();
        resetButton.setText("reset");
        panel4.add(resetButton, new GridConstraints(1, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        automaticRedrawCheckBox = new JCheckBox();
        automaticRedrawCheckBox.setSelected(true);
        automaticRedrawCheckBox.setText("Redraw automatically");
        panel4.add(automaticRedrawCheckBox, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        redrawButton = new JButton();
        redrawButton.setText("Redraw");
        panel4.add(redrawButton, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
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

    private void setProgram(String program) {
        inputArea.getDocument().removeDocumentListener(documentListener);
        inputArea.setText(program);
        processAndUpdate(program);
        inputArea.getDocument().addDocumentListener(documentListener);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        BasicUI ui = new BasicUI();
        frame.getContentPane().add(ui.panel1);
        frame.pack();
        frame.setSize(1500, 800);
        frame.setVisible(true);
        ui.setProgram("h input int l = 0b0u; l output int o = l;");
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

        Arrays.asList(p("hi", "h input"), p("lo", "l output"), p("diamond", "use_sec diamond;")).forEach(p -> provider.addCompletion(new ShorthandCompletion(provider, p.first, p.second)));

        return provider;
    }

}
