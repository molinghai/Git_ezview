package com.nm.server.adapter.snmp;

import java.math.BigInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Counter64;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.IpAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.Opaque;
import org.snmp4j.smi.SMIConstants;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;

import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.AttributeDescriptor.AttributeType;

/**
 * 
 * @version 1.0
 */

public class SnmpDataConvert {
	protected static Log log = LogFactory.getLog(SnmpDataConvert.class);
	/**
	 * The following methods cover all SNMP types due to inheritance.
	 */

	/**
	 * SNMP Message Translator translates PDU to name value pairs and vice
	 * versa. The value type of a pair is assumed to be java type. The following
	 * table is used to translate SNMP Types to Java Type SNMP Type Java Type
	 * -------------------------------------------------- INT32 Integer UINT32
	 * Long OCTET String COUNTER32 Long Counter64 Long GAUGE Long TIMETICKS Long
	 * TIMESTAMP Long TestAndIncr Integer StorageType Integer TimeInterval
	 * Integer OPAQUE String OID String TRUTHVALUE Boolean IpAddress IpAddress
	 * BitString OctetString
	 */

	// public static final Log log = new Log(HHSnmpUtil.class);
	static char[] hexChar = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
			'a', 'b', 'c', 'd', 'e', 'f' };
	

	static public Object convertFromSNMPType(Variable v) {
		try{
			switch (v.getSyntax()) {
			case SMIConstants.SYNTAX_NULL:
				return null;

			case SMIConstants.SYNTAX_INTEGER32:
				return new Integer(((Integer32) v).getValue());

			case SMIConstants.SYNTAX_GAUGE32:
				return new Long(((UnsignedInteger32) v).getValue());
			case SMIConstants.SYNTAX_COUNTER32:
				return new Long(((Counter32) v).getValue());
			case SMIConstants.SYNTAX_COUNTER64:
				try{
					Long l = new Long(((Counter64) v).getValue());
					if(l == -1){
						BigInteger bi = new BigInteger(((Counter64) v).toString());
						return bi;
					}
					return l;
				}catch(Exception ex){
					return Long.MAX_VALUE;
				}

			case SMIConstants.SYNTAX_TIMETICKS:
				return v;
			case SMIConstants.SYNTAX_OCTET_STRING:
				// return ((OctetString)v).getValue();
				// if (v instanceof OctetString)
				return v;
				// return reverseBits(((OctetString) v).getValue());
				// else // fall back to use the converted object v
				// return ZmUtil.reverseBits(v.getBytes());
			default:
				return v;
			}
		}catch(Exception e){
			log.error(e, e);
		}
		return v.toString();
		
	}

	static public String convertFromSNMPType2String(Variable v) {
		switch (v.getSyntax()) {
		case SMIConstants.SYNTAX_NULL:
			return null;

		case SMIConstants.SYNTAX_INTEGER32:
			return ((Integer32) v).getValue() + "";

		case SMIConstants.SYNTAX_GAUGE32:
		case SMIConstants.SYNTAX_COUNTER32:
			return ((UnsignedInteger32) v).getValue() + "";

		case SMIConstants.SYNTAX_COUNTER64:
			return ((Counter64) v).getValue() + "";

		case SMIConstants.SYNTAX_TIMETICKS:
			return v.toString();
		case SMIConstants.SYNTAX_OCTET_STRING:
			return ((OctetString) v).getValue() + "";
		default:
			return v.toString();
		}
	}

	public static String specificProcessMacAddress(Variable v) {
		String str = null;
		switch (v.getSyntax()) {
		case SMIConstants.SYNTAX_OCTET_STRING:
			byte[] buf = ((OctetString) v).getValue();
			if (buf == null)
				return str;

			str = toMacAddress(buf);
			break;
		default:
			str = v.toString();
		}
		return str;
	}

	// add end

	static public Object covertToDBTypeFromMibInstance(
			Object mibInstanceAttrValue, AttributeDescriptor ad) {
		AttributeType type = ad.getAttributeType();
//		try {
			if (type == AttributeDescriptor.AttributeType.AT_STRING
					|| (type == AttributeDescriptor.AttributeType.AT_ENUM && ad
							.getExternalType().equals("OCTET"))) {
				// add by wq becusue thoes those fields may return too long
				// data.
				if (ad.getName().equalsIgnoreCase(
						"dot1agCfmMepDiagCfgltmdestmacaddr")
						|| ad.getName().equalsIgnoreCase(
								"dot1agCfmMepDiagCfglbmdestmacaddr")
						|| ad.getName().equalsIgnoreCase("dot3OamLbDmac")) {
					if (mibInstanceAttrValue.toString().length() >= 18) {
						return mibInstanceAttrValue.toString().substring(0, 17);
					}
				}
				return mibInstanceAttrValue.toString();
			} else if (type == AttributeDescriptor.AttributeType.AT_BITS) {
				OctetString data = (OctetString) mibInstanceAttrValue;
				byte[] bytes = data.getValue();
				String value = convertBits(bytes);
				return value;
			} else if (type == AttributeDescriptor.AttributeType.AT_PORTLIST) {
				OctetString data = (OctetString) mibInstanceAttrValue;
				byte[] bytes = data.getValue();
				// for(byte b :bytes){
				// value.add(b);
				// }
				return bytes;
			} else if (type == AttributeDescriptor.AttributeType.AT_BOOLEAN
					|| type == AttributeDescriptor.AttributeType.AT_ENUM
					|| type == AttributeDescriptor.AttributeType.AT_INT) {
				return Integer.valueOf(mibInstanceAttrValue.toString());
			} else if (type == AttributeDescriptor.AttributeType.AT_LONG) {
				if (mibInstanceAttrValue instanceof BigInteger) {
					BigInteger mibValue = new BigInteger(String.valueOf(mibInstanceAttrValue));
					mibInstanceAttrValue = mibValue.longValue();
				}
				return Long.valueOf(mibInstanceAttrValue.toString());
			} else if (type == AttributeDescriptor.AttributeType.AT_IPSTRING) {
				String ipAddress = parseIPString(mibInstanceAttrValue
						.toString());
				return ipAddress;
			} else if (type == AttributeDescriptor.AttributeType.AT_MACSTRING) {
				String macAddress = parseMACString(mibInstanceAttrValue
						.toString());
				return macAddress;
			}
//		} catch (Exception e) {
//			log.error(e, e);
//		}
		return mibInstanceAttrValue;

		// int type = ad.getType() ;
		// switch(type)
		// {
		// case AttributeData.TEXT_ATTR :
		// case AttributeData.STRING_ENUM_ATTR :
		// case AttributeData.STRING_ENUM_LABEL_ATTR :
		// case AttributeData.TEXT_AREA_ATTR :
		// return mibInstanceAttrValue ;
		// case AttributeData.INT_ATTR :
		// case AttributeData.INT_ENUM_ATTR :
		// case AttributeData.INT_ENUM_LABEL_ATTR :
		// case AttributeData.BOOL_ATTR :
		// return Integer.valueOf(mibInstanceAttrValue) ;
		// case AttributeData.LONG_ATTR :
		// case AttributeData.LONG_ENUM_ATTR :
		// case AttributeData.LONG_ENUM_LABEL_ATTR :
		// return Long.valueOf(mibInstanceAttrValue) ;
		// case AttributeData.FLOAT_ATTR :
		// return Float.valueOf(mibInstanceAttrValue) ;
		// case AttributeData.DOUBLE_ATTR :
		// return Double.valueOf(mibInstanceAttrValue) ;
		// case AttributeData.BITSET8_ATTR :
		// case AttributeData.BITSET16_ATTR :
		// case AttributeData.BITSET32_ATTR :
		// case AttributeData.BITSET64_ATTR :
		// byte[] bytes = mibInstanceAttrValue.getBytes();
		// return bytes ;
		// default : return mibInstanceAttrValue ;
		// }

	}

	static public Variable convertToSNMPType(Object v, AttributeDescriptor ad) {
		String type = ad.getExternalType();
		Variable var = null;
		int ival = 0;
		long lval = 0;
		// use the type parameter to construct an object of the required type
		if (type.equalsIgnoreCase(SnmpEumDef.INT32)
				|| type.equalsIgnoreCase(SnmpEumDef.INTEGER)) {
			if (v instanceof Integer) {
				ival = ((Integer) v).intValue();
			} else if (v instanceof Long) {
				ival = (int) ((Long) v).longValue();
			} else if(v instanceof String){
				ival = Integer.valueOf((String)v);
			}
			var = new Integer32(ival);
		} else if (type.equalsIgnoreCase(SnmpEumDef.UINT32)) {
			if (v instanceof Integer) {
				lval = (long) ((Integer) v).intValue();
			} else if (v instanceof Long) {
				lval = ((Long) v).longValue();
			}
			var = new UnsignedInteger32(lval);
		} else if (type.equalsIgnoreCase(SnmpEumDef.OCTET)) {
			String v_string = v.toString();
			if (ad.getAttributeType() == AttributeDescriptor.AttributeType.AT_BITS) {
				byte[] v_bytes = convertSnmpBits(v_string);
				var = new OctetString(v_bytes);
			} 
//			else if (v_string.contains("'")) {// Bug #21821
//				v_string = v_string.replace("'", "");
//				var = new OctetString(OctetString.fromHexString(v_string));
//			} 
			else {
				var = new OctetString(v_string);
			}
		}else if (type.equalsIgnoreCase(SnmpEumDef.COUNTER32)) {
			if (v instanceof Integer) {
				lval = (long) ((Integer) v).intValue();
			} else if (v instanceof Long) {
				lval = ((Long) v).longValue();
			}
			var = new Counter32(lval);
		} else if (type.equalsIgnoreCase(SnmpEumDef.COUNTER64)) {
			if (v instanceof Integer) {
				lval = (long) ((Integer) v).intValue();
			} else if (v instanceof Long) {
				lval = ((Long) v).longValue();
			}
			var = new Counter64(lval);
		} else if (type.equalsIgnoreCase(SnmpEumDef.GAUGE)) {
			if (v instanceof Integer) {
				lval = (long) ((Integer) v).intValue();
			} else if (v instanceof Long) {
				lval = ((Long) v).longValue();
			}
			var = new Gauge32(lval);
		} else if (type.equalsIgnoreCase(SnmpEumDef.tOID)) {
			var = new OID(v.toString());
		} else if (type.equalsIgnoreCase(SnmpEumDef.TRUTHVALUE)) {
			if (v instanceof Boolean) {
				if (((Boolean) v).booleanValue()) {
					var = new Integer32(1);
				} else {
					var = new Integer32(2);
				}
			} else if (v instanceof Integer) {
				var = new Integer32(((Integer) v).intValue());
			} else {
				var = new Integer32(2);
			}
		} else if (type.equalsIgnoreCase(SnmpEumDef.TIMETICKS)
				|| type.equalsIgnoreCase(SnmpEumDef.TIMESTAMP)) {
			if (v instanceof Integer) {
				lval = (long) ((Integer) v).intValue();
			} else if (v instanceof Long) {
				lval = ((Long) v).longValue();
			}
			var = new TimeTicks(lval);
		} else if (type.equalsIgnoreCase(SnmpEumDef.OPAQUE)) {
			var = new Opaque(v.toString().getBytes());
		} else if (type.equalsIgnoreCase(SnmpEumDef.TimeInterval)
				|| type.equalsIgnoreCase(SnmpEumDef.StorageType)) {
			if (v instanceof Integer) {
				ival = ((Integer) v).intValue();
			} else if (v instanceof Long) {
				ival = (int) ((Long) v).longValue();
			}
			var = new Integer32(ival);
		} else if (type.equalsIgnoreCase(SnmpEumDef.IpAddress)) {
			var = new IpAddress(v.toString());
		} else if (type.equalsIgnoreCase(SnmpEumDef.BitString)) {
			if (v instanceof String) {
				String v_string = v.toString();
				byte[] v_bytes = convertSnmpBits(v_string);
				var = new OctetString(v_bytes);
			}
		}else if (type.equalsIgnoreCase(SnmpEumDef.PortList)) {
			String v_string = v.toString();
			if (ad.getAttributeType() == AttributeDescriptor.AttributeType.AT_BITS) {
				byte[] bytes = convertSnmpPortList(v_string);
				var = new OctetString(bytes);
			}else if(ad.getAttributeType() == AttributeDescriptor.AttributeType.AT_PORTLIST){
				byte[] bytes = (byte[])v;
				var = new OctetString(bytes);
			}
		}else if (type.equalsIgnoreCase(SnmpEumDef.MACString)) {
			String v_string = v.toString();
			if (ad.getAttributeType() == AttributeDescriptor.AttributeType.AT_MACSTRING) {
				String value = convertMACToString(v_string);
				var = new OctetString(value);
			}
		}else if (type.equalsIgnoreCase(SnmpEumDef.MACAddress)) {
			String v_string = v.toString();
			if (ad.getAttributeType() == AttributeDescriptor.AttributeType.AT_MACSTRING) {
				byte[] bytes = convertSnmpMACAddress(v_string);
				var = new OctetString(bytes);
			}else{
				var = new OctetString(v.toString());
			}
	
		} else {
			var = new OctetString(v.toString());
		}
		return var;
	}

	// static public Variable convertToSNMPType(Object v, AttributeDescriptor
	// ad) {
	// int type = Integer.parseInt(ad.getExternalType());
	// Variable var = null;
	// int ival = 0;
	// long lval = 0;
	// // use the type parameter to construct an object of the required type
	// switch (type) {
	// case SMIConstants.SYNTAX_INTEGER32:
	// if (v instanceof Integer) {
	// ival = ( (Integer) v).intValue();
	// }
	// else if (v instanceof Long) {
	// ival = (int) ( (Long) v).longValue();
	// }
	// var = new Integer32(ival);
	// break;
	// // case SMIConstants.SYNTAX_UNSIGNED_INTEGER32:
	// // if (v instanceof Integer) {
	// // lval = (long) ( (Integer) v).intValue();
	// // }
	// // else if (v instanceof Long) {
	// // lval = ( (Long) v).longValue();
	// // }
	// // var = new UnsignedInteger32(lval);
	// // break;
	// case SMIConstants.SYNTAX_OCTET_STRING:
	// String value = v.toString();
	// if (value.length() > ad.getLength()) {
	// // log.warn("Truncating " + ad.name + " <" + value + "> to " +
	// // ad.length + " bytes");
	// value = value.substring(0, ad.getLength());
	// }
	// var = new OctetString(value);
	// break;
	// case SMIConstants.SYNTAX_COUNTER32:
	// if (v instanceof Integer) {
	// lval = (long) ( (Integer) v).intValue();
	// }
	// else if (v instanceof Long) {
	// lval = ( (Long) v).longValue();
	// }
	// var = new Counter32(lval);
	// break;
	// case SMIConstants.SYNTAX_COUNTER64:
	// if (v instanceof Integer) {
	// lval = (long) ( (Integer) v).intValue();
	// }
	// else if (v instanceof Long) {
	// lval = ( (Long) v).longValue();
	// }
	// var = new Counter64(lval);
	// break;
	// case SMIConstants.SYNTAX_GAUGE32:
	// if (v instanceof Integer) {
	// lval = (long) ( (Integer) v).intValue();
	// }
	// else if (v instanceof Long) {
	// lval = ( (Long) v).longValue();
	// }
	// var = new Gauge32(lval);
	// break;
	// case SMIConstants.SYNTAX_TIMETICKS:
	// if (v instanceof Integer) {
	// lval = (long) ( (Integer) v).intValue();
	// }
	// else if (v instanceof Long) {
	// lval = ( (Long) v).longValue();
	// }
	// var = new TimeTicks(lval);
	// break;
	// case SMIConstants.SYNTAX_OPAQUE:
	// var = new Opaque(v.toString().getBytes());
	// break;
	// // case AttributeData.OID_EXT:
	// // var = new OID(v.toString());
	// // break;
	// // case AttributeData.TRUTHVALUE_EXT:
	// // if (v instanceof Boolean) {
	// // if ( ( (Boolean) v).booleanValue()) {
	// // var = new Integer32(1);
	// // }
	// // else {
	// // var = new Integer32(2);
	// // }
	// // }
	// // else if (v instanceof Integer) {
	// // var = new Integer32( ( (Integer) v).intValue());
	// // }
	// // else {
	// // var = new Integer32(2);
	// // }
	// // break;
	// // case AttributeData.TESTANDINCR_EXT:
	// // case AttributeData.TIMEINTERVAL_EXT:
	// // case AttributeData.STORAGETYPE_EXT:
	// // if (v instanceof Integer) {
	// // ival = ( (Integer) v).intValue();
	// // }
	// // else if (v instanceof Long) {
	// // ival = (int) ( (Long) v).longValue();
	// // }
	// // var = new Integer32(ival);
	// // break;
	// // case AttributeData.BITSTRING_EXT:
	// // if (v instanceof byte[]) {
	// // var = new OctetString( (byte[]) v);
	// // }
	// // break;
	// // case AttributeData.IPADDRESS_EXT:
	// // var = new IpAddress(v.toString());
	// // break;
	// //
	// // case AttributeData.MACADDRESS_EXT:
	// // if (v instanceof byte[]) {
	// // var = new OctetString((byte[])v);
	// // }
	// // break;
	// default:
	// var = new OctetString(v.toString());
	// }
	// return var;
	// }

	static public byte[] convertSnmpBits(String v_string) {

		char[] count = v_string.toCharArray();
		int byteSize = count.length / 8;
		int insuff = 8 - count.length % 8;
		if (count.length % 8 != 0)
			byteSize = byteSize + 1;
		if (count.length % 8 != 0)
			for (; insuff > 0; insuff--) {
				v_string += "0";
			}
		byte[] v_bytes = new byte[byteSize];
		for (int i = 0; i < byteSize; i++) {
			int v_int = Integer.parseInt(v_string, 2);
			v_bytes[i] = (byte) v_int;
		}
		return v_bytes;
	}

	static public byte[] convertSnmpPortList(String v_string) {

		char[] count = v_string.toCharArray();
		int byteSize = count.length / 8;
		int insuff = 8 - count.length % 8;
		if (count.length % 8 != 0)
			byteSize = byteSize + 1;
		if (count.length % 8 != 0)
			for (; insuff > 0; insuff--) {
				v_string += "0";
			}
		byte[] v_bytes = new byte[byteSize];
		for (int i = 0; i < byteSize; i++) {
			int v_int = Integer.parseInt(v_string.substring(i*8, (i+1)*8), 2);
			byte val = (byte)v_int;
//			v_bytes[i] = (byte) (val >= 0 ? val : 256 + val);
			v_bytes[i] = val;
		}
		return v_bytes;
	}
	
	static public String convertBits(byte[] bytes) {

		String value = "";
		for (int i = 0; i < bytes.length; i++) {
			String binstring = "";
			if (bytes[i] < 0) {
				binstring = Integer.toBinaryString(bytes[i]).substring(24)
						.toString();
			} else {
				binstring = Integer.toBinaryString(bytes[i]).toString();
			}
			int size = (8 - binstring.length());
			for (int j = 0; j < size; j++) {
				binstring = "0" + binstring;
			}
			value += binstring;
		}
		return value;
	}

	static public byte[] reverseBits(byte[] origArr) {

		int len = origArr.length;
		byte[] b = new byte[len];
		int i, j;
		for (i = (origArr.length * 8) - 1, j = 0; i >= 0; i--, j++) {
			b[len - j / 8 - 1] |= ((((origArr[origArr.length - i / 8 - 1] & (1 << (i % 8))) > 0) ? 0x01
					: 0x00) << (j % 8));
		}
		return b;
	}

	public static String toMacAddress(byte[] b) {
		StringBuffer sb = new StringBuffer(b.length * 2);
		for (int i = 0; i < b.length; i++) {
			// look up high nibble char
			sb.append(hexChar[(b[i] & 0xf0) >>> 4]);

			// look up low nibble char
			sb.append(hexChar[b[i] & 0x0f]);

			if (i < b.length - 1) {
				sb.append(":");
			}
		}

		return sb.toString();
	}
	// add end

	public static String parseIPString(String ipString){
		StringBuffer sb = new StringBuffer();
		String[] values = ipString.split(":");
		for(String value : values){
			if(value.equals("2e")){
				sb.append(".");
			}else if(!value.equals("00")){
				sb.append(Integer.parseInt(value) - 30);
			}
		}
		return sb.toString();
	}
	
	public static String parseMACString(String macString){
		StringBuffer sb = new StringBuffer();
		String[] values = macString.replace('.', '#').split("#");
		if(values.length > 1 && !macString.contains("#")){
			
			for(int i=0; i<values.length; i++){
				sb.append(values[i].substring(0, 2));
				sb.append(":");
				sb.append(values[i].substring(2, 4));
				if(i != values.length-1){
					sb.append(":");
				}
			}
			return sb.toString();
		}else if(!macString.contains(":") && macString.length() == 6){
			StringBuilder sbu = new StringBuilder();
			for(int i=0; i<macString.length(); i++){
				
				byte b = (byte) macString.charAt(i);
				String a = Integer.toString(b, 16);
				sbu.append(a);
				if(i < macString.length() - 1){
					sbu.append(":");
				}
			}
			return sbu.toString();
		}else{
			return macString;
		}
	}
	
	public static String convertMACToString(String mac){
		StringBuffer sb = new StringBuffer();
		String[] values = mac.split(":");
		int count = values.length / 2;
		for(int i=0; i < count; i++){
				sb.append(addZeros(values[2*i]));
				sb.append(addZeros(values[2*i +1]));
				if(i != count-1){
					sb.append(".");
				}
		}
		return sb.toString();
	}
	
	public static String addZeros(String value){
		int size = (2 - value.length());
		for (int j = 0; j < size; j++) {
			value = "0" + value;
		}
		return value;
	}
	
	static public byte[] convertSnmpMACAddress(String v_string) {

		String[] hexs = v_string.split(":");
		byte[] v_bytes = new byte[hexs.length];
		for (int i = 0; i < hexs.length; i++) {
			int v_int = Integer.parseInt(hexs[i], 16);
			byte val = (byte)v_int;
			v_bytes[i] = val;
		}
		return v_bytes;
	}
}
