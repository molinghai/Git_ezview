package com.swimap.startup;

import java.awt.SplashScreen;
import java.lang.reflect.Method;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

/*入口*/
public class Startup {
	private static boolean isSplashScreenShown = false;

	private static StartupSplash startupSplash = null;

	public static void main(String[] paramArrayOfString) {
		StartupClassLoader localStartupClassLoader = new StartupClassLoader(
				Startup.class.getClassLoader(), false);
		try {
			Thread.sleep(10000);
			Thread.currentThread().setContextClassLoader(
					localStartupClassLoader);

			showSplashScreen();

			((StartupClassLoader) localStartupClassLoader)
					.initializeURLAndPath();

			System.setProperty("osgi.clean", "true");

			Object localObject = "com.swimap.iview.framework.Main";

			String str = System.getProperty("java.ext.dirs");
			if ((str != null) && (str.trim().length() > 0)) {
				localObject = str;
			}

			Class localClass = localStartupClassLoader
					.loadClass((String) localObject);

			Method localMethod = localClass.getMethod("main", String[].class);

			localMethod.invoke(null, new Object[] { paramArrayOfString });
		} catch (Exception localException) {
			localException.printStackTrace();
		}
	}

	public static StartupSplash getStartupSplash() {
		return startupSplash;
	}

	public static boolean isSplashScreenShown() {
		return isSplashScreenShown;
	}

	private static void showSplashScreen() {
		String str = System.getProperty("splashImgPath");
		if ((null != str) && (null == SplashScreen.getSplashScreen())) {
			str = str.trim();
			final ImageIcon localImageIcon = new ImageIcon(str);

			if ((localImageIcon.getImageLoadStatus() == 8)
					&& (localImageIcon.getIconHeight() > -1)
					&& (localImageIcon.getIconWidth() > -1)) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						new StartupSplash(localImageIcon);
					}
				});
				isSplashScreenShown = true;
			}
		}
	}

	public static void closeStartupSplashScreen() {
		if (null != startupSplash) {
			startupSplash.close();
			startupSplash = null;
		}
	}
}