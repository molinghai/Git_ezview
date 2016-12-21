package com.nm.server.adapter.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.TelnetClient;

import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.NeDescriptor;
import com.nm.error.ErrorMessageTable;
import com.nm.server.adapter.base.comm.SnmpNewMessage;
import com.nm.server.common.access.DataAccess;
import com.nm.server.common.util.ToolUtil;

public class CliDataAccess implements DataAccess {
	protected static Log log = LogFactory.getLog(CliDataAccess.class);
	private String ip;
	private int port = 23;
	private String user;
	private String password;
	private NeDescriptor nedes;
	
	private TelnetClient tc;
	private InputStream in; // 输入流，接收返回信息  
    private PrintStream out; //像 服务器写入 命令  
    
    private String globalMode = ">";  // 全局模式
    private String prompt = "#"; //结束标识字符串,Windows中是>,Linux中是#  
    private char promptChar = '#';   //结束标识字符 
//    private String moreStr = "--More-- or (q)uit";
    
    private int cliTimeOut = 100000;  // 单位毫秒
    protected int reConnectCounter = 0;
    protected final int reConnectMaxTime = 4;
    private boolean neMultiUserEnable = false;
    private int telnetConnectErrorCode = ErrorMessageTable.SUCCESS;
	
    protected CliDataAccess(){}
	public CliDataAccess getInstance(String ip, int port, String user, String password, NeDescriptor nedes){
		CliDataAccess cli = new CliDataAccess(ip, port, user, password, nedes);
		return cli;
	}
	public CliDataAccess(String ip, int port, String user, String password, NeDescriptor nedes){
		try {  
			tc = new TelnetClient();  
			
			cliTimeOut = this.readCliTimeOutFromConfig(cliTimeOut);
			if(!connect(ip, port, user, password, nedes)){
				log.error("Telnet connect failed! ip:" + ip + " port:" + port);
			}
	    }catch(Exception ex) {  
	    	log.error(ex, ex);
	    }  
	}
	
	/**
	 * 从config.properties配置文件读telnet超时时间"CLI_TIMEOUT_SEC"
	 * */
	private int readCliTimeOutFromConfig(int cliTimeOut) {
		int defaultCliTimeOut = cliTimeOut / 1000;
		String cliTimeOutStr = ToolUtil.getProperty("CLI_TIMEOUT_SEC");
		if (cliTimeOutStr != null && cliTimeOutStr.matches("[0-9]+")) {
			defaultCliTimeOut = Integer.valueOf(cliTimeOutStr);
			log.info("read value of cliTimeOut from config.properties is "  + defaultCliTimeOut + "s");
		} else {
			log.info("Failed when read value of cliTimeOut from config.properties, use defaultValue "  + defaultCliTimeOut + "s");
		}
		String neMultiUserEnableStr = ToolUtil.getProperty("NE_MULTI_USER_ENABLE");
		if (neMultiUserEnableStr != null && neMultiUserEnableStr.trim().equals("1")) {
			neMultiUserEnable = true;
			log.info("read value of NE_MULTI_USER_ENABLE from config.properties is "  + neMultiUserEnableStr + " (enable:1, disable:0)");
		} else {
			neMultiUserEnable = false;
			log.info("Failed when read value of NE_MULTI_USER_ENABLE from config.properties, use defaultValue 0(disable)");
		}
		return defaultCliTimeOut * 1000;
	}
	
	public boolean reconnect(){
		if (out != null) {
			try {
				out.close();
			} catch (Exception e) {
				log.error(e, e);
			} finally {
				out = null;
			}
		}
		if (in != null) {
			try {
				in.close();
			} catch (IOException e) {
				log.error(e, e);
			} finally {
				in = null;
			}
		}
		try {
			if(tc != null && tc.isConnected()){
				tc.disconnect();
			}
		} catch (IOException e) {
			log.error(e, e);
		} finally {
			tc = new TelnetClient();
		}
		if (reConnectCounter >= reConnectMaxTime) {
			reConnectCounter = 0;
			log.info("reconnect ip:" + ip + " " + port + "$$$$$$$$$$$$$$$$$$$$$ current reConnectCounter = " + reConnectCounter + ", reconect failed, return!!!");
			this.telnetConnectErrorCode = ErrorMessageTable.telnetFailedError;
			return false;
		}
		reConnectCounter++;
		log.info("reconnect ip:" + ip + " " + port + "$$$$$$$$$$$$$$$$$$$$$ current reConnectCounter = " + reConnectCounter);
		this.telnetConnectErrorCode = ErrorMessageTable.SUCCESS;
		return connect(ip, port, user, password, nedes);
	}
	
	public boolean isConnected() throws IOException{
		return (tc.isConnected() && out != null && !out.checkError() && in != null);
	}
	
	public void reSetUserAndPwd(String user, String password, NeDescriptor nedes) {
		cliTimeOut = this.readCliTimeOutFromConfig(cliTimeOut);
		FunctionDescriptor funcDse = nedes.getFuncDesByName("neMulitUserSupport");
		if (neMultiUserEnable && funcDse != null && funcDse.getSupported()) {
			this.user = user;
			this.password = password;
			if (password == null || password.isEmpty()) {
				if(nedes.getUserName() != null){
					this.user = nedes.getUserName();
		        }  else {
		        	this.user = null;
		        }
		        if(nedes.getPassword() != null){
		        	this.password = nedes.getPassword();
		        } else {
		        	this.password = null;
		        }
			}
		} else {
			if(nedes.getUserName() != null){
				this.user = nedes.getUserName();
	        }  else {
	        	this.user = null;
	        }
	        if(nedes.getPassword() != null){
	        	this.password = nedes.getPassword();
	        } else {
	        	this.password = null;
	        }
		}
		this.telnetConnectErrorCode = ErrorMessageTable.SUCCESS;
	}

	public boolean connect(String ip, int port, String user, String password, NeDescriptor neDes){
		try {  
			this.ip = ip;
			this.port = port;
			this.nedes = neDes;
			reSetUserAndPwd(user, password, neDes);
			log.info("ip:" + ip + " " + port);
			try {
				tc.addOptionHandler(new org.apache.commons.net.telnet.WindowSizeOptionHandler(150,14, true, true, false, false));
			} catch (InvalidTelnetOptionException e) {
				log.info("InvalidTelnetOptionException on tc.addOptionHandler !!");
			}
            tc.connect(ip, port);
            tc.setDefaultTimeout(cliTimeOut);
            tc.setConnectTimeout(cliTimeOut);
            tc.setSoTimeout(cliTimeOut);
            tc.setKeepAlive(true);
            in = tc.getInputStream();  
            out = new PrintStream(tc.getOutputStream());  
            if(this.user != null){
            	String recho = readUntilLogin();
            	if (!writeToLog(recho)) {
                	return false;
                }
            	if (recho.endsWith("Username:")) {
            		write(this.user);
            	} else {
            		this.telnetConnectErrorCode = ErrorMessageTable.TELNET_USERNAME_OR_PWS_INCORRENT;
            		return false;
            	}
            }
            if(this.password != null){
                String recho = readUntilLogin();
            	if (!writeToLog(recho)) {
                	return false;
                }
            	if (recho.endsWith("Password:")) {
            		write(this.password);
            	} else {
            		this.telnetConnectErrorCode = ErrorMessageTable.TELNET_USERNAME_OR_PWS_INCORRENT;
            		return false;
            	}
            }
            String recho = readUntilLogin();
            if (!writeToLog(recho)) {
            	return false;
            }
            if (!recho.endsWith(globalMode)) {
        		this.telnetConnectErrorCode = ErrorMessageTable.TELNET_USERNAME_OR_PWS_INCORRENT;
        		return false;
        	}
            write("enable");  // 进入特权模式
            if (!writeToLog(readUntil(prompt))) {
            	return false;
            }
            
            String confMode = nedes.getConfMode();
            if(confMode != null && !confMode.isEmpty()){
        	   write(confMode);  // 进入配置模式
               String rs = readUntil(null);  
               if(!writeToLog(rs) && rs != null && rs.contains("Failed")){  
                   return false; 
               }  
            }
            this.telnetConnectErrorCode = ErrorMessageTable.SUCCESS;
	    }catch(Exception ex) {  
	    	log.error("ip:" + ip + ", port:" + port, ex);
	    	this.telnetConnectErrorCode = ErrorMessageTable.telnetFailedError;
	    	return false;
	    }  
		return true;
	}
	
	public boolean reconnect(String userName, String password){
		if (out != null) {
			try {
				out.close();
			} catch (Exception e) {
				log.error(e, e);
			} finally {
				out = null;
			}
		}
		if (in != null) {
			try {
				in.close();
			} catch (IOException e) {
				log.error(e, e);
			} finally {
				in = null;
			}
		}
		try {
			if(tc != null){
				tc.disconnect();
			}
		} catch (IOException e) {
			log.error(e, e);
		} finally {
			tc = new TelnetClient();
		}
		if (reConnectCounter >= reConnectMaxTime) {
			reConnectCounter = 0;
			log.info("reconnect ip:" + ip + " " + port + "$$$$$$$$$$$$$$$$$$$$$ current reConnectCounter = " + reConnectCounter + ", reconect failed, return!!!");
			return false;
		}
		reConnectCounter++;
		log.info("reconnect ip:" + ip + " " + port + "$$$$$$$$$$$$$$$$$$$$$ current reConnectCounter = " + reConnectCounter);
		
		try{
			log.info("ip:" + ip + " " + port);
			try {
				tc.addOptionHandler(new org.apache.commons.net.telnet.WindowSizeOptionHandler(150,14, true, true, false, false));
			} catch (InvalidTelnetOptionException e) {
				log.info("InvalidTelnetOptionException on tc.addOptionHandler !!");
			}
            tc.connect(ip, port);
            tc.setDefaultTimeout(20000);
            tc.setConnectTimeout(20000);
            tc.setSoTimeout(20000);
            tc.setKeepAlive(true);
            in = tc.getInputStream();  
            out = new PrintStream(tc.getOutputStream());  
            
            if(userName != null){
            	if (!writeToLog(readUntil("Username:"))) {
                	return false;
                }
                write(userName);  
            }
            if(password != null){
            	if (!writeToLog(readUntil("Password:"))) {
                	return false;
                }
                write(password);  
            } 
            StringBuffer sb = new StringBuffer();  
            try {  
                char ch;  
                int code = -1;  
                while ((code = in.read()) != -1) {  
                    ch = (char)code;  
                    sb.append(ch); 
                    //匹配到结束标识时返回结果
                    if (ch == '>' && sb.toString().endsWith(">") || ch == '#' && sb.toString().endsWith("#")) {  
                        return true;  
                    }
                      
                    if(sb.toString().contains("Login Failed")){  
                        return false;  
                    }  
                }  
            } catch (Exception ex) {
            	log.info(ip + ">>>>>>>>>>>>>>>" + sb.toString());
            	return false;
            }
            
		}catch(Exception ex) {  
	    	log.error("ip:" + ip + ", port:" + port, ex);
	    	return false;
	    }  
		return true;
	}
	
	private boolean writeToLog(String cmdPrint) {
		if (cmdPrint != null && !cmdPrint.isEmpty()) {
			log.info(ip + ">>>>>>>>>>>>>>>" + cmdPrint);
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean addDatas(FunctionDescriptor fd, List<Map<String, Object>> values) {
		// TODO Auto-generated method stub
		try{
			StringBuffer sb = new StringBuffer();  
			for(Map<String, Object> v : values){
				sb.setLength(0);
				sb.append("mpls-tp ");
				sb.append(fd.getName());
				
				Set<Entry<String, Object>> es = v.entrySet();
				for(Entry<String, Object> e : es){
					sb.append(" ");
					sb.append(e.getKey());
					sb.append(" ");
					sb.append(e.getValue());
				}
				sendCommand(sb.toString(),null);
			}

		}catch(Exception ex){
			log.error(ex, ex);
			return false;
		}
		return true;
	}

	@Override
	public boolean deleteDatas(FunctionDescriptor fd, List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
        try{
        	StringBuffer sb = new StringBuffer();  
        	sb.append("no ");
        	sb.append(fd.getName());
        	
        	if(conditions != null){
        		for(Map<String, Object> m : conditions){
        			Set<Entry<String, Object>> es = m.entrySet();
    				for(Entry<String, Object> e : es){
    					sb.append(" ");
    					sb.append(e.getKey());
    					sb.append(" ");
    					sb.append(e.getValue());
    				}
        		}
        	}
        	
        	sendCommand(sb.toString(),null);
		}catch(Exception ex){
			log.error(ex, ex);
			return false;
		}
		return true;
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor fd) {
       return getDatas(fd, null);
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor fd, List<String> fields) {
        return getDatas(fd, fields, null);
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor fd,
			List<String> fields, List<Map<String, Object>> conditions) {
        try{
        	StringBuffer sb = new StringBuffer();  
        	sb.append("show ");
        	sb.append(fd.getName());
        	
        	if(conditions != null){
        		for(Map<String, Object> m : conditions){
        			Set<Entry<String, Object>> es = m.entrySet();
    				for(Entry<String, Object> e : es){
    					sb.append(" ");
    					sb.append(e.getKey());
    					sb.append(" ");
    					sb.append(e.getValue());
    				}
        		}
        	}
        	
        	String res = sendCommand(sb.toString(),null);
        	if(res != null && !res.isEmpty()){
        		//解析返回结果,只保留fields中对应的值
        		//
        		//
        		//
        		//
        		
        	}
		}catch(Exception ex){
			log.error(ex, ex);
		}
		return null;
	}

	@Override
	public boolean updateDatas(FunctionDescriptor fd,
			Map<String, Object> value, List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
        try{
        	StringBuffer sb = new StringBuffer();  
			for(Map<String, Object> c : conditions){
				sb.setLength(0);
				sb.append("mpls-tp ");
				sb.append(fd.getName());
				
				//设置值
				Set<Entry<String, Object>> ves = value.entrySet();
				for(Entry<String, Object> ve : ves){
					sb.append(" ");
					sb.append(ve.getKey());
					sb.append(" ");
					sb.append(ve.getValue());
				}
				
				//设置条件
				Set<Entry<String, Object>> es = c.entrySet();
				for(Entry<String, Object> e : es){
					sb.append(" ");
					sb.append(e.getKey());
					sb.append(" ");
					sb.append(e.getValue());
				}
				sendCommand(sb.toString(),null);
			}
		}catch(Exception ex){
			log.error(ex, ex);
			return false;
		}
		return true;
	}
	
	/** 
     * 读取分析结果 
     *  
     * @param pattern   匹配到该字符串时返回结果 
     * @return 
     */  
    public String readUntil(String pattern) {  
        StringBuffer sb = new StringBuffer();  
        try {  
            char lastChar = (char)-1;  
            boolean flag = pattern!=null&&pattern.length()>0;  
            if(flag)  
                lastChar = pattern.charAt(pattern.length() - 1);  
            char ch;  
            int code = -1;  
            while ((code = in.read()) != -1) {  
                ch = (char)code;  
                sb.append(ch);  
                  
                //匹配到结束标识时返回结果  
                if (flag) {  
                    if (ch == lastChar && sb.toString().endsWith(pattern)) {  
                        return sb.toString();  
                    } else if (sb.toString().endsWith("--More--")) {
                    	return sb.toString();
                    }
                }else{  
                    //如果没指定结束标识,匹配到默认结束标识字符时返回结果  
                    if(ch == promptChar)  
                        return sb.toString();  
                }  
                //登录失败时返回结果  
                if(sb.toString().contains("Login Failed")){  
                    return null;  
                }  
            }  
        } catch (Exception ex) {  
        	log.error(ex, ex);  
        	try{
    			this.reconnect();
    		}catch(Exception e){
    			log.error(ex, e);
    		}
        	return null;
        }  
        return sb.toString();  
    }  
    
    /** 
     * 读取分析结果 
     *  
     * @param pattern   匹配到该字符串时返回结果 
     * @return 
     */  
    public String readUntilLogin() {  
        StringBuffer sb = new StringBuffer();  
        try {  
            char lastChar = ':';
            char lastCharOr = globalMode.charAt(globalMode.length() - 1); 
            char ch;  
            int code = -1;  
            while ((code = in.read()) != -1) {  
                ch = (char)code;  
                sb.append(ch);  
                  
                //匹配到结束标识时返回结果  
                if (ch == lastChar || ch == lastCharOr) {  
                    return sb.toString();  
                }
            }  
        } catch (Exception ex) {  
        	log.error(ex, ex);  
        	this.telnetConnectErrorCode = ErrorMessageTable.telnetFailedError;
        	return null;
        }  
        return sb.toString();  
    }
    
    public String readUntil_Array(String pattern) {  
        StringBuffer sb = new StringBuffer();  
        try {  
            char lastChar = (char)-1;  
            boolean flag = pattern!=null&&pattern.length()>0;  
            if(flag)  
                lastChar = pattern.charAt(pattern.length() - 1);  
            char ch;  
            int length = -1;  
            byte[] readBuff = new byte[4096];
            while ((length = in.read(readBuff)) != -1) {  
                ch = (char)readBuff[length-1];  
                for (int off = 0; off < length; off++) {
                	sb.append((char)readBuff[off]); 
                }
                  
                //匹配到结束标识时返回结果  
                if (flag) {  
                    if (ch == lastChar && sb.toString().endsWith(pattern)
                    		&& !sb.toString().endsWith("\r\n[\\s]*"+lastChar)
                    		&& !sb.toString().matches("[\\s\\S]+?\r\n[\\s]*"+lastChar+"+")) {  
                    	String returnStr = sb.toString();
                    	writeToLog(returnStr);
                    	returnStr = returnStr.replaceAll("\b+\\s+\b+", "");
                    	returnStr = returnStr.replaceAll("\0+", "");
                    	returnStr = returnStr.replaceAll("\b+", "");
                    	return returnStr;
                    } else if (sb.toString().endsWith("--More--")) {
                    	String returnStr = sb.toString();
                    	writeToLog(returnStr);
                    	returnStr = returnStr.replaceAll("\b+\\s+\b+", "");
                    	returnStr = returnStr.replaceAll("\0+", "");
                    	returnStr = returnStr.replaceAll("\b+", "");
                    	return returnStr;
                    } else if (sb.toString().trim().endsWith("--More--")) {
                    	String returnStr = sb.toString();
                    	writeToLog(returnStr);
                    	returnStr = returnStr.replaceAll("\b+\\s+\b+", "");
                    	returnStr = returnStr.replaceAll("\0+", "");
                    	returnStr = returnStr.replaceAll("\b+", "");
                    	//int off = returnStr.lastIndexOf("--More--") + 8;
                    	//returnStr = returnStr.substring(0, off);
                    	return returnStr;
                    } else if (sb.toString().trim().endsWith("[yes/no]:")) {
                    	String returnStr = sb.toString();
                    	return returnStr;
                    }
                }else{  
                    //如果没指定结束标识,匹配到默认结束标识字符时返回结果  
                    if(ch == promptChar)  
                        return sb.toString();  
                }  
                //登录失败时返回结果  
                if(sb.toString().contains("Login Failed")){  
                    return null;  
                }  
            }  
        } catch (Exception ex) {  
        	log.error(ex, ex);  
        	try{
    			this.reconnect();
    		}catch(Exception e){
    			log.error(ex, e);
    		}
        	return null;
        }  
        return sb.toString();  
    }
      
    /** 
     * 发送命令 
     *  
     * @param value 
     */  
    public void write(String value) {  
    	try{
    		writeToLog(ip + " port:" + port + " send: " + value);
    		out.println(value);  
            out.flush();
    	}catch(Exception e){
    		log.error(e, e);
    		try{
    			this.reconnect();
    		}catch(Exception ex){
    			log.error(ex, ex);
    			return;
    		}
    		writeToLog(ip + " send Second: " + value);
    		out.println(value);  
            out.flush();
    	}
    }  
      
    /** 
     * 发送命令,返回执行结果 
     *  
     * @param command 
     * @return 
     */  
    public String sendCommand(String command,SnmpNewMessage msg) {  
    	try {
			if (out == null || out.checkError() 
					|| !tc.getKeepAlive() 
					|| !tc.isConnected()) {
//				log.info("out.checkError():" + out.checkError());
				log.info("tc.isConnected():" + tc.isConnected());
				if (!reconnect()) {
					return null;
				}
			}
		} catch (Exception e) {
			log.error(e, e);
			return null;
		}
		
		SnmpNewMessage locMsg = msg != null ? msg : new SnmpNewMessage();
		locMsg.setTimeThreshold(cliTimeOut);
		log.info("************read from telnet data timeThreshold = "+ locMsg.getTimeThreshold() + "**********************");
		String cmdPrint = null;
        try {
        	write(command); 
        	locMsg.setBeginTime(new Date());
			cmdPrint = readUntil_Array(prompt);
			String returnEcho = "";
			if (!nedes.getProductName().equalsIgnoreCase("NE470")
        			&& !nedes.getProductName().equalsIgnoreCase("NE471")
        			&& !nedes.getProductName().equalsIgnoreCase("NE472")) 
			{
				while (cmdPrint != null && !cmdPrint.contains(command) 
						&& !locMsg.isTimeOut()) {
					cmdPrint = readUntil_Array(prompt);
				}
			} else {
				while ((cmdPrint == null || cmdPrint.isEmpty()) 
						&& !locMsg.isTimeOut()) {
					cmdPrint = readUntil_Array(prompt);
				}
			}
			if (cmdPrint != null && cmdPrint.endsWith("[yes/no]:")) {
				returnEcho += cmdPrint;
				writeToLog("--?[yes/no]:--");
				write("yes");
				cmdPrint = readUntil_Array(prompt);
				if (cmdPrint != null) {
					returnEcho += cmdPrint;
				}
			}
			while (cmdPrint != null && (cmdPrint.matches("^[\\s\\S]*[\\s]*--More--[\\s]*(\\sor\\s\\(q\\)uit){0,1}(\r\\s+\r){0,1}$")
					|| cmdPrint.matches("^[\\s\\S]*[\\s]*--More--[\\s]*$")) 
					&& !locMsg.isTimeOut()) {
				returnEcho += cmdPrint;
				writeToLog("--More--");
				write(" ");
				cmdPrint = readUntil_Array(prompt);
			}
			if (cmdPrint != null) {
				returnEcho += cmdPrint;
			}
			if (returnEcho != null) {
				returnEcho = returnEcho.replaceAll("\r\n[\\s]*--More--[\\s]*(or\\s\\(q\\)uit){0,1}\r\\s+\r", "\r\n");
				returnEcho = returnEcho.replaceAll("\\s--More--\\s", "");
				returnEcho = returnEcho.replaceAll("--More--", "");
			}
			if (!writeToLog(returnEcho)) {
				log.error("read from telnet error...");
			}
			cmdPrint = returnEcho;
			
			if(locMsg.isTimeOut()) {
				log.info("************read from telnet data timeout! timeThreshold = "+ locMsg.getTimeThreshold() + "**********************");
				return null;
			}
			
		} catch (Exception e) {
			try{
    			this.reconnect();
    		}catch(Exception ex){
    			log.error(ex, ex);
    		}
			log.error(e, e);
			return null;
		}
    	
        return cmdPrint;  
    } 
    
    public String sendCommand(String command,SnmpNewMessage msg, int cliTimeOut) {  
    	try {
			if (out == null || out.checkError() 
					|| !tc.getKeepAlive() 
					|| !tc.isConnected()) {
//				log.info("out.checkError():" + out.checkError());
				log.info("tc.isConnected():" + tc.isConnected());
				if (!reconnect()) {
					return null;
				}
			}
		} catch (Exception e) {
			log.error(e, e);
			return null;
		}
		
		SnmpNewMessage locMsg = msg != null ? msg : new SnmpNewMessage();
		locMsg.setTimeThreshold(cliTimeOut);
		log.info("************read from telnet data timeThreshold = "+ locMsg.getTimeThreshold() + "**********************");
		String cmdPrint = null;
        try {
        	write(command); 
        	locMsg.setBeginTime(new Date());
			cmdPrint = readUntil_Array(prompt);
			String returnEcho = "";
			if (!nedes.getProductName().equalsIgnoreCase("NE470")
        			&& !nedes.getProductName().equalsIgnoreCase("NE471")
        			&& !nedes.getProductName().equalsIgnoreCase("NE472")) 
			{
				while (cmdPrint != null && !cmdPrint.contains(command) 
						&& !locMsg.isTimeOut()) {
					cmdPrint = readUntil_Array(prompt);
				}
			} else {
				while ((cmdPrint == null || cmdPrint.isEmpty()) 
						&& !locMsg.isTimeOut()) {
					cmdPrint = readUntil_Array(prompt);
				}
			}
			if (cmdPrint != null && cmdPrint.endsWith("[yes/no]:")) {
				returnEcho += cmdPrint;
				writeToLog("--?[yes/no]:--");
				write("yes");
				cmdPrint = readUntil_Array(prompt);
				if (cmdPrint != null) {
					returnEcho += cmdPrint;
				}
			}
			while (cmdPrint != null && (cmdPrint.matches("^[\\s\\S]*[\\s]*--More--[\\s]*(\\sor\\s\\(q\\)uit){0,1}(\r\\s+\r){0,1}$")
					|| cmdPrint.matches("^[\\s\\S]*[\\s]*--More--[\\s]*$")) 
					&& !locMsg.isTimeOut()) {
				returnEcho += cmdPrint;
				write(" ");
				cmdPrint = readUntil_Array(prompt);
			}
			if (cmdPrint != null) {
				returnEcho += cmdPrint;
			}
			if (returnEcho != null) {
				returnEcho = returnEcho.replaceAll("\r\n[\\s]*--More--[\\s]*(or\\s\\(q\\)uit){0,1}\r\\s+\r", "\r\n");
//				returnEcho = returnEcho.replaceAll("\r\n[\\s]*--More--[\\s]*", "\r\n");
				returnEcho = returnEcho.replaceAll("\\s--More--\\s", "");
				returnEcho = returnEcho.replaceAll("--More--", "");
			}
			if (!writeToLog(returnEcho)) {
				log.error("read from telnet error...");
			}
			cmdPrint = returnEcho;
			
			if(locMsg.isTimeOut()) {
				log.info("************read from telnet data timeout! timeThreshold = "+ locMsg.getTimeThreshold() + "**********************");
				return null;
			}
			
		} catch (Exception e) {
			try{
    			this.reconnect();
    		}catch(Exception ex){
    			log.error(ex, ex);
    		}
			log.error(e, e);
			return null;
		}
    	
        return cmdPrint;  
    }
      
    /** 
     * 关闭连接 
     */  
    public void disconnect(){  
        try {  
//        	if (this.isConnected()) {
//        		this.write("exit");
//        	}
        	if (out != null) {
    			try {
    				out.close();
    			} catch (Exception e) {
    				log.error(e, e);
    			} finally {
    				out = null;
    			}
    		}
    		if (in != null) {
    			try {
    				in.close();
    			} catch (IOException e) {
    				log.error(e, e);
    			} finally {
    				in = null;
    			}
    		}
    		try {
    			if(tc != null){
    				tc.disconnect();
    			}
    		} catch (IOException e) {
    			log.error(e, e);
    		}
        } catch (Exception ex) {  
            log.error(ex, ex);  
        }  
    }  
  
    public void setPrompt(String prompt) {  
        if(prompt!=null){  
            this.prompt = prompt;  
            this.promptChar = prompt.charAt(prompt.length()-1);  
        }  
    }
	@Override
	public boolean addDatas(Class arg0, List<Map<String, Object>> arg1) {
		return false;
	}
	@Override
	public boolean deleteDatas(Class arg0, List<Map<String, Object>> arg1) {
		return false;
	}
	@SuppressWarnings("rawtypes")
	@Override
	public List<Map<String, Object>> getDatas(Class arg0) {
		return null;
	}
	@Override
	public List<Map<String, Object>> getDatas(Class arg0, List<String> arg1) {
		return null;
	}
	@Override
	public List<Map<String, Object>> getDatas(Class arg0, List<String> arg1,
			List<Map<String, Object>> arg2) {
		return null;
	}
	@Override
	public boolean updateDatas(Class arg0, Map<String, Object> arg1,
			List<Map<String, Object>> arg2) {
		return false;
	}

	public int getCliTimeOut() {
		return cliTimeOut;
	}  
	
	public boolean isNeMultiUserEnable() {
		return neMultiUserEnable;
	}

	public void setNeMultiUserEnable(boolean neMultiUserEnable) {
		this.neMultiUserEnable = neMultiUserEnable;
	}

	public int getTelnetConnectErrorCode() {
		return telnetConnectErrorCode;
	}
}
