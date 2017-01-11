package com.swimap.startup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Vector;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import sun.misc.Resource;
import sun.misc.URLClassPath;

public class StartupClassLoader extends URLClassLoader {
	private static StartupClassLoader instance = null;

	private static final String PATCH_FOLDER = System.getProperty("user.dir")
			+ "/patch";
	private static final String JAR_SUFFIX = ".jar";
	private static final String LIB = System.getProperty("user.dir") + "/lib";

	private static final String PRODUCTLIB_FOLDER = System
			.getProperty("user.dir") + "/productlib";

	private static String pathName = "link.properties";

	private static final boolean LINK_EXIST = checklinkExists();

	private static String patchFilePath = null;

	private URLClassPath dynamicUCP = null;
	private AccessControlContext dynamicAcc = null;

	public StartupClassLoader(ClassLoader paramClassLoader) {
		this(paramClassLoader, true);
	}

	public StartupClassLoader(ClassLoader paramClassLoader, boolean paramBoolean) {
		super(new URL[0], paramClassLoader);
		this.dynamicAcc = AccessController.getContext();
		try {
			patchFilePath = new File(PATCH_FOLDER).getCanonicalPath();
		} catch (IOException localIOException) {
			localIOException.printStackTrace();
		}

		if (paramBoolean) {
			initializeURLAndPath();
		}
		instance = this;
	}

	public static StartupClassLoader getInstance() {
		return instance;
	}

	public static StartupClassLoader createInstance() {
		if (null == instance) {
			instance = new StartupClassLoader(
					StartupClassLoader.class.getClassLoader());

			Thread.currentThread().setContextClassLoader(instance);
		}
		return instance;
	}

	private void initURL() {
		LinkedHashMap localLinkedHashMap = readConfigFile();
		File localFile = new File(PATCH_FOLDER);
		URL[] arrayOfURL;
		if (localFile.exists()) {
			if (!LINK_EXIST) {
				arrayOfURL = searchURL(PATCH_FOLDER);
				boolean bool = Boolean.valueOf(System.getProperty("patchtime"))
						.booleanValue();

				if (bool) {
					addURLTimeStampStratergy(arrayOfURL, false);
				} else {
					addURL(arrayOfURL, localLinkedHashMap, patchFilePath);
				}

			}

		}

		if (!loadURLFromCfg(localLinkedHashMap)) {
			arrayOfURL = searchURL(LIB);
			addURL(arrayOfURL, localLinkedHashMap, LIB);
		}
	}

	private boolean loadURLFromCfg(
			LinkedHashMap<String, ArrayList> paramLinkedHashMap) {
		int i = 0;
		if ((null != paramLinkedHashMap) && (!paramLinkedHashMap.isEmpty())) {
			LinkedHashSet localLinkedHashSet = new LinkedHashSet(
					paramLinkedHashMap.keySet());

			Iterator localIterator = localLinkedHashSet.iterator();
			String str1 = null;
			String str2 = null;
			try {
				str1 = new File(LIB).getCanonicalPath();
				str2 = new File(PRODUCTLIB_FOLDER).getCanonicalPath();
			} catch (IOException localIOException1) {
				printStackTrace(localIOException1);
			}
			String str3 = null;
			String str4 = null;
			while (localIterator.hasNext()) {
				str4 = (String) localIterator.next();
				try {
					str3 = new File(str4).getCanonicalPath();

					if (str3.equals(str1)) {
						i = 1;
					} else if ((str3.equals(patchFilePath))
							|| ((str3.equals(str2)) && (LINK_EXIST))) {
						continue;
					}

					URL[] arrayOfURL = searchURL(str3);
					addURL(arrayOfURL, paramLinkedHashMap, str4);
				} catch (IOException localIOException2) {
					printStackTrace(localIOException2);
				}
			}
		}
		return i != 0;
	}

	public void loadProductLibJars(List<String> paramList) {
		ArrayList localArrayList = new ArrayList(10);
		for (Iterator localIterator = paramList.iterator(); localIterator
				.hasNext();) {

			String localObject = (String) localIterator.next();
			getURLList(PRODUCTLIB_FOLDER + '/' + (String) localObject,
					localArrayList);
		}
		Object localObject;
		for (Iterator localIterator = localArrayList.iterator(); localIterator
				.hasNext();) {

			localObject = (URL) localIterator.next();
			addURL((URL) localObject, true);
		}
	}

	public void loadPatchJars(List<String> paramList) {
		ArrayList localArrayList = new ArrayList(10);
		for (Iterator localIterator = paramList.iterator(); localIterator
				.hasNext();) {

			String localObject = (String) localIterator.next();
			getURLList(PATCH_FOLDER + '/' + (String) localObject,
					localArrayList);
		}
		int i = localArrayList.size();
		if (i > 0) {
			URL[] localObject = new URL[i];

			localArrayList.toArray(localObject);

			addURLTimeStampStratergy(localObject, true);
		}
	}

	private static File getAbsoluteFile(String paramString) {
		File localFile = new File(paramString);
		return localFile.getAbsoluteFile();
	}

	private static boolean checklinkExists() {
		String str = System.getProperty("linkfile");
		File localFile = null;
		if (null != str) {
			localFile = getAbsoluteFile(str);
			if (!localFile.exists()) {
				str = pathName;
				localFile = getAbsoluteFile(str);
			}
		} else {
			str = pathName;
			localFile = getAbsoluteFile(str);
		}
		return localFile.exists();
	}

	private void initLibrary() {
		File localFile = new File(LIB);
		if (localFile.exists()) {
			String str = System.getProperty("java.library.path");
			if (str != null) {
				if (str.endsWith(System.getProperty("path.separator"))) {
					str = str + localFile.getAbsoluteFile().toString();
				} else {
					str = str + System.getProperty("path.separator")
							+ localFile.getAbsoluteFile().toString();
				}

				System.setProperty("java.library.path", str);
			} else {
				System.setProperty("java.library.path", localFile
						.getAbsoluteFile().toString());
			}
		}
	}

	public void addURL(URL[] paramArrayOfURL) {
		if (null != paramArrayOfURL) {
			for (URL localURL : paramArrayOfURL) {
				addURL(localURL);
			}
		}
	}

	public void clearDynamicUCP() {
		this.dynamicUCP = null;
	}

	protected void addURL(URL paramURL, boolean paramBoolean) {
		if (paramBoolean) {
			if (null == this.dynamicUCP) {
				this.dynamicUCP = new URLClassPath(new URL[0]);
			}
			this.dynamicUCP.addURL(paramURL);
		} else {
			super.addURL(paramURL);
		}
	}

	public URL[] getURLs() {
		URL[] arrayOfURL1 = super.getURLs();
		if (null == this.dynamicUCP) {
			return arrayOfURL1;
		}
		URL[] arrayOfURL2 = this.dynamicUCP.getURLs();
		URL[] arrayOfURL3 = new URL[arrayOfURL2.length + arrayOfURL1.length];

		System.arraycopy(arrayOfURL2, 0, arrayOfURL3, 0, arrayOfURL2.length);
		System.arraycopy(arrayOfURL1, 0, arrayOfURL3, arrayOfURL2.length,
				arrayOfURL1.length);

		return arrayOfURL3;
	}

	protected Class<?> findClass(String paramString)
			throws ClassNotFoundException {
		if (null != this.dynamicUCP) {
			try {
				return findClass(paramString, this.dynamicUCP);
			} catch (ClassNotFoundException localClassNotFoundException) {
			}

		}

		return super.findClass(paramString);
	}

	public URL findResource(String paramString) {
		if (null != this.dynamicUCP) {
			URL localURL = findResource(paramString, this.dynamicUCP);
			if (null != localURL) {
				return localURL;
			}
		}

		return super.findResource(paramString);
	}

	private URL findResource(final String paramString,
			final URLClassPath paramURLClassPath) {
		URL localURL = (URL) AccessController.doPrivileged(
				new PrivilegedAction() {
					public Object run() {
						return paramURLClassPath
								.findResource(paramString, true);
					}
				}, this.dynamicAcc);

		return localURL != null ? paramURLClassPath.checkURL(localURL) : null;
	}

	public Enumeration<URL> findResources(String paramString)
			throws IOException {
		Vector localVector = new Vector();

		if (null != this.dynamicUCP) {
			try {
				localVector = findResources(paramString, this.dynamicUCP);
			} catch (IOException localIOException) {
			}

		}

		Enumeration localEnumeration = super.findResources(paramString);
		while (localEnumeration.hasMoreElements()) {
			localVector.add(localEnumeration.nextElement());
		}
		return localVector.elements();
	}

	private Vector<URL> findResources(String paramString,
			final URLClassPath paramURLClassPath) throws IOException {
		final Enumeration localEnumeration = paramURLClassPath.findResources(
				paramString, true);

		Enumeration localEnumeration2 = new Enumeration() {
			private URL url = null;

			private boolean next() {
				if (this.url != null)
					return true;
				do {
					URL localURL = (URL) AccessController.doPrivileged(
							new PrivilegedAction() {
								public Object run() {
									if (!localEnumeration.hasMoreElements())
										return null;
									return localEnumeration.nextElement();
								}
							}, StartupClassLoader.this.dynamicAcc);

					if (localURL == null)
						break;
					this.url = paramURLClassPath.checkURL(localURL);
				} while (this.url == null);
				return this.url != null;
			}

			public URL nextElement() {
				if (!next()) {
					throw new NoSuchElementException();
				}
				URL localURL = this.url;
				this.url = null;
				return localURL;
			}

			public boolean hasMoreElements() {
				return next();
			}
		};
		Vector localVector = new Vector();
		while (localEnumeration2.hasMoreElements()) {
			localVector.add(localEnumeration2.nextElement());
		}

		return localVector;
	}

	protected Class<?> findClass(final String name,
			final URLClassPath paramURLClassPath) throws ClassNotFoundException {
		try {
			return (Class) AccessController.doPrivileged(
					new PrivilegedExceptionAction() {
						public Object run() throws ClassNotFoundException {
							String str = name.replace('.', '/')
									.concat(".class");
							Resource localResource = paramURLClassPath
									.getResource(str, false);
							if (localResource != null) {
								try {
									return StartupClassLoader.this.defineClass(
											name, localResource);
								} catch (IOException localIOException) {
									throw new ClassNotFoundException(name,
											localIOException);
								}

							}

							throw new ClassNotFoundException(name);
						}
					}, this.dynamicAcc);
		} catch (PrivilegedActionException localPrivilegedActionException) {
			throw ((ClassNotFoundException) localPrivilegedActionException
					.getException());
		}

	}

	private Class defineClass(String name, Resource paramResource)
			throws IOException {
		int i = name.lastIndexOf(46);
		URL localURL = paramResource.getCodeSourceURL();
		if (i != -1) {
			String pkName = name.substring(0, i);
			Package pk = getPackage(pkName);
			Manifest manifest = paramResource.getManifest();
			if (pk != null) {
				if (pk.isSealed()) {
					if (!pk.isSealed(localURL)) {
						throw new SecurityException(
								"sealing violation: package " + pkName
										+ " is sealed");
					}

				} else if ((manifest != null) && (isSealed(pkName, manifest))) {
					throw new SecurityException(
							"sealing violation: can't seal package "
									+ (String) pkName + ": already loaded");
				}

			} else if (manifest != null)
				definePackage((String) pkName, (Manifest) manifest, localURL);
			else {
				definePackage((String) pkName, null, null, null, null, null,
						null, null);
			}

		}

		ByteBuffer bb = paramResource.getByteBuffer();
		if (bb != null) {
			CodeSigner[] css = paramResource.getCodeSigners();
			CodeSource cs = new CodeSource(localURL, css);
			return defineClass(name, (ByteBuffer) bb, (CodeSource) cs);
		}
		byte[] byteArr = paramResource.getBytes();

		CodeSigner[] css = paramResource.getCodeSigners();
		CodeSource localCodeSource = new CodeSource(localURL, css);
		return (Class<?>) defineClass(name, byteArr, 0, byteArr.length,
				localCodeSource);
	}

	protected Package definePackage(String paramString, Manifest paramManifest,
			URL paramURL) throws IllegalArgumentException {
		String str1 = paramString.replace('.', '/').concat("/");
		String str2 = null;
		String str3 = null;
		String str4 = null;
		String str5 = null;
		String str6 = null;
		String str7 = null;
		String str8 = null;
		URL localURL = null;

		Attributes localAttributes = paramManifest.getAttributes(str1);
		if (localAttributes != null) {
			str2 = localAttributes
					.getValue(Attributes.Name.SPECIFICATION_TITLE);
			str3 = localAttributes
					.getValue(Attributes.Name.SPECIFICATION_VERSION);
			str4 = localAttributes
					.getValue(Attributes.Name.SPECIFICATION_VENDOR);
			str5 = localAttributes
					.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
			str6 = localAttributes
					.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
			str7 = localAttributes
					.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
			str8 = localAttributes.getValue(Attributes.Name.SEALED);
		}
		localAttributes = paramManifest.getMainAttributes();
		if (localAttributes != null) {
			if (str2 == null) {
				str2 = localAttributes
						.getValue(Attributes.Name.SPECIFICATION_TITLE);
			}
			if (str3 == null) {
				str3 = localAttributes
						.getValue(Attributes.Name.SPECIFICATION_VERSION);
			}
			if (str4 == null) {
				str4 = localAttributes
						.getValue(Attributes.Name.SPECIFICATION_VENDOR);
			}
			if (str5 == null) {
				str5 = localAttributes
						.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
			}
			if (str6 == null) {
				str6 = localAttributes
						.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
			}
			if (str7 == null) {
				str7 = localAttributes
						.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
			}
			if (str8 == null) {
				str8 = localAttributes.getValue(Attributes.Name.SEALED);
			}
		}
		if ("true".equalsIgnoreCase(str8)) {
			localURL = paramURL;
		}
		return definePackage(paramString, str2, str3, str4, str5, str6, str7,
				localURL);
	}

	private boolean isSealed(String paramString, Manifest paramManifest) {
		String str1 = paramString.replace('.', '/').concat("/");
		Attributes localAttributes = paramManifest.getAttributes(str1);
		String str2 = null;
		if (localAttributes != null) {
			str2 = localAttributes.getValue(Attributes.Name.SEALED);
		}
		if ((str2 == null)
				&& ((localAttributes = paramManifest.getMainAttributes()) != null)) {
			str2 = localAttributes.getValue(Attributes.Name.SEALED);
		}

		return "true".equalsIgnoreCase(str2);
	}

	private void getURLList(String paramString, ArrayList<URL> paramArrayList) {
		File localFile = null;
		try {
			localFile = new File(paramString).getCanonicalFile();
		} catch (IOException localIOException) {
			printStackTrace(localIOException);
		}

		if (null != localFile) {
			if (localFile.exists()) {
				searchURL(localFile, paramArrayList);
			}
		}
	}

	private URL[] searchURL(String paramString) {
		ArrayList localArrayList = new ArrayList(10);
		getURLList(paramString, localArrayList);
		URL[] arrayOfURL = new URL[localArrayList.size()];

		return (URL[]) localArrayList.toArray(arrayOfURL);
	}

	public void installPlugins(File[] paramArrayOfFile) {
		ArrayList localArrayList1 = null;
		ArrayList localArrayList2 = null;

		File[] arrayOfFile1 = paramArrayOfFile;
		if (null != arrayOfFile1) {
			int i = paramArrayOfFile.length;
			localArrayList1 = new ArrayList(i);
			localArrayList2 = new ArrayList(i);

			for (File localFile : arrayOfFile1) {
				if (isOSGIPlugin(localFile)) {
					localArrayList1.add(localFile);
				} else {
					localArrayList2.add(localFile);
				}
			}
		}
		if (null != localArrayList2) {
			URL[] arrayOfURL = searchPluginLibs(localArrayList2);

			addURL(arrayOfURL);
		}
	}

	private URL[] searchPluginLibs(List<File> paramList) {
		ArrayList localArrayList = new ArrayList(5);
		for (Object localObject = paramList.iterator(); ((Iterator) localObject)
				.hasNext();) {

			File localFile = (File) ((Iterator) localObject).next();
			localArrayList.addAll(searchSinglePlugin(localFile));
		}
		URL[] urlArr = new URL[localArrayList.size()];
		return (URL[]) localArrayList.toArray(urlArr);
	}

	private boolean isOSGIPlugin(File paramFile) {
		if (null == paramFile) {
			return false;
		}
		Object localObject1;
		Object localObject2;
		Object localObject3;
		Object localObject4;
		if (paramFile.toString().endsWith(JAR_SUFFIX)) {
			localObject1 = null;
			try {
				localObject1 = new JarFile(paramFile);
				Enumeration localEnumeration = ((JarFile) localObject1)
						.entries();
				localObject2 = null;
				localObject3 = null;
				while (localEnumeration.hasMoreElements()) {
					localObject2 = (JarEntry) localEnumeration.nextElement();
					localObject3 = ((JarEntry) localObject2).getName();
					if ((!((String) localObject3).startsWith("META-INF"))
							|| (!((String) localObject3)
									.endsWith("MANIFEST.MF"))) {
						continue;
					}
					localObject4 = ((JarFile) localObject1)
							.getInputStream((ZipEntry) localObject2);
					if (null != localObject4) {
						try {
							Manifest localManifest = new Manifest(
									(InputStream) localObject4);

							Attributes localAttributes = localManifest
									.getMainAttributes();

							int j = localAttributes
									.getValue("Bundle-SymbolicName") != null ? 1
									: 0;
							try {
								((InputStream) localObject4).close();
							} catch (IOException localIOException10) {
								printStackTrace(localIOException10);
							}

							if (null != localObject1) {
								try {
									((JarFile) localObject1).close();
								} catch (IOException localIOException11) {
									printStackTrace(localIOException11);
								}
							}
							return j == 1;
						} catch (IOException localIOException8) {
							printStackTrace(localIOException8);
						} finally {
							try {
								((InputStream) localObject4).close();
							} catch (IOException localIOException12) {
								printStackTrace(localIOException12);
							}

						}

					}

				}

				if (null != localObject1) {
					try {
						((JarFile) localObject1).close();
					} catch (IOException localIOException1) {
						printStackTrace(localIOException1);
					}
				}
			} catch (IOException localIOException2) {
				printStackTrace(localIOException2);

				if (null != localObject1) {
					try {
						((JarFile) localObject1).close();
					} catch (IOException localIOException3) {
						printStackTrace(localIOException3);
					}
				}
			} catch (SecurityException localSecurityException) {
				printStackTrace(localSecurityException);

				if (null != localObject1) {
					try {
						((JarFile) localObject1).close();
					} catch (IOException localIOException4) {
						printStackTrace(localIOException4);
					}
				}
			} finally {
				if (null != localObject1) {
					try {
						((JarFile) localObject1).close();
					} catch (IOException localIOException13) {
						printStackTrace(localIOException13);
					}
				}
			}
			return false;
		}

		localObject1 = new File(paramFile, "META-INF");
		if (((File) localObject1).exists()) {
			File localFile = new File((File) localObject1, "MANIFEST.MF");

			if (localFile.exists()) {
				localObject2 = null;
				try {
					localObject2 = new FileInputStream(localFile);
					localObject3 = new Manifest((InputStream) localObject2);

					localObject4 = ((Manifest) localObject3)
							.getMainAttributes();

					int i = ((Attributes) localObject4)
							.getValue("Bundle-SymbolicName") != null ? 1 : 0;
					return i == 1;
				} catch (IOException localIOException5) {
					printStackTrace(localIOException5);

					if (null != localObject2) {
						try {
							((FileInputStream) localObject2).close();
						} catch (IOException localIOException6) {
							printStackTrace(localIOException6);
						}
					}
				} finally {
					if (null != localObject2) {
						try {
							((FileInputStream) localObject2).close();
						} catch (IOException localIOException14) {
							printStackTrace(localIOException14);
						}
					}
				}
			}
		}
		return false;
	}

	private ArrayList<URL> searchSinglePlugin(File paramFile) {
		int i = 0;
		ArrayList localArrayList = new ArrayList(10);

		if ((paramFile.exists())
				&& (!paramFile.getAbsolutePath().endsWith(JAR_SUFFIX))) {
			PluginFileFilter localPluginFileFilter = new PluginFileFilter();

			File[] arrayOfFile = paramFile.listFiles(localPluginFileFilter);
			for (File localObject2 : arrayOfFile) {
				try {
					if ((localObject2.isDirectory()) && (i == 0)) {
						localArrayList.add(paramFile.toURI().toURL());
						i = 1;
					} else if (localObject2.getName().endsWith(JAR_SUFFIX)) {
						localArrayList.add(localObject2.toURI().toURL());
					}
				} catch (MalformedURLException localMalformedURLException2) {
					printStackTrace(localMalformedURLException2);
				}
			}

			File fileLib = new File(paramFile, "lib");
			if (fileLib.exists()) {
				searchURL(fileLib, localArrayList);
			}
		} else {
			handleJarInsideJar(paramFile);
			try {
				localArrayList.add(paramFile.toURI().toURL());
			} catch (MalformedURLException localMalformedURLException1) {
				printStackTrace(localMalformedURLException1);
			}
		}
		return (ArrayList<URL>) localArrayList;
	}

	private void handleJarInsideJar(File paramFile) {
		JarFile localJarFile = null;
		InputStream localInputStream = null;
		try {
			String str1 = getNameFromPluginURL(paramFile);

			localJarFile = new JarFile(paramFile);
			Enumeration localEnumeration = localJarFile.entries();
			JarEntry localJarEntry = null;
			String str2 = null;
			while (localEnumeration.hasMoreElements()) {
				localJarEntry = (JarEntry) localEnumeration.nextElement();
				if (localJarEntry.isDirectory()) {
					continue;
				}

				str2 = localJarEntry.getName();

				if ((!str2.startsWith("lib")) || (!str2.endsWith(JAR_SUFFIX)))
					continue;
				localInputStream = localJarFile.getInputStream(localJarEntry);
				if (null == localInputStream) {
					continue;
				}

				String str3 = System.getProperty("user.dir")
						+ "/configuration/iView/plugins/" + str1 + '/'
						+ localJarEntry;

				copy(localInputStream, str3);
				File localFile = new File(str3);

				addURL(localFile.toURI().toURL());
				try {
					localInputStream.close();
				} catch (IOException localIOException8) {
					printStackTrace(localIOException8);
				}

			}

			if (null != localJarFile) {
				try {
					localJarFile.close();
				} catch (IOException localIOException1) {
					printStackTrace(localIOException1);
				}
			}

			if (null != localInputStream) {
				try {
					localInputStream.close();
				} catch (IOException localIOException2) {
					printStackTrace(localIOException2);
				}
			}
		} catch (IOException localIOException3) {
			printStackTrace(localIOException3);

			if (null != localJarFile) {
				try {
					localJarFile.close();
				} catch (IOException localIOException4) {
					printStackTrace(localIOException4);
				}
			}

			if (null != localInputStream) {
				try {
					localInputStream.close();
				} catch (IOException localIOException5) {
					printStackTrace(localIOException5);
				}
			}
		} catch (SecurityException localSecurityException) {
			printStackTrace(localSecurityException);

			if (null != localJarFile) {
				try {
					localJarFile.close();
				} catch (IOException localIOException6) {
					printStackTrace(localIOException6);
				}
			}

			if (null != localInputStream) {
				try {
					localInputStream.close();
				} catch (IOException localIOException7) {
					printStackTrace(localIOException7);
				}
			}
		} finally {
			if (null != localJarFile) {
				try {
					localJarFile.close();
				} catch (IOException localIOException9) {
					printStackTrace(localIOException9);
				}
			}

			if (null != localInputStream) {
				try {
					localInputStream.close();
				} catch (IOException localIOException10) {
					printStackTrace(localIOException10);
				}
			}
		}
	}

	private String getNameFromPluginURL(File paramFile) {
		String str = paramFile.getPath().replaceAll("%20", " ");

		int i = str.lastIndexOf(System.getProperty("file.separator"));

		int j = str.lastIndexOf(JAR_SUFFIX);
		str = str.substring(i + 1, j);

		return str;
	}

	private boolean copy(InputStream paramInputStream, String paramString) {
		File localFile1 = new File(paramString);

		String str = localFile1.getParent();
		File localFile2 = new File(str);
		localFile2.getAbsoluteFile().mkdirs();

		if (localFile1.isDirectory()) {
			localFile1 = new File(localFile1.getParent(), localFile1.getName());
		}

		if (localFile1.exists()) {
			if (!localFile1.canWrite()) {
				return false;
			}
		}
		InputStream localInputStream = paramInputStream;
		FileOutputStream localFileOutputStream = null;
		try {
			localFileOutputStream = new FileOutputStream(localFile1);
			byte[] arrayOfByte = new byte[4096];
			int i = localInputStream.read(arrayOfByte);

			while (-1 != i) {
				localFileOutputStream.write(arrayOfByte, 0, i);
				i = localInputStream.read(arrayOfByte);
			}
		} catch (IOException localIOException3) {
			printStackTrace(localIOException3);
		} finally {
			if (localInputStream != null) {
				try {
					localInputStream.close();
				} catch (IOException localIOException6) {
					printStackTrace(localIOException6);
				}
			}
			if (localFileOutputStream != null) {
				try {
					localFileOutputStream.close();
				} catch (IOException localIOException7) {
					printStackTrace(localIOException7);
				}
			}
		}

		return true;
	}

	private void searchURL(File paramFile, ArrayList<URL> paramArrayList) {
		if (null != paramFile) {
			if ((paramFile.isFile()) && (paramFile.getName().endsWith(JAR_SUFFIX))) {
				try {
					paramArrayList.add(paramFile.toURI().toURL());
				} catch (MalformedURLException localMalformedURLException) {
					printStackTrace(localMalformedURLException);
				}

			} else {
				File[] arrayOfFile1 = paramFile.listFiles();
				if (null != arrayOfFile1) {
					Arrays.sort(arrayOfFile1);
					for (File localFile : arrayOfFile1) {
						searchURL(localFile, paramArrayList);
					}
				}
			}
		}
	}

	private LinkedHashMap<String, ArrayList> readConfigFile() {
		String str1 = System.getProperty("user.dir");

		File localFile = new File(str1, "loadingstrategy.cfg");

		LinkedHashMap localLinkedHashMap = null;

		if (localFile.exists()) {
			FileReader localFileReader = null;
			BufferedReader localBufferedReader = null;
			try {
				localFileReader = new FileReader(localFile);

				localBufferedReader = new BufferedReader(localFileReader);

				String str2 = localBufferedReader.readLine();
				localLinkedHashMap = new LinkedHashMap();
				ArrayList localArrayList = null;
				int i = 0;

				while (null != str2) {
					str2 = str2.trim();
					i = str2.length();

					if ((i > 1) && (str2.charAt(0) == '[')
							&& (str2.charAt(i - 1) == ']')) {
						localArrayList = new ArrayList(10);

						localLinkedHashMap.put(
								new File(str2.substring(1, i - 1))
										.getCanonicalPath(), localArrayList);
					} else if ((i > 0) && (null != localArrayList)) {
						localArrayList.add(str2);
					}

					str2 = localBufferedReader.readLine();
				}

				if (null != localBufferedReader) {
					try {
						localBufferedReader.close();
					} catch (IOException localIOException1) {
						printStackTrace(localIOException1);
					}
				}

				if (null != localFileReader) {
					try {
						localFileReader.close();
					} catch (IOException localIOException2) {
						printStackTrace(localIOException2);
					}
				}
			} catch (IOException localIOException3) {
				printStackTrace(localIOException3);

				if (null != localBufferedReader) {
					try {
						localBufferedReader.close();
					} catch (IOException localIOException4) {
						printStackTrace(localIOException4);
					}
				}

				if (null != localFileReader) {
					try {
						localFileReader.close();
					} catch (IOException localIOException5) {
						printStackTrace(localIOException5);
					}
				}
			} finally {
				if (null != localBufferedReader) {
					try {
						localBufferedReader.close();
					} catch (IOException localIOException6) {
						printStackTrace(localIOException6);
					}
				}

				if (null != localFileReader) {
					try {
						localFileReader.close();
					} catch (IOException localIOException7) {
						printStackTrace(localIOException7);
					}
				}

			}

		}

		return localLinkedHashMap;
	}

	private void addURL(URL[] paramArrayOfURL,
			LinkedHashMap<String, ArrayList> paramLinkedHashMap,
			String paramString) {
		ArrayList localArrayList = new ArrayList(Arrays.asList(paramArrayOfURL));

		if ((null != paramLinkedHashMap)
				&& (null != paramLinkedHashMap.get(paramString))) {
			if (!localArrayList.isEmpty()) {
				validateAndAddURLFromCfg(localArrayList, paramLinkedHashMap,
						paramString);
			}

			paramLinkedHashMap.remove(paramString);
		}

		if (paramString.equals(patchFilePath)) {
			addURLReverseStrategy(localArrayList);
		} else {
			addURLForwardStrategy(localArrayList);
		}
	}

	private void validateAndAddURLFromCfg(List<URL> paramList,
			LinkedHashMap<String, ArrayList> paramLinkedHashMap,
			String paramString) {
		String str = System.getProperty("file.separator");

		ArrayList localArrayList = (ArrayList) paramLinkedHashMap
				.get(paramString);
		URL localURL = null;
		try {
			File localFile = null;

			while ((localArrayList != null) && (!localArrayList.isEmpty())) {
				try {
					localFile = new File(paramString + str
							+ localArrayList.get(0)).getCanonicalFile();
				} catch (IOException localIOException) {
					printStackTrace(localIOException);
				}
				if (null != localFile) {
					localURL = localFile.toURI().toURL();

					if (paramList.contains(localURL)) {
						addURL(localURL);
						paramList.remove(localURL);
					}
				}

				localArrayList.remove(0);
			}
		} catch (MalformedURLException localMalformedURLException) {
			printStackTrace(localMalformedURLException);
		}
	}

	private void addURLReverseStrategy(List<URL> paramList) {
		ListIterator localListIterator = paramList.listIterator(paramList
				.size());

		URL localURL = null;
		boolean bool = localListIterator.hasPrevious();
		while (bool) {
			localURL = (URL) localListIterator.previous();
			addURL(localURL);
			bool = localListIterator.hasPrevious();
		}
	}

	private void addURLForwardStrategy(List<URL> paramList) {
		for (URL localURL : paramList) {
			addURL(localURL);
		}
	}

	private void addURLTimeStampStratergy(URL[] paramArrayOfURL,
			boolean paramBoolean) {
		ArrayList localArrayList1 = new ArrayList(10);
		ArrayList localArrayList2 = new ArrayList(5);
		JarFile localJarFile = null;
		PatchInfo localPatchInfo = null;
		File localFile1 = null;
		URL localURL1 = null;

		for (int i = 0; i < paramArrayOfURL.length; i++) {
			try {
				String str = paramArrayOfURL[i].toURI().getPath();
				localURL1 = new URL("file", null, str);
			} catch (URISyntaxException localURISyntaxException) {
				printStackTrace(localURISyntaxException);
				continue;
			} catch (MalformedURLException localMalformedURLException1) {
				printStackTrace(localMalformedURLException1);
				continue;
			}
			localFile1 = new File(localURL1.getPath());

			if (!localFile1.isFile()) {
				continue;
			}

			try {
				localJarFile = new JarFile(localFile1);
			} catch (Exception localException) {
				printStackTrace(localException);
				localJarFile = null;

				continue;
			}

			localPatchInfo = PatchInfo
					.createPatchInfo(localJarFile, localFile1);

			if (null != localJarFile) {
				try {
					localJarFile.close();
				} catch (IOException localIOException) {
					printStackTrace(localIOException);
				}
			}

			if (null != localPatchInfo) {
				localArrayList1.add(localPatchInfo);
			} else {
				localArrayList2.add(localFile1);
			}

		}

		Collections.sort(localArrayList1);

		PatchInfo[] arrayOfPatchInfo = (PatchInfo[]) (PatchInfo[]) localArrayList1
				.toArray(new PatchInfo[localArrayList1.size()]);

		URL localURL2 = null;
		for (int j = arrayOfPatchInfo.length - 1; j >= 0; j--) {
			localURL2 = arrayOfPatchInfo[j].getURL();
			if (null == localURL2)
				continue;
			addURL(localURL2, paramBoolean);
		}

		for (Object localFile2 : localArrayList2) {
			try {
				localURL2 = ((File)localFile2).toURI().toURL();
				if (null != localURL2) {
					addURL(localURL2, paramBoolean);
				}
			} catch (MalformedURLException localMalformedURLException2) {
				printStackTrace(localMalformedURLException2);
			}
		}
	}

	private static void printStackTrace(Exception paramException) {
		paramException.printStackTrace();
	}

	public static List<String> getAllU2000Plugins(final List<String> paramList) {
		File localFile1 = new File(PRODUCTLIB_FOLDER);
		File[] arrayOfFile1 = localFile1.listFiles(new FileFilter() {
			public boolean accept(File paramFile) {
				return paramList.contains(paramFile.getName());
			}
		});
		ArrayList localArrayList = new ArrayList();
		if (null != arrayOfFile1) {
			for (File localFile2 : arrayOfFile1) {
				localArrayList.add(localFile2.getName());
			}
		}

		return localArrayList;
	}

	void initializeURLAndPath() {
		initURL();

		initLibrary();
	}
}