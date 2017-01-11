package com.swimap.startup;

import java.io.File;
import java.io.FileFilter;

class PluginFileFilter implements FileFilter {
	public boolean accept(File paramFile) {
		if ((paramFile.getName().endsWith("style"))
				|| (paramFile.getName().endsWith("lib"))) {
			return false;
		}

		if (paramFile.isDirectory()) {
			return true;
		}

		return paramFile.getName().endsWith(".jar");
	}
}