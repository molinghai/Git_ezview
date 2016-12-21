package com.nm.server.adapter.snmp.mibload;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.percederberg.mibble.Mib;
import net.percederberg.mibble.MibLoader;
import net.percederberg.mibble.MibLoaderException;
import net.percederberg.mibble.MibLoaderLog.LogEntry;
import net.percederberg.mibble.MibSymbol;
import net.percederberg.mibble.MibType;
import net.percederberg.mibble.MibTypeSymbol;
import net.percederberg.mibble.MibValueSymbol;
import net.percederberg.mibble.snmp.SnmpNotificationType;
import net.percederberg.mibble.snmp.SnmpTrapType;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.base.util.MibTreeNode;

@SuppressWarnings("static-access")
public class MibLoaderMap implements IMibLoaderMap {
	protected static Log log = LogFactory.getLog(MibLoaderMap.class);
	/**
	 * The command-line help output.
	 */
	private static final String COMMAND_HELP = "Prints the contents of an SNMP MIB file. This program comes with\n"
			+ "ABSOLUTELY NO WARRANTY; for details see the LICENSE.txt file.\n"
			+ "\n"
			+ "Syntax: MibblePrinter [--oid-tree] <file(s) or URL(s)>\n"
			+ "\n"
			+ "    --oid-tree      Prints the complete OID tree, including all\n"
			+ "                    nodes in imported MIB files";

	/**
	 * The internal error message.
	 */
	private static final String INTERNAL_ERROR = "INTERNAL ERROR: An internal error has been found. Please report\n"
			+ "    this error to the maintainers (see the web site for\n"
			+ "    instructions). Be sure to include the version number, as\n"
			+ "    well as the text below:\n";

	private MibTreeNode mibTreeRoot;
	//key->domain(neType), value-><oidName, oid>
	 private Map<String, Map<String, String>> domainMibMap = new ConcurrentHashMap<String, Map<String, String>>();
	 
	 public MibLoaderMap(List<String> mibFiles, Map<String, String> domainMap) {
		 initalMibMap(mibFiles, domainMap);
	 }
	public void initalMibMap(List<String> mibFiles, Map<String, String> domainMap) {
		mibTreeRoot = new MibTreeNode("Mib","");
		MibLoader loader = new MibLoader();
		Mib mib = null;
		File file;
		URL url;
		// Check filePaths arguments
		if (mibFiles.size() < 1) {
			printHelp("No MIB file or URL specified");
		} 
		// Parse the MIB files
		try {
			
	//		while (!mibFiles.isEmpty()) {
				Iterator<String> it = mibFiles.iterator();
				while (it.hasNext()) {
					String mibFile = it.next();
					log.info("loading : " + mibFile);
					try {
						url = new URL(mibFile);
					} catch (MalformedURLException e) {
						url = null;
					}
					try {
						if (url == null) {
							if (mibFile != null) {
								file = new File(mibFile);
								loader.addDir(file.getParentFile());
								mib = loader.load(file);
							}
						} else {
							mib = loader.load(url);
						}
						if (mib.getLog().warningCount() > 0) {
							mib.getLog().printTo(System.err);
						}
						log.info(mibFile + "---ok!");
						it.remove();
					}  catch (MibLoaderException e) {
						log.error(e.getLog().toString(), e);
						StringBuffer localStringBuffer = new StringBuffer();
						@SuppressWarnings("unchecked")
						Iterator<LogEntry> ite = e.getLog().entries();
						while (ite.hasNext()) {
							LogEntry localLogEntry = ite.next();
							localStringBuffer.setLength(0);
							switch (localLogEntry.getType()) {
							case 2:
								localStringBuffer.append("Error: ");
								break;
							case 3:
								localStringBuffer.append("Warning: ");
								break;
							default:
								localStringBuffer.append("Internal Error: ");
							}
							localStringBuffer.append("in ");
							localStringBuffer.append(relativeFilename(localLogEntry.getFile()));
							if (localLogEntry.getLineNumber() > 0) {
								localStringBuffer.append(": line ");
								localStringBuffer.append(localLogEntry.getLineNumber());
							}
							localStringBuffer.append(":\n");
							String str = linebreakString(localLogEntry.getMessage(), "    ",
									70);
							localStringBuffer.append(str);
							str = localLogEntry.readLine();
							if (str != null) {
								localStringBuffer.append("\n\n");
								localStringBuffer.append(str);
								localStringBuffer.append("\n");
								for (int j = 1; j < localLogEntry.getColumnNumber(); ++j)
									if (str.charAt(j - 1) == '\t')
										localStringBuffer.append("\t");
									else
										localStringBuffer.append(" ");
								localStringBuffer.append("^");
							}
							log.error(localStringBuffer.toString());
						}
//						e.getLog().printTo(System.err);
						continue;
					} catch (Exception ex) {
						log.error(mibFile, ex);
						continue;
					}
                     
					MibTreeNode mibFileNode = new MibTreeNode(mib.getName(),"");
					mibTreeRoot.add(mibFileNode);
					String domain = domainMap.get(mibFile);
					@SuppressWarnings("unchecked")
					Iterator<MibSymbol> iter = mib.getAllSymbols().iterator();
					while (iter.hasNext()) {
						MibSymbol symbol = (MibSymbol) iter.next();
						if (symbol instanceof MibValueSymbol) {
							MibValueSymbol valueSymbol = (MibValueSymbol) symbol;
							String symbolName = valueSymbol.getName();
							String oid = valueSymbol.getValue().toString();
							MibTreeNode mibNode = new MibTreeNode(symbolName, oid);
							mibFileNode.add(mibNode);
							MibType type = valueSymbol.getType();
							if (type instanceof SnmpTrapType) {
								saveTrapTypeNode(valueSymbol);
							} else if (type instanceof SnmpNotificationType) {
								saveNotifyTypeNode(valueSymbol);
							} else {
								Map<String, String> mibMap = domainMibMap.get(domain);
								if (mibMap == null) {
									mibMap = new HashMap<String, String>();
									domainMibMap.put(domain, mibMap);
								}
								mibMap.put(symbolName, oid);
							}
						} else if (symbol instanceof MibTypeSymbol) {
						}
					}
				}

	//		}
		} catch (RuntimeException e) {
			printInternalError(e);
		}
//			printMibs(loadedMibs);
	}
	
	public MibTreeNode getMibTreeRoot(){
		return mibTreeRoot;
	}
	public String getNamebyOID(String oid) {
		return this.nameHashTable.get(oid);
	}
	
	public String getOIDbyName(String mibName) {
		
		return this.oidHashTable.get(mibName);
	}
	
	public String getOIDbyName(String domain, String mibName){
		if(domain == null){
			domain = "common";
		}
		log.info("getOIDbyName domain:" + domain + " mib:" + mibName);
		Map<String, String> h = this.domainMibMap.get(domain);
		if(h != null){
			return h.get(mibName);
		}
		return null;
	}
	
	public String[] getTrapBindOids(String trapName) {
		return this.trapBindHashTable.get(trapName);
	}

	/**
	 * Prints the contents of a list of MIBs.
	 * 
	 * @param mibs
	 *            the list of MIBs
	 */
	@SuppressWarnings({ "unused", "rawtypes" })
	private  void printMibs(ArrayList mibs) {
		Iterator iter;
		for (int i = 0; i < mibs.size(); i++) {
			iter = ((Mib) mibs.get(i)).getAllSymbols().iterator();
			while (iter.hasNext()) {
				MibSymbol symbol = (MibSymbol) iter.next();
				if (symbol instanceof MibValueSymbol) {
					 parseMibValueSymbol((MibValueSymbol) symbol);
				} else if (symbol instanceof MibTypeSymbol) {
				}
			}
		}
	}
	@SuppressWarnings("rawtypes")
	private String[] ArrayToOID(ArrayList arry){
		Object[] oido =  (Object[]) arry.toArray();
		String[] oids = new String[oido.length];
		for(int i=0;i<oido.length;i++){
			oids[i] = oido[i].toString();
		}
		return oids;
	}
	 
	@SuppressWarnings("rawtypes")
	private void saveNotifyTypeNode(MibValueSymbol node) {
		 String name = node.getName();
		 SnmpNotificationType type = (SnmpNotificationType) node.getType();
		 String oid  = node.getValue().toString();
		 ArrayList var = (type).getObjects();
		 String[] trapBindoids = ArrayToOID(var);
		 this.trapBindHashTable.put(name, trapBindoids);
		 this.oidHashTable.put(name, oid);
		 this.nameHashTable.put(oid, name);
	 }
	 @SuppressWarnings("rawtypes")
	private void saveTrapTypeNode(MibValueSymbol node) {
		 String name = node.getName();
		 SnmpTrapType type = (SnmpTrapType) node.getType();
		 String oid = type.getEnterprise().toString()+ node.getValue().toString();
		 ArrayList var = type.getVariables();
		 String[] trapBindoids = ArrayToOID(var);
		 this.trapBindHashTable.put(name, trapBindoids);
		 this.oidHashTable.put(name, oid);
		 this.nameHashTable.put(oid, name);
	 }
	 private void saveCommTypeNode(MibValueSymbol node){
		 String name = node.getName();
		 String oid  =node.getValue().toString();
		 this.oidHashTable.put(name, oid);
		 this.nameHashTable.put(oid, name);
//		 
//		 String neType = node.getMib().getFile().getName();
//		 Hashtable<String, String> h = oidNeHashTable.get(neType);
//		 if(h == null){
//			 h = new Hashtable<String, String>();
//		 }
//		 h.put(name, oid);
//		 this.oidNeHashTable.put(neType, h);
	 }
	private void parseMibValueSymbol(MibValueSymbol valueSymbol) {
		MibType type = valueSymbol.getType();
		if (type instanceof SnmpTrapType) {
			saveTrapTypeNode(valueSymbol);
		} else if (type instanceof SnmpNotificationType) {
			saveNotifyTypeNode(valueSymbol);
		}else{
			saveCommTypeNode(valueSymbol);
		}
	}


	/**
	 * Prints command-line help information.
	 * 
	 * @param error
	 *            an optional error message, or null
	 */
	private static void printHelp(String error) {
		System.err.println(COMMAND_HELP);
		System.err.println();
		if (error != null) {
			printError(error);
		}
	}

	/**
	 * Prints an internal error message. This type of error should only be
	 * reported when run-time exceptions occur, such as null pointer and the
	 * likes. All these error should be reported as bugs to the program
	 * maintainers.
	 * 
	 * @param e
	 *            the exception to be reported
	 */
	private static void printInternalError(Exception e) {
		System.err.println(INTERNAL_ERROR);
		e.printStackTrace();
	}

	/**
	 * Prints an error message.
	 * 
	 * @param message
	 *            the error message
	 */
	private static void printError(String message) {
		System.err.print("Error: ");
		System.err.println(message);
	}

	/**
	 * Prints a file not found error message.
	 * 
	 * @param file
	 *            the file name not found
	 * @param e
	 *            the detailed exception
	 */
	@SuppressWarnings("unused")
	private static void printError(String file, FileNotFoundException e) {
		StringBuffer buffer = new StringBuffer();

		buffer.append("couldn't open file:\n    ");
		buffer.append(file);
		printError(buffer.toString());
	}

	/**
	 * Prints a URL not found error message.
	 * 
	 * @param url
	 *            the URL not found
	 * @param e
	 *            the detailed exception
	 */
	@SuppressWarnings("unused")
	private static void printError(String url, IOException e) {
		StringBuffer buffer = new StringBuffer();

		buffer.append("couldn't open URL:\n    ");
		buffer.append(url);
		printError(buffer.toString());
	}
	
	private String relativeFilename(File paramFile) {
		if (paramFile == null)
			return "<unknown file>";
		try {
			String str1 = new File(".").getCanonicalPath();
			String str2 = paramFile.getCanonicalPath();
			if (str2.startsWith(str1)) {
				str2 = str2.substring(str1.length());
				if ((str2.charAt(0) == '/') || (str2.charAt(0) == '\\'))
					return str2.substring(1);
				return str2;
			}
		} catch (IOException localIOException) {
		}
		return paramFile.toString();
	}
	
	private String linebreakString(String paramString1, String paramString2,
			int paramInt) {
		StringBuffer localStringBuffer = new StringBuffer();
		while (paramString1.length() + paramString2.length() > paramInt) {
			int i = paramString1.lastIndexOf(32,
					paramInt - paramString2.length());
			if (i < 0) {
				i = paramString1.indexOf(32);
				if (i < 0)
					break;
			}
			localStringBuffer.append(paramString2);
			localStringBuffer.append(paramString1.substring(0, i));
			paramString1 = paramString1.substring(i + 1);
			localStringBuffer.append("\n");
		}
		localStringBuffer.append(paramString2);
		localStringBuffer.append(paramString1);
		return localStringBuffer.toString();
	}
}
