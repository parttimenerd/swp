package nildumu.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import javax.swing.*;

public class ViewImage {
    public static void view(Path imgPath) throws IOException {
        //new Thread(() -> {
            BufferedImage img= null;
            try {
                img = ImageIO.read(imgPath.toFile());
            } catch (IOException e) {
                e.printStackTrace();
            }
            ImageIcon icon=new ImageIcon(img);
            JFrame frame=new JFrame();
            frame.setLayout(new FlowLayout());
            frame.setSize(800,800);
            JLabel lbl=new JLabel();
            lbl.setIcon(icon);
            frame.add(lbl);
            frame.setVisible(true);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            while (frame.isVisible()){
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        //}).start();
    }
}
