package com.swimap.startup;

import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PatchInfo implements Comparable {
	private static final String PATCH_TIME = "Patch-Time";
	private static final int NEGATIVE = -1;
	private static final int POSITIVE = 1;
	private Date patchTime = null;
	private File file;

	private PatchInfo(String paramString, File paramFile) {
		parsePatchTime(paramString);
		this.file = paramFile;
	}

	public int compareTo(Object paramObject) {
		if (null == paramObject) {
			return 1;
		}

		PatchInfo localPatchInfo = (PatchInfo) paramObject;

		if ((null != this.patchTime) && (null != localPatchInfo.patchTime)) {
			return this.patchTime.compareTo(localPatchInfo.patchTime);
		}
		if (null == localPatchInfo.patchTime) {
			return 1;
		}

		return -1;
	}

	private void parsePatchTime(String paramString) {
		paramString = paramString.trim();
		int[] arrayOfInt;
		int i;
		String str;
		Integer localInteger;
		if (paramString.indexOf(':') != -1) {
			try {
				paramString = paramString.replace(' ', ',');
				paramString = paramString.replace(':', ',');
				paramString = paramString.replace('-', ',');

				StringTokenizer localStringTokenizer1 = new StringTokenizer(
						paramString, ",");

				arrayOfInt = new int[6];

				i = 0;
				while ((localStringTokenizer1.hasMoreTokens()) && (i < 6)) {
					str = localStringTokenizer1.nextToken();
					localInteger = new Integer(str);
					arrayOfInt[(i++)] = localInteger.intValue();
				}
				this.patchTime = new Date(arrayOfInt[0], arrayOfInt[1] - 1,
						arrayOfInt[2], arrayOfInt[3], arrayOfInt[4],
						arrayOfInt[5]);
			} catch (Exception localException1) {
				localException1.printStackTrace();
			}
		} else {
			try {
				StringTokenizer localStringTokenizer2 = new StringTokenizer(
						paramString, "-");

				arrayOfInt = new int[3];

				i = 0;
				while ((localStringTokenizer2.hasMoreTokens()) && (i < 3)) {
					str = localStringTokenizer2.nextToken();
					localInteger = new Integer(str);
					arrayOfInt[(i++)] = localInteger.intValue();
				}
				this.patchTime = new Date(arrayOfInt[0], arrayOfInt[1] - 1,
						arrayOfInt[2]);
			} catch (Exception localException2) {
				localException2.printStackTrace();
			}
		}
	}

	URL getURL() {
		URL localURL = null;
		try {
			localURL = this.file.toURL();
		} catch (Exception localException) {
			localException.printStackTrace();
		}

		return localURL;
	}

	static PatchInfo createPatchInfo(JarFile paramJarFile, File paramFile) {
		Manifest localManifest = null;
		try {
			localManifest = paramJarFile.getManifest();
		} catch (Exception localException) {
			localException.printStackTrace();
		}

		if (localManifest != null) {
			Attributes localAttributes = localManifest.getMainAttributes();

			if (localAttributes != null) {
				String str = localAttributes.getValue("Patch-Time");

				if ((null != str) && (str.trim().length() > 0)) {
					PatchInfo localPatchInfo = new PatchInfo(str, paramFile);
					return localPatchInfo;
				}
			}
		}

		return null;
	}
	
	public static void main(String[] args) {
		Pattern pattern = Pattern.compile("^Jav*");
		  Matcher matcher = pattern.matcher("Javaaa.jar");
		  boolean b= matcher.matches();
		  System.out.println(b);
	}
}