package nildumu.ui;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import javax.swing.*;

import com.google.common.base.Function;
import com.google.common.base.Functions;

import edu.uci.ics.jung.algorithms.layout.DAGLayout;
import edu.uci.ics.jung.algorithms.layout.KKLayout;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.visualization.GraphZoomScrollPane;
import edu.uci.ics.jung.visualization.Layer;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.AbstractModalGraphMouse;
import edu.uci.ics.jung.visualization.control.CrossoverScalingControl;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.GraphMouseListener;
import edu.uci.ics.jung.visualization.control.ScalingControl;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import edu.uci.ics.jung.visualization.renderers.DefaultVertexLabelRenderer;
import edu.uci.ics.jung.visualization.renderers.GradientVertexRenderer;
import edu.uci.ics.jung.visualization.renderers.Renderer;
import edu.uci.ics.jung.visualization.renderers.BasicVertexLabelRenderer.InsidePositioner;
import edu.uci.ics.jung.visualization.renderers.VertexLabelRenderer;
import edu.uci.ics.jung.visualization.transform.shape.GraphicsDecorator;
import nildumu.LeakageCalculation;
import nildumu.Util;

import static nildumu.LeakageCalculation.*;

/**
 * Based on the GraphZoomScrollPaneDemo
 *
 */
public class JungPanel extends JPanel {

    DirectedGraph<VisuNode, VisuEdge> graph;

    VisualizationViewer<VisuNode, VisuEdge> vv;

    ScalingControl scaler = new CrossoverScalingControl();

    public void update(JungGraph jungGraph){
        if (jungGraph.graph == this.graph){
            return;
        }
        this.graph = jungGraph.graph;

        DAGLayout<VisuNode, VisuEdge> layout = new DAGLayout<>(graph);
        layout.setRoot(jungGraph.input);

        vv =  new VisualizationViewer<VisuNode, VisuEdge>(layout);

        vv.getRenderer().setVertexRenderer(
                new GradientVertexRenderer<VisuNode,VisuEdge>(
                        Color.white, Color.red,
                        Color.white, Color.blue,
                        vv.getPickedVertexState(),
                        false){
                    /**
                     * Adapted from GradientVertexRenderer
                     */
                    protected void paintShapeForVertex(RenderContext<VisuNode,VisuEdge> rc, VisuNode v, Shape shape) {
                        GraphicsDecorator g = rc.getGraphicsContext();
                        Paint oldPaint = g.getPaint();
                        Rectangle r = shape.getBounds();
                        float y2 = (float)r.getMaxY();
                        Paint fillPaint = null;

                        if(vv.getPickedEdgeState() != null && vv.getPickedVertexState().isPicked(v)) {
                            fillPaint = new GradientPaint((float)r.getMinX(), (float)r.getMinY(), Color.white,
                                    (float)r.getMinX(), y2, Color.blue, false);
                        } else {
                            fillPaint = new GradientPaint((float)r.getMinX(), (float)r.getMinY(), Color.white,
                                    (float)r.getMinX(), y2, v.marked() ? Color.red : Color.black, false);
                        }
                        g.setPaint(fillPaint);
                        g.fill(shape);
                        g.setPaint(oldPaint);
                        Paint drawPaint = rc.getVertexDrawPaintTransformer().apply(v);
                        if(drawPaint != null) {
                            g.setPaint(drawPaint);
                        }
                        Stroke oldStroke = g.getStroke();
                        Stroke stroke = rc.getVertexStrokeTransformer().apply(v);
                        if(stroke != null) {
                            g.setStroke(stroke);
                        }
                        g.draw(shape);
                        g.setPaint(oldPaint);
                        g.setStroke(oldStroke);
                    }
                });
        vv.getRenderContext().setEdgeDrawPaintTransformer(Functions.<Paint>constant(Color.lightGray));
        vv.getRenderContext().setArrowFillPaintTransformer(Functions.<Paint>constant(Color.lightGray));
        vv.getRenderContext().setArrowDrawPaintTransformer(Functions.<Paint>constant(Color.lightGray));
        // add my listeners for ToolTips
        vv.setVertexToolTipTransformer(VisuNode::repr);
        vv.setEdgeToolTipTransformer(VisuEdge::repr);
        vv.getRenderContext().setVertexLabelRenderer(new DefaultVertexLabelRenderer(Color.blue) {
            @Override
            public <V> Component getVertexLabelRendererComponent(JComponent vv, Object value, Font font, boolean isSelected, V vertex) {
                super.getVertexLabelRendererComponent(vv, value, font, isSelected, vertex);
                if (((VisuNode)vertex).marked()) {
                    setForeground(Color.red);
                }
                return this;
            }
        });

        vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller());
        vv.getRenderContext().setEdgeLabelTransformer(new ToStringLabeller());
        vv.getRenderer().getVertexLabelRenderer().setPositioner(new InsidePositioner());
        vv.getRenderer().getVertexLabelRenderer().setPosition(Renderer.VertexLabel.Position.AUTO);
        vv.setForeground(Color.lightGray);

        final GraphZoomScrollPane panel = new GraphZoomScrollPane(vv);

        this.removeAll();
        this.add(panel);
        final AbstractModalGraphMouse graphMouse = new DefaultModalGraphMouse<String,Number>();
        vv.setGraphMouse(graphMouse);

        vv.addKeyListener(graphMouse.getModeKeyListener());
        vv.setToolTipText("<html><center>Type 'p' for Pick mode<p>Type 't' for Transform mode");

        this.setLayout(new GridLayout(1, 1));
        this.setBorder(BorderFactory.createLineBorder(Color.blue));
        this.revalidate();
        this.repaint();
    }

    public void scale(float factor){
        if (vv != null) {
            scaler.scale(vv, factor, vv.getCenter());
        }
    }

    public void reset(){
        if (vv != null) {
            vv.getRenderContext().getMultiLayerTransformer().getTransformer(Layer.LAYOUT).setToIdentity();
            vv.getRenderContext().getMultiLayerTransformer().getTransformer(Layer.VIEW).setToIdentity();
        }
    }

    public static void show(JungGraph graph){
        JFrame frame = new JFrame();
        JungPanel panel = new JungPanel();
        panel.update(graph);
        JButton plus = new JButton("+");
        plus.addActionListener(e -> panel.scale(1.1f));
        JButton minus = new JButton("-");
        minus.addActionListener(e -> panel.scale(1/1.1f));

        JButton reset = new JButton("reset");
        reset.addActionListener(e -> panel.reset());

        JPanel controls = new JPanel();
        controls.add(plus);
        controls.add(minus);
        controls.add(reset);
        panel.add(controls, BorderLayout.SOUTH);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        while (frame.isVisible()){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
