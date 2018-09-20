package nildumu.ui;

import com.intellij.uiDesigner.core.*;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.swing.*;
import org.apache.batik.swing.gvt.*;
import org.apache.batik.swing.svg.SVGUserAgentAdapter;
import org.apache.batik.util.XMLResourceDescriptor;
import org.xml.sax.*;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.event.ListDataListener;

import nildumu.*;
import swp.util.Pair;

public class DotPanel extends JPanel {

    private JPanel rootPanel;
    private JComboBox topicSelector;
    private JComboBox fileSelector;
    private JButton launchNewButton;
    private JSVGScrollPane dotScrollPane;
    private JCheckBox autoRedrawCheckBox;
    private JPanel controlPanel;
    private JSVGCanvas dotCanvas;
    private Stack<Pair<String, DotRegistry.DotFile>> lastActions = new Stack<>();
    private ResponsiveTimer reloadTimer;
    private final BasicUI parentUI;
    private boolean insideUpdate = false;

    DotPanel(BasicUI parentUI, boolean autoRedraw, Consumer<Boolean> autoRedrawSet) {
        this.parentUI = parentUI;
        $$$setupUI$$$();
        autoRedrawCheckBox.setSelected(autoRedraw);
        this.setLayout(new BorderLayout());
        this.add(rootPanel);
        if (parentUI != null) {
            parentUI.addDotFrame(this);
        }
        reloadTimer = new ResponsiveTimer(() -> {
            if (autoRedrawCheckBox.isSelected()) {
                set((String) topicSelector.getSelectedItem(), (String) fileSelector.getSelectedItem());
            }
        }, () -> {
        }, d -> {
        }, 20);
        reloadTimer.setAutoRun(true);
        topicSelector.addActionListener(e -> {
            if (!insideUpdate) {
                update();
                controlPanel.invalidate();
            }
        });
        fileSelector.addActionListener(e -> {
            if (!insideUpdate) {
                System.out.println("H");
                update();
                controlPanel.invalidate();
            }
        });
        launchNewButton.addActionListener(e -> {
            launch(parentUI).set((String) topicSelector.getSelectedItem(), (String) fileSelector.getSelectedItem());
        });
        dotCanvas.setEnableImageZoomInteractor(true);
        dotCanvas.setEnablePanInteractor(true);
        autoRedrawCheckBox.setSelected(autoRedraw);
        autoRedrawCheckBox.addActionListener(e -> {
            autoRedrawSet.accept(autoRedrawCheckBox.isSelected());
            reloadTimer.request();
            update();
        });

        topicSelector.setModel(new ComboBoxModel() {

            private String selectedTopic = "main";

            @Override
            public void setSelectedItem(Object anItem) {
                selectedTopic = (String) anItem;
                fileSelector.invalidate();
            }

            @Override
            public Object getSelectedItem() {
                return selectedTopic;
            }

            @Override
            public int getSize() {
                return DotRegistry.get().getFilesPerTopic().size();
            }

            @Override
            public Object getElementAt(int index) {
                return new ArrayList<>(DotRegistry.get().getFilesPerTopic().keySet()).get(index);
            }

            @Override
            public void addListDataListener(ListDataListener l) {

            }

            @Override
            public void removeListDataListener(ListDataListener l) {

            }
        });
        fileSelector.setModel(new ComboBoxModel() {

            private String selectedFile = "Attacker level: l";

            @Override
            public void setSelectedItem(Object anItem) {
                selectedFile = (String) anItem;
            }

            @Override
            public Object getSelectedItem() {
                String topic = (String) topicSelector.getSelectedItem();
                if (!DotRegistry.get().has(topic, selectedFile) && DotRegistry.get().getFilesPerTopic(topic).size() > 0) {
                    selectedFile = DotRegistry.get().getFilesPerTopic(topic).keySet().iterator().next();
                    fileSelector.setSelectedItem(selectedFile);
                    fileSelector.invalidate();
                }
                return selectedFile;
            }

            @Override
            public int getSize() {
                return DotRegistry.get().getFilesPerTopic((String) topicSelector.getSelectedItem()).size();
            }

            @Override
            public Object getElementAt(int index) {
                return new ArrayList<>(DotRegistry.get().getFilesPerTopic((String) topicSelector.getSelectedItem()).keySet()).get(index);
            }

            @Override
            public void addListDataListener(ListDataListener l) {

            }

            @Override
            public void removeListDataListener(ListDataListener l) {

            }
        });
        reloadTimer.start();
    }

    private void update() {
        outerUpdate();
        reloadTimer.restart();
        set((String) topicSelector.getSelectedItem(), (String) fileSelector.getSelectedItem());
    }

    void outerUpdate() {
        insideUpdate = true;
        updateTopicSelector();
        updateFileSelector((String) topicSelector.getSelectedItem());
        controlPanel.invalidate();
        insideUpdate = false;
        reloadTimer.request();
    }

    private void updateTopicSelector() {
        String prevSel = topicSelector.getItemCount() > 0 ? (String) topicSelector.getSelectedItem() : "";
        DotRegistry reg = DotRegistry.get();
        if (reg.hasTopic(prevSel)) {
            topicSelector.setSelectedItem(prevSel);
        }
        updateFileSelector((String) topicSelector.getSelectedItem());
    }

    private void updateFileSelector(String topic) {
        String prevSel = fileSelector.getItemCount() > 0 ? (String) fileSelector.getSelectedItem() : "";
        DotRegistry reg = DotRegistry.get();
        if (reg.has(topic, prevSel)) {
            fileSelector.setSelectedItem(prevSel);
        }
    }

    public void set(String topic, String name) {
        if (topicSelector.getSelectedItem() == null || !topicSelector.getSelectedItem().equals(topic)) {
            topicSelector.setSelectedItem(topic);
            updateFileSelector(topic);
        }
        if (fileSelector.getSelectedItem() == null || !fileSelector.getSelectedItem().equals(name)) {
            fileSelector.setSelectedItem(name);
        }
        DotRegistry.get().get(topic, name).ifPresent(this::setDotFile);
    }

    private void setDotFile(DotRegistry.DotFile file) {
        try {
            String parser = XMLResourceDescriptor.getXMLParserClassName();
            SAXSVGDocumentFactory factory = new SAXSVGDocumentFactory(parser);
            factory.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    //exception.printStackTrace();
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    //exception.printStackTrace();
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    //exception.printStackTrace();
                }
            });
            dotCanvas.setDocument(factory.createDocument(file.getSvgPath().toUri().toURL().toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public DotPanel launch(BasicUI parentUI) {
        DotPanel dot = new DotPanel(parentUI, autoRedrawCheckBox.isSelected(), b -> {
        });
        Thread t = new Thread(() -> {
            JFrame frame = new JFrame();
            frame.getContentPane().add(dot);
            frame.pack();
            frame.setSize(800, 800);
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
            dot.parentUI.removeDotFrame(dot);
        });
        t.setDaemon(true);
        t.start();
        return dot;
    }

    private void createUIComponents() {
        dotCanvas = new JSVGCanvas(new SVGUserAgentAdapter(), true, true);
        dotCanvas.getInteractors().add(new AbstractPanInteractor() {
            @Override
            public boolean startInteraction(InputEvent ie) {
                return ie.getID() == MouseEvent.MOUSE_PRESSED && !ie.isShiftDown();
            }
        });
        dotCanvas.getInteractors().add(new AbstractZoomInteractor() {
            @Override
            public boolean startInteraction(InputEvent ie) {
                return ie.getID() == MouseEvent.MOUSE_MOVED && ie.isShiftDown();
            }
        });
        dotScrollPane = new JSVGScrollPane(dotCanvas);
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer >>> IMPORTANT!! <<< DO NOT edit this method OR
     * call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        createUIComponents();
        rootPanel = new JPanel();
        rootPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, -1));
        controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayoutManager(1, 4, new Insets(0, 0, 0, 0), -1, -1));
        controlPanel.setRequestFocusEnabled(false);
        rootPanel.add(controlPanel, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        topicSelector = new JComboBox();
        controlPanel.add(topicSelector, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        fileSelector = new JComboBox();
        controlPanel.add(fileSelector, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        launchNewButton = new JButton();
        launchNewButton.setText("Launch new");
        controlPanel.add(launchNewButton, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        autoRedrawCheckBox = new JCheckBox();
        autoRedrawCheckBox.setText("Auto redraw");
        controlPanel.add(autoRedrawCheckBox, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new BorderLayout(0, 0));
        rootPanel.add(panel1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel1.add(dotScrollPane, BorderLayout.CENTER);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }
}
