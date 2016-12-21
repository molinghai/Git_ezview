package com.nm.server.config.platform;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.annotation.Transactional;

import com.nm.base.util.MibTreeNode;
import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.AttributeDescriptor.AttributeType;
import com.nm.descriptor.EquipmentDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.FunctionDescriptorLoader;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.descriptor.TPDescriptor;
import com.nm.descriptor.view.ActionTree;
import com.nm.descriptor.view.FunctionView;
import com.nm.descriptor.view.OptionsBox;
import com.nm.descriptor.view.OptionsLoader;
import com.nm.descriptor.view.ViewLoader;
import com.nm.error.ErrorMessageTable;
import com.nm.message.CommandDestination;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.nmm.res.Equipment;
import com.nm.nmm.res.EquipmentHolder;
import com.nm.nmm.res.ManagedElement;
import com.nm.nmm.service.TopoManager;
import com.nm.nmm.sync.DifBase;
import com.nm.server.adapter.snmp.SnmpEumDef;
import com.nm.server.adapter.snmp.pool.SnmpDataAccessPool;
import com.nm.server.common.access.DataAccess;
import com.nm.server.common.config.MessageParser;
import com.nm.server.common.config.TaskResult;
import com.nm.server.common.util.ToolUtil;
import com.nm.server.config.util.TPUtil;



public class PlatformMsgParser extends MessageParser{
	private Log log = LogFactory.getLog(this.getClass());

	 TopoManager topoManager = null; 
	 DataAccess da = null;
	private CommandDestination cd = null;
	public void init(){
		if(da == null){
			da = (DataBaseDataAccess)this.appContext.getBean("dbDataAccess");
		}
		if(topoManager == null){
			this.topoManager = (TopoManager) appContext.getBean("mstpTopoManager");
		}
	}
	
	@Override
	public void init(List<Object> objList){
		if(objList != null && !objList.isEmpty()){
			cd = (CommandDestination)objList.get(0);
		}
	}
	
	//loadResourceActionTree
	public void loadActionTree(CsCommand cmd, Response res){
		Set<CommandParameter> params = cmd.getParamSet();		
		if (params == null){
			res.setResult(ErrorMessageTable.BADPARAM);
			return;
		}
		try{
			CommandDestination cd = cmd.getDest();
			String neType = cd.getNeType();
			String neVersion = cd.getNeVersion();
			String cardName = cd.getEquipName();
			Integer slot = cd.getSlot();
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(neType, neVersion);
			if(neDes == null){
				res.setResult(ErrorMessageTable.BADPARAM);
				return;
			}
			
			String atName;
			
			if(cardName == null || slot < 0){
				atName = neDes.getFunctionsTreeName();
			}else{
				EquipmentDescriptor ed = neDes.getEquipmentDescriptor(cardName, cd.getEquipVersion());
				atName = ed.getFunctionsTreeName();
			}
			if(atName != null){
				ActionTree at = ViewLoader.getInstance().getActionTree(atName);
				CommandParameter cp = new CommandParameter(at);
				res.addParam(cp);
			}

			res.setResult(ErrorMessageTable.SUCCESS);
		}catch(Exception e){
			log.error(e,e);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}
	
	public void loadFunctionView(CsCommand cmd, Response res){
		Set<CommandParameter> params = cmd.getParamSet();		
		if (params == null){
			res.setResult(ErrorMessageTable.BADPARAM);
			return;
		}
		try{
			CommandDestination cd = cmd.getDest();
			String neType = cd.getNeType();
			String neVersion = cd.getNeVersion();
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(neType, neVersion);
			if(neDes == null){
				res.setResult(ErrorMessageTable.BADPARAM);
				return;
			}
			Iterator<CommandParameter> it = params.iterator();
			while(it.hasNext()){
				CommandParameter cp = (CommandParameter)it.next();
				String fvName = (String)cp.getObj("funcViewName");
				
				FunctionView fv = ViewLoader.getInstance().getFunctionView(neDes, fvName);
				if(cd.getSlot() > 0){
					//
					String equipName = cd.getEquipName();
					if(equipName != null){
						EquipmentDescriptor eqDes = neDes.getEquipmentDescriptor(equipName, cd.getEquipVersion());
						fv = ViewLoader.getInstance().getFunctionView(neDes, eqDes, fvName);
					}
					
				}
				//属性 supportPortType只用于标识不需要返回
				FunctionView funcViewClone = null;
				if(fv != null){
					AttributeDescriptor attr = fv.getAttribute("supportPortType");
					if(attr != null){
						funcViewClone = (FunctionView) this.cloneObject(fv);
						attr = funcViewClone.getAttribute("supportPortType");
						funcViewClone.getAllAttributes().remove(attr);
					}
				}
				if(funcViewClone != null){
					cp.setParamter(funcViewClone);
				} else {
					cp.setParamter(fv);
				}
				res.addParam(cp);
			}
			res.setResult(ErrorMessageTable.SUCCESS);
		}catch(Exception e){
			log.error(e,e);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}
	
	public void loadOptions(CsCommand cmd, Response res){
		try{
			CommandDestination cd = cmd.getDest();

			CommandParameter cp = new CommandParameter();
			OptionsBox ob = OptionsLoader.getInstance().getOptionsBox();
			cp.setParamter(ob);
			res.addParam(cp);
			res.setResult(ErrorMessageTable.SUCCESS);
		}catch(Exception e){
			log.error(e,e);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}
	
	public void loadMibTreeNodes(CsCommand cmd, Response res){
		try{
			MibTreeNode root = SnmpDataAccessPool.getInstance().getMibTreeNode();
			CommandParameter cp = new CommandParameter(root);
			res.addParam(cp);
		}catch(Exception ex){
			log.error(ex,ex);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}
	
	@Transactional
	public void processAddCommand(CsCommand cmd, Response res){
		Set<CommandParameter> params = cmd.getParamSet();		
		if (params == null){
			res.setResult(ErrorMessageTable.BADPARAM);
			return;
		}
		try{	
			FunctionDescriptor fd = null;
			CommandDestination cd = cmd.getDest();
			int neId = cd.getNeId();
			int slot = cd.getSlot() > 0 ? cd.getSlot() : 1;
			ManagedElement me = topoManager.getManagedElementById(neId);
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			
			if(cd.getSlot() < 0){
				Set<EquipmentHolder> equipHolders = me.getEquipmentHolders();
				for(EquipmentHolder eh : equipHolders){
					Equipment equip = eh.getInstalledEquipment();
					if(equip == null){
						continue;
					}
					EquipmentDescriptor equipDes = neDes
							.getEquipmentDescriptor(equip.getName(), equip.getSoftwareVersion());
					if(equipDes.getFuncDesByName("MainBoard") != null){
						slot = eh.getLocalIndex();
						break;
					}
				}
			}
			
			String funcName = cmd.getFuncName();
			Iterator<CommandParameter> it = params.iterator();
			while(it.hasNext()){
				CommandParameter cp = it.next();
				List<Map<String, Object>> values = (List<Map<String, Object>>)cp.getObj("guiGenData");
				for(Map<String, Object> v : values){
					v.put("neId", neId);
					v.put("slot", slot);
				}
				
				fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
				da.addDatas(fd, values);
			}
			res.setResult(ErrorMessageTable.SUCCESS);
		}catch(Exception e){
			log.error(e,e);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}
	
	@Transactional
	public void processSetCommand(CsCommand cmd, Response res){
		Set<CommandParameter> params = cmd.getParamSet();		
		if (params == null){
			res.setResult(ErrorMessageTable.BADPARAM);
			return;
		}
		try{	
			Map<String, Object> value = null;;
			FunctionDescriptor fd = null;
			CommandDestination cd = cmd.getDest();
			int neId = cd.getNeId();
			int slot = cd.getSlot();
			if(slot < 0){
				slot = 1;
			}
			int port = cd.getTpIndex();
			ManagedElement me = topoManager.getManagedElementById(neId);
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			
			if(cd.getSlot() < 0){
				Set<EquipmentHolder> equipHolders = me.getEquipmentHolders();
				for(EquipmentHolder eh : equipHolders){
					Equipment equip = eh.getInstalledEquipment();
					if(equip == null){
						continue;
					}
					EquipmentDescriptor equipDes = neDes
							.getEquipmentDescriptor(equip.getName(), equip.getSoftwareVersion());
					if(equipDes.getFuncDesByName("MainBoard") != null){
						slot = eh.getLocalIndex();
						break;
					}
				}
			}
			
			String funcName = cmd.getFuncName();
			Iterator<CommandParameter> it = params.iterator();
			while(it.hasNext()){
				CommandParameter cp = it.next();
				Object data = cp.getObj("guiGenData");
				if(data != null){
					if(data instanceof Map){
						value = (Map<String, Object>)data;
					}else if(data instanceof List){
						value = ((List<Map<String, Object>>)data).get(0);
					}
				}
				List<Map<String, Object>> conditions = (List<Map<String, Object>>)cp.getObj("guiGenCondition");
				if(conditions == null){
					conditions = new ArrayList<Map<String, Object>>();
					Map<String, Object> c = new HashMap<String, Object>();
					c.put("neId", neId);
					if(slot >= 0){
						c.put("slot", slot);
					}
					if(port > 0){
						c.put("port", port);
					}
					conditions.add(c);
				}else{
					for(Map<String, Object> v : conditions){
						v.put("neId", neId);
						if(slot >= 0){
							v.put("slot", slot);
						}
						if(port > 0){
							v.put("port", port);
						}
						if(null == v.get("id")){
							v.remove("id");
						}
						//包含端口索引的时候，需要进行转换，客户端传过来的有可能不是真实索引
						if(v.get("ifIndex") != null){
							TPUtil tpUtil = (TPUtil)this.appContext.getBean("localTPUtil");
							Object ifIndexObj = v.get("ifIndex");
							int index = 0;
							if (ifIndexObj instanceof Integer) {
            					index = (Integer) ifIndexObj;
							} else if (ifIndexObj instanceof Long) {
								index = ((Long) ifIndexObj).intValue();
							}
            				index = tpUtil.getPortIndexByL2PI(me, slot < 1 ? 1 : slot, index);
            				v.put("ifIndex", index);
						}
					}
				}
				fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
				List datas = da.getDatas(fd, null, conditions);
				if(datas == null || datas.isEmpty()){
					List<Map<String, Object>> vs = new ArrayList<Map<String, Object>>();
					for(Map m : conditions){
						Map <String, Object> val = new HashMap<String, Object>();
						val.putAll(value);
						val.putAll(m);
						vs.add(val);
					}
					da.addDatas(fd, vs);
				}else{
					da.updateDatas(fd, value, conditions);
				}
				
			}
			res.setResult(ErrorMessageTable.SUCCESS);
		}catch(Exception e){
			log.error(e,e);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}

	
	@Transactional
	public void processGetCommand(CsCommand cmd, Response res){
		Set<CommandParameter> params = cmd.getParamSet();		
		if (params == null){
			res.setResult(ErrorMessageTable.BADPARAM);
			return;
		}
		try{
			FunctionDescriptor fd = null;
			CommandDestination cd = cmd.getDest();
			int neId = cd.getNeId();
			int slot = cd.getSlot();
			if(slot < 0){
				slot = 1;
			}
			
			int port = cd.getTpIndex();
			ManagedElement me = topoManager.getManagedElementById(neId);
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			
			if(cd.getSlot() < 0){
				Set<EquipmentHolder> equipHolders = me.getEquipmentHolders();
				for(EquipmentHolder eh : equipHolders){
					Equipment equip = eh.getInstalledEquipment();
					if(equip == null){
						continue;
					}
					EquipmentDescriptor equipDes = neDes
							.getEquipmentDescriptor(equip.getName(), equip.getSoftwareVersion());
					if(equipDes.getFuncDesByName("MainBoard") != null){
						slot = eh.getLocalIndex();
						break;
					}
				}
			}
			
			List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
			Map<String, Object> c = new HashMap<String, Object>();
			c.put("neId", neId);
			c.put("storageType", 0);
			if(slot >= 0 && me.getEquipmentHolders().size() > 1){
				c.put("slot", slot);
			}
			if(port > 0){
				c.put("port", port);
			}
			conditions.add(c);

			EquipmentDescriptor ed = null;
			List<TPDescriptor> tpdList = new ArrayList<TPDescriptor>();
			
			fd = neDes.getFuncDesByName(funcName);
			if(fd == null){
				EquipmentHolder eh = me.getEquipmentHolder(slot);
				if(eh == null){
					eh = me.getEquipmentHolder(1);
				}
				if(eh != null){
					Equipment eqp = eh.getInstalledEquipment();
					if (eqp == null) {
						log.error("Equipment is null when query "+funcName+", current slot = " + slot + " , and cd.getSlot() = " + cd.getSlot());
					}else{
						ed = neDes.getEquipmentDescriptor(eqp.getName(), cd.getEquipVersion());
						fd = ed.getFuncDesByName(funcName);
						if(fd == null){
							Set<TPDescriptor> tpds = ed.getTpSet();
							if(tpds != null){
								FunctionDescriptor funcDes = null;
								for(TPDescriptor tpd : tpds){
									funcDes = tpd.getFuncDesByName(funcName);
									if(funcDes != null){
										tpdList.add(tpd);
										fd = funcDes;
									}
								}
							}
						}
					}
				}
			}else{
				EquipmentHolder eh = me.getEquipmentHolder(slot);
				if(eh != null){
					Equipment eqp = eh.getInstalledEquipment();
					if (eqp == null) {
						log.error("Equipment is null when query "+funcName+", current slot = " + slot + " , and cd.getSlot() = " + cd.getSlot());
					}else{
						ed = neDes.getEquipmentDescriptor(eqp.getName(), eqp.getSoftwareVersion());
					}
				}
			}
			if(fd == null){
				res.setResult(ErrorMessageTable.BADPARAM);
				log.error(neDes.getProductName()+" can't find function " + funcName);
				return;
			}
			List<Map<String, Object>> values = da.getDatas(fd, null, conditions);
			if(values == null){
				values = new ArrayList<Map<String, Object>>();
			}
			if(values.isEmpty()){
				//加载默认值
				if(!tpdList.isEmpty()){
					for(TPDescriptor tpd : tpdList){
						int num = ed.getTPNumBySubTpType(tpd.getSubType());
						for(int i = 1; i <= num; i++){
							Map<String, Object> vm = new HashMap<String, Object>();
							vm.put("neId", neId);
							vm.put("chassisIndex", me.getShelfNum());
							vm.put("slot", slot);
							vm.put("port", i);
							String tpName = tpd.getTpName();
							if(tpName == null){
								tpName = tpd.getSubType().toString();
							}
							vm.put("index_Lable", tpName + ":" + i);
							vm.put("port_Type", tpd.getSubType().ordinal());
							Map<String, Object> v = loadDefaultValue(fd);
							if(!v.isEmpty()){
								vm.putAll(v);
								values.add(vm);
							}
						}
					}
				}else if(ed != null){
					Map<String, Object> vm = null;
					List<AttributeDescriptor> ads = fd.getAttributeList();
					if(ads != null){
						for(AttributeDescriptor ad : ads){
							if(ad.getName().equals("ifIndex")){//索引
								Set<TPDescriptor> tpds = ed.getTpSet();
								if(tpds != null){
									for(TPDescriptor tpd : tpds){
										int num = ed.getTPNumBySubTpType(tpd.getSubType());
										for(int i = 1; i <= num; i++){
											vm = new HashMap<String, Object>();
											vm.put("neId", neId);
											vm.put("chassisIndex", me.getShelfNum());
											vm.put("slot", slot);
											vm.put("port", i);
											vm.put("ifIndex", i);
											vm.put("index_Lable", tpd.getSubType() + ":" + i);
											vm.put("port_Type", tpd.getSubType().ordinal());
											Map<String, Object> v = loadDefaultValue(fd);
											if(!v.isEmpty()){
												vm.putAll(v);
												values.add(vm);
											}
										}
									}
								}
								return;
							}
						}
					}
					vm = new HashMap<String, Object>();
					vm.put("neId", neId);
					vm.put("slot", slot);
					vm.put("chassisIndex", me.getShelfNum());
					Map<String, Object> v = loadDefaultValue(fd);
					if(!v.isEmpty()){
						vm.putAll(v);
						values.add(vm);
					}
				}else{
					
					Map<String, Object> vm = new HashMap<String, Object>();
					vm.put("neId", neId);
					Map<String, Object> v = loadDefaultValue(fd);
					if(!v.isEmpty()){
						vm.putAll(v);
						values.add(vm);
					}
				}
				da.addDatas(fd, values);
			}
			values = da.getDatas(fd, null, conditions);
			
			//转换端口
			TPUtil tpUtil = (TPUtil)this.appContext.getBean("localTPUtil");
			//端口Vlan配置功能暂时屏蔽转换端口索引
			if(!"GPNl2IfEntryPX10".equals(funcName)
					&& !"GpnifExtL3Address".equals(funcName)){
				tpUtil.parsePortIndex(me, slot, fd, values);
			}
			for(CommandParameter cp : params){
				//暂时写死
				if(!funcName.equals("GPNdot1agCfmMaNetEntry")){
					break;
				}
				String subFuncName = (String)cp.getParameter();
				FunctionDescriptor subFunc = neDes.getFuncDesByName("GPNdot1agCfmMaCompEntry");
				if(subFunc == null){
					subFunc = neDes.getEquipmentDescriptor(cd.getEquipName(), cd.getEquipVersion()).getFuncDesByName("GPNdot1agCfmMaCompEntry");
				}
				List<Map<String, Object>> subValues = da.getDatas(subFunc, null, conditions);
				
				for(Map<String, Object> subValue : subValues){
					Long	 index1 = (Long)subValue.get("dot1agCfmMdIndex");
					Long index2 = (Long)subValue.get("dot1agCfmMaIndex");
					for(Map<String, Object> value : values){
						Long vsIndex1 = (Long)value.get("dot1agCfmMdIndex");
						Long vsIndex2 = (Long)value.get("dot1agCfmMaIndex");
						if(index1.equals(vsIndex1) && index2.equals(vsIndex2)){
							value.putAll(subValue);
						}
					}
				}
			}
			CommandParameter cp = new CommandParameter(values);
			
			res.addParam(cp);
			res.setResult(ErrorMessageTable.SUCCESS);
		}catch(Exception e){
			log.error(funcName,e);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}
	
	private Map<String, Object> loadDefaultValue(FunctionDescriptor fd){
		Map<String, Object> vm = new HashMap<String, Object>();
		List<AttributeDescriptor> ads = fd.getAttributeList();
		if(ads != null){
			Object value = null;
			String showingVlans = null;
			for(AttributeDescriptor ad : ads){
				//如果是动态表，则不添加默认值
				String propMask = ad.getExternalPropMask();
				if(propMask != null){
					Integer pm = Integer.valueOf(propMask);
					if(pm != null && pm > 3){
						vm.clear();
						return vm;
					}
				}
				
				String dv = ad.getDefaultValue();
				if(dv == null){
					if(!(fd.getName().equalsIgnoreCase("GPNVlanListTable")
							&& ad.getName().contains("l2VlanList"))){
						continue;
					}
				}
				
				if(ad.getAttributeType() == AttributeType.AT_BOOLEAN){
					value = Boolean.valueOf(dv);
				}else if(ad.getAttributeType() == AttributeType.AT_INT){
					value = Integer.valueOf(dv);
				}else if(ad.getAttributeType() == AttributeType.AT_LONG){
					value = Long.valueOf(dv);
				}else if(ad.getAttributeType() == AttributeType.AT_ENUM){
					value = Integer.valueOf(dv);
				}else if(ad.getAttributeType() == AttributeType.AT_BITS){
					value = Integer.valueOf(dv);
				}else if(ad.getAttributeType() == AttributeType.AT_STRING){
					value = dv;
				}else if(ad.getAttributeType() == AttributeType.AT_IPSTRING){
					value = dv;
				}else if(ad.getAttributeType() == AttributeType.AT_MACSTRING){
					value = dv;
				}else if(ad.getAttributeType() == AttributeType.AT_PORTLIST){
					if(fd.getName().equalsIgnoreCase("GPNVlanListTable")){
						if(ad.getName().contains("l2VlanList")){
							if(dv != null){
								showingVlans = convertValue(dv);
							}
							int i = Integer.valueOf(ad.getName().substring(ad.getName().indexOf("l2VlanList") + 10).trim());
							value = convertSnmpPortList(showingVlans.substring(512*(i-1), 512*i));
						}else {
							value = dv;
						}
					}
				}
				if(value != null){
					vm.put(ad.getName(), value);
				}
			}
		}
		return vm;
	}
	
	@Transactional
	public void processDeleteCommand(CsCommand cmd, Response res){
		Set<CommandParameter> params = cmd.getParamSet();		
		if (params == null){
			res.setResult(ErrorMessageTable.BADPARAM);
			return;
		}
		try{	
			FunctionDescriptor fd = null;
			CommandDestination cd = cmd.getDest();
			int neId = cd.getNeId();
			int slot = cd.getSlot();
			if(slot < 0){
				slot = 1;
			}
			int port = cd.getTpIndex();
			ManagedElement me = topoManager.getManagedElementById(neId);
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			
			if(cd.getSlot() < 0){
				Set<EquipmentHolder> equipHolders = me.getEquipmentHolders();
				for(EquipmentHolder eh : equipHolders){
					Equipment equip = eh.getInstalledEquipment();
					if(equip == null){
						continue;
					}
					EquipmentDescriptor equipDes = neDes
							.getEquipmentDescriptor(equip.getName(), equip.getSoftwareVersion());
					if(equipDes.getFuncDesByName("MainBoard") != null){
						slot = eh.getLocalIndex();
						break;
					}
				}
			}
			
			String funcName = cmd.getFuncName();
			fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
			Iterator<CommandParameter> it = params.iterator();
			while(it.hasNext()){
				CommandParameter cp = it.next();
				List<Map<String, Object>> conditions = (List<Map<String, Object>>)cp.getObj("guiGenCondition");
				if(conditions == null){
					conditions = new ArrayList<Map<String, Object>>();
					Map<String, Object> c = new HashMap<String, Object>();
					c.put("neId", neId);
					if(slot >= 0){
						c.put("slot", slot);
					}
					if(port > 0){
						c.put("port", port);
					}
					conditions.add(c);
				}else{
					for(Map<String, Object> v : conditions){
						v.put("neId", neId);
					}
				}
				da.deleteDatas(fd, conditions);
			}
			res.setResult(ErrorMessageTable.SUCCESS);
		}catch(Exception e){
			log.error(funcName,e);
			res.setResult(ErrorMessageTable.UNKNOW);
		}
	}
	
	@Override
	@Transactional
	public List<Object> compare(List<Object> destList,TaskResult tRes)
	{
		List<Object> objList = null;
		if(destList == null)
		{
			return null;
		}
		try{
			FunctionDescriptor fd = null;
			
			for(int destIndex = 0; destIndex < destList.size(); destIndex++)
			{
				DifBase param = (DifBase)destList.get(destIndex);
				
				CommandDestination nmDestL = (CommandDestination)param.getObjNm();
				CommandDestination neDestL = (CommandDestination)param.getObjNe();
				
				List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
				Map<String, Object> m = new HashMap<String, Object>();
				int neId = nmDestL.getNeId();
				m.put("neId", neId);
				m.put("storageType", 0);
				conditions.add(m);
				
				ManagedElement me = topoManager.getManagedElementById(neId);
				int slot = nmDestL.getSlot();
				fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
				List<String> indexList = new ArrayList<String>();
				List<AttributeDescriptor> ads = fd.getAttributeList();
				for(AttributeDescriptor ad : ads){
					if(ad.getExternalPropMask() != null && ad.getExternalPropMask().equals(SnmpEumDef.Index)){
						indexList.add(ad.getName());
					}
				}
				
				List<Map<String, Object>> nmValues = da.getDatas(fd, null, conditions);
				
				m.put("neId", neDestL.getNeId());
				m.put("storageType", 1);
				List<Map<String, Object>> neValues = da.getDatas(fd, null, conditions);
				if(neValues == null){
					continue;
				}
				
				boolean same = true;
				if(nmValues.size() != neValues.size()) {
					same = false;
				} else {
					for(Map<String, Object> nm : nmValues){
						if(!same){
							break;
						}
						for(Map<String, Object> ne : neValues){
							boolean indexEqual = true;
							for(String indexName : indexList){
								if(!nm.get(indexName).equals(ne.get(indexName))){
									indexEqual = false;
									break;
								}
							}
							if(indexEqual){
								Set<Entry<String, Object>> es = ne.entrySet();
								for(Entry<String, Object> e : es){
									if(!e.getKey().equals("id")){
										Object object_NM = nm.get(e.getKey());
										Object object_NE = e.getValue();
										if (object_NM==null ^ object_NE==null) {//异或运算,相同为false,不同为true
											same = false;
											break;
										} else {//object_NM和object_NE都为空,或者都不为空
											if (object_NM!=null) {
												if (object_NM instanceof byte[]) {
													same = Arrays.equals((byte[])object_NM, (byte[])object_NE);
													if (!same) {
														break;
													}
												} else {
													if(!object_NM.equals(object_NE)){
														if ("Systeminfo".equals(funcName)) {
															if ("sysUpTime".equals(e.getKey())) {
																continue;
															}
														}
														same = false;
														break;
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
				if ("GPNIFInfoEntry".equals(funcName)) {
					if (neValues!=null && !neValues.isEmpty()) {
						//删除网管侧数据
						m.put("storageType", 0);
						da.deleteDatas(fd, conditions);
						//以网元侧数据为准
						Map<String, Object> values = new HashMap<String, Object>();
						values.put("storageType", 0);
						m.remove("storageType");
						da.updateDatas(fd, values, conditions);
					}
					continue;
				}
				// 网管与网元数据不一致
				if (same == false) {
					DifBase dif = new DifBase("", nmValues, neValues);
					dif.setFuncName(funcName);
					objList = new ArrayList<Object>();
					objList.add(dif);
				} else {// 相同则删除同步数据
					// OrderWire is single per NE, so remove the first data directly
					if ("1".equals(ToolUtil.getProperty("DELETE_SAMESYNCDATA"))) {
						da.deleteDatas(fd, conditions);
					}
				}
			}
		}catch(Exception ex){
			log.error(funcName, ex);
		}
		return objList;
	}
	
	@Override
	@Transactional
	public void uploadReplace(List<Object>destList,TaskResult tRes)
	{
		try{
			if(destList != null)
			{
				FunctionDescriptor fd = null;
				//网元上载，先删除网管侧数据，再更新网元侧数据，并将标志位写0			
				for(int destIndex = 0; destIndex < destList.size(); destIndex++)
				{
					DifBase param = (DifBase)destList.get(destIndex);					
					CommandDestination nmDestL = (CommandDestination)param.getObjNm();
					CommandDestination neDestL = (CommandDestination)param.getObjNe();	
					boolean hasSlot = false;
					if(fd == null){
						ManagedElement me = null;
						if(nmDestL != null){
							me = topoManager.getManagedElementById(nmDestL.getNeId());
						}else{
							me = topoManager.getManagedElementById(neDestL.getNeId());
						}
						NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
						int slot =0;
						if(nmDestL != null){
							slot = nmDestL.getSlot();
						}else{
							slot = neDestL.getSlot();
						}
						log.info("##########槽位信息slot="+ slot);
						fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
						if(fd==null){
							Set<EquipmentDescriptor> equipDesSet = neDes.getEquipmentSet();
							if(equipDesSet != null && !equipDesSet.isEmpty()){
								Iterator<EquipmentDescriptor> iterator = equipDesSet.iterator();
								while(iterator.hasNext()){
									EquipmentDescriptor eqDes = iterator.next();
									fd = eqDes.getFuncDesByName(funcName);
									if (fd == null) {		//艾赛设备DGFirst端口级功能读不到信息，添加此方法
										Set<TPDescriptor> tpds = eqDes.getTpSet();
										if(tpds != null){
											for(TPDescriptor tpd : tpds){
												fd = tpd.getFuncDesByName(funcName);
												if(fd != null){
													break;
												}
											}
										}
									}
									if(fd != null
											&&("snmp".equalsIgnoreCase(fd.getProtocol())
													|| "cli".equalsIgnoreCase(fd.getProtocol())
													|| "PT_SNMP".equalsIgnoreCase(fd.getProtocol())
													|| "PT_CLI".equalsIgnoreCase(fd.getProtocol())
													|| "CP_UDP".equalsIgnoreCase(fd.getProtocol())
													|| "CP_SNMP_II".equalsIgnoreCase(fd.getProtocol())
													|| "CP_SNMP_I".equalsIgnoreCase(fd.getProtocol()))){
										break;
									}
								}
							}
						}
						hasSlot = me.getEquipmentHolders().size() > 1;
					}
					
					if(nmDestL != null && neDestL != null)
					{
						List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
						Map<String, Object> m = new HashMap<String, Object>();
						m.put("neId", nmDestL.getNeId());
						if(nmDestL.getSlot() > 0 && hasSlot){
							m.put("slot", nmDestL.getSlot());
							
							if(nmDestL.getTpType() != 0){
								if(fd.getAttributeByName("port_Type") != null){
									m.put("port_Type", nmDestL.getTpType());
//									m.put("port", nmDestL.getTpIndex());
								}								
							}
						}
						
						m.put("storageType", 0);
						conditions.add(m);
						
						da.deleteDatas(fd, conditions);
//						m.remove("storageType");
						List<Map<String, Object>> updateConditions = new ArrayList<Map<String, Object>>();
						Map<String, Object> updateConditionsMap = new HashMap<String, Object>();
						updateConditionsMap.put("neId", nmDestL.getNeId());
						updateConditionsMap.put("storageType", 1);
						
						if(nmDestL.getSlot() > 0 && hasSlot){
							updateConditionsMap.put("slot", nmDestL.getSlot());
							
							if(nmDestL.getTpType() != 0){
								if(fd.getAttributeByName("port_Type") != null){
									updateConditionsMap.put("port_Type", nmDestL.getTpType());
//									updateConditionsMap.put("port", nmDestL.getTpIndex());
								}	
							}
						}
						
						updateConditions.add(updateConditionsMap);
						Map<String, Object> v = new HashMap<String, Object>();
						v.put("storageType", 0);
						log.info("######nmDestL != null && neDestL != null#####fd.getName() == " + fd.getName() + "  conditions:");
						for(String key : updateConditionsMap.keySet()){
							log.info("####" + key + " = " + updateConditionsMap.get(key));
						}
						da.updateDatas(fd, v, updateConditions);
					}
					else
					{
						// delete
						if(nmDestL != null)
						{			
							List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
							Map<String, Object> m = new HashMap<String, Object>();
							m.put("neId", nmDestL.getNeId());
							m.put("storageType", 0);
							if(nmDestL.getSlot() > 0 && hasSlot){
								m.put("slot", nmDestL.getSlot());
							}
							
							if(neDestL.getTpType() != 0){
								if(fd.getAttributeByName("port_Type") != null){
									m.put("port_Type", neDestL.getTpType());
//									m.put("port", neDestL.getTpIndex());
								}	
							}
							conditions.add(m);
							
							da.deleteDatas(fd, conditions);
						}					
						//update
						if(neDestL != null)
						{
							List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
							Map<String, Object> m = new HashMap<String, Object>();
							m.put("neId", neDestL.getNeId());
							conditions.add(m);
							
							Map<String, Object> v = new HashMap<String, Object>();
							v.put("storageType", 0);
							if(neDestL.getSlot() > 0 && hasSlot){
								m.put("slot", neDestL.getSlot());
							}
							
							if(neDestL.getTpType() != 0){
								if(fd.getAttributeByName("port_Type") != null){
									m.put("port_Type", neDestL.getTpType());
//									m.put("port", neDestL.getTpIndex());
								}	
							}
//							da.deleteDatas(funcName, conditions);
							log.info("-------------------------"+fd);
							log.info("######neDestL != null#####fd.getName() == " + fd.getName() + "  conditions:");
							for(String key : m.keySet()){
								log.info("##neDestL != null##" + key + " = " + m.get(key));
							}
							da.updateDatas(fd, v, conditions);
						}
					}										
				}				
			}
		}catch(Exception ex){
			log.error(funcName, ex);
		}
	}
	
	@Override
	@Transactional
	public void confirmByNe(List<Object>destList,TaskResult tRes)
	{
		try{
			if(destList != null)
			{
				//网元同步时，确认差异，以网元为准
				for(int destIndex = 0; destIndex < destList.size(); destIndex++)
				{
					DifBase param = (DifBase)destList.get(destIndex);			
					
					List<Map<String, Object>> nmDestL = (List<Map<String, Object>>)param.getObjNm();
					List<Map<String, Object>> neDestL = (List<Map<String, Object>>)param.getObjNe();		
					
					List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
					Map<String, Object> m = new HashMap<String, Object>();
					
					long neId = 0;
					int slot = 1;
					if((nmDestL == null || nmDestL.isEmpty() &&(neDestL == null || neDestL.isEmpty()))){
						continue;
					}else if(nmDestL != null && !nmDestL.isEmpty()){
						neId = (Integer)nmDestL.get(0).get("neId");
						if(nmDestL.get(0).get("slot") != null){
							slot = Integer.valueOf(String.valueOf(nmDestL.get(0).get("slot")));
						}
					}else{
						neId = (Integer)neDestL.get(0).get("neId");
						if(neDestL.get(0).get("slot") != null){
							slot = Integer.valueOf(String.valueOf(neDestL.get(0).get("slot")));
						}
					}
					m.put("neId", neId);
					m.put("storageType", 0);
					conditions.add(m);
					ManagedElement me = topoManager.getManagedElementById(cd.getNeId());
					FunctionDescriptor fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
					da.deleteDatas(fd, conditions);
					
					Map<String, Object> values = new HashMap<String, Object>();
					values.put("storageType", 0);
					m.remove("storageType");
					da.updateDatas(fd, values, conditions);
					
				}
			}
		}catch(Exception ex){
			log.error(funcName, ex);
		}
	}	
	
	@Override
	@Transactional
	public void delete(List<Object>destList, int dataType, TaskResult tRes)
	{
		//初始化网管侧数据，先将网管数据库中的数据删除
		if(destList == null)
		{
			return;
		}
		FunctionDescriptor fd = null;
		
		try{
			if(dataType == 2)
			{
				for(int destIndex = 0; destIndex < destList.size(); destIndex++)
				{
					CommandDestination  dest = (CommandDestination)destList.get(destIndex);					
					// delete
					if(dest == null)
					{
						continue;
					}	
					
					try{
						int neId = dest.getNeId();
						if(fd == null){
							ManagedElement me = topoManager.getManagedElementById(neId);
							NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
							fd=FunctionDescriptorLoader.getInstance().getFuncDesBy(me, dest.getSlot(), funcName); 
//							fd = neDes.getFuncDesByName(funcName);
							if(fd == null){
								EquipmentDescriptor eqDes = neDes.getEquipmentDescriptor(dest.getEquipName(), dest.getEquipVersion());
								if(eqDes != null)
								{
									fd = eqDes.getFuncDesByName(funcName);
								}
							}
						}
						List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
						Map<String, Object> c = new HashMap<String, Object>();
						c.put("neId", neId);
						conditions.add(c);
						da.deleteDatas(fd, conditions);
					}catch(Exception e){
						log.error(funcName, e);
					}
					
				}
			}	
		}catch(Exception ex){
			log.error(funcName, ex);
		}
	}	
	
	@Override
	@Transactional
	public void copy(List<Object>destList,TaskResult tRes)
	{		
		if(destList == null)
		{
			return;
		}
		FunctionDescriptor fd = null;
		try{
			//网元拷贝
			for(int destIndex = 0; destIndex < destList.size(); destIndex++)
			{
				DifBase dif = (DifBase)destList.get(destIndex);
				//目的网元
				CommandDestination dest = (CommandDestination)dif.getObjNm();
				//源网元
				CommandDestination src =  (CommandDestination)dif.getObjNe();	
				
				try{
					ManagedElement me = topoManager.getManagedElementById(src
							.getNeId());
					int slot = src.getSlot();
					fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
					
					List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
					Map<String, Object> m = new HashMap<String, Object>();
					m.put("neId", src.getNeId());
					if(src.getTpIndex() > 0){
						m.put("port", src.getTpIndex());
					}
					conditions.add(m);
					
					List<Map<String, Object>> srcValues = da.getDatas(fd, null, conditions);
					if(srcValues != null && !srcValues.isEmpty()){
						List<Map<String, Object>> destValues = new ArrayList<Map<String, Object>>();
						for(Map<String, Object> v : srcValues){
							Map<String, Object> d = new HashMap<String, Object>();
							d.putAll(v);
							d.put("neId", dest.getNeId());
							d.remove("id");
							destValues.add(d);
						}
						
						da.addDatas(fd, destValues);
					}
					
				}catch(Exception e){
					log.error(e, e);
				}
			}	
		}catch(Exception ex){
			log.error(funcName, ex);
		}
	}
	
	/**
	 * vlan 格式转换 1-10,30 ---> 010101
	 * @param value
	 * @return
	 */
	protected String convertValue(String value) {
        byte[] flags = new byte[512 * 8];
        StringBuffer sb = new StringBuffer();
        String[] ports = value.split(",");
        if (!value.trim().equals("")) {
            for (String port : ports) {
                if (port.contains("-")) {
                    String[] ps = port.split("-");
                    int start = Integer.parseInt(ps[0]);
                    int end = Integer.parseInt(ps[1]);
                    for (int i = start; i <= end; i++) {
                        flags[i - 1] = 1;
                    }
                } else {
                    int current = Integer.parseInt(port);
                    flags[current - 1] = 1;
                }
            }
        }
        for (int j = 0; j < flags.length; j++) {
            sb.append(flags[j]);
        }
        return sb.toString();
    }
	
	/**
	 * 
	 * @param v_string
	 * @return
	 */
	protected byte[] convertSnmpPortList(String v_string) {

        char[] count = v_string.toCharArray();
        int byteSize = count.length / 8;
        int insuff = 8 - count.length % 8;
        if (count.length % 8 != 0) {
            byteSize = byteSize + 1;
        }
        if (count.length % 8 != 0) {
            for (; insuff > 0; insuff--) {
                v_string += "0";
            }
        }
        byte[] v_bytes = new byte[byteSize];
        for (int i = 0; i < byteSize; i++) {
            int v_int = Integer.parseInt(v_string.substring(i * 8, (i + 1) * 8), 2);
            byte val = (byte) v_int;
            v_bytes[i] = val;
        }
        return v_bytes;
    }
	
	public Object cloneObject(Object src) {
        // add by jiachao
        Object dest = null;
        ByteArrayOutputStream byteOut;
        ObjectOutputStream objOut = null;
        ByteArrayInputStream byteIn;
        ObjectInputStream objIn = null;

        try {
            byteOut = new ByteArrayOutputStream();
            objOut = new ObjectOutputStream(byteOut);
            objOut.writeObject(src);

            byteIn = new ByteArrayInputStream(byteOut.toByteArray());
            objIn = new ObjectInputStream(byteIn);

            return dest = objIn.readObject();
        } catch (IOException e) {
            log.error(e, e);
        } catch (ClassNotFoundException e) {
            log.error(e, e);
        } catch (Exception e) {
            log.error(e, e);
        } finally {
            try {
                byteIn = null;
                byteOut = null;
                if (objOut != null) {
                    objOut.close();
                }
                if (objIn != null) {
                    objIn.close();
                }
            } catch (IOException e) {
                log.error(e, e);
            }
        }
        return dest;
    }
}
