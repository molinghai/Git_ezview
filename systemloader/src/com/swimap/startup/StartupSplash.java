package com.swimap.startup;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.border.BevelBorder;

class StartupSplash extends JWindow {
	public StartupSplash(ImageIcon paramImageIcon) {
		super(new JFrame());
		setName("SplashScreen");

		JPanel localJPanel = new JPanel(new BorderLayout());
		localJPanel.paintImmediately(localJPanel.getVisibleRect());
		localJPanel.setLayout(new BorderLayout());

		localJPanel.add(new JLabel(paramImageIcon), "Center");

		localJPanel.setBorder(new BevelBorder(0));

		getContentPane().add(localJPanel);

		pack();

		Dimension localDimension1 = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension localDimension2 = getSize();
		Point localPoint = new Point();
		localPoint.x = (int) (localDimension1.getWidth() / 2.0D - localDimension2
				.getWidth() / 2.0D);
		localPoint.y = (int) (localDimension1.getHeight() / 2.0D - localDimension2
				.getHeight() / 2.0D);
		setLocation(localPoint);

		Component localComponent = getGlassPane();
		localComponent.setCursor(new Cursor(3));
		localComponent.setVisible(true);
		setVisible(true);
	}

	public void close() {
		try {
			SwingUtilities.invokeAndWait(new CloseSplashScreen());
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}

	private final class CloseSplashScreen implements Runnable {
		private CloseSplashScreen() {
		}

		public void run() {
			StartupSplash.this.setVisible(false);

			((Window) StartupSplash.this.getParent()).dispose();
		}
	}
}