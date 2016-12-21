package com.nm.server.adapter.snmp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;


import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.UserTarget;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.context.ApplicationContext;

import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.AttributeDescriptor.AttributeType;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.error.ErrorMessageTable;
import com.nm.server.adapter.base.comm.SnmpMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.adapter.snmp.mibload.ManFactoryMibLoader;
import com.nm.server.comm.com.ScheduleType;
import com.nm.server.common.access.DataAccess;
import com.nm.server.common.util.FileTool;
import com.nm.server.common.util.ToolUtil;

public class SnmpDataAccess implements DataAccess {
	protected static Log log = LogFactory.getLog(SnmpDataAccess.class);
	public ManFactoryMibLoader facMibLoaderInstace = ManFactoryMibLoader.getInstace();// use
																						// to
																						// load
																						// mib

	private static int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();
	@SuppressWarnings("unused")
	private static final int MAX_ATTR_SIZE = 55; // to avoid too big error
	public static final Integer32 MAX_REPETITION = new Integer32(30); // hesp
																		// changed
																		// from
																		// 30

	// default number of retries is 2
	private static final int DEFAULT_RETRIES = 1;
	// default timeout is 10 seconds (stored below in hundredths of second)
	private static final int DEFAULT_TIMEOUT = 20 * 500; // hesp changed from 10
	// default read community is 'public'
	private static String FactoryID = "9966";
	private static DataAccess dbDataAccess;
	private int snmpVersion = SnmpConstants.version2c;

	private OctetString readCommunity;
	private OctetString writeCommunity;
	// private CommunityTarget readTarget;
	// private CommunityTarget writeTarget;
	//
	private String neType;

	private Target target;
	// for snmpv3
	private String userName;
	private OID authProtocol;
	private OctetString authPassword;
	private OID privProtocol;
	private OctetString privPassword;

	private String ip_port;

	private int port = 166;

	private Snmp snmp = null;
	private ScheduleType scType = ScheduleType.ST_NA;
	private boolean isSync = false;

	public SnmpDataAccess(String ipAddress, String readCommunity, String writeCommunity, int timeout, int retries, int snmp_version,
			int port) {

		this(ipAddress, readCommunity, writeCommunity, snmp_version, port);
		// netaphor expects timeout in hundredths of second, hence multiplying
		// by 100
		if (timeout < 5) {
			timeout = 5;
		}

		target.setTimeout(timeout * 1000);
		target.setRetries(retries);
	}

	private SnmpDataAccess(String ipAddress, String readCommunity, String writeCommunity, int snmp_version, int port) {

		this.ip_port = ipAddress;
		this.readCommunity = new OctetString(readCommunity);
		this.writeCommunity = new OctetString(writeCommunity);
		// setup the target
		Address address = GenericAddress.parse(ipAddress);
		target = new CommunityTarget();
		target.setAddress(address);
		target.setVersion(snmp_version);
		target.setTimeout(DEFAULT_TIMEOUT);
		target.setRetries(DEFAULT_RETRIES);
		this.port = port;
	}

	// snmpv3
	public SnmpDataAccess(String ipAddress, String userName, int securityLevel, OID authProtocol, String authPassword, OID privProtocol, String privPassword) {
		this.ip_port = ipAddress;
		this.userName = userName;
		target = new UserTarget();
		((UserTarget) target).setSecurityLevel(securityLevel);
		((UserTarget) target).setSecurityName(new OctetString(userName));

		this.authProtocol = authProtocol;
		this.privProtocol = privProtocol;
		if (authPassword != null) {
			this.authPassword = new OctetString(authPassword);
		}
		if (privPassword != null) {
			this.privPassword = new OctetString(privPassword);
		}

		Address address = GenericAddress.parse(ipAddress);
		target.setAddress(address);
		snmpVersion = SnmpConstants.version3;
		target.setVersion(snmpVersion);
		target.setTimeout(DEFAULT_TIMEOUT);
		target.setRetries(DEFAULT_RETRIES);
	}

	public void destroy() {
		try {
			if (snmp != null) {
				snmp.close();
			}
		} catch (Exception e) {
			log.error(e, e);
		} finally {
			snmp = null;
		}
	}

	public int getPort() {
		return port;
	}

	public void setNeType(String neType) {
		this.neType = neType;
	}

	// v2
	public void setReadCommunity(String community) {
		this.readCommunity = new OctetString(community);
	}

	public void setWriteCommunity(String community) {
		this.writeCommunity = new OctetString(community);
	}

	public void setIpAddress(String ip) {
		this.ip_port = ip;
		Address address = GenericAddress.parse(ip);
		target.setAddress(address);
	}

	public String getIpAddress() {
		return ip_port;
	}

	public void setTimeOut(long timeout) {
		target.setTimeout(timeout * 500);
	}

	// public void updateCmdAdapter(Map nodeAttrs) {
	//
	// String snmpRead = (String) nodeAttrs.get(SnmpEumDef.snmpRead);
	// if (snmpRead != null) {
	// this.readTarget.setCommunity(new OctetString(snmpRead));
	// }
	// String snmpWrite = (String) nodeAttrs.get(SnmpEumDef.snmpWrite);
	// if (snmpWrite != null) {
	// this.writeTarget.setCommunity(new OctetString(snmpWrite));
	// }
	// Integer snmpRetry = (Integer) nodeAttrs.get(SnmpEumDef.snmpRetry);
	// if (snmpRetry != null) {
	// this.readTarget.setRetries(snmpRetry.intValue());
	// this.writeTarget.setRetries(snmpRetry.intValue());
	// }
	// Integer snmpTimeout = (Integer) nodeAttrs.get(SnmpEumDef.snmpTimeout);
	// if (snmpTimeout != null) {
	// this.readTarget.setTimeout(snmpTimeout.intValue() * 100);
	// this.writeTarget.setTimeout(snmpTimeout.intValue() * 100);
	// }
	// String ip = (String) nodeAttrs.get(SnmpEumDef.neId);
	// if (ip != null) {
	// IpAddress addr = new IpAddress(ip);
	// readTarget.setAddress(addr);
	// writeTarget.setAddress(addr);
	// }
	// }
	public void update(FunctionDescriptor funDescriptor, Map<String, Object> setattributes, List<Object> extIdxes) {

		PDU request;
		List<AttributeDescriptor> setAttrDataArr = new ArrayList<AttributeDescriptor>();
		request = getSetRequestPDU(setattributes, extIdxes, funDescriptor, setAttrDataArr);

		snmpSet(request, funDescriptor, setAttrDataArr);

	}

	public PDU getSetRequestPDU(Map<String, Object> setMap, List<Object> indexValues, FunctionDescriptor cd, List<AttributeDescriptor> setAttrDataArr) {
		if (setAttrDataArr == null) {
			// throw new CodedException( -2,
			// "Null value for setAttrDataArr input arg");
		}
		PDU pdu = new PDU();
		if (snmpVersion == SnmpConstants.version3) {
			pdu = new ScopedPDU();
		} else {
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
		}

		List<AttributeDescriptor> attrs = cd.getAttributeList();
		List<AttributeDescriptor> clonAttrs = this.cloneAttrs(attrs);
		if (clonAttrs == null || clonAttrs.size() == 0)
			return pdu;
		for (AttributeDescriptor attr : clonAttrs) {
			try {
				FactoryID = "9966";
				if (!attr.isDownload() || attr.getExternalName() == null || "".equals(attr.getExternalName())
						|| "null".equalsIgnoreCase(attr.getExternalName())
						|| (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index) && attr.isReadOnly()) || !attr.getSupported())
					continue;
				log.info("######## FactoryID==========" + FactoryID);
				FactoryID = "9966";
				String oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, neType, attr.getExternalName());
				if (oidStr == null&&cd.getDomain()!=null) {
					oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, cd.getDomain().toLowerCase(), attr.getExternalName());
				}
				if (oidStr == null) {
					oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, null, attr.getExternalName());
				}
				OID atrroid = new OID(oidStr);// get oid from externalName
				if (attr.getExternalName() != null) {
					attr.setExternalName(atrroid.toString());
				} else {

				}

				String attrName = null;
				if (attr.getName() != null)
					attrName = attr.getName();
				Object obj = setMap.get(attrName);
				if (obj != null) {
					String exname = null;
					String indexValue = "";
					// if ( (ad.externalPropMask & 0x1) != 0) {
					for (int m = 0; m < indexValues.size(); m++) {
						indexValue += "." + indexValues.get(m);
					}
					// if ( (attr.externalPropMask & 0x1) != 0) {
					// if (attr.externalPropMask != 3)
					// continue ;

					if (!attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Scalar)) {
						if (indexValue.equalsIgnoreCase("")) {
							// throw new
							// CodedException(-2,"List contains a table type attribute (cell) but no index value specified."
							// + attrName);
							log.error("###########indexValue.equalsIgnoreCase()");
						} else {
							exname = attr.getExternalName() + "." + indexValue;
						}
					} else {
						if(indexValues.size() > 0){
							exname = attr.getExternalName() + "." + indexValue;
						}else{
							exname = attr.getExternalName() + ".0";
						}
					}
					System.out.println("############exname==" + exname);
					OID oid = new OID(exname);
					// create a variable binding
					VariableBinding vb = new VariableBinding();

					// set the OID in the variable binding
					vb.setOid(oid);
					Object v = setMap.get(attrName);
					if (v != null) {
						Variable var = SnmpDataConvert.convertToSNMPType(v, attr);
						if (var == null) {
							// throw new
							// CodedException(-2,"Failed to convert to SNMP type"
							// + attrName);
						}

						vb.setVariable(var);
						pdu.add(vb);

						setAttrDataArr.add(attr);
					}
				}
			} catch (Exception e) {
				log.error(e, e);
			}
		}
		return pdu;
	}

	public PDU getSetRequestPDU(Map<String, Object> setMap, String indexValue, FunctionDescriptor cd, AttributeDescriptor[] setAttrDataArr) {
		if (setAttrDataArr == null) {
		}
		PDU pdu = new PDU();
		List<AttributeDescriptor> attrs = cd.getAttributeList();
		if (attrs == null || attrs.size() == 0)
			return pdu;
		int k = 0;
		for (AttributeDescriptor attr : attrs) {
			String attrName = attr.getName();
			if (attr.getExternalName() == null || "".equals(attr.getExternalName()) || "null".equalsIgnoreCase(attr.getExternalName()))
				continue;
			Object obj = setMap.get(attrName);
			if (obj != null) {
				String exname = null;
				// if ( (ad.externalPropMask & 0x1) != 0) {
				// if (attr.externalPropMask == 3)
				// continue ;

				if (!attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Scalar)) {
					if (indexValue == null) {
						// throw new
						// CodedException(-2,"List contains a table type attribute (cell) but no index value specified."
						// + attrName);
					} else {
						exname = attr.getExternalName() + "." + indexValue;
					}
				} else {
					exname = attr.getExternalName();
				}

				OID oid = new OID(exname);
				// create a variable binding
				VariableBinding vb = new VariableBinding();

				// set the OID in the variable binding
				vb.setOid(oid);
				Object v = setMap.get(attrName);
				Variable var = SnmpDataConvert.convertToSNMPType(v, attr);
				vb.setVariable(var);
				if (var == null) {
					// throw new
					// CodedException(-2,"Failed to convert to SNMP type" +
					// attrName);
				}
				// add the variable binding to the PDU
				pdu.add(vb);
				setAttrDataArr[k++] = attr;
			}
		}

		return pdu;
	}

	private List<AttributeDescriptor> cloneAttrs(List<AttributeDescriptor> attrs) {
		List<AttributeDescriptor> cloneattrs = new ArrayList<AttributeDescriptor>();
		for (AttributeDescriptor attr : attrs) {
			cloneattrs.add(attr.clone());
		}
		return cloneattrs;
	}

	public List<Map<String, Object>> getDatas(List<AttributeDescriptor> attrs, String domain) {
		return getDatas(attrs, domain, null);
	}

	public List<Map<String, Object>> getDatas(List<AttributeDescriptor> attrs, String domain, List<Object> getBulkInsts) {
		List<AttributeDescriptor> clonAttrs = this.cloneAttrs(attrs);
		List<AttributeDescriptor> needloadattr = new ArrayList<AttributeDescriptor>();
		List<AttributeDescriptor> indexList = new ArrayList<AttributeDescriptor>();
		List<OID> oidList = new ArrayList<OID>();
		List<OID> oldOidList = new ArrayList<OID>();
		String exterName;

		for (AttributeDescriptor attr : clonAttrs) {
			if (attr.getExternalName() != null && attr.getExternalPropMask() != null) {
				if (!attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)) {
					needloadattr.add(attr);
					if (attr.getExternalName() != null && !attr.getExternalName().isEmpty() && !"null".equalsIgnoreCase(attr.getExternalName())) {
						exterName = attr.getExternalName();
						try {
							FactoryID = "9966";
							String oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, neType, exterName);
							if (oidStr == null&&domain!=null) {
								oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, domain.toLowerCase(), exterName);
							}
							if (oidStr == null) {
								oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, null, exterName);
							}
							OID oid = new OID(oidStr);
							OID oldOid = (OID) oid.clone();
							oldOidList.add(oldOid);
							if (getBulkInsts != null) {
								for (Object o : getBulkInsts) {
									oid.append(o.toString());
								}
							}
							oidList.add(oid);
							attr.setExternalName(oldOid.toString());
						} catch (NullPointerException e) {
							log.error("Can't find " + exterName + " in mibfile!");
						}
					}
				} else {
					indexList.add(attr);
					if (!attr.isReadOnly()) {
						needloadattr.add(attr);
						if (attr.getExternalName() != null && !attr.getExternalName().isEmpty() && !"null".equalsIgnoreCase(attr.getExternalName())) {
							exterName = attr.getExternalName();
							try {
								FactoryID = "9966";
								String oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, neType, exterName);
								if (oidStr == null&&domain!=null) {
									oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, domain.toLowerCase(), exterName);
								}
								if (oidStr == null) {
									oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, null, exterName);
								}
								OID oid = new OID(oidStr);
								OID oldOid = (OID) oid.clone();
								oldOidList.add(oldOid);
								oidList.add(oid);
								attr.setExternalName(oid.toString());
							} catch (NullPointerException e) {
								log.error("Can't find " + exterName + " in mibfile!");
							}
						}
					}
				}
			}
		}

		int count = 0;

		List<Map<String, Object>> tableDatas = new ArrayList<Map<String, Object>>();

		List<VariableBinding> vars = new ArrayList<VariableBinding>();
		OID oid;

		VariableBinding[][] nextvbs = null;
		if (indexList.isEmpty()) {
			if ("ce".equalsIgnoreCase(domain)) {
				if (getBulkInsts != null) {
					nextvbs = getBulk(oidList);
				} else {
					nextvbs = getNext(oidList);
				}
			} else {
				for (OID o : oidList) {
					o.append(0);
				}
				nextvbs = get(oidList);
			}
		} else {
			if (getBulkInsts != null) {
				nextvbs = getBulk(oidList);
			} else {
				nextvbs = getNext(oidList);
			}
		}

		List<OID> returnOids = new ArrayList<OID>();

		try {
			if (nextvbs != null && nextvbs[0].length > 0) {
				for (int i = 0; i < nextvbs[0].length; i++) {
					returnOids.add(nextvbs[0][i].getOid());
				}
				OID returnOid = nextvbs[0][0].getOid();
				int syntax = nextvbs[0][0].getSyntax();

				if (syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW || syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE
						|| !returnOid.startsWith(oldOidList.get(0))) {
					return tableDatas;
				}

				if (nextvbs == null || nextvbs.length == 0)
					return null;

				// AttributeDescriptor[] attrArrays = new
				// AttributeDescriptor[needloadattr.size()];
				// int j = 0 ;
				// for(AttributeDescriptor ad : needloadattr)
				// {
				// attrArrays[j++] = ad ;
				// }

				List<Map<String, Object>> attrsMaps = new LinkedList<Map<String, Object>>();

				if (getBulkInsts != null) {
					attrsMaps = getGetBulkResponseMap(nextvbs, needloadattr);
				} else {
					Map<String, Object> m = getGetResponseMap(nextvbs[0], needloadattr);
					attrsMaps.add(m);
				}

				for (Map<String, Object> attrsmap : attrsMaps) {
					Object v = attrsmap.get(SnmpEumDef.mibInstance);
					if(v == null){
						continue;
					}
					String intstance = v.toString();
					intstance = intstance.replace('.', '#');
					boolean hasMACIndex = false;
					boolean hasIPIndex = false;
					boolean hasStringIndex = false;
					boolean hasIPV6Index = false;
					boolean otherIndex = false;
					Map<String, Integer> macIndexs = new HashMap<String, Integer>();
					Map<String, Integer> ipIndexs = new HashMap<String, Integer>();
					Map<String, Integer> strIndexs = new HashMap<String, Integer>();
					Map<String, Integer> ipv6Indexs = new HashMap<String, Integer>();
					Map<String, Integer> otherIndexs = new HashMap<String, Integer>();
					if (indexList.size() != 0) {
						for (int i = 0; i < indexList.size(); i++) {
							if (indexList.get(i).getExternalType().equalsIgnoreCase(SnmpEumDef.MACAddress)) {
								hasMACIndex = true;
								macIndexs.put(indexList.get(i).getName(), i);
							} else if(indexList.get(i).getExternalType().equalsIgnoreCase(SnmpEumDef.IpAddress)){
								hasIPIndex = true;
								ipIndexs.put(indexList.get(i).getName(), i);
							} else if(indexList.get(i).getExternalType().equalsIgnoreCase(SnmpEumDef.OCTET)){
								if (indexList.get(i).getAttributeType().equals(AttributeType.AT_IPV6)) {
									hasIPV6Index = true;
									ipv6Indexs.put(indexList.get(i).getName(), i);
								}else {
									hasStringIndex = true;
									strIndexs.put(indexList.get(i).getName(), i);
								}
							} else {
								otherIndex = true;
								otherIndexs.put(indexList.get(i).getName(), i);
							}
						}
						if (hasMACIndex && intstance.indexOf("#") != -1) {
							String[] ins = intstance.split("#");
							String[] indexs = new String[indexList.size()];
							for (int m = 0; m < indexList.size(); m++) {
								if (macIndexs.containsKey(indexList.get(m).getName())) {
									StringBuilder sb = new StringBuilder();
									for (int j = m; j < m + 6; j++) {
										sb.append(ins[j]);
										if (j < m + 5) {
											sb.append(".");
										}
									}
									indexs[m] = sb.toString();

								} else {
									int temp = 0;
									for (String key : macIndexs.keySet()) {

										if (m < macIndexs.get(key)) {
											indexs[m] = ins[m];
										} else {
											temp = m + 6;
											indexs[m] = ins[temp - 1];
										}
									}
								}
							}
							for (int i = 0; i < indexs.length; i++) {
								attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
							}
						} else if(hasIPIndex && intstance.indexOf("#") != -1){
							String[] ins = intstance.split("#");
							String[] indexs = new String[indexList.size()];
							int insIndex = 0;
							boolean lag = true;
							for (int m = 0; m < indexList.size(); m++) {
								if (ipIndexs.containsKey(indexList.get(m).getName())) {
									StringBuilder sb = new StringBuilder();
									for (int j = insIndex; j < insIndex + 4; j++) {
										sb.append(ins[j]);
										if (j < insIndex + 3) {
											sb.append(".");
										}
									}
									indexs[m] = sb.toString();
									insIndex = insIndex + 4;
								} else {
									if(hasStringIndex){
										Integer strindex = strIndexs.get(indexList.get(m).getName());
										if(strindex != null && strindex == m){
											int strLength = Integer.parseInt(ins[insIndex]);
											StringBuilder sb = new StringBuilder();
											sb.append(strLength);
											for(int i = insIndex; i < insIndex + strLength; i++){
												sb.append("#");
												sb.append(ins[i+1]);
											}
											indexs[m] = sb.toString();
											insIndex = insIndex + strLength;
											lag = false;
										}
									}else if(otherIndex){
										Integer othindex = otherIndexs.get(indexList.get(m).getName());
										if(othindex != null && othindex == m){
//											StringBuilder sb = new StringBuilder();
//											for(int i = insIndex; i <= insIndex; i++){
//												sb.append(ins[i]);
//											}
											indexs[m] = ins[insIndex];
											insIndex++;
											lag = false;
										}
									}
									if(lag){
										for (String key : ipIndexs.keySet()) {
											if (m < ipIndexs.get(key)) {
												indexs[m] = ins[insIndex];
												insIndex++;
											} 
										}
									}
								}
							}
							for (int i = 0; i < indexs.length; i++) {
								attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
							}
						} else if(hasIPV6Index && intstance.indexOf("#") != -1){
							String[] ins = intstance.split("#");
							String[] indexs = new String[indexList.size()];
							int insIndex = 0;
							boolean lag = true;
							for (int m = 0; m < indexList.size(); m++) {
								if (ipv6Indexs.containsKey(indexList.get(m).getName())) {
									StringBuilder sb = new StringBuilder();
									for (int j = insIndex; j < insIndex + 6; j++) {
										sb.append(ins[j]);
										if (j < insIndex + 5) {
											sb.append(".");
										}
									}
									indexs[m] = sb.toString();
									insIndex = insIndex + 6;
								} else {
									if(hasStringIndex){
										Integer strindex = strIndexs.get(indexList.get(m).getName());
										if(strindex != null && strindex == m){
											int strLength = Integer.parseInt(ins[insIndex]);
											StringBuilder sb = new StringBuilder();
											sb.append(strLength);
											for(int i = insIndex; i < insIndex + strLength; i++){
												sb.append("#");
												sb.append(ins[i+1]);
											}
											indexs[m] = sb.toString();
											insIndex = insIndex + strLength;
											lag = false;
										}
									}else if(otherIndex){
										Integer othindex = otherIndexs.get(indexList.get(m).getName());
										if(othindex != null && othindex == m){
//											int strLength = Integer.parseInt(ins[insIndex]);
//											StringBuilder sb = new StringBuilder();
//											for(int i = insIndex; i <= insIndex; i++){
//												sb.append(ins[i]);
//											}
											indexs[m] = ins[insIndex];
											insIndex++;
											lag = false;
										}
									}
									if(lag){
										for (String key : ipv6Indexs.keySet()) {
											if (m < ipv6Indexs.get(key)) {
												indexs[m] = ins[insIndex];
												insIndex++;
											} 
										}
									}
								}
							}
							for (int i = 0; i < indexs.length; i++) {
								attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
							}
						} else {
							if (intstance.indexOf("#") != -1) {
								String[] indexs = intstance.split("#");
								if (indexList.size() == indexs.length) {
									for (int i = 0; i < indexs.length; i++) {
										attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
									}
								} else {
									if (indexList.size() == 1) {
										attrsmap.put(indexList.get(0).getName(), intstance);
									} else if (hasStringIndex && indexs.length > indexList.size()) {
										AttributeDescriptor indexAttr;
										int offInstance = 0;
										for (int off = 0; off < indexList.size(); off++) {
											indexAttr = indexList.get(off);
											if (indexAttr.getExternalType().equalsIgnoreCase(SnmpEumDef.OCTET)) {
												int strIndexLength = Integer.valueOf(indexs[offInstance]);
												if (indexs.length > strIndexLength + offInstance) {
													StringBuffer strB = new StringBuffer();
													for (int a = offInstance; a <= offInstance + strIndexLength; a++) {
														if (strB.length() > 0) {
															strB.append("#");
														}
														strB.append(indexs[a]);
													}
													attrsmap.put(indexAttr.getName(), strB.toString());
												}
												offInstance += strIndexLength + 1;
											} else {
												attrsmap.put(indexAttr.getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[offInstance], indexAttr));
												offInstance += 1;
											}
										}
									}
								}

							} else {
								if (indexList.get(0).isReadOnly()) {
									AttributeDescriptor ad = indexList.get(0);
									attrsmap.put(indexList.get(0).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(intstance.toString(), ad));
								}
							}
						}
					}

					tableDatas.add(attrsmap);

					if (!indexList.isEmpty() && getBulkInsts == null) {
						while (returnOid.startsWith(oidList.get(0)) && syntax != SMIConstants.EXCEPTION_NO_SUCH_INSTANCE
								&& syntax != SMIConstants.EXCEPTION_END_OF_MIB_VIEW) {
							try {
								Thread.sleep(getWriteFrameInterval());
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							// if(indexList.isEmpty()){
							// nextvbs = get(returnOids);
							// }else{
							// nextvbs = getNext(returnOids);
							// }
							nextvbs = getNext(returnOids);
							returnOids.clear();
							for (int i = 0; i < nextvbs[0].length; i++) {
								returnOids.add(nextvbs[0][i].getOid());
							}

							returnOid = nextvbs[0][0].getOid();
							syntax = nextvbs[0][0].getSyntax();

							if (syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW || syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE
									|| !returnOid.startsWith(oidList.get(0))) {
								return tableDatas;
							}

							// attrArrays = new
							// AttributeDescriptor[needloadattr.size()];
							// j = 0 ;
							// for(AttributeDescriptor ad : needloadattr)
							// {
							// attrArrays[j++] = ad ;
							// }
							//
							attrsmap = getGetResponseMap(nextvbs[0], needloadattr);
							intstance = attrsmap.get(SnmpEumDef.mibInstance).toString();

							intstance = intstance.replace('.', '#');
							// boolean hasMACIndex = false;
							// Map<String, Integer> macIndexs = new
							// HashMap<String,
							// Integer>();
							if (indexList.size() != 0) {
								for (int i = 0; i < indexList.size(); i++) {
									if (indexList.get(i).getExternalType().equalsIgnoreCase(SnmpEumDef.MACAddress)) {
										hasMACIndex = true;
										macIndexs.put(indexList.get(i).getName(), i);
									} else if(indexList.get(i).getExternalType().equalsIgnoreCase(SnmpEumDef.IpAddress)){
										hasIPIndex = true;
										ipIndexs.put(indexList.get(i).getName(), i);
									} else if(indexList.get(i).getExternalType().equalsIgnoreCase(SnmpEumDef.OCTET)){
//										hasStringIndex = true;
//										strIndexs.put(indexList.get(i).getName(), i);
										if (indexList.get(i).getAttributeType().equals(AttributeType.AT_IPV6)) {
											hasIPV6Index = true;
											ipv6Indexs.put(indexList.get(i).getName(), i);
										}else {
											hasStringIndex = true;
											strIndexs.put(indexList.get(i).getName(), i);
										}
									} else {
										otherIndex = true;
										otherIndexs.put(indexList.get(i).getName(), i);
									}
									
								}
								if (hasMACIndex && intstance.indexOf("#") != -1) {
									String[] ins = intstance.split("#");
									String[] indexs = new String[indexList.size()];
									for (int m = 0; m < indexList.size(); m++) {
										if (macIndexs.containsKey(indexList.get(m).getName())) {
											StringBuilder sb = new StringBuilder();
											for (int j = m; j < m + 6; j++) {
												sb.append(ins[j]);
												if (j < m + 5) {
													sb.append(".");
												}
											}
											indexs[m] = sb.toString();

										} else {
											int temp = 0;
											for (String key : macIndexs.keySet()) {

												if (m < macIndexs.get(key)) {
													indexs[m] = ins[m];
												} else {
													temp = m + 6;
													indexs[m] = ins[temp - 1];
												}
											}
										}
									}
									for (int i = 0; i < indexs.length; i++) {
										attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
									}
								} else if(hasIPIndex && intstance.indexOf("#") != -1){
									String[] ins = intstance.split("#");
									String[] indexs = new String[indexList.size()];
									int insIndex = 0;
									boolean lag = true;
									for (int m = 0; m < indexList.size(); m++) {
										if (ipIndexs.containsKey(indexList.get(m).getName())) {
											StringBuilder sb = new StringBuilder();
											for (int j = insIndex; j < insIndex + 4; j++) {
												sb.append(ins[j]);
												if (j < insIndex + 3) {
													sb.append(".");
												}
											}
											indexs[m] = sb.toString();
											insIndex =insIndex + 4;
										} else {
											if(hasStringIndex){
												Integer strindex = strIndexs.get(indexList.get(m).getName());
												if(strindex != null && strindex == m){
													int strLength = Integer.parseInt(ins[insIndex]);
													StringBuilder sb = new StringBuilder();
													sb.append(strLength);
													for(int i = insIndex; i < insIndex + strLength; i++){
														sb.append("#");
														sb.append(ins[i+1]);
													}
													indexs[m] = sb.toString();
													insIndex = insIndex + strLength;
													lag = false;
												}
											}else if(otherIndex){
												Integer othindex = otherIndexs.get(indexList.get(m).getName());
												if(othindex != null && othindex == m){
//													int strLength = Integer.parseInt(ins[insIndex]);
//													StringBuilder sb = new StringBuilder();
//													for(int i = insIndex; i <= insIndex; i++){
//														sb.append(ins[i]);
//													}
													indexs[m] = ins[insIndex];
													insIndex++;
													lag = false;
												}
											}
											if(lag){
												for (String key : ipIndexs.keySet()) {
													if (m < ipIndexs.get(key)) {
														indexs[m] = ins[insIndex];
														insIndex++;
													} 
												}
											}
										}
									}
									for (int i = 0; i < indexs.length; i++) {
										attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
									}
								}else if(hasIPV6Index && intstance.indexOf("#") != -1){
									String[] ins = intstance.split("#");
									String[] indexs = new String[indexList.size()];
									int insIndex = 0;
									boolean lag = true;
									for (int m = 0; m < indexList.size(); m++) {
										if (ipv6Indexs.containsKey(indexList.get(m).getName())) {
											StringBuilder sb = new StringBuilder();
											for (int j = insIndex; j < insIndex + 6; j++) {
												sb.append(ins[j]);
												if (j < insIndex + 5) {
													sb.append(".");
												}
											}
											indexs[m] = sb.toString();
											insIndex = insIndex + 6;
										} else {
											if(hasStringIndex){
												Integer strindex = strIndexs.get(indexList.get(m).getName());
												if(strindex != null && strindex == m){
													int strLength = Integer.parseInt(ins[insIndex]);
													StringBuilder sb = new StringBuilder();
													sb.append(strLength);
													for(int i = insIndex; i < insIndex + strLength; i++){
														sb.append("#");
														sb.append(ins[i+1]);
													}
													indexs[m] = sb.toString();
													insIndex = insIndex + strLength;
													lag = false;
												}
											}else if(otherIndex){
												Integer othindex = otherIndexs.get(indexList.get(m).getName());
												if(othindex != null && othindex == m){
//													int strLength = Integer.parseInt(ins[insIndex]);
//													StringBuilder sb = new StringBuilder();
//													for(int i = insIndex; i <= insIndex; i++){
//														sb.append(ins[i]);
//													}
													indexs[m] = ins[insIndex];
													insIndex++;
													lag = false;
												}
											}
											if(lag){
												for (String key : ipv6Indexs.keySet()) {
													if (m < ipv6Indexs.get(key)) {
														indexs[m] = ins[insIndex];
														insIndex++;
													} 
												}
											}
										}
									}
									for (int i = 0; i < indexs.length; i++) {
										attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
									}
								} else {
									if (intstance.indexOf("#") != -1) {
										String[] indexs = intstance.split("#");
										if (indexList.size() == indexs.length) {
											for (int i = 0; i < indexs.length; i++) {
												attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
											}
										} else {
											if (indexList.size() == 1) {
												attrsmap.put(indexList.get(0).getName(), intstance);
											} else if (hasStringIndex && indexs.length > indexList.size()) {
												AttributeDescriptor indexAttr;
												int offInstance = 0;
												for (int off = 0; off < indexList.size(); off++) {
													indexAttr = indexList.get(off);
													if (indexAttr.getExternalType().equalsIgnoreCase(SnmpEumDef.OCTET)) {
														int strIndexLength = Integer.valueOf(indexs[offInstance]);
														if (indexs.length > strIndexLength + offInstance) {
															StringBuffer strB = new StringBuffer();
															for (int a = offInstance; a <= offInstance + strIndexLength; a++) {
																if (strB.length() > 0) {
																	strB.append("#");
																}
																strB.append(indexs[a]);
															}
															attrsmap.put(indexAttr.getName(), strB.toString());
														}
														offInstance += strIndexLength + 1;
													} else {
														attrsmap.put(indexAttr.getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[offInstance], indexAttr));
														offInstance += 1;
													}
												}
											}
										}

									} else {
										if (indexList.get(0).isReadOnly()) {
											AttributeDescriptor ad = indexList.get(0);
											attrsmap.put(indexList.get(0).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(intstance.toString(), ad));
										}
									}
								}
							}

							tableDatas.add(attrsmap);
						}
					}
				}

				// log.debug("Fetched "+classData.name
				// +"'Table Data by SNMP End") ;
				return tableDatas;
			}
		} catch (Exception ex) {
			throw new SnmpDataException("getDatas error", ex);
		}
		// log.debug("Fetched "+classData.name +"'Table Data by SNMP End") ;
		return tableDatas;
	}

	private OID getStdOID(OID oid, List<Object> getBulkInsts) {
		OID stdOID = new OID(oid.toString());
		if (getBulkInsts != null) {
			for (Object o : getBulkInsts) {
				stdOID.append(o.toString());
			}
		}
		return stdOID;
	}

	public List<Map<String, Object>> getGetBulkResponseMap(VariableBinding[][] vbss, List<AttributeDescriptor> ads) {
		List<Map<String, Object>> res = new LinkedList<Map<String, Object>>();
		String instanceID = null;
		for (int i = 0; i < vbss.length; i++) {
			AttributeDescriptor ad = ads.get(i);
			VariableBinding[] vbs = vbss[i];

			for (int j = 0; j < vbs.length; j++) {
				VariableBinding vb = vbs[j];
				Variable snmpValue = vb.getVariable();
				String oidName = vb.getOid().toString();
				int syntax = snmpValue.getSyntax();
				if (syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE || syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW || syntax == SMIConstants.SYNTAX_NULL) {
					continue;
				}

				Map<String, Object> m = null;
				if (i == 0) {
					m = new HashMap<String, Object>();
					res.add(m);
				} else {
					m = res.get(j);
				}
				// checkResponseVbValue(snmpValue);
				if (oidName != null)
					if (oidName.endsWith(".0") && ad.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Scalar))
						// instanceID =
						// oidName.substring(ad.externalName.length()+0
						// ) ;
						instanceID = "0";
					else if(oidName.indexOf(ad.getExternalName()) >= 0){
						instanceID = oidName.substring(ad.getExternalName().length() + 1);
					}else{
						continue;
					}
						
				Object v = null;
				if (snmpValue != null) {
					if (ad.getExternalType() != null && !ad.getExternalType().equals("")) {
						try {
							Object value = SnmpDataConvert.convertFromSNMPType(snmpValue);
							v = SnmpDataConvert.covertToDBTypeFromMibInstance(value, ad);
						} catch (NumberFormatException e) {
							log.error("when covert " + ad.getExternalName() + "'s value to dbtype occur wrong!!");
							log.error("the ExternalType of " + ad.getExternalName() + " is " + ad.getExternalType() + " in configfile");
							log.error("the ExternalType of " + ad.getExternalName() + " is " + ad.getExternalType() + " in configfile");
							log.error("the ExternalType of " + ad.getExternalName() + " is " + snmpValue.getSyntaxString() + " in mibfile" + ",snmpValue:"
									+ snmpValue + ",Syntax:" + snmpValue.getSyntax());
						}
					}
					// log.debug(ad.name+" : " + v.toString());

					m.put(ad.getName(), v);

				}

				m.put(SnmpEumDef.mibInstance, instanceID);
			}

		}

		return res;
	}

	public Map<String, Object> getGetResponseMap(VariableBinding[] vbs, List<AttributeDescriptor> ads) {
		Map<String, Object> res = new HashMap<String, Object>();
		String instanceID = null;
		for (int i = 0; i < vbs.length; i++) {
			VariableBinding vb = vbs[i];
			AttributeDescriptor ad = ads.get(i);
			Variable snmpValue = vb.getVariable();
			String oidName = vb.getOid().toString();
			int syntax = snmpValue.getSyntax();
			if (syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE || syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW || syntax == SMIConstants.SYNTAX_NULL) {
				continue;
			}
			// checkResponseVbValue(snmpValue);
			if (oidName != null)
				if (oidName.endsWith(".0") && ad.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Scalar))
					// instanceID = oidName.substring(ad.externalName.length()+0
					// ) ;
					instanceID = "0";
				else
					instanceID = oidName.substring(ad.getExternalName().length() + 1);
			Object v = null;
			if (snmpValue != null) {
				if (ad.getExternalType() != null && !ad.getExternalType().equals("")) {
					try {
						Object value = SnmpDataConvert.convertFromSNMPType(snmpValue);
						v = SnmpDataConvert.covertToDBTypeFromMibInstance(value, ad);
					} catch (NumberFormatException e) {
						log.error("when covert " + ad.getExternalName() + "'s value to dbtype occur wrong!!");
						log.error("the ExternalType of " + ad.getExternalName() + " is " + ad.getExternalType() + " in configfile");
						log.error("the ExternalType of " + ad.getExternalName() + " is " + ad.getExternalType() + " in configfile");
						log.error("the ExternalType of " + ad.getExternalName() + " is " + snmpValue.getSyntaxString() + " in mibfile" + ",snmpValue:"
								+ snmpValue + ",Syntax:" + snmpValue.getSyntax());
					}
				}
				// log.debug(ad.name+" : " + v.toString());
				res.put(ad.getName(), v);
			}

			res.put(SnmpEumDef.mibInstance, instanceID);
		}

		return res;
	}

	public List<Map<String, Object>> getGetResponseMap(VariableBinding[] vbs, AttributeDescriptor attribute) {

		String instanceID = null;
		List<Map<String, Object>> tableDatas = new ArrayList<Map<String, Object>>();
		for (int i = 0; i < vbs.length; i++) {
			Map<String, Object> res = new HashMap<String, Object>();
			VariableBinding vb = vbs[i];
			Variable snmpValue = vb.getVariable();
			String oidName = vb.getOid().toString();
			if (oidName != null)
				if (oidName.endsWith(".0") && !attribute.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Scalar))
					// instanceID = oidName.substring(ad.externalName.length()+0
					// ) ;
					instanceID = "0";
				else
					instanceID = oidName.substring(attribute.getExternalName().toString().length() + 1);
			Object v = null;
			if (snmpValue != null) {
				if (!attribute.getExternalType().equalsIgnoreCase("") && attribute.getExternalType() != null) {
					try {
						Object Value = SnmpDataConvert.convertFromSNMPType(snmpValue);
						v = SnmpDataConvert.covertToDBTypeFromMibInstance(Value, attribute);
					} catch (NumberFormatException e) {
						log.error("when covert " + attribute.getExternalName() + "'s value to dbtype occur wrong!!");
						log.error("the ExternalType of " + attribute.getExternalName() + " is " + attribute.getExternalType() + " in configfile");
						log.error("the ExternalType of " + attribute.getExternalName() + " is " + attribute.getExternalType() + " in configfile");
						log.error("the ExternalType of " + attribute.getExternalName() + " is " + snmpValue.getSyntaxString() + " in mibfile");
					}
				}
				// log.debug(ad.name+" : " + v.toString());
				res.put(attribute.getName(), v);
			}
			res.put(SnmpEumDef.mibInstance, instanceID);
			tableDatas.add(res);
		}

		// log.debug("result : " + res.size());
		return tableDatas;

	}

	public VariableBinding[] getNext(OID startingOID) {
		// creating PDU
		PDU pdu = new PDU();
		pdu.add(new VariableBinding(startingOID));
		return snmpSession(pdu, target, PDU.GETNEXT);
	}

	/**
	 * a centrialized getNext operation wrapper max pdu size will be checked and
	 * enforced here later on
	 */
	protected VariableBinding[][] getNext(List<OID> oids) {
		PDU pdu = null;
		if (snmpVersion == SnmpConstants.version3) {
			pdu = new ScopedPDU();
		} else {
			pdu = new PDU();
			((CommunityTarget) target).setCommunity(readCommunity);
		}
		for (OID oid : oids) {
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);
		}
		VariableBinding[][] vbss = new VariableBinding[1][];
		vbss[0] = snmpSession(pdu, target, PDU.GETNEXT);
		return vbss;
	}

	protected VariableBinding[][] getBulk(List<OID> oids) {
		List<VariableBinding[]> vbList = new LinkedList<VariableBinding[]>();
		for (OID oid : oids) {
			PDU pdu = null;
			if (snmpVersion == SnmpConstants.version3) {
				pdu = new ScopedPDU();
			} else {
				pdu = new PDU();
				((CommunityTarget) target).setCommunity(readCommunity);
			}
			pdu.setMaxRepetitions(50);// 每次返回条数
			pdu.setNonRepeaters(0);// 标量个数
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);
			VariableBinding[] vbs = snmpSession(pdu, target, PDU.GETBULK);
			oid.removeLast();
			if (vbs != null && vbs.length > 0) {
				vbList.add(vbs);
			}
		}

		return vbList.toArray(new VariableBinding[vbList.size()][]);
	}

	public VariableBinding[][] get(List<OID> oids) {
		PDU pdu = null;
		if (snmpVersion != SnmpConstants.version3) {
			pdu = new PDU();
		} else {
			pdu = new ScopedPDU();
		}

		for (OID oid : oids) {
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);
		}

		if (snmpVersion != SnmpConstants.version3) {
			((CommunityTarget) target).setCommunity(readCommunity);
		}
		VariableBinding[][] vbss = new VariableBinding[1][];
		vbss[0] = snmpSession(pdu, target, PDU.GET);
		return vbss;
	}

	public VariableBinding[] get(PDU pdu) {
		if (snmpVersion != SnmpConstants.version3) {
			((CommunityTarget) target).setCommunity(readCommunity);
		}
		return snmpSession(pdu, target, PDU.GET);
	}

	public VariableBinding get(OID oid) {
		List<OID> oids = new ArrayList<OID>();
		oids.add(oid);
		VariableBinding[][] vbs = get(oids);
		return vbs[0][0];
	}

	public String getFactoryId() {
		VariableBinding var = get(new OID("1.3.6.1.2.1.1.2.0"));
//		Variable snmpValue = var.getVariable();
		int syntax = var.getSyntax();
		if (syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE || syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW) {
			return "";
		}
//		String[] factoryStrings = snmpValue.toString().split("\\.");
		// FactoryID = factoryStrings[6];
		return FactoryID;
	}
	
	public String getPollFactoryId(){
		List<OID> oids = new ArrayList<OID>();
		oids.add(new OID("1.3.6.1.2.1.1.2.0"));
		PDU pdu = null;
		if (snmpVersion != SnmpConstants.version3) {
			pdu = new PDU();
		} else {
			pdu = new ScopedPDU();
		}

		for (OID oid : oids) {
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);
		}

		if (snmpVersion != SnmpConstants.version3) {
			((CommunityTarget) target).setCommunity(readCommunity);
		}
		snmpPollSession(pdu, target, PDU.GET);
		return FactoryID;
		
	}
	
	private void snmpPollSession(final PDU pdu, final Target target, int snmpType) {
		try {
			final int pollFrameOnOff = ToolUtil.getEntryConfig().getPollFrameRecordOnOff();
			pdu.setType(snmpType);
			if (pollFrameOnOff == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "PollCommand", target.toString() + "\n\n" + sb.toString());
			}
			PDU respPdu = null;
			if (snmp == null) {
				initSnmp();
			}
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				log.info("snmpSession response");
				respPdu = response.getResponse();
				checkResponsePdu(respPdu);
				if (respPdu == null) {
					log.error("commit out time");
				} else {
					if (pollFrameOnOff == 1) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "PollResponse",
								target.toString() + "\n\n" + sb.toString());
					}
				}
			} else {
				checkResponsePdu(respPdu);
			}
		} catch (Exception e) {
			this.close();
			throw new SnmpDataException("snmpSession error", e);
		} finally{
			this.close();
		}
	}

	public String getNeType() {

		VariableBinding var = get(new OID("1.3.6.1.2.1.1.2.0"));

		int syntax = var.getSyntax();
		if (syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE || syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW) {
			VariableBinding[] vbs = getNext(new OID("1.3.6.1.4.1.9966"));
			var = vbs[0];
			syntax = var.getSyntax();
			if (syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE || syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW) {
				return "";
			} else {
				return var.toValueString();
			}
		}

		Variable snmpValue = var.getVariable();
		String[] objIds = snmpValue.toString().split("\\.");
		if (objIds.length == 7 && objIds[6].equals("9966")) {
			return "9966";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 7; i < objIds.length; i++) {
			sb.append(objIds[i]);
		}
		return sb.toString();
	}
	
	public String getObjId(){
		VariableBinding var = get(new OID("1.3.6.1.2.1.1.2.0"));

		if(var == null){
			return null;
		}
		int syntax = var.getSyntax();
		if (syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE || syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW) {
			return null;
		}

		Variable snmpValue = var.getVariable();
		String[] objIds = snmpValue.toString().split("\\.");
		if (objIds.length < 8) {
			return null;
		}
		
		/*
		 * 例如：1.3.6.1.4.1.9966.2.18.1 返回18.1
		 */
		int startIndex = 6;
		if ("9966".equals(objIds[startIndex])) {
			startIndex = 8;
		}
		
		StringBuilder sb = new StringBuilder();
		for (int i = startIndex; i < objIds.length; i++) {
			sb.append(objIds[i]);
			sb.append(".");
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}
	
	public Map<String, String> getIpRanMACs() {
		List<OID> oids = new ArrayList<OID>();
		oids.add(new OID("1.3.6.1.4.1.3902.3.103.2.1.2.3.1.21"));
		VariableBinding[][] vars = getBulk(oids);
		Map<Integer,String> macs = new HashMap<Integer, String>();
		for (VariableBinding[] var : vars) {
			for (VariableBinding v : var) {
				String mac = v.toValueString();
				if (mac.matches("([0-9,a-f]{2}:){5}[0-9,a-f]{2}")) {
					macs.put(v.getOid().last(), mac);
				}
			}
		}
		oids.clear();
		oids.add(new OID("1.3.6.1.4.1.3902.3.103.2.1.2.3.1.2"));
		Map<Integer, String> ports = new HashMap<Integer, String>();
		VariableBinding[][] varsEx = getBulk(oids);
		for (VariableBinding[] var : varsEx) {
			for (VariableBinding v : var) {
				String port = v.toValueString();
				if (port.contains("gei")) {
					ports.put(v.getOid().last(), port);
				}
			}
		}
		Map<String, String> res = new HashMap<String, String>();
		for (int index : ports.keySet()) {
			res.put(ports.get(index), macs.get(index));
		}
		return res;
	}

	public PDU ackNeDiscoveryTrap() {
		PDU rePdu = null;
		OID oid = new OID("1.3.6.1.4.1.9966.1.12");
		for (int i = 0; i < 3; i++) {
			try {
				// String oidStr = getOID("dc","neDiscoverAck");
				// oid = new OID(oidStr);
				PDU pdu = new PDU();
				if (writeCommunity == null) {
					writeCommunity = new OctetString("private");
				}
				((CommunityTarget) target).setCommunity(writeCommunity);

				log.info("ackNeDiscoveryTrap:" + ((CommunityTarget) target).getAddress());

				VariableBinding vb = new VariableBinding();
				vb.setOid(oid);
				Variable v = new Integer32(1);
				vb.setVariable(v);
				pdu.add(vb);
				rePdu = snmpSetSession(pdu, target, PDU.SET);
				if (rePdu==null || (rePdu!=null&&rePdu.getErrorStatus() != 0)) {
					oid = new OID("1.3.6.1.4.1.9966.1.12.0");
					vb.setOid(oid);
					rePdu = snmpSetSession(pdu, target, PDU.SET);
				}
				return rePdu;
			} catch (Exception e) {
				oid = new OID("1.3.6.1.4.1.9966.1.12.0");
				log.error(e, e);
			}
		}
		return null;
	}

	private void snmpSet(PDU request, FunctionDescriptor fucview, List<AttributeDescriptor> setAttrDataArr) {
		PDU response = snmpSetSession(request, target, PDU.SET);
		checkResponsePduForNullOrErr(response, fucview, setAttrDataArr);
		@SuppressWarnings("unchecked")
		Vector<? extends VariableBinding> vec = response.getVariableBindings();
		int i = 0;

		VariableBinding[] varbs = new VariableBinding[vec.size()];
		for (VariableBinding ad : vec) {
			varbs[i++] = ad;
		}
		Map<String, Object> result = getGetResponseMap(varbs, setAttrDataArr);
		System.out.println(result);
	}

	private void checkResponsePdu(PDU pdu) {
		if (pdu == null) {
			// agent not reachable
			this.close();
			String neID = target.getAddress().toString();
			// neCollection.agentNotResponding(neID);
			throw new SnmpDataException(SnmpDataException.NO_RESPONSE, 10182, neID + ": " + "Node did not respond to request"
					+ " within the timeout period of:" + +this.target.getTimeout() / 500 + " secs.");
		}
		int errorStatus = pdu.getErrorStatus();
		int errorIndex = pdu.getErrorIndex();
		if (errorStatus != PDU.noError) {
			// agent reports snmp error
			String erroredAttr = null;
			if (errorIndex > 0) {
				erroredAttr = pdu.get(errorIndex - 1).getOid().toString();
			}
			// use specific too big error code
			throw new SnmpDataException(SnmpDataException.AGT_ERROR, errorStatus, new StringBuffer("Protocol Error: error status = ")
					.append(pdu.getErrorStatusText()).append(", errored attribute = ").append(erroredAttr).toString());
		}
	}

	private void checkResponsePduForNullOrErr(PDU pdu, FunctionDescriptor cd, List<AttributeDescriptor> adArr) {
		if (pdu == null) {
			this.close();
			// agent not reachable
			String neID = target.getAddress().toString();
			// neCollection.agentNotResponding(neID);
			// comment end
			throw new SnmpDataException(SnmpDataException.NO_RESPONSE, 10182, neID + ": Node did not respond to request" + " within the timeout period of "
					+ +target.getTimeout() / 500 + " secs.");
		}
		int errorStatus = pdu.getErrorStatus();
		if (errorStatus != PDU.noError) {
			// agent reports snmp error
			StringBuffer erroredAttr = null;
			int errorIndex = pdu.getErrorIndex();
			if (errorIndex > 0) {
				AttributeDescriptor ad = (AttributeDescriptor) adArr.get(errorIndex - 1);
				String errAttrOid = pdu.get(errorIndex - 1).getOid().toString();
				String extIdx = null;
				if (errAttrOid.length() > ad.getExternalName().length() + 1)
					extIdx = errAttrOid.substring(ad.getExternalName().length() + 1);
				erroredAttr = new StringBuffer(ad.getName()).append(" (Managed Object Class = ").append(cd.getName());
				if (extIdx != null)
					erroredAttr.append(" ext index = ").append(extIdx);
				erroredAttr.append(")");
			}
			throw new SnmpDataException(SnmpDataException.AGT_ERROR, errorStatus, new StringBuffer(cd.getName() + ":Protocol Error: error status = ")
					.append(pdu.getErrorStatusText()).append(", errored attribute = ").append(erroredAttr).toString());
		}
	}

	private PDU snmpSetSession(PDU pdu, Target target, int snmpType) {
		try {
			PDU respPdu = new PDU();
			pdu.setType(snmpType);
			if (snmp == null) {
				initSnmp();
			}

			writeFrameRecord(pdu,"FrameInfo", true);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			ResponseEvent response = snmp.send(pdu, target);
			
			if (response != null) {
				log.info("snmpSession response");
				respPdu = response.getResponse();
				writeFrameRecord(respPdu,"FrameInfo", false);
			}
			log.info("snmpSetSession return");
			return respPdu;
		} catch (Exception e) {
			this.close();
			log.error(e,e);
			throw new SnmpDataException("snmpSetSession error", e);
		} finally{
			this.close();
		}
	}

	private VariableBinding[] snmpSession(PDU pdu, Target target, int snmpType) {
		try {
			PDU respPdu = null;
			VariableBinding[] vbs = null;
			pdu.setType(snmpType);
			if (snmp == null) {
				initSnmp();
			}
			writeFrameRecord(pdu,"FrameInfo", true);
			// try{
			// Thread.sleep(1000);
			// }catch(Exception e){
			// log.error(e, e);
			// }
			ResponseEvent response = snmp.send(pdu, target);
			
			if (response != null) {
				log.info("snmpSession response");
				respPdu = response.getResponse();
				checkResponsePdu(respPdu);
				if (respPdu == null) {
					log.error("commit out time");
				} else {
					vbs = respPdu.toArray();
					writeFrameRecord(respPdu,"FrameInfo", false);

				}
			}
			log.info("snmpSession return");
			return vbs;
		} catch (Exception e) {
			this.close();
			log.error(e.getMessage());
			throw new SnmpDataException("snmpSession error", e);
		} finally{
			this.close();
		}

	}

	private boolean isContainInstanceID(List<AttributeDescriptor> list) {
		for (AttributeDescriptor attr : list) {
			if (attr.getName().equalsIgnoreCase(SnmpEumDef.mibInstance)) {
				return true;
			}
		}
		return false;
	}

	private void initSnmp() throws IOException {
		log.info(port);

		TransportMapping transport = null;
		if(port > 0){
			try{
				transport = new DefaultUdpTransportMapping(new UdpAddress(port), false); 
			}catch(Exception ex){
				log.error(port, ex);
				transport = null;
			}
		}
		if(transport == null){
			transport = new DefaultUdpTransportMapping();
		}
		snmp = new Snmp(transport);
		transport.listen();

		if (snmpVersion == SnmpConstants.version3) {
			USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
			SecurityModels.getInstance().addSecurityModel(usm);
			UsmUser user = new UsmUser(new OctetString(userName), authProtocol, authPassword, privProtocol, privPassword);
			snmp.getUSM().addUser(new OctetString(userName), user);
		}

	}

	public void setAppContext(ApplicationContext context) {
		dbDataAccess = (DataAccess) context.getBean("dbDataAccess");
	}

	/**
	 * @param arg0
	 *            FuncationDescriptor name
	 * @param arg1
	 *            include the value of the row to set and must include the
	 *            instance to add. Notice: only one map to set,and the key
	 *            should be AttributeDescriptor name.
	 */

	public boolean addDatas(FunctionDescriptor fd, List<Map<String, Object>> arg1) {
		List<AttributeDescriptor> attrList = fd.getAttributeList();
		String rowstatus = "";
		List<Object> indexValueList = new ArrayList<Object>();
		Map<String, Object> map = arg1.get(0);
		Map<String, Object> rowstatusmap = new HashMap<String, Object>();
		for (AttributeDescriptor attr : attrList) {
			if (attr.getExternalName() != null && attr.getExternalPropMask() != null) {
				if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatus)
						|| attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForAddAndUpdate)
						|| attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForUpdateThenCreate)) {
					rowstatus = attr.getName();
					if (attr.getDefaultValue() != null) {
						map.put(rowstatus, attr.getDefaultValue());
					}
				}
				if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.EntryStatus)) {
					rowstatus = attr.getName();
					if (attr.getDefaultValue() != null) {
						map.put(rowstatus, attr.getDefaultValue());
					} else {
						map.put(rowstatus, 2);
					}
				}
				if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)) {
					indexValueList.add(map.get(attr.getName()));
					// map.remove(attr.getName());//ifIndex not to set or get
				}
			} else {
				map.remove(attr.getName());
			}
		}
		if (fd.getName().equalsIgnoreCase("EthVlanConf")) {
			rowstatusmap.put(rowstatus, 4);// when rowstatus is 4 means to add
			update(fd, rowstatusmap, indexValueList);
			update(fd, map, indexValueList);
		} else {
			if (!rowstatus.isEmpty()) {
				Integer rs = (Integer) map.get(rowstatus);
				if (rs != null && !rs.equals(0)) {
					System.out.println("######### rowstatus :" + Integer.valueOf(rs));
					map.put(rowstatus, rs);
				} else {
					map.put(rowstatus, 4);// when rowstatus is 4 means to add
				}
			} else {
				log.error("find on rowstatus in table " + fd.getDisplayName() + " !!");
			}
			if (indexValueList.isEmpty()) {
				log.error("don't give the index of " + fd.getDisplayName() + " to add");
			}
			update(fd, map, indexValueList);
		}
		// if(arg0.equalsIgnoreCase("MtsEvc")){
		// List<Map<String, Object>> results = getDatas(arg0);
		// for(Map<String, Object> result:results){
		// this.dbDataAccess.updateDatas(arg0, result, arg1);
		// }
		// }

		return true;
	}

	/**
	 * @param arg0
	 *            FuncationDescriptor name
	 * @param arg1
	 *            must include the instance to delete Notice: only one map to
	 *            set,and the key should be AttributeDescriptor name.
	 */
	public boolean deleteDatas(FunctionDescriptor fd, List<Map<String, Object>> arg1) {

		List<AttributeDescriptor> attrList = fd.getAttributeList();
		String rowstatus = "";
		List<Object> indexValueList = new ArrayList<Object>();
		Map<String, Object> map = new HashMap<String, Object>();
		for (AttributeDescriptor attr : attrList) {
			if (attr.getExternalName() != null && attr.getExternalPropMask() != null) {
				if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatus)
						|| attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForUpdateThenCreate)
						|| attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatusForAddAndUpdate)) {
					rowstatus = attr.getName();
				}
				if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.EntryStatus)) {
					rowstatus = attr.getName();
					map.put(rowstatus, 4);
				}
				if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)) {
					for (Map<String, Object> m : arg1) {
						if (isContainInstanceID(attrList)) {
							List<Object> results = new ArrayList<Object>();
							results.add(SnmpEumDef.mibInstance);
							@SuppressWarnings("static-access")
							List<Map<String, Object>> result = this.dbDataAccess.getDatas(fd, null, arg1);
							Object instance = result.get(0).get(SnmpEumDef.mibInstance);
							if (null == instance) {
								instance = result.get(0).get(attr.getExternalName());
							}
							indexValueList.add(instance);
						} else {
							indexValueList.add(m.get(attr.getName()));
						}
					}
				}
			} else {
				map.remove(attr.getName());
			}
		}

		if (indexValueList.isEmpty()) {
			log.error("don't give the index of " + fd.getDisplayName() + " to delete");
		}
		if (!rowstatus.isEmpty()) {
			Integer rs = (Integer) map.get(rowstatus);
			if (rs != null) {
				map.put(rowstatus, rs);
			} else {
				map.put(rowstatus, 6);// when rowstatus is 6 means to delete
			}
		} else {
			log.error("find on rowstatus in table " + fd.getDisplayName() + " !!");
		}
		// map.put(rowstatus,6);//when rowstatus is 6 means to delete
		update(fd, map, indexValueList);
		return true;
	}

	/**
	 * @param arg0
	 *            FuncationDescriptor name
	 * @param arg1
	 *            must include the instance to modify Notice: only one map to
	 *            set,and the key should be AttributeDescriptor name.
	 */
	public boolean updateDatas(FunctionDescriptor fd, Map<String, Object> arg1, List<Map<String, Object>> arg2) {
		
		Map<String, Object> value = new HashMap<String, Object>();
		value.putAll(arg1);
		List<AttributeDescriptor> attrList = new ArrayList<AttributeDescriptor>();
		attrList.addAll(fd.getAttributeList());
		List<Object> indexValueList = new ArrayList<Object>();

		Map<String, Object> indexValuemap = null;
		if (arg2 != null && !arg2.isEmpty()) {
			indexValuemap = arg2.get(0);
		} else {
			indexValuemap = value;
		}
		for (AttributeDescriptor attr : attrList) {
			if (attr.getExternalName() != null && attr.getExternalPropMask() != null && attr.isDownload()) {
				if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.RowStatus)) {
				} else if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.EntryStatus)) {
				} else if (attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)) {
					Object indexValue = indexValuemap.get(attr.getName());
					if (indexValue == null) {
						indexValue = value.get(attr.getName());
						value.remove(attr.getName());
					}
					if (indexValue != null) {
						if (isContainInstanceID(attrList)) {
							List<String> results = new ArrayList<String>();
							List<Map<String, Object>> con = new ArrayList<Map<String, Object>>();
							Map<String, Object> temp = new HashMap<String, Object>();
							temp.put("id", indexValuemap.get("id"));
							con.add(temp);
							results.add(SnmpEumDef.mibInstance);
							@SuppressWarnings("static-access")
							List<Map<String, Object>> result = this.dbDataAccess.getDatas(fd, results, con);
							indexValueList.add(result.get(0).get(SnmpEumDef.mibInstance));
						} else {
							indexValueList.add(indexValue);
						}
					}
				} else if (!attr.isDownload()) {
					value.remove(attr.getName());
				}
			} else {
				value.remove(attr.getName());
			}
		}
		if (indexValueList.isEmpty()) {
			log.error("don't give the index of " + fd.getDisplayName() + " to modify");
		}
		if(value == null || value.isEmpty()){
			log.info("##### value is Empty! VBS[]!");
			return true;
		}
		update(fd, value, indexValueList);
		return true;
	}

	/**
	 * @param arg0
	 *            FuncationDescriptor name
	 */
	public List<Map<String, Object>> getDatas(FunctionDescriptor fd) {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();

		List<AttributeDescriptor> attrList = new ArrayList<AttributeDescriptor>();
		List<AttributeDescriptor> al = fd.getAttributeList();
		for (AttributeDescriptor attr : al) {
			if (attr.getExternalName() != null && attr.getExternalPropMask() != null && attr.isUpload()) {
				attrList.add(attr);
			}
		}
		if (!attrList.isEmpty()) {
			result = getDatas(attrList, fd.getDomain());
		}
		return result;
	}

	// public List<Map<String, Object>> getDatasByBulk(FunctionDescriptor fd) {
	// List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
	//
	// List<AttributeDescriptor> attrList = new
	// ArrayList<AttributeDescriptor>();
	// List<AttributeDescriptor> al = fd.getAttributeList();
	// for (AttributeDescriptor attr : al) {
	// if (attr.getExternalName() != null && attr.getExternalPropMask() != null
	// && attr.isUpload()) {
	// attrList.add(attr);
	// }
	// }
	// if (!attrList.isEmpty()) {
	// result = getDatas(attrList, fd.getDomain(), true);
	// }
	// return result;
	// }

	/**
	 * @param arg0
	 *            FuncationDescriptor name
	 * @param arg1
	 *            the
	 */

	public List<Map<String, Object>> getDatas(FunctionDescriptor fd, List<String> arg1) {
		if (arg1 == null) {
			return this.getDatas(fd);
		}
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		List<AttributeDescriptor> attrs = new ArrayList<AttributeDescriptor>();
		for (String name : arg1) {
			AttributeDescriptor attr = fd.getAttribute(name);
			if (attr.getExternalName() != null && attr.getExternalPropMask() != null && attr.isUpload()) {
				attrs.add(attr);
			}
		}
		if (!attrs.isEmpty()) {
			result = getDatas(attrs, fd.getDomain());
		}
		return result;
	}

	// public List<Map<String, Object>> getDatasByBulk(FunctionDescriptor fd,
	// List<String> fields) {
	// if (arg1 == null) {
	// return this.getDatas(fd);
	// }
	// List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
	// List<AttributeDescriptor> attrs = new ArrayList<AttributeDescriptor>();
	// for (String name : arg1) {
	// AttributeDescriptor attr = fd.getAttribute(name);
	// if (attr.getExternalName() != null && attr.getExternalPropMask() != null
	// && attr.isUpload()) {
	// attrs.add(attr);
	// }
	// }
	// if (!attrs.isEmpty()) {
	// result = getDatas(attrs, fd.getDomain(), true);
	// }
	// return result;
	// }

	public List<Map<String, Object>> getDatas(FunctionDescriptor fd, List<String> fields, List<Map<String, Object>> conditions) {
		List<Map<String, Object>> result = null;

		List<String> instanceNames = new ArrayList<String>();
		List<AttributeDescriptor> attrList = fd.getAttributeList();
		List<AttributeDescriptor> attrs = new ArrayList<AttributeDescriptor>();
		if (fields != null) {
			for (String name : fields) {
				AttributeDescriptor attr = fd.getAttribute(name);
				if (attr == null) {
					log.error("功能属性无法识别：" + name);
					continue;
				}
				if (attr.getExternalName() != null && attr.getExternalPropMask() != null && attr.isUpload()) {
					attrs.add(attr);
				}
			}
		} else {
			for (AttributeDescriptor attr : attrList) {
				if (attr.getExternalName() != null && attr.getExternalPropMask() != null && attr.isUpload()) {
					attrs.add(attr);
				}
			}
		}

		for (AttributeDescriptor attr : attrList) {
			if (attr.getExternalName() != null && attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)) {
				instanceNames.add(attr.getName());
				if (fields != null && !fields.contains(attr.getName())) {
					attrs.add(attr);
				}
			}
		}

		List<List<Object>> instanceIDs = new ArrayList<List<Object>>();
		if (conditions != null) {
			for (Map<String, Object> c : conditions) {
				List<Object> instances = new ArrayList<Object>();
				for (String insName : instanceNames) {
					Object v = c.get(insName);
					if (v != null) {
						instances.add(String.valueOf(v));
					}

				}
				instanceIDs.add(instances);
			}

		}

		if (!attrs.isEmpty() && instanceIDs.isEmpty()) {
			result = getDatas(attrs, fd.getDomain());
		} else {
			result = getDatasByCondition(attrs, fd.getDomain(), instanceIDs);
		}
		return result;
	}

	public List<Map<String, Object>> getDatasByBulk(FunctionDescriptor fd, List<String> fields, List<Map<String, Object>> conditions) {
		List<Map<String, Object>> result = null;
		List<String> instanceNames = new ArrayList<String>();
		List<AttributeDescriptor> attrList = fd.getAttributeList();
		List<AttributeDescriptor> attrs = new ArrayList<AttributeDescriptor>();
		if (fields != null) {
			for (String name : fields) {
				AttributeDescriptor attr = fd.getAttribute(name);
				if (attr == null) {
					log.error("功能属性无法识别：" + name);
					continue;
				}
				if (attr.getExternalName() != null && attr.getExternalPropMask() != null && attr.isUpload()) {
					attrs.add(attr);
				}
			}
		} else {
			for (AttributeDescriptor attr : attrList) {
				if (attr.getExternalName() != null && attr.getExternalPropMask() != null && attr.isUpload()) {
					attrs.add(attr);
				}
			}
		}

		for (AttributeDescriptor attr : attrList) {
			if (attr.getExternalName() != null && attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)) {
				instanceNames.add(attr.getName());
			}
		}

		List<Object> instanceIDs = new ArrayList<Object>();
		if (conditions != null) {
			for (Map<String, Object> c : conditions) {
				for (String insName : instanceNames) {
					Object v = c.get(insName);
					if (v != null) {
						instanceIDs.add(v);
					}

				}
			}

		}

		if (!attrs.isEmpty()) {
			result = getDatas(attrs, fd.getDomain(), instanceIDs);
		}
		return result;
	}

	protected int getWriteFrameInterval() {
		return ToolUtil.getEntryConfig().getWriteFrameInterval();
	}

	// protected int getSectionInterval(){
	// if(sectionInterval != null && sectionInterval > 0){
	// return sectionInterval;
	// }
	// String interval = ToolUtil.getProperty("SECTION_INTERNAL");
	// if(interval != null){
	// sectionInterval = Integer.valueOf(interval);
	// if(sectionInterval != null){
	// return sectionInterval;
	// }
	// }
	// return 0;
	// }

	public static String getOID(String externalName) {
		return ManFactoryMibLoader.getInstace().getOIDbyName(null, externalName);
	}

	public static String getOID(String neType, String externalName) {
		return ManFactoryMibLoader.getInstace().getOIDbyName(neType, externalName);
	}

	public boolean updateFirmware(String neType, boolean soft, boolean hard) {
		try {
			// String oidStr = facMibLoaderInstace.getOIDbyName(FactoryID,
			// neType, attr.getExternalName());
			// if(oidStr == null){
			// oidStr = facMibLoaderInstace.getOIDbyName(FactoryID,
			// cd.getDomain().toLowerCase(), attr.getExternalName());
			// }
			// if(oidStr == null){
			// oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, null,
			// attr.getExternalName());
			// }
			// OID atrroid = new OID(oidStr);//get oid from externalName

			String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName(neType, "updateMode");
			if (StringUtils.isEmpty(oidStr)) {
				oidStr = "1.3.6.1.4.1.9966.2.100.4.1.1.6.1";
			}
			OID oid = new OID(oidStr);
			oid.append(0);

			Variable v = null;
			if (neType.equals("NE504") || neType.equals("NE503") || neType.equals("NE502")) {
				if (soft && hard) {
					v = new OctetString(new byte[] { (byte) 0x03 });
				} else if (soft) {
					v = new OctetString(new byte[] { (byte) 0x01 });
				} else if (hard) {
					v = new OctetString(new byte[] { (byte) 0x02 });
				}
			} else if (neType.equals("NE102")) {
				if (soft) {
					v = new OctetString(new byte[] { (byte) 0x80 });
				} else if (hard) {
					v = new OctetString(new byte[] { (byte) 0x40 });
				}
			}

			boolean ok = doUpdateFirmware(oid, v, soft, hard);
			if (!ok) {
				oid = new OID("1.3.6.1.4.1.9966.2.100.8.1.1.4.4.0");
				ok = doUpdateFirmware(oid, v, soft, hard);
			}
			close();
			return ok;
		} catch (Exception e) {
			close();
			log.error(e, e);
		}
		close();
		return false;
	}

	private boolean doUpdateFirmware(OID oid, Variable v, boolean soft, boolean hard) {
		try {
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);

			vb.setVariable(v);
			PDU pdu = new PDU();
			pdu.setType(PDU.SET);
			pdu.add(vb);
			if (snmp == null) {
				initSnmp();
			}

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			ResponseEvent response = snmp.send(pdu, target);
			PDU respPdu = response.getResponse();
			if (frameOn == 1) {
				if (respPdu != null) {
					String[] ss = respPdu.toString().split(";");
					StringBuilder sb = new StringBuilder();
					for (String s : ss) {
						sb.append(s);
						sb.append("\n");
					}
					FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
				}
			}
			close();
			return 0 == respPdu.getErrorStatus();
		} catch (Exception e) {
			close();
			return false;
		}
	}
	
	/**
	 * 查询安装进度
	 * @param neType
	 * @return
	 */
	public int getFTPInstalProgress(String neType) {
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}

			String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName(neType, "updateProgress");
			if (StringUtils.isEmpty(oidStr)) {
				if(neType.equals("NE504") || neType.equals("NE503") || neType.equals("NE502")){
					oidStr = ".1.3.6.1.4.1.9966.2.100.4.1.1.6.3";
				}else if(neType.equals("NE102")){
					oidStr = ".1.3.6.1.4.1.9966.2.100.8.1.1.4.6";
				}
			}
			OID oid = new OID(oidStr);
			oid.append(0);

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(readCommunity);
			}

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
					}
				}
				close();
				return new Integer(((Integer32) respPdu.get(0).getVariable()).getValue());
			}
		} catch (Exception ex) {
//			log.error(ex, ex);
			close();
			return -1;
		}
		close();
		return -1;
	}
	
	/**
	 * 查询安装状态
	 * @param neType
	 * @return
	 */
	public int getFTPInstalState(String neType) {
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}
			
			String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName(neType, "updateState");
			if (StringUtils.isEmpty(oidStr)) {
				if(neType.equals("NE504") || neType.equals("NE503") || neType.equals("NE502")){
					oidStr = ".1.3.6.1.4.1.9966.2.100.4.1.1.6.2";
				}else if(neType.equals("NE102")){
					oidStr = ".1.3.6.1.4.1.9966.2.100.8.1.1.4.5";
				}
			}
			OID oid = new OID(oidStr);
			oid.append(0);

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(readCommunity);
			}

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
					}
				}
				close();
				return new Integer(((Integer32) respPdu.get(0).getVariable()).getValue());

			}
		} catch (Exception ex) {
			// log.error(ex, ex);
			close();
			return -1;
		}
		close();
		return -1;
	}

	// 重启edd设备板卡
	public boolean setEstFileRestart(String neType) {
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.SET);
			if (snmp == null) {
				initSnmp();
			}
			OID oid = null;
			if (neType.toLowerCase().equals("ce")) {
				// oid = new
				// OID(ManFactoryMibLoader.getInstace().getOIDbyName("ce",
				// "estSysRestart"));
				// if(oid == null){
				// oid = new OID("1.3.6.1.4.1.9966.5.25.19.1.2.27.0");
				// }else{
				// oid.append(0);
				// }
			} else {
				oid = new OID(ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estSysRestart"));
				{
					oid.append(0);
				}
			}

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			Variable v = new Integer32(1);
			vb.setVariable(v);
			pdu.add(vb);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
						close();
						return respPdu.getErrorStatus() == 0;
					}
				}
			}
		} catch (Exception ex) {
			close();
			log.error(ex, ex);
		}
		close();
		return false;
	}

	// 查询edd设备传送状态、升级状态
	public int getEstFileState(String neType) {
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}
			OID oid = null;
			if (neType.toLowerCase().equals("ce")) {

			} else {
				String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estFileState");
				if (oidStr == null) {
					oid = new OID("1.3.6.1.4.1.9966.5.25.19.1.5.1.0");
				} else {
					oid = new OID(oidStr);
					oid.append(0);
				}

			}

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(readCommunity);
			}

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
					}
				}
				close();
				return new Integer(((Integer32) respPdu.get(0).getVariable()).getValue());

			}
		} catch (Exception ex) {
			// log.error(ex, ex);
			close();
			return -1;
		}
		close();
		return -1;
	}

	// tftp方式向edd设备传升级文件
	public int setEstFileMgmt(String neType, int estFileCmd, String ipAddress, int tftp, String user, String password, String estFileName) {
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.SET);
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			if (snmp == null) {
				initSnmp();
			}
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			
			OID oid = getOIDByName("gpn", "estFileProtocol", "1.3.6.1.4.1.9966.5.25.19.1.5.8.0");
			Variable v = new Integer32(tftp);
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			ResponseEvent response = snmp.send(pdu, target);// estFileProtocol
			PDU respPdu = response.getResponse();
			if (frameOn == 1) {
				if (respPdu != null) {
					String[] ss = respPdu.toString().split(";");
					StringBuilder sb = new StringBuilder();
					for (String s : ss) {
						sb.append(s);
						sb.append("\n");
					}
					FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
				}
			}
			Thread.sleep(100);

			oid = getOIDByName("gpn", "estServerAddress", "1.3.6.1.4.1.9966.5.25.19.1.5.6.0");

			pdu = new PDU();
			pdu.setType(PDU.SET);
			v = new IpAddress(ipAddress);
			// v = new OctetString(ipAddress);
			vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			response = snmp.send(pdu, target);// estServerAddress

			respPdu = response.getResponse();
			if (frameOn == 1) {
				if (respPdu != null) {
					String[] ss = respPdu.toString().split(";");
					StringBuilder sb = new StringBuilder();
					for (String s : ss) {
						sb.append(s);
						sb.append("\n");
					}
					FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
				}
			}
			Thread.sleep(100);

			pdu = new PDU();
			pdu.setType(PDU.SET);
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			
			oid = getOIDByName("gpn", "estFileName", "1.3.6.1.4.1.9966.5.25.19.1.5.2.0");
			v = new OctetString(estFileName);
			vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			response = snmp.send(pdu, target);// estFileName

			respPdu = response.getResponse();
			if (frameOn == 1) {
				if (respPdu != null) {
					String[] ss = respPdu.toString().split(";");
					StringBuilder sb = new StringBuilder();
					for (String s : ss) {
						sb.append(s);
						sb.append("\n");
					}
					FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
				}
			}
			Thread.sleep(100);

			if (neType.toLowerCase().equals("ce")) {

			} else {
				oid = getOIDByName("gpn", "estFileCmd", "1.3.6.1.4.1.9966.5.25.19.1.5.1.0");
			}

			pdu = new PDU();
			pdu.setType(PDU.SET);
			v = new Integer32(estFileCmd);
			vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			response = snmp.send(pdu, target);// estFileCmd
			respPdu = response.getResponse();
			if (frameOn == 1) {
				if (respPdu != null) {
					String[] ss = respPdu.toString().split(";");
					StringBuilder sb = new StringBuilder();
					for (String s : ss) {
						sb.append(s);
						sb.append("\n");
					}
					FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
				}
			}
			close();
			return respPdu.getErrorStatus();
		} catch (Exception ex) {
			// log.error(ex, ex);
			close();
			return -1;
		}
	}

	private OID getOIDByName(String domain, String attrName, String oid) {
		String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName(domain, attrName);
		if (oidStr == null) {
			return new OID(oid);
		} else {
			OID o = new OID(oidStr); 
			o.append(0);
			return o;
		}
	}

	// 根据文件名安装edd设备升级文件
	public int setEddDeviceImageAction(String imageFileName, int action) {
		try {

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(readCommunity);
			}
			if (snmp == null) {
				initSnmp();
			}
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(readCommunity);
			}
			
			OID oid;
			String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estImageName");
			if (oidStr == null) {
				oid = new OID("1.3.6.1.4.1.9966.5.25.19.1.4.1.1.3");
			} else {
				oid = new OID(oidStr);
			}
			
			PDU pdu = new PDU();
			pdu.setType(PDU.GETNEXT);
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			ResponseEvent response = snmp.send(pdu, target);// estFileProtocol
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				VariableBinding vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
				OID oidNext = vbRes.getOid();
				while (oid.leftMostCompare(oid.size(), oidNext) == 0) {//
					int index = oidNext.last();
					vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
					oidNext = vbRes.getOid();
					Variable vRes = vbRes.getVariable();
					String vResStr = vRes.toString();

					if (vResStr.equals(imageFileName)) {// 找到刚上传的安装文件，并安装；找到要删除的文件，删除之
						if (snmpVersion != SnmpConstants.version3) {
							((CommunityTarget) target).setCommunity(writeCommunity);
						}
						OID oidImageAction;
						String oidImageActionStr = ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estImageAction");
						if (oidImageActionStr == null) {
							oidImageAction = new OID("1.3.6.1.4.1.9966.5.25.19.1.4.1.1.17");
						} else {
							oidImageAction = new OID(oidImageActionStr);
						}
						oidImageAction = new OID(oidImageAction.toString() + "." + index);
						PDU pduImageAction = new PDU();
						pduImageAction.setType(PDU.SET);
						Variable v = new Integer32(action);
						VariableBinding vbImageAction = new VariableBinding();
						vbImageAction.setOid(oidImageAction);
						vbImageAction.setVariable(v);
						pduImageAction.add(vbImageAction);
						target.setRetries(0);
						target.setTimeout(20000);
						if (frameOn == 1) {
							String[] ss = pduImageAction.toString().split(";");
							StringBuilder sb = new StringBuilder();
							for (String s : ss) {
								sb.append(s);
								sb.append("\n");
							}
							FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
						}
						ResponseEvent responseImageAction = snmp.send(pduImageAction, target);// estFileProtocol
						if (frameOn == 1) {
							if (responseImageAction != null) {
								String[] ss = responseImageAction.getResponse().toString().split(";");
								StringBuilder sb = new StringBuilder();
								for (String s : ss) {
									sb.append(s);
									sb.append("\n");
								}
								FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
							}
						}
						close();
						return responseImageAction.getResponse().getErrorStatus();
					}

					// index++;
					oidNext = new OID(oid.toString() + "." + index);
					pdu = new PDU();
					pdu.setType(PDU.GETNEXT);
					vb = new VariableBinding();
					vb.setOid(oidNext);
					pdu.add(vb);
					response = snmp.send(pdu, target);// estFileProtocol
					respPdu = response.getResponse();
					vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
					oidNext = vbRes.getOid();
				}
				close();
				return ErrorMessageTable.FILE_NO_EXISTED;
			}
			close();
			return -1;
		} catch (Exception ex) {
			log.error(ex, ex);
			close();
			return -1;
		}

	}

	// 查询edd设备内已存在的升级文件
	public List<String> queryEddDeviceSysGroup() {
		try {
			List<String> existFileList = new ArrayList<String>();

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			if (snmp == null) {
				initSnmp();
			}
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			OID oid;
			String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estImageName");
			if (oidStr == null) {
				oid = new OID("1.3.6.1.4.1.9966.5.25.19.1.4.1.1.3");
			} else {
				oid = new OID(oidStr);
			}
			PDU pdu = new PDU();
			pdu.setType(PDU.GETNEXT);
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			ResponseEvent response = snmp.send(pdu, target);// estFileProtocol
			if (response != null) {
				PDU respPdu = response.getResponse();
				VariableBinding vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
				OID oidNext = vbRes.getOid();
				while (oid.leftMostCompare(oid.size(), oidNext) == 0) {// 找所有存在的升级文件
					vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
					oidNext = vbRes.getOid();
					Variable vRes = vbRes.getVariable();
					String vResStr = vRes.toString();
					existFileList.add(vResStr);// 找到一个

					pdu = new PDU();
					pdu.setType(PDU.GETNEXT);
					vb = new VariableBinding();
					vb.setOid(oidNext);
					pdu.add(vb);
					response = snmp.send(pdu, target);// estFileProtocol
					respPdu = response.getResponse();
					vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
					oidNext = vbRes.getOid();
				}

				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
					}
				}
			}
			close();
			return existFileList;
		} catch (Exception ex) {
			log.error(ex, ex);
			close();
			return null;
		}

	}

	// 获取磁盘空间大小
	public int getEstFreeDisk() {
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}
			OID oid = null;

			oid = getOIDByName("gpn", "estFreeDisk", "1.3.6.1.4.1.9966.5.25.19.1.4.6.0");

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString());
			}
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString());
					}
				}
				close();
				return new Integer(((Integer32) respPdu.get(0).getVariable()).getValue());
			}
		} catch (Exception ex) {
			log.error(ex, ex);
			close();
		}
		return -1;
	}

	public String get01100snVersion() {
		try {
			String version = "";
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}
			
			OctetString writeCommunity = new OctetString("public");
			((CommunityTarget) target).setCommunity(writeCommunity);
			

			OID oid = new OID("1.3.6.1.4.1.9966.2.100.5.1.1.1.0");// h0fls01100sn.objects.prosystem.sysSoftVer

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString() + "\n");
			}
			ResponseEvent response = snmp.send(pdu, target);
			
			if (response == null || response.getResponse() == null) {
				Thread.sleep(2000);
				response = snmp.send(pdu, target);
			}
			
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString() + "\n");

					}
				}
				version += "S:" + respPdu.get(0).getVariable().toString() + ";";
			}else{
				return "";
			}
			Thread.sleep(100);
			
			oid = new OID("1.3.6.1.4.1.9966.2.100.5.1.1.2.0");// h0fls01100sn.objects.prosystem.sysFpgaVer

			//pdu = new PDU();
			pdu.setType(PDU.GETNEXT);
//			vb = new VariableBinding();
//			vb.setOid(oid);
//			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString() + "\n");
			}
			response = snmp.send(pdu, target);
			if (response == null || response.getResponse() == null) {
				Thread.sleep(2000);
				response = snmp.send(pdu, target);
			}
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString() + "\n");

					}
				}
				version += "F:" + respPdu.get(0).getVariable().toString() + ";";
			}else{
				log.info("response is null!");
				return "";
			}
			log.info(version);
			close();
			return version;
		} catch (Exception ex) {
			log.error(ex, ex);
		}
		return "";
	}

	
	public String get04100snv3Version() {
		try {
			String version = "";
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}
			
			OctetString writeCommunity = new OctetString("public");
			((CommunityTarget) target).setCommunity(writeCommunity);
			

			OID oid = new OID("1.3.6.1.4.1.9966.2.100.7.1.1.1.0");// h0fls01100sn.objects.prosystem.sysSoftVer，1.3.6.1.4.1.9966.2.100.7.1.1.1

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString() + "\n");
			}
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString() + "\n");

					}
				}
				version += "S:" + respPdu.get(0).getVariable().toString() + ";";
			}
			Thread.sleep(100);

			oid = new OID("1.3.6.1.4.1.9966.2.100.7.1.1.2.0");// h0fls01100sn.objects.prosystem.sysFpgaVer

			//pdu = new PDU();
			pdu.setType(PDU.GETNEXT);
//			vb = new VariableBinding();
//			vb.setOid(oid);
//			pdu.add(vb);

			if (frameOn == 1) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write("FrameInfo", "Command", target.toString() + "\n\n" + sb.toString() + "\n");
			}
			response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				if (frameOn == 1) {
					if (respPdu != null) {
						String[] ss = respPdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						FileTool.write("FrameInfo", "Response", target.toString() + "\n\n" + sb.toString() + "\n");

					}
				}
				version += "F:" + respPdu.get(0).getVariable().toString() + ";";
			}
			log.info(version);
			close();
			return version;
		} catch (Exception ex) {
			log.error(ex, ex);
		}
		return "";
	}
	
	/**
	 * 用于v8e设备的CPX10盘
	 * @return
	 */
	public int setEstFileMgmt(String neType, int estFileCmd, String ipAddress, int tftp, String user, String password, String estFileName,int estFileOperateSlot) {
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.SET);
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			if (snmp == null) {
				initSnmp();
			}
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			pdu = new PDU();
			pdu.setType(PDU.SET);
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			OID oid = getOIDByName("gpn", "estFileName", "1.3.6.1.4.1.9966.5.25.19.1.5.2.0");
			Variable v = new OctetString(estFileName);
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			writeFrameRecord(pdu,"FrameInfo", true);
			ResponseEvent response = snmp.send(pdu, target);// estFileName

			PDU respPdu = response.getResponse();
			writeFrameRecord(respPdu,"FrameInfo", false);
			Thread.sleep(100);

			oid = getOIDByName("gpn", "estServerAddress", "1.3.6.1.4.1.9966.5.25.19.1.5.6.0");

			pdu = new PDU();
			pdu.setType(PDU.SET);
			v = new IpAddress(ipAddress);
			vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			writeFrameRecord(pdu,"FrameInfo", true);
			response = snmp.send(pdu, target);// estServerAddress

			respPdu = response.getResponse();
			writeFrameRecord(respPdu,"FrameInfo", false);
			Thread.sleep(100);
			

			if (neType.toLowerCase().equals("ce")) {

			} else {
				oid = getOIDByName("gpn", "estFileCmd", "1.3.6.1.4.1.9966.5.25.19.1.5.1.0");
			}

			pdu = new PDU();
			pdu.setType(PDU.SET);
			v = new Integer32(estFileCmd);
			vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			writeFrameRecord(pdu,"FrameInfo", true);
			response = snmp.send(pdu, target);// estFileCmd
			respPdu = response.getResponse();
			writeFrameRecord(respPdu,"FrameInfo", false);
			close();
			return respPdu.getErrorStatus();
		} catch (Exception ex) {
			 log.error(ex, ex);
			close();
			return -1;
		}
	}
	
	
	
	public int setV8EDeviceImageAction(String imageFileName, int action) {
		try {

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			if (snmp == null) {
				initSnmp();
			}
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			OID oid;
			String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estImageName");
			if (oidStr == null) {
				oid = new OID("1.3.6.1.4.1.9966.5.25.19.1.4.1.1.3");
			} else {
				oid = new OID(oidStr);
			}
			PDU pdu = new PDU();
			pdu.setType(PDU.GETNEXT);
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			ResponseEvent response = snmp.send(pdu, target);// estFileProtocol
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				VariableBinding vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
				OID oidNext = vbRes.getOid();
				while (oid.leftMostCompare(oid.size(), oidNext) == 0) {//
					int index = oidNext.last();
					vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
					oidNext = vbRes.getOid();
					Variable vRes = vbRes.getVariable();
					String vResStr = vRes.toString();

					if (vResStr.equals(imageFileName)) {// 找到刚上传的安装文件，并安装；找到要删除的文件，删除之
						
						
						OID oidImageAction;
						String oidImageActionStr = ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estImageAction");
						if (oidImageActionStr == null) {
							oidImageAction = new OID("1.3.6.1.4.1.9966.5.25.19.1.4.1.1.17");
						} else {
							oidImageAction = new OID(oidImageActionStr);
						}
						oidImageAction = new OID(oidImageAction.toString() + "." + index);
						PDU pduImageAction = new PDU();
						pduImageAction.setType(PDU.SET);
						Variable v = new Integer32(action);
						VariableBinding vbImageAction = new VariableBinding();
						vbImageAction.setOid(oidImageAction);
						vbImageAction.setVariable(v);
						pduImageAction.add(vbImageAction);
						target.setRetries(0);
						target.setTimeout(20000);
						writeFrameRecord(pduImageAction,"FrameInfo", true);
						ResponseEvent responseImageAction = snmp.send(pduImageAction, target);// estFileProtocol
						writeFrameRecord(responseImageAction.getResponse(),"FrameInfo", false);
						close();
						return responseImageAction.getResponse().getErrorStatus();
					}

					// index++;
					oidNext = new OID(oid.toString() + "." + index);
					pdu = new PDU();
					pdu.setType(PDU.GETNEXT);
					vb = new VariableBinding();
					vb.setOid(oidNext);
					pdu.add(vb);
					response = snmp.send(pdu, target);// estFileProtocol
					respPdu = response.getResponse();
					vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
					oidNext = vbRes.getOid();
				}
				close();
				return -1;
			}
			close();
			return -1;
		} catch (Exception ex) {
			log.error(ex, ex);
			close();
			return -1;
		}

	}
	
	/**
	 * 将NM盘上的升级文件拷贝到指定槽位的单盘下
	 * @param neType 网元类型
	 * @param estFileCmd 文件类型
	 * @param estFileState 升级文件在网管盘的状态
	 * @param estFileName 文件名称
	 * @param estFileOperateSlot 支路盘槽位
	 * @return
	 */
	public int setEstFileTraned(String neType, int estFileCmd, String estFileName, int estFileOperateSlot){
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.SET);
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			if (snmp == null) {
				initSnmp();
			}
			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}
			
			OID oid;
			String oidStr = ManFactoryMibLoader.getInstace().getOIDbyName("gpn", "estImageName");
			if (oidStr == null) {
				oid = new OID("1.3.6.1.4.1.9966.5.25.19.1.4.1.1.3");
			} else {
				oid = new OID(oidStr);
			}
			pdu = new PDU();
			pdu.setType(PDU.GETNEXT);
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			ResponseEvent response = snmp.send(pdu, target);// estFileProtocol
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				VariableBinding vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
				OID oidNext = vbRes.getOid();
				while (oid.leftMostCompare(oid.size(), oidNext) == 0) {//
					int index = oidNext.last();
					vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
					oidNext = vbRes.getOid();
					Variable vRes = vbRes.getVariable();
					String vResStr = vRes.toString();

					if (vResStr.equals(estFileName)) {// 找到刚上传的安装文件，
						pdu = new PDU();
						pdu.setType(PDU.SET);
						if (snmpVersion != SnmpConstants.version3) {
							((CommunityTarget) target).setCommunity(writeCommunity);
						}

						oid = getOIDByName("gpn", "estFileName", "1.3.6.1.4.1.9966.5.25.19.1.5.2.0");
						Variable v = new OctetString(estFileName);
						vb = new VariableBinding();
						vb.setOid(oid);
						vb.setVariable(v);
						pdu.add(vb);

						writeFrameRecord(pdu,"FrameInfo", true);
						response = snmp.send(pdu, target);// estFileName

						respPdu = response.getResponse();
						writeFrameRecord(respPdu,"FrameInfo", false);
						
						break;
					}else{
						oidNext = new OID(oid.toString() + "." + index);
						pdu = new PDU();
						pdu.setType(PDU.GETNEXT);
						vb = new VariableBinding();
						vb.setOid(oidNext);
						pdu.add(vb);
						response = snmp.send(pdu, target);// estFileProtocol
						respPdu = response.getResponse();
						vbRes = (VariableBinding) respPdu.getVariableBindings().get(0);
						oidNext = vbRes.getOid();
					}
				}
			}

			oid = getOIDByName("gpn", "estFileOperateSlot", "1.3.6.1.4.1.9966.5.25.19.1.5.6.0");

			pdu = new PDU();
			pdu.setType(PDU.SET);
			Variable v = new Integer32(estFileOperateSlot);
			vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			writeFrameRecord(pdu,"FrameInfo", true);
			response = snmp.send(pdu, target);// estServerAddress

			PDU respPdu = response.getResponse();
			writeFrameRecord(respPdu,"FrameInfo", false);
			Thread.sleep(100);

			if (neType.toLowerCase().equals("ce")) {

			} else {
				oid = getOIDByName("gpn", "estFileCmd", "1.3.6.1.4.1.9966.5.25.19.1.5.1.0");
			}

			pdu = new PDU();
			pdu.setType(PDU.SET);
			v = new Integer32(estFileCmd);
			vb = new VariableBinding();
			vb.setOid(oid);
			vb.setVariable(v);
			pdu.add(vb);

			writeFrameRecord(pdu,"FrameInfo", true);
			response = snmp.send(pdu, target);// estFileCmd
			respPdu = response.getResponse();
			writeFrameRecord(respPdu,"FrameInfo", false);
			close();
			return respPdu.getErrorStatus();
		} catch (Exception ex) {
			 log.error(ex, ex);
			close();
			return -1;
		}
	}
	
	/**
	 * 查询文件从NM盘下发到支路盘进度
	 * @return 返回执行进度，1~1000
	 */
	public int getEstFileTranedState(String neType){
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}
			OID oid = null;
			if (neType.toLowerCase().equals("ce")) {

			} else {
				oid = getOIDByName("gpn", "estFileTranedPercent", "1.3.6.1.4.1.9966.5.25.19.1.5.1.0");
			}

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			writeFrameRecord(pdu,"FrameInfo", true);
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				writeFrameRecord(respPdu,"FrameInfo", false);
				close();
				return new Integer(((Integer32) respPdu.get(0).getVariable()).getValue());
			}
		} catch (Exception ex) {
			// log.error(ex, ex);
			close();
			return -1;
		}
		close();
		return -1;
	}
	
	/**
	 * 查询安装状态
	 * @param neType
	 * @return
	 */
	public int getEstImageActionState(String neType){
		try {
			PDU pdu = new PDU();
			pdu.setType(PDU.GET);
			if (snmp == null) {
				initSnmp();
			}
			OID oid = null;
			if (neType.toLowerCase().equals("ce")) {

			} else {
				oid = getOIDByName("gpn", "estImageActionState", "1.3.6.1.4.1.9966.5.25.19.1.5.21.0");
			}

			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);

			if (snmpVersion != SnmpConstants.version3) {
				((CommunityTarget) target).setCommunity(writeCommunity);
			}

			writeFrameRecord(pdu,"FrameInfo", true);
			ResponseEvent response = snmp.send(pdu, target);
			if (response != null && response.getResponse() != null) {
				PDU respPdu = response.getResponse();
				writeFrameRecord(respPdu,"FrameInfo", false);
				close();
				return new Integer(((Integer32) respPdu.get(0).getVariable()).getValue());
			}
		} catch (Exception ex) {
			 log.error(ex, ex);
			close();
			return -1;
		}
		close();
		return -1;
	}
	
	private void writeFrameRecord(PDU pdu, String filePath, boolean isCommand){
		String fileName = this.getRecordFileName(isCommand);
		int frameOn = ToolUtil.getEntryConfig().getFrameRecordOnOff();
		if (frameOn == 1) {
			if (pdu != null) {
				String[] ss = pdu.toString().split(";");
				StringBuilder sb = new StringBuilder();
				for (String s : ss) {
					sb.append(s);
					sb.append("\n");
				}
				FileTool.write(filePath, fileName, target.toString() + "\n\n" + sb.toString());
			}
		}
	}
	
	public void close() {
		try {
			if (snmp != null) {
				snmp.close();
			}
		} catch (Exception e) {
			log.error(e, e);
		} finally {
			snmp = null;
		}
	}

	public boolean addDatas(Class clazz, List<Map<String, Object>> values) {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean deleteDatas(Class clazz, List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
		return false;
	}

	public List<Map<String, Object>> getDatas(Class clazz) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<Map<String, Object>> getDatas(Class clazz, List<String> fields) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<Map<String, Object>> getDatas(Class clazz, List<String> fields, List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean updateDatas(Class clazz, Map<String, Object> values, List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
		return false;
	}
	
	private List<Map<String, Object>> getDatasByCondition(List<AttributeDescriptor> attrs, String domain, List<List<Object>> conditions) {
		
		List<Map<String, Object>> tableDatas = new ArrayList<Map<String, Object>>();
		for(List<Object> con : conditions){
			List<AttributeDescriptor> clonAttrs = this.cloneAttrs(attrs);
			List<AttributeDescriptor> needloadattr = new ArrayList<AttributeDescriptor>();
			List<AttributeDescriptor> indexList = new ArrayList<AttributeDescriptor>();
			List<OID> oidList = new ArrayList<OID>();
			List<OID> oldOidList = new ArrayList<OID>();
			String exterName;

			for (AttributeDescriptor attr : clonAttrs) {
				if (attr.getExternalName() != null && attr.getExternalPropMask() != null) {
					if (!attr.getExternalPropMask().equalsIgnoreCase(SnmpEumDef.Index)) {
						needloadattr.add(attr);
						if (attr.getExternalName() != null && !attr.getExternalName().isEmpty() && !"null".equalsIgnoreCase(attr.getExternalName())) {
							exterName = attr.getExternalName();
							initAttr(attr, oidList, oldOidList, domain, exterName, con);
						}
					} else {
						indexList.add(attr);
						if (!attr.isReadOnly()) {
							needloadattr.add(attr);
							if (attr.getExternalName() != null && !attr.getExternalName().isEmpty() && !"null".equalsIgnoreCase(attr.getExternalName())) {
								exterName = attr.getExternalName();
								initAttr(attr, oidList, oldOidList, domain, exterName, con);
							}
						}
					}
				}
			}

			VariableBinding[][] nextvbs = null;
			if (indexList.isEmpty()) {
				if ("ce".equalsIgnoreCase(domain)) {
					if (conditions != null) {
						nextvbs = getBulk(oidList);
					} else {
						nextvbs = getNext(oidList);
					}
				} else {
					for (OID o : oidList) {
						o.append(0);
					}
					nextvbs = get(oidList);
				}
			} else {
				if(con.size() == indexList.size()){
					nextvbs = get(oidList);
				}else{
					nextvbs = getNext(oidList);
				}
			}

			List<OID> returnOids = new ArrayList<OID>();

			try {
				if (nextvbs != null && nextvbs[0].length > 0) {
					for (int i = 0; i < nextvbs[0].length; i++) {
						returnOids.add(nextvbs[0][i].getOid());
					}
					OID returnOid = nextvbs[0][0].getOid();
					int syntax = nextvbs[0][0].getSyntax();
					OID stdOID = getStdOID(oldOidList.get(0), con);
					if (syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW || syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE
							|| !returnOid.startsWith(stdOID)) {
						break;
					}

					if (nextvbs == null || nextvbs.length == 0)
						return null;

					List<Map<String, Object>> attrsMaps = new LinkedList<Map<String, Object>>();

					if (conditions != null && "ce".equalsIgnoreCase(domain)) {
						attrsMaps = getGetBulkResponseMap(nextvbs, needloadattr);
					} else {
						Map<String, Object> m = getGetResponseMap(nextvbs[0], needloadattr);
						attrsMaps.add(m);
					}

					for (Map<String, Object> attrsmap : attrsMaps) {
						Object v = attrsmap.get(SnmpEumDef.mibInstance);
						if(v == null){
							continue;
						}
						String intstance = v.toString();
						intstance = intstance.replace('.', '#');
						if (indexList.size() != 0) {
							if (intstance.indexOf("#") != -1) {
								String[] indexs = intstance.split("#");
								for (int i = 0; i < indexs.length; i++) {
									attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
								}

							} else {
								if (indexList.get(0).isReadOnly()) {
									AttributeDescriptor ad = indexList.get(0);
									attrsmap.put(indexList.get(0).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(intstance.toString(), ad));
								}
							}
						}

						tableDatas.add(attrsmap);

						if (!indexList.isEmpty() && con.size() < indexList.size()) {
							while (returnOid.startsWith(oidList.get(0)) && syntax != SMIConstants.EXCEPTION_NO_SUCH_INSTANCE
									&& syntax != SMIConstants.EXCEPTION_END_OF_MIB_VIEW) {
								try {
									Thread.sleep(getWriteFrameInterval());
								} catch (InterruptedException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
								nextvbs = getNext(returnOids);
								returnOids.clear();
								for (int i = 0; i < nextvbs[0].length; i++) {
									returnOids.add(nextvbs[0][i].getOid());
								}

								returnOid = nextvbs[0][0].getOid();
								syntax = nextvbs[0][0].getSyntax();

								if (syntax == SMIConstants.EXCEPTION_END_OF_MIB_VIEW || syntax == SMIConstants.EXCEPTION_NO_SUCH_INSTANCE
										|| !returnOid.startsWith(stdOID)) {
									break;
								}

								attrsmap = getGetResponseMap(nextvbs[0], needloadattr);
								intstance = attrsmap.get(SnmpEumDef.mibInstance).toString();

								intstance = intstance.replace('.', '#');
								if (indexList.size() != 0) {
									if (intstance.indexOf("#") != -1) {
										String[] indexs = intstance.split("#");
										for (int i = 0; i < indexs.length; i++) {
											attrsmap.put(indexList.get(i).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(indexs[i], indexList.get(i)));
										}

									} else {
										if (indexList.get(0).isReadOnly()) {
											AttributeDescriptor ad = indexList.get(0);
											attrsmap.put(indexList.get(0).getName(), SnmpDataConvert.covertToDBTypeFromMibInstance(intstance.toString(), ad));
										}
									}
								}

								tableDatas.add(attrsmap);
							}
						}
					}
				}
			} catch (Exception ex) {
				log.error("getDatasByCondition error:", ex);
			}
		}
		// log.debug("Fetched "+classData.name +"'Table Data by SNMP End") ;
		return tableDatas;
	}

	private void initAttr(AttributeDescriptor attr, List<OID> oidList, List<OID> oldOidList, String domain, String exterName, List<Object> con) {
		try {
			FactoryID = "9966";
			String oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, neType, exterName);
			if (oidStr == null && domain!=null) {
				oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, domain.toLowerCase(), exterName);
			}
			if (oidStr == null) {
				oidStr = facMibLoaderInstace.getOIDbyName(FactoryID, null, exterName);
			}
			OID oid = new OID(oidStr);
			OID oldOid = (OID) oid.clone();
			oldOidList.add(oldOid);
			if (con != null) {
				for (Object o : con) {
					oid.append(o.toString());
				}
			}
			oidList.add(oid);
			attr.setExternalName(oldOid.toString());
		} catch (NullPointerException e) {
			log.error("Can't find " + exterName + " in mibfile!");
		}
		
	}

public void snmpNePoll(TaskCell tc){
		List<OID> oids = new ArrayList<OID>();
		oids.add(new OID("1.3.6.1.2.1.1.2.0"));
		PDU pdu = null;
		if (snmpVersion != SnmpConstants.version3) {
			pdu = new PDU();
		} else {
			pdu = new ScopedPDU();
		}

		for (OID oid : oids) {
			VariableBinding vb = new VariableBinding();
			vb.setOid(oid);
			pdu.add(vb);
		}

		if (snmpVersion != SnmpConstants.version3) {
			((CommunityTarget) target).setCommunity(readCommunity);
		}
		VariableBinding[][] vbss = new VariableBinding[1][];
		sendPollCommand(tc, pdu);
	}
	
	private void sendPollCommand(final TaskCell taskCell, PDU pdu){
		ResponseListener listener = new ResponseListener() {

			@SuppressWarnings("unchecked")
			@Override
			public void onResponse(ResponseEvent respEvnt) {
				// TODO Auto-generated method stub
				if (respEvnt != null && respEvnt.getResponse() != null) {
					PDU pdu = respEvnt.getResponse();
					SnmpMessage resMsg = new SnmpMessage();
					resMsg.setPdu(pdu);
					// log.info("Receive response "+
					// pdu.get(0).getOid().toString());
					taskCell.addResMsg(pdu.get(0).getOid().toString(), resMsg);

					if (frameOn == 1) {
						String[] ss = pdu.toString().split(";");
						StringBuilder sb = new StringBuilder();
						for (String s : ss) {
							sb.append(s);
							sb.append("\n");
						}
						writeFrameRecord(pdu,"FrameInfo", false);
					}
				}
				((Snmp) respEvnt.getSource()).cancel(respEvnt.getRequest(), this);
			}
		};
		snmpPollSession(pdu, target, PDU.GET, listener);
	}
	
	private void snmpPollSession(PDU pdu, Target target, int snmpType, ResponseListener listener) {
		try {
			int pollFrameOnOff = ToolUtil.getEntryConfig().getPollFrameRecordOnOff();
			PDU respPdu = null;
			pdu.setType(snmpType);
			if (snmp == null) {
				initSnmp();
			}
			if (pollFrameOnOff == 1) {
				writeFrameRecord(pdu,"FrameInfo", true);
			}
			snmp.send(respPdu, target, null, listener);
		} catch (Exception e) {
			this.close();
			log.error(e,e);
			throw new SnmpDataException("snmpSession error", e);
		} finally{
			this.close();
		}
	}

	public void setScType(ScheduleType scType) {
		this.scType = scType;
	}
	
	public void setSync(boolean isSync) {
		this.isSync = isSync;
	}

	private String getRecordFileName(boolean isCommand) {
		
		if(isCommand){
			if(this.isSync) {
				return "SyncCommand";
			} else {
				if(this.scType == ScheduleType.ST_NA){
					return "Command";
				}else if(this.scType == ScheduleType.ST_NEPOLL
						|| this.scType == ScheduleType.ST_GWPOLL){
					return "PollCommand";
				}else if(this.scType == ScheduleType.ST_ALARMPOLL
						|| this.scType == ScheduleType.ST_HISALARMPOLL){
					return "AlarmPollCommand";
				}else{
					return "Command";
				}
			}
		}else{
			if(this.isSync) {
				return "SyncResponse";
			} else {
				if(this.scType == ScheduleType.ST_NA){
					return "Response";
				}else if(this.scType == ScheduleType.ST_NEPOLL
						|| this.scType == ScheduleType.ST_GWPOLL){
					return "PollResponse";
				}else if(this.scType == ScheduleType.ST_ALARMPOLL
						|| this.scType == ScheduleType.ST_HISALARMPOLL){
					return "AlarmPollResponse";
				}else{
					return "Response";
				}
			}
		}
	}
}
