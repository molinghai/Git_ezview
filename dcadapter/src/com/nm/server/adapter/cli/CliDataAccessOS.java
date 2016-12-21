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
import org.apache.commons.net.telnet.TelnetClient;

import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.NeDescriptor;
import com.nm.server.adapter.base.comm.SnmpNewMessage;
import com.nm.server.common.access.DataAccess;
import com.nm.server.common.util.ToolUtil;

public class CliDataAccessOS implements DataAccess {
	protected static Log log = LogFactory.getLog(CliDataAccessOS.class);
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
    
    private int cliTimeOut = 30000;  // 单位毫秒
    protected int reConnectCounter = 0;
    private final int reConnectMaxTime = 1;
	
	public CliDataAccessOS(String ip, int port, String user, String password, NeDescriptor nedes){
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
	
	private int readCliTimeOutFromConfig(int cliTimeOut) {
		int defaultCliTimeOut = cliTimeOut / 1000;
		String cliTimeOutStr = ToolUtil.getProperty("CLI_TIMEOUT_SEC");
		if (cliTimeOutStr != null && cliTimeOutStr.matches("[0-9]+")) {
			defaultCliTimeOut = Integer.valueOf(cliTimeOutStr);
			log.info("read value of cliTimeOut from config.properties is "  + defaultCliTimeOut + "s");
		} else {
			log.info("Failed when read value of cliTimeOut from config.properties, use defaultValue "  + defaultCliTimeOut + "s");
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
		return connect(ip, port, user, password, nedes);
	}
	
	public boolean isConnected() throws IOException{
		return (tc.isConnected() && out != null && !out.checkError() && in != null);
	}

	public boolean connect(String ip, int port, String user, String password, NeDescriptor nedes){
		try {  
			this.ip = ip;
			this.port = port;
			this.user = user;
			this.password = password;
			this.nedes = nedes;
			log.info("ip:" + ip + " " + port);
			//tc.addOptionHandler(new org.apache.commons.net.telnet.WindowSizeOptionHandler(100,21, true, true, false, false));
            tc.connect(ip, port);
            tc.setDefaultTimeout(cliTimeOut);
            tc.setConnectTimeout(cliTimeOut);
            tc.setSoTimeout(cliTimeOut);
            tc.setKeepAlive(true);
            in = tc.getInputStream();  
            out = new PrintStream(tc.getOutputStream());  
            if(this.user != null){
            	if (!writeToLog(readUntil("login:"))) {
                	return false;
                }
                write(this.user);  
            }
            if(this.password != null){
            	if (!writeToLog(readUntil("Password:"))) {
                	return false;
                }
                write(this.password);  
            }
            
            if (!writeToLog(readUntil(prompt))) {
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
    			//this.reconnect();
    		}catch(Exception e){
    			log.error(ex, e);
    		}
        	return null;
        }  
        return sb.toString();  
    }  
    
    
    public String readUntil_Array(String pattern) {  
        StringBuffer sb = new StringBuffer();  
        try {  
            char lastChar = (char)-1;  
            char lastCharOr = (char)-1; 
            boolean flag = pattern!=null&&pattern.length()>0;  
            if(flag) {
            	lastChar = pattern.charAt(pattern.length() - 1);
            	lastCharOr = globalMode.charAt(globalMode.length() - 1);
            } 
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
                    if ((ch == lastChar && sb.toString().endsWith(pattern))
                    		|| sb.toString().trim().endsWith(pattern)) {  
                    	String returnStr = sb.toString();
                    	returnStr = returnStr.replaceAll("\b+\\s+\b+", "");
                    	return returnStr;
                    } else if ((ch == lastCharOr && sb.toString().endsWith(globalMode)) 
                    		|| sb.toString().trim().endsWith(globalMode)) {  
                    	String returnStr = sb.toString();
                    	returnStr = returnStr.replaceAll("\b+\\s+\b+", "");
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
    			//this.reconnect();
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
    			//this.reconnect();
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
        	if (command.contains("bcm.user.proxy")) {
        		write(" ");
        	}
        	locMsg.setBeginTime(new Date());
			cmdPrint = readUntil_Array(prompt);
			String returnEcho = "";
			if (cmdPrint != null) {
				returnEcho += cmdPrint;
			}
			if (returnEcho != null) {
				returnEcho = returnEcho.replaceAll("\r\n[\\s]*--More--[\\s]*(or\\s\\(q\\)uit){0,1}\r\\s+\r", "\r\n");
				returnEcho = returnEcho.replaceAll("\r\n[\\s]*--More--[\\s]*", "\r\n");
				returnEcho = returnEcho.replaceAll("--More--", "");
			}
			if (!writeToLog(returnEcho)) {
				log.error("read from telnet error...");
			}
			cmdPrint = returnEcho;
			
			if(locMsg.isTimeOut()) {
				log.info("************read from telnet data timeout! timeThreshold = "+ locMsg.getTimeThreshold() + "**********************");
//				return null;
			}
			
		} catch (Exception e) {
			try{
    			//this.reconnect();
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
            if(tc != null && !tc.isConnected()){
            	tc.disconnect();
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

	@Override
	public boolean addDatas(FunctionDescriptor funcDes,
			List<Map<String, Object>> values) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteDatas(FunctionDescriptor funcDes,
			List<Map<String, Object>> conditions) {
		return false;
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor funcDes) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor funcDes,
			List<String> fields) {
		return null;
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor funcDes,
			List<String> fields, List<Map<String, Object>> conditions) {
		return null;
	}

	@Override
	public boolean updateDatas(FunctionDescriptor funcDes,
			Map<String, Object> values, List<Map<String, Object>> conditions) {
		return false;
	}  
}
