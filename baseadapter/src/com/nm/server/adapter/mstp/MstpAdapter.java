package com.nm.server.adapter.mstp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZInputStream;
import com.jcraft.jzlib.ZOutputStream;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.FunctionDescriptorLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.error.ErrorMessageTable;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.nmm.res.ManagedElement;
import com.nm.server.adapter.base.BaseAdapter;
import com.nm.server.adapter.base.CommProtocol;
import com.nm.server.adapter.base.comm.AsynTask;
import com.nm.server.adapter.base.comm.MsdhMessage;
import com.nm.server.adapter.base.comm.TaskCell;
import com.nm.server.comm.HiSysMessage;
import com.nm.server.comm.sync.SyncNotifyManager;
import com.nm.server.common.config.DeviceDest;
import com.nm.server.common.config.ResFunc;
import com.nm.server.common.config.TaskResult;
import com.nm.server.common.util.ToolUtil;

public class MstpAdapter extends BaseAdapter
{
	protected static ThreadLocal<ManagedElement> threadMe = new ThreadLocal<ManagedElement>();
	private static final Log log = LogFactory.getLog(MstpAdapter.class);
	
	@SuppressWarnings("unchecked")
	public HiSysMessage getMsg(Object destData,Object userData,ResFunc resFunc,TaskResult tRes)
	{		
		MsdhMessage msg = null;
		
		if(userData != null && resFunc != null)
		{
			byte[] userDataB = null;
			
			if(userData instanceof byte[])
			{
				userDataB = (byte[])userData;
			}
			else if(userData instanceof List<?>)
			{
				
				List<Byte> userDataByte = (List<Byte>)userData;
				
				userDataB = new byte[userDataByte.size()];
				
				for(int index = 0; index < userDataByte.size(); index++)
				{
					userDataB[index] = userDataByte.get(index).byteValue();
				}
				
				userDataByte.clear();
			}				
			
			if(userDataB != null && userDataB.length > 0)
			{
				msg = new MsdhMessage();
				
//				String destIP = ToolUtil.longToIp(getThreadMe().getAddress().getIpAddress());			
				
				byte [] srcAddr = ToolUtil.ipStringToBytes(ToolUtil.getHostIp());
//				byte [] destAddr = ToolUtil.ipStringToBytes(destIP);
				
				int ctrlPos = 0;
				
				if(resFunc.getFrameType() == 1 || userDataB[0] == (byte)0xFF)
				{
					msg.setHeadCtrlBit(1, (byte)0x20);
					
					if(userDataB[0] == (byte)0xFF)
					{
						byte[] userDataT = userDataB;
						userDataB = new byte[userDataT.length - 1];
						
						System.arraycopy(userDataT, 1, userDataB, 0, userDataB.length);
						userDataT = null;
					}
					
					ctrlPos = 1;
				} 
				
				if((userDataB[ctrlPos] & 0x08) > 0 
						&& ToolUtil.getEntryConfig().getSetOnOff() == 0) {
					tRes.setResult(ErrorMessageTable.NM_NOT_SUPPORT_SET);
				}
				
				if(resFunc.getZipType() == 1) {
					msg.setHeadByte(10, (byte)0x03);
				}
				
				/* support tabs*/
				if(this.getExtendObj() != null) {
					ManagedElement me = (ManagedElement)this.getExtendObj();
					int tabs = me.getAddress().getTabsAddress();
					
					if(tabs > 0) {
						byte headCtrl = (byte)0x10;
						msg.setHeadByte(1, (byte)(headCtrl | msg.getHeadByte(1)));
					}
				}
				
				msg.setSrcAddress(srcAddr);
//				msg.setDstAddress(destAddr);
				
				if(resFunc.getCodecType() == 0)
				{		
		    		try
		    		{
		    			msg.setFixedData(userDataB,userDataB.length);
		    		}
		    		catch (Exception e)
		    		{
		    			LogFactory.getLog(
		    					MstpAdapter.class).error("getMsg exception:",e);
		    			tRes.setResult(ErrorMessageTable.FRAME_ERROR);
		    		}
				}
//				else
//				{
//					CoDecFrame codec = (CoDecFrame)this.codecContext.getBean("codec" + resFunc.getCodecType());
//					codec.combineFrame(destData, userDataB, msg);
//				}
			} else {
				LogFactory.getLog(MstpAdapter.class).info(
						"-----" + resFunc.getCmdId() + "'s UserData is empty!-----");
			}
			

		}
		

		
		
		return msg;
	}
	
	public List<Object> getReturnUserDataL(TaskCell taskCell)
	{
		List<Object> userDataL = null;
		
		try{
			if(taskCell != null)
			{
				List<HiSysMessage> msgList = taskCell.getResMsgList();
				
				if(msgList != null && msgList.size() > 0)
				{
					// 解析用户数据字节
					userDataL = new ArrayList<Object>();
					
					for(HiSysMessage msg:msgList)
					{
						if(msg instanceof MsdhMessage) {
						
							MsdhMessage msdhMsg = (MsdhMessage)msg;
							byte[] userData = msdhMsg.getFixedData();
							if((msdhMsg.getHeadByte(10) & 0x0f) == (byte)0x03) {
								userData = this.zlibUnZip(userData);
								msdhMsg.setFixedData(userData, userData.length);
								LogFactory.getLog(MstpAdapter.class).info(
										"*********unzip UserData:" + this.bytesToHexString(userData) + "********");
							}
							userDataL.add(userData);
						}
					}	
				}			

			}	
		}catch(Exception e) {
			LogFactory.getLog(MstpAdapter.class).error(
					"getReturnUserDataL exception:", e);
		}
		
		return userDataL;
	}
	
	public void isGetFramesValid(
			NeDescriptor neDes,List<HiSysMessage> msgList, int isSync,TaskResult tRes) {
		
		if(msgList == null || msgList.isEmpty()) {
			return;
		}
		
		for(Object obj : msgList) {
			MsdhMessage msg = (MsdhMessage)obj;

			this.isGetFrameValid(neDes, msg,isSync, tRes);
			
			if(tRes.getResult() != 0) {
				return;
			}
				
		}
	}
	
	public void isGetFrameValid(
			NeDescriptor neDes,HiSysMessage hiMsg, int isSync, TaskResult tRes) {
		
		MsdhMessage msg = (MsdhMessage)hiMsg;

		boolean isMultiFrame = (msg.getHeadByte(1) & ((byte)0x20)) == 0 ? false : true;
		
		if(!isMultiFrame) {
			this.isGetFrameValid(neDes, msg.getFixedData(),isSync, tRes);
		} else {
			
			byte[] userData = new byte[msg.getFixedData().length - 1];
			
			System.arraycopy(msg.getFixedData(), 1, userData, 0, msg.getFixedData().length - 1);
			
			this.isGetFrameValid(neDes, userData , isSync, tRes);
		}
	}
	
	protected void isGetFrameValid(
			NeDescriptor neDes,byte[]userData, int isSync, TaskResult tRes) {
			
		ResFunc resFunc = this.getResFunc(
				"FrameValid",
				neDes.getProductName(),
				neDes.getVersion());
		
		if(resFunc != null) {
			resFunc.setSync(isSync);
			resFunc.setExtendObj(this.extendObj);
			resFunc.isGetFrameValid(neDes, userData, tRes);
		} else {
			this.isGetFrameValidDefault(neDes, userData, tRes);
		}
	}
	
	private void isGetFrameValidDefault(
			NeDescriptor neDes,byte[]userData, TaskResult tRes) {
			
		if(userData == null || userData.length == 0) {
			return;
		}
		
		if(userData.length == 3) {
			if(userData[2] != 0) {
				tRes.setResult(20000 + userData[2]);
			}
			
			return;
		}
		
		if(userData[2] == (byte)neDes.getSeriesID() 
				&& (userData[3] == (byte)neDes.getModelID())) {
			
			if(userData[4] != 0) {
//				tRes.setResult(20000 + userData[4]);
				return;
			}
		} else {
			tRes.setResult(ErrorMessageTable.NE_TYPE_MISMACTH);
			return;
		}
	}
	
	protected byte[] gZip(byte[] data) {
		byte[] b = null;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			GZIPOutputStream gzip = new GZIPOutputStream(bos);
			gzip.write(data);
			gzip.finish();
			gzip.close();
			b = bos.toByteArray();
			bos.close();
		} catch (Exception e) {
			LogFactory.getLog(MstpAdapter.class).error(
					"gZip exception:",e);
		}
		return b;
	}
	protected byte[] unGZip(byte[] data) {
		byte[] b = null;
		try {
			ByteArrayInputStream bis = new ByteArrayInputStream(data);
			GZIPInputStream gzip = new GZIPInputStream(bis);
			byte[] buf = new byte[1024];
			int num = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while ((num = gzip.read(buf, 0, buf.length)) != -1) {
				baos.write(buf, 0, num);
			}
			b = baos.toByteArray();
			baos.flush();
			baos.close();
			gzip.close();
			bis.close();
		} catch (Exception e) {
			LogFactory.getLog(MstpAdapter.class).error(
					"unGZip exception:",e);
		}
		return b;
	}

	protected byte[] zlibZip(byte[] object) {
		byte[] data = null;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ZOutputStream zOut = new ZOutputStream(out,JZlib.Z_DEFAULT_COMPRESSION);
			DataOutputStream objOut = new DataOutputStream(zOut);
			objOut.write(object);
			objOut.flush();
			zOut.close();
			data = out.toByteArray();
			out.close();
		} catch (Exception e) {
			LogFactory.getLog(MstpAdapter.class).error(
					"jzlib exception:",e);
		}
		return data;
	}

	protected byte[] zlibUnZip(byte[] object) {
		byte[] data = null;
		try {
			ByteArrayInputStream in = new ByteArrayInputStream(object);
			ZInputStream zIn = new ZInputStream(in);
			byte[] buf = new byte[1024];
			int num = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while ((num = zIn.read(buf, 0, buf.length)) != -1) {
				baos.write(buf, 0, num);
			}
			data = baos.toByteArray();
			baos.flush();
			baos.close();
			zIn.close();
			in.close();
		} catch (Exception e) {
			LogFactory.getLog(MstpAdapter.class).error(
					"unjzlib exception:",e);
		}
		return data;
	}
	
	protected byte[] zlibUnZipFrameData(byte[] object) {
		
		byte[] zipData = new byte[object.length - 12];
		System.arraycopy(object, 12, zipData, 0, object.length - 12);
				
		byte[] data = null;
		try {
			ByteArrayInputStream in = new ByteArrayInputStream(zipData);
			ZInputStream zIn = new ZInputStream(in);
			byte[] buf = new byte[1024];
			int num = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			while ((num = zIn.read(buf, 0, buf.length)) != -1) {
				baos.write(buf, 0, num);
			}
			data = baos.toByteArray();
			baos.flush();
			baos.close();
			zIn.close();
			in.close();
		} catch (Exception e) {
			LogFactory.getLog(MstpAdapter.class).error(
					"unjzlib exception:",e);
		}
		byte[] unzipData = new byte[data.length + 12];
		System.arraycopy(object, 0, unzipData, 0, 12);
		System.arraycopy(data, 0, unzipData, 12, data.length);
		data = unzipData;
		
		return data;
	}
	
	public String bytesToHexString(byte[] bArray) {
		StringBuffer sb = new StringBuffer(bArray.length);
		String sTemp;
		for (int i = 0; i < bArray.length; i++) {
			sTemp = Integer.toHexString(0xFF & bArray[i]);
			if (sTemp.length() < 2)
				sb.append(0);
			sb.append(sTemp.toLowerCase());
		}
		return sb.toString();
	}
	@Override
	public int processSyncResult(CsCommand cmd, AsynTask resTask,Response rs, AsynTask cmdTask)
	{
		log.info("-------------------processSyncResult------------begin-------");
		SyncNotifyManager.getInstance().notifyToClientProgress(
				resTask.getConnectionId(), 
				resTask.getMsgId(), 
				this.getNeId(), 
				30,
				"processing data begin");		
		
		if(resTask == null || rs.getResult() != 0)
		{
			return -1;
		}
		if(resTask.getTaskCellList().size() != 0)
		{
			for(TaskCell taskCell:resTask.getTaskCellList())
			{
				if(taskCell.getProtocolType() != null && !CommProtocol.CP_MSDH.equals(taskCell.getProtocolType())){
					continue;
				}
				// 一个对象对应多个帧的情况，需要组合多个帧
//				List<HiSysMessage> msgList = taskCell.getResMsgList();				
				
				ResFunc resFunc = null;
				
				// Trap
				if(cmd == null)
				{
					rs.setcmdId(taskCell.getStrID());
					resFunc = this.getResFunc(taskCell.getStrID());
				}
				else
				{
					resFunc = (ResFunc)taskCell.getObj();
				}
				
				if(resFunc == null){
					log.info("**********************error,can not find resFunc, cmdID = " + cmd.getId() + "--" + taskCell.getStrID());
					continue;
				}
				resFunc.setFuncName(cmd.getFuncName());
				// 解析用户数据字节
				List<Object> userDataL = this.getReturnUserDataL(taskCell);	
				
//				for(HiSysMessage msg:msgList)
//				{
//					MsdhMessage msdhMsg = (MsdhMessage)msg;
//					
//					byte[] userData = msdhMsg.getFixedData();
//					
//					userDataL.add(userData);
//				}	
				
				if(userDataL != null && !userDataL.isEmpty())
				{
					TaskResult tRes = new TaskResult();
					log.info("-------------------command:" + resFunc.getCmdId()+ " updateQuery------------begin-------");
					// 更新数据库
					resFunc.updateQuery(taskCell.getDestList(),userDataL,tRes);
					log.info("-------------------command:" + resFunc.getCmdId()+ " updateQuery------------end-------");
				}
				taskCell.clear();
				taskCell = null;
		
			}
			
		}
		SyncNotifyManager.getInstance().notifyToClientProgress(
				resTask.getConnectionId(), 
				resTask.getMsgId(), 
				this.getNeId(), 
				40,
				"processing data end");				
		log.info("-------------------processSyncResult------------end-------");
		return 0;
	}
	
	@Override
	public TaskCell getTaskCell(
			int cmdType,
			List<Object> destList,
			String clsName,
			List<Object> objList,
			int isSync,
			TaskResult tRes, String funcName)
	{		
		
		
		
		TaskCell taskCell = new TaskCell(clsName,destList);		
		taskCell.setProtocolType(CommProtocol.CP_MSDH);
//		ResFunc resFunc = this.getResFunc(clsName);	
		DeviceDest dest = null;
		if(destList != null && !destList.isEmpty()
				&& destList.get(0) instanceof DeviceDest){
			dest = (DeviceDest) destList.get(0);
			if(this.getEquipName() == null){
				this.setEquipName(dest.getEquipName());
			}
			if(funcName != null){
				int slot = dest.getSlot();
				ManagedElement me = (ManagedElement)this.getExtendObj();
				FunctionDescriptor funcDes = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, isSync, funcName);
				if(funcDes != null){
					this.domain = funcDes.getDomain();
					CommProtocol pt = getProtocolType(dest, me, funcName);
					taskCell.setProtocolType(pt);
				}
			}
		}
		
		if(taskCell.getProtocolType() == null){
			taskCell.setProtocolType(this.getProtocol());
		}
		ResFunc resFunc = getResFunc(clsName);		
		if(resFunc != null)
		{	
			resFunc.setSync(isSync);
			if(funcName != null){
				resFunc.setFuncName(funcName);
			}

			resFunc.setAppContext(this.appContext);	
			resFunc.setExtendObj(this.getExtendObj());
    		resFunc.init(objList);  	
    		
    		taskCell.setNotfify(resFunc.isNotify());
    		taskCell.setNotifyToServer(resFunc.isNotifyToServer());
    		
    		taskCell.setObj(resFunc);
    		taskCell.setStrID(clsName);
    		if(destList.isEmpty()){
    			log.info("########## dest == null");
    		}
    		List<HiSysMessage> msgList = this.getTaskMsgList(cmdType,destList,resFunc,tRes, taskCell);
    		
    		taskCell.addMsgList(msgList);	
		}
		
		return taskCell;
		
	}
}