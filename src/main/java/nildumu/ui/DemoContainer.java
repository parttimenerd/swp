package nildumu.ui;

import com.sun.org.apache.xalan.internal.xsltc.compiler.util.Type;

import java.awt.*;
import java.awt.geom.Point2D;

import java.awt.Dimension;

import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.util.Relaxer;
import edu.uci.ics.jung.algorithms.layout.util.VisRunner;
import edu.uci.ics.jung.algorithms.util.IterativeContext;
import edu.uci.ics.jung.graph.util.TestGraphs;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CircleBuilder;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineBuilder;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;
import javafx.stage.Stage;

import edu.uci.ics.jung.algorithms.layout.AbstractLayout;
import edu.uci.ics.jung.algorithms.layout.DAGLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.visualization.RenderContext;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.picking.PickedState;
import edu.uci.ics.jung.visualization.renderers.Renderer;
import edu.uci.ics.jung.visualization.transform.shape.GraphicsDecorator;
import nildumu.LeakageCalculation;

import static com.sun.org.apache.xalan.internal.xsltc.compiler.util.Type.Text;
import static nildumu.LeakageCalculation.EdgeGraph.Edge;
import static nildumu.LeakageCalculation.EdgeGraph.Node;

/**
 * https://gist.github.com/osjayaprakash/5783558
 */
public class DemoContainer extends Container {

    /**
     * @author jeffreyguenther
     */
    public static class GraphVisTester extends Application {

        private static Graph<Node, Edge> graph;

        @Override
        public void start(Stage stage) throws Exception {
            Layout<Node, Edge> layout = new CircleLayout<>(graph);
            layout.setSize(new Dimension(800, 800));
            GraphViz<Node, Edge> viewer = new GraphViz<>(layout);

            stage.setScene(new Scene(new Group(viewer)));
            stage.show();
        }

        public static void show(LeakageCalculation.JungEdgeGraph graph){
            GraphVisTester.graph = graph.graph;
            launch(new String[0]);
        }


    }

    public static class GraphViz<V, E> extends Region {
        private Relaxer relaxer;
        private Layout<V,E> layout;
        private double CIRCLE_SIZE = 25;

        public GraphViz(Layout<V, E> layout) {
            this.layout = layout;
        }



        @Override
        protected void layoutChildren() {
            super.layoutChildren();


            layout.setSize(new Dimension(widthProperty().intValue(), heightProperty().intValue()));

            // relax the layout
            if(relaxer != null) {
                relaxer.stop();
                relaxer = null;
            }
            if(layout instanceof IterativeContext) {
                layout.initialize();
                if(relaxer == null) {
                    relaxer = new VisRunner((IterativeContext)this.layout);
                    relaxer.prerelax();
                    relaxer.relax();
                }
            }

            Graph<V, E> graph = layout.getGraph();


            // draw the vertices in the graph
            for (V v : graph.getVertices()) {
                // Get the position of the vertex

                java.awt.geom.Point2D p = transform(layout, v);

                // draw the vertex as a circle
                Circle circle = CircleBuilder.create()
                        .centerX(p.getX())
                        .centerY(p.getY())
                        .radius(CIRCLE_SIZE)
                        .build();
                circle.setFill(javafx.scene.paint.Color.RED);

                javafx.scene.text.Text text = new Text(((Node)v).bit.repr());
                text.setBoundsType(TextBoundsType.VISUAL);
                text.setStyle(
                                "-fx-font-style: italic;" +
                                "-fx-font-size: 10px;"
                );
                text.setLayoutX(p.getX());
                text.setLayoutY(p.getY());

                // add it to the group, so it is shown on screen
                this.getChildren().add(circle);
                this.getChildren().add(text);
            }

            // draw the edges
            for (E e : graph.getEdges()) {
                // get the end points of the edge
                edu.uci.ics.jung.graph.util.Pair<V> endpoints = graph.getEndpoints(e);

                // Get the end points as Point2D objects so we can use them in the
                // builder
                java.awt.geom.Point2D pStart = transform(layout, endpoints.getFirst());
                java.awt.geom.Point2D pEnd = transform(layout, endpoints.getSecond());

                // Draw the line
                Line line = LineBuilder.create()
                        .startX(pStart.getX())
                        .startY(pStart.getY())
                        .endX(pEnd.getX())
                        .endY(pEnd.getY())
                        .build();
                // add the edges to the screen
                this.getChildren().add(line);
            }
        }

        private Point2D transform(Layout<V, E> layout, V node){
            AbstractLayout<V, E> al = (AbstractLayout<V, E>)layout;
            return new Point2D.Double(Math.abs(al.getX(node)), Math.abs(al.getY(node)));
        }



    }
}
