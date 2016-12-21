package com.nm.server.adapter.syslog;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.nm.base.coding.PortIndex;
import com.nm.descriptor.NeDescriptor;
import com.nm.descriptor.enumdefine.SubTpType;
import com.nm.mpls.oam.MaintenanceEntityTransient;
import com.nm.nmm.res.ManagedElement;
import com.nm.server.config.mpls.oam.MaintenanceEntityManager;
import com.nm.server.config.util.TPUtil;

public class AlarmParser {
   protected static Log log = LogFactory.getLog(AlarmParser.class);
   private TPUtil tpUtil = new TPUtil();
   
   private List<MaintenanceEntityManager> oamMeManagerList = null;
    
   private static AlarmParser instance = new AlarmParser();
   private AlarmParser(){}
   
   public static AlarmParser getInstance(){
	   return instance;
   }
   
   public void setMaintenanceEntityManagers(List<MaintenanceEntityManager> oamMeManagerList){
	   this.oamMeManagerList = oamMeManagerList;
   }
   
   public  Map<String, Object> parse(NeDescriptor neDes, ManagedElement me, String message){
	   try{
		   log.info("parse --->" + message);
		   String alarmName = null;;
		   Boolean occur = null;
		   Integer slot = 0;
		   Integer port = null;
		   Integer md = 0;
		   Integer ma = 0;
		   Integer mep = 0;
		   SubTpType tpType = null;
		   Integer vlan = 0;
		   
		   Integer almLevel = 4;
		   Date date = new Date();
		   Long occurTime = date.getTime();
		   Long clearTime = date.getTime();;
		   
		   int index = message.indexOf("/");
		   if(index > 0){
			   int lastIndex = Math.min(index + 2, message.length());
			   String ifName = message.substring(index - 1, lastIndex);
               log.info("***ifName***:" + ifName);
               log.info("neDes:" + neDes);
               log.info("me:" + me);
			   PortIndex pi = tpUtil.parseIfIndexBy(neDes, me, ifName);
			   log.info("pi:" + pi);
			   if(pi == null){
				   return null;
			   }
			   if(message.contains("Link Up:")){
				   occur = false;
				   alarmName = "ETH_LINK_DOWN";
				   
				   slot = pi.getSlot();
				   port = pi.getLabel().getPort();
				   tpType = pi.getPortType();
			   }else if(message.contains("Link Down:")){
				   occur = true;
				   alarmName = "ETH_LINK_DOWN";
				   
				   slot = pi.getSlot();
				   port = pi.getLabel().getPort();
				   tpType = pi.getPortType();
			   }else if(message.contains("SFP removed")){
				   occur = true;
				   alarmName = "OPT_MOD_FAIL_T";
				   
				   slot = pi.getSlot();
				   port = pi.getLabel().getPort();
				   tpType = pi.getPortType();
			   }else if(message.contains("SFP inserted")){
				   occur = false;
				   alarmName = "OPT_MOD_FAIL_T";
				   
				   slot = pi.getSlot();
				   port = pi.getLabel().getPort();
				   tpType = pi.getPortType();
			   }else if(message.contains("SFP lost power")){
				   occur = true;
				   alarmName = "OPT_MOD_LOS_T";
				   
				   slot = pi.getSlot();
				   port = pi.getLabel().getPort();
				   tpType = pi.getPortType();
			   }
		   }

		   else if(message.contains("device lost power")){
			   occur = true;
			   alarmName = "POW_DOWN";
		   }
		   else if(message.contains("MepId:")){
//			   MdIndex:[6] MaIndex:[6] MepId:[511] Defect:[RDICCM]
			   

			   int s = message.indexOf("MdIndex");
			   if(s > 0){
				   int t = message.indexOf("]", s);
				   md = Integer.valueOf(message.substring(s+9, t));
			   }
			   
			   
			   s = message.indexOf("MaIndex");
			   if(s > 0){
				   int t = message.indexOf("]", s);
				   ma = Integer.valueOf(message.substring(s+9, t));
			   }
			   
			   
			   s = message.indexOf("MepId");
			   if(s > 0){
				   int t = message.indexOf("]", s);
				   mep = Integer.valueOf(message.substring(s+7, t));
			   }
			   
			   tpType = this.getSubTpType(me.getId(), 0, md, ma, mep);
               alarmName = getAlarmName(tpType, message); 
               

			   int i = message.indexOf("Defect:");
			   int j = message.indexOf("]", i);
			   
			   if(i > 0 && j > 0){
				   occur = true;

			   }else{
				   occur = false;
			   }
			   
		   }
		   else{
			   return null;
		   }
		   
		   
		   if(slot == 0){
			   slot = 1;
		   }
		   if(port == null){
			   log.info("port == null");
			   return null;
		   }
	   
		   Map<String, Object> alarmMap = new HashMap<String, Object>();
		   alarmMap.put("occur", occur);
		   alarmMap.put("alarmName", alarmName);
		   alarmMap.put("slot", slot);
		   alarmMap.put("port", port);
		   alarmMap.put("md", md);
		   alarmMap.put("ma", ma);
		   alarmMap.put("mep", mep);
		   alarmMap.put("vlanId", vlan);
	       alarmMap.put("tpType", tpType);
		   alarmMap.put("occurTime", occurTime);
		   if(!occur){
			   alarmMap.put("clearTime", clearTime);
		   }
		   alarmMap.put("almLevel", almLevel);
		   log.info("AlarmParser:" + alarmMap);
		   return alarmMap;
	   }catch(Exception ex){
		   log.error(ex, ex);
	   }
	   return null;
   }
   
   private String getAlarmName(SubTpType tpType, String almStr){
	   String alarmName = null;
	   if(almStr.contains("LOC")){//连续性丢失（LOC）
		   alarmName = "LOC";
//		   ETH_CFM_LOC_T
//		   VS_LOC
//		   LSP_LOC
//		   PW_LOC
	   }else if(almStr.contains("RDI")){//远端缺陷指示（RDI）
		   alarmName = "RDI";
//		   ETH_CFM_RDI_T
//		   VS_RDI
//		   LSP_RDI
//		   PW_RDI
	   }else if(almStr.contains("MEG")){//未期望的MEG
		   alarmName = "MEG";
//		   ETH_CFM_MMG_T
//		   VS_MMG
//		   LSP_RDI
//		   PW_RDI				   
	   }else if(almStr.contains("MEP")){//未期望的MEP  //未期望的CV包周期
		   alarmName = "MEP";
//		   ETH_CFM_MEP_T
//		   VS_MEP
//		   LSP_MEP
//		   PW_MEP
	   }else if(almStr.contains("UNM")){//未期望的UNM //未期望的CV包周期
		   alarmName = "UNM";
//		   ETH_CFM_UNM_T
//		   VS_UNM
//		   LSP_UNM
//		   PW_UNM
	   }else if(almStr.contains("AIS")){
		   alarmName = "AIS";
	   }else if(almStr.contains("VP_FAIL")){
		   alarmName = "VP_FAIL";
	   }else if(almStr.contains("FRE")){
		   alarmName = "FRE";
	   }else if(almStr.contains("CRE")){
		   alarmName = "CRE";
	   }
	   
	   if(tpType != null){
		   switch(tpType){
		   case ETH:
			   alarmName = "ETH_CFM_" + alarmName + "_T";
			   break;
		   case LSP:
			   alarmName = "LSP_" + alarmName;
			   break;
		   case PW:
			   alarmName = "PW_" + alarmName;
			   break;
		   case VS:
			   alarmName = "VS_" + alarmName;
			   break;
			   
		   }
	   }
	   
	   return alarmName;
   }
   
   
   private SubTpType getSubTpType(int neId, int cardId, int md, int ma, int mep){

	   if (oamMeManagerList != null) {
		   MaintenanceEntityTransient mem = null;
			for (MaintenanceEntityManager mm : oamMeManagerList) {
				mem = mm.getMaintenanceEntityTransient(neId, cardId, md, ma, mep);
				if (mem != null) {
					break;
				}
			}
			if (mem != null) {
				return mem.getTpType();
			} else {
				log.error("mem == null");
			}
		}
	   return null;
   }
}
