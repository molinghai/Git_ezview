package com.nm.server.adapter.snmp;

import java.util.Hashtable;
import java.util.ResourceBundle;

import org.snmp4j.PDU;


/**
 * <p>Title: Zodiac Manager Server</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2011 --- 2012 - All rights reserved</p>
 * <p>Company: Deane Li, Peking,CHINA</p>

 * @version 1.0
 */

public class SnmpDataException extends RuntimeException {

  /**
	 * 
	 */
  private static final long serialVersionUID = 1L;
  public static int snmpNoError = PDU.noError;
  public static int snmpAuthorizationError = PDU.authorizationError;
  public static int snmpBadValue = PDU.badValue;
  public static int snmpCommitFailed = PDU.commitFailed;
  public static int snmpGenError = PDU.genErr;
  public static int snmpInconsistentName = PDU.inconsistentName;
  public static int snmpInconsistentValue  = PDU.inconsistentValue;
  public static int snmpNoAccess = PDU.noAccess;
  public static int snmpNoCreation  = PDU.noCreation;
  public static int snmpNoSuchName  = PDU.noSuchName;
  public static int snmpNotWritable  = PDU.notWritable;
  public static int snmpReadOnly = PDU.readOnly;
  public static int snmpResourceUnavailable = PDU.resourceUnavailable;
  public static int snmpTooBig = PDU.tooBig;
  public static int snmpUndoFailed  = PDU.undoFailed;
  public static int snmpWrongEncoding = PDU.wrongEncoding;
  public static int snmpWrongLength  = PDU.wrongLength;
  public static int snmpWrongType = PDU.wrongType;
  public static int snmpWrongValue  = PDU.wrongValue;

  public static int UNKNOWN_CODE = 0;
  public static int NO_RESPONSE = 1;
  public static int AGT_ERROR = 2;
  public static int AGT_NO_OBJECT = 3;
  public static int AGT_NO_INSTANCE = 4;
  public static int ADAPTER_ERROR = 5;
  public static int NULL_VALUE = 6;

  private int agentErrCode;
  private int protocolErrCode = snmpNoError;
  public SnmpDataException(int errCode, String defaultString, Exception nestedExc, String suffixString, boolean appExc)
  {
      super(defaultString, nestedExc);
      applicationException = false;
      MessageBySpecChar = "";
      this.errCode = errCode;
      this.nestedExc = nestedExc;
      this.suffixString = suffixString;
      applicationException = appExc;
  }
  public SnmpDataException(int e, String defaultString,
	        Exception nestedExc, String suffixString) {
	        this(e, defaultString, nestedExc, suffixString,
	            e >= 10000);
	    }
  public SnmpDataException(String message, Exception e)
  {
     // super(-1, message, e);
      this(-2, message, e, null);
      this.protocolErrCode =  ADAPTER_ERROR;
  }

  public SnmpDataException(int agentErrCode, String message)
  {
    //super(-1, message);
//    super(-2, message);
    this(-2, message, null, null);
    this.agentErrCode = agentErrCode;
  }

  public SnmpDataException(int agentErrCode, int protocolErr, String message)
  {
    //super(-1, message);
//    super(-2, message);
	this(-2, message, null, null);
    this.agentErrCode = agentErrCode;
    protocolErrCode = protocolErr;
  }

  public int getAgentErrCode()
  {
    return agentErrCode;
  }

  public int getProtocolErrCode()
  {
    return protocolErrCode;
  }
  public static final int invalidLogin = 100;
  public static final int OBJ_NOT_FOUND = 101;
  protected int errCode;
  protected Exception nestedExc;
  protected String suffixString;
  protected boolean applicationException;
  @SuppressWarnings("rawtypes")
  protected static Hashtable expTable = new Hashtable();
  protected static ResourceBundle resourceBundle;
  protected String MessageBySpecChar;
}
