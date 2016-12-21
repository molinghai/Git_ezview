package com.nm.server.config.platform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.annotation.Transactional;

import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.AttributeDescriptor.AttributeType;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.descriptor.FunctionDescriptorLoader;
import com.nm.descriptor.NeDescriptionLoader;
import com.nm.descriptor.NeDescriptor;
import com.nm.error.ErrorMessageTable;
import com.nm.message.CommandDestination;
import com.nm.message.CommandParameter;
import com.nm.message.CsCommand;
import com.nm.message.Response;
import com.nm.nmm.res.ManagedElement;



public class PlatformL3DataMsgParser extends PlatformMsgParser{
	private Log log = LogFactory.getLog(this.getClass());
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
			int port = cd.getTpIndex();
			ManagedElement me = topoManager.getManagedElementById(neId);
			NeDescriptor neDes = NeDescriptionLoader.getNeDescriptor(me.getProductName(), me.getVersion());
			
			List<Map<String, Object>> conditions = new ArrayList<Map<String, Object>>();
			Map<String, Object> c = new HashMap<String, Object>();
			c.put("neId", neId);
			if(slot < 0){
				slot = 1;
			}
			if(slot >= 0){
				c.put("slot", slot);
			}
			if(port > 0){
				c.put("port", port);
			}
			conditions.add(c);

			fd = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, funcName);
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
				FunctionDescriptor portInfoFunc = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, "GpnIfExtEntry");
				List<Map<String, Object>> portInfos = da.getDatas(portInfoFunc, null, conditions);
				//加载默认值,只涉及三层端口的功能
				if(!portInfos.isEmpty()){
					for(Map<String, Object> portInfo : portInfos){
						int switchPort = (Integer)portInfo.get("ifExtSwitchPortSet");
						boolean isL3Port = switchPort == 0;
						if(!isL3Port){
							continue;
						}
						Map<String, Object> vm = new HashMap<String, Object>();
						vm.put("neId", neId);
						vm.put("slot", slot);
						vm.put("port", portInfo.get("port"));
						vm.put("index_Lable", portInfo.get("index_Lable"));
						vm.put("port_Type", portInfo.get("port_Type"));
						Map<String, Object> v = loadDefaultValue(fd);
						if(!v.isEmpty()){
							vm.putAll(v);
						}
						values.add(vm);
					}
				}
				da.addDatas(fd, values);
				values = da.getDatas(fd, null, conditions);
			}else {
				List<Map<String, Object>> tempValues = new ArrayList<Map<String, Object>>();
				FunctionDescriptor portInfoFunc = FunctionDescriptorLoader.getInstance().getFuncDesBy(me, slot, "GpnIfExtEntry");
				List<Map<String, Object>> portInfos = da.getDatas(portInfoFunc, null, conditions);
				//加载默认值,只涉及三层端口的功能
				if(!portInfos.isEmpty()){
					for(Map<String, Object> portInfo : portInfos){
						int switchPort = (Integer)portInfo.get("ifExtSwitchPortSet");
						boolean isL3Port = switchPort == 0;
						if(!isL3Port){
							continue;
						}
						boolean isExistL3Port = false;
						for(Map<String, Object> value : values){
							if(value.get("index_Lable").equals(portInfo.get("index_Lable"))){
								isExistL3Port = true;
								break;
							}
						}
						if(!isExistL3Port){
							Map<String, Object> vm = new HashMap<String, Object>();
							vm.put("neId", neId);
							vm.put("slot", slot);
							vm.put("port", portInfo.get("port"));
							vm.put("index_Lable", portInfo.get("index_Lable"));
							vm.put("port_Type", portInfo.get("port_Type"));
							Map<String, Object> v = loadDefaultValue(fd);
							if(!v.isEmpty()){
								vm.putAll(v);
							}
							tempValues.clear();
							tempValues.add(vm);
							da.addDatas(fd, tempValues);
						}
					}
				}
				values = da.getDatas(fd, null, conditions);
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
					continue;
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
				}
				if(value != null){
					vm.put(ad.getName(), value);
				}
			}
		}
		return vm;
	}
	
}
