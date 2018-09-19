package nildumu.ui;

import com.intellij.uiDesigner.core.*;

import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.swing.*;
import org.apache.batik.swing.gvt.*;
import org.apache.batik.swing.svg.SVGUserAgentAdapter;
import org.apache.batik.transcoder.*;
import org.apache.batik.transcoder.svg2svg.SVGTranscoder;
import org.apache.batik.util.XMLResourceDescriptor;
import org.xml.sax.*;
import org.xml.sax.ErrorHandler;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Stack;

import javax.swing.*;

import nildumu.*;
import swp.util.Pair;

public class DotFrame {

    private JPanel rootPanel;
    private JComboBox topicSelector;
    private JComboBox fileSelector;
    private JButton launchNewButton;
    private JSVGScrollPane dotScrollPane;
    private JSVGCanvas dotCanvas;
    private Stack<Pair<String, DotRegistry.DotFile>> lastActions = new Stack<>();
    private ResponsiveTimer reloadTimer;
    private final BasicUI parentUI;
    private boolean insideUpdate = false;

    private DotFrame(BasicUI parentUI) {
        this.parentUI = parentUI;
        $$$setupUI$$$();
        if (parentUI != null) {
            parentUI.addDotFrame(this);
        }
        reloadTimer = new ResponsiveTimer(() -> {
            set((String) topicSelector.getSelectedItem(), (String) fileSelector.getSelectedItem());
        }, () -> {
        }, d -> {
        }, 20);
        topicSelector.addActionListener(e -> {
            update();
        });
        fileSelector.addActionListener(e -> {
            if (!insideUpdate) {
                System.out.println("H");
                reloadTimer.request();
            }
        });
        launchNewButton.addActionListener(e -> {
            DotFrame.launch(parentUI).set((String) topicSelector.getSelectedItem(), (String) fileSelector.getSelectedItem());
        });
        dotCanvas.setEnableImageZoomInteractor(true);
        dotCanvas.setEnablePanInteractor(true);
        update();
        reloadTimer.start();
        reloadTimer.setAutoRun(true);
    }

    public void update() {
        insideUpdate = true;
        updateTopicSelector();
        insideUpdate = false;
        reloadTimer.request();
    }

    public void updateTopicSelector() {
        String prevSel = topicSelector.getItemCount() > 0 ? (String) topicSelector.getSelectedItem() : "";
        topicSelector.removeAllItems();
        DotRegistry reg = DotRegistry.get();
        reg.getFilesPerTopic().keySet().stream().sorted().forEach(topicSelector::addItem);
        if (reg.hasTopic(prevSel)) {
            topicSelector.setSelectedItem(prevSel);
        }
        updateFileSelector((String) topicSelector.getSelectedItem());
    }

    private void updateFileSelector(String topic) {
        String prevSel = fileSelector.getItemCount() > 0 ? (String) fileSelector.getSelectedItem() : "";
        fileSelector.removeAllItems();
        DotRegistry reg = DotRegistry.get();
        reg.getFilesPerTopic(topic).keySet().forEach(fileSelector::addItem);
        if (reg.has(topic, prevSel)) {
            fileSelector.setSelectedItem(prevSel);
        }
    }

    public void set(String topic, String name) {
        if (!topicSelector.getSelectedItem().equals(topic)) {
            topicSelector.setSelectedItem(topic);
            updateFileSelector(topic);
        }
        if (!fileSelector.getSelectedItem().equals(name)) {
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
                    exception.printStackTrace();
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    exception.printStackTrace();
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    exception.printStackTrace();
                }
            });
            dotCanvas.setDocument(factory.createDocument(file.getSvgPath().toUri().toURL().toString()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static DotFrame launch(BasicUI parentUI) {
        DotFrame dot = new DotFrame(parentUI);
        Thread t = new Thread(() -> {
            JFrame frame = new JFrame();
            frame.getContentPane().add(dot.rootPanel);
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

    public JPanel getRootPanel() {
        return rootPanel;
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
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
        panel1.setRequestFocusEnabled(false);
        rootPanel.add(panel1, new GridConstraints(0, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        topicSelector = new JComboBox();
        panel1.add(topicSelector, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        fileSelector = new JComboBox();
        panel1.add(fileSelector, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        launchNewButton = new JButton();
        launchNewButton.setText("Launch new");
        panel1.add(launchNewButton, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new BorderLayout(0, 0));
        rootPanel.add(panel2, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel2.add(dotScrollPane, BorderLayout.CENTER);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return rootPanel;
    }
}
