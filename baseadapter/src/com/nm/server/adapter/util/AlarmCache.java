package com.nm.server.adapter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.nm.alarm.EndedAlarmRecordItem;
import com.nm.alarm.NeAlarmAttribute;
import com.nm.alarm.OccuringAlarmRecordItem;
import com.nm.alarm.OneMonitoredObjectAlarmCfg;

/**
 * 适配器端告警缓存
 * @author gailf
 *
 */
public class AlarmCache {
	protected Map<Integer, List<OccuringAlarmRecordItem>> occurAlarmCache = new WeakHashMap<Integer, List<OccuringAlarmRecordItem>>();
	protected Map<Integer, List<EndedAlarmRecordItem>> endAlarmCache = new WeakHashMap<Integer, List<EndedAlarmRecordItem>>();
	protected Map<Integer, List<OneMonitoredObjectAlarmCfg>> alarmConfigCache = new WeakHashMap<Integer, List<OneMonitoredObjectAlarmCfg>>();
	protected Map<Integer, NeAlarmAttribute> alarmAttributeCache = new WeakHashMap<Integer, NeAlarmAttribute>();
	
	static private AlarmCache instance = new AlarmCache(); 
	private AlarmCache(){
		
	}
	
	static public AlarmCache getInstance(){
		return instance;
	}
	
	//未结束告警
	public List<OccuringAlarmRecordItem> getOccurAlarms(int neId){
		synchronized(occurAlarmCache){
			List<OccuringAlarmRecordItem> alarms = occurAlarmCache.get(neId);
			if(alarms != null){
				return new ArrayList<OccuringAlarmRecordItem>(alarms);
			}
			return null;
		}
	}
	
	public void setOccurAlarms(int neId, List<OccuringAlarmRecordItem> alarms){
		synchronized(occurAlarmCache){
			if(occurAlarmCache.size() > 15){
				occurAlarmCache.clear();
			}
			occurAlarmCache.put(neId, alarms);
		}
	}
	
	public void removeOccurAlarms(int neId){
		synchronized(occurAlarmCache){
			occurAlarmCache.remove(neId);
		}
	}
	
	public void addOccurAlarms(OccuringAlarmRecordItem alarm){
		synchronized(occurAlarmCache){
			Integer neId = alarm.getNeId();
			if(neId != null){
				List<OccuringAlarmRecordItem> alarms = occurAlarmCache.get(neId);
				if(alarms != null){
					alarms.add(alarm);
				}
			}
		}
	}
	
	public void removeOccurAlarms(OccuringAlarmRecordItem alarm){
		synchronized(occurAlarmCache){
			Integer neId = alarm.getNeId();
			if(neId != null){
				List<OccuringAlarmRecordItem> alarms = occurAlarmCache.get(neId);
				if(alarms != null){
					alarms.remove(alarm);
				}
			}
		}
	}
	
	
	//结束告警
	public List<EndedAlarmRecordItem> getEndAlarms(int neId){
		synchronized(endAlarmCache){
			List<EndedAlarmRecordItem> alarms = endAlarmCache.get(neId);
			if(alarms != null){
				return new ArrayList<EndedAlarmRecordItem>(alarms);
			}
			return null;
		}
	}
	
	public void setEndAlarms(int neId, List<EndedAlarmRecordItem> alarms){
		synchronized(endAlarmCache){
			if(endAlarmCache.size() > 15){
				endAlarmCache.clear();
			}
			endAlarmCache.put(neId, alarms);
		}
	}
	
	public void removeEndAlarms(int neId){
		synchronized(endAlarmCache){
			endAlarmCache.remove(neId);
		}
	}
	
	public void addEndAlarms(EndedAlarmRecordItem alarm){
		synchronized(endAlarmCache){
			Integer neId = alarm.getNeId();
			if(neId != null){
				List<EndedAlarmRecordItem> alarms = endAlarmCache.get(neId);
				if(alarms != null){
					alarms.add(alarm);
				}
				
			}
		}
	}
	
	public void removeEndAlarms(EndedAlarmRecordItem alarm){
		synchronized(endAlarmCache){
			Integer neId = alarm.getNeId();
			if(neId != null){
				List<EndedAlarmRecordItem> alarms = endAlarmCache.get(neId);
				if(alarms != null){
					alarms.remove(alarm);
				}
			}
		}
	}
	
	
	//告警配置
	public List<OneMonitoredObjectAlarmCfg> getAlarmConfigs(int neId){
		synchronized(alarmConfigCache){
			List<OneMonitoredObjectAlarmCfg> omoacs = alarmConfigCache.get(neId);
			if(omoacs != null){
				return new ArrayList<OneMonitoredObjectAlarmCfg>(omoacs);
			}
			return null;
		}
	}
	
	public void setAlarmConfigs(int neId, List<OneMonitoredObjectAlarmCfg> omoacs){
		synchronized(alarmConfigCache){
			if(alarmConfigCache.size() > 15){
				alarmConfigCache.clear();
			}
			alarmConfigCache.put(neId, omoacs);
		}
	}
	
	public void removeAlarmConfigs(int neId){
		synchronized(alarmConfigCache){
			alarmConfigCache.remove(neId);
		}
	}
	
	public void addAlarmConfigs(OneMonitoredObjectAlarmCfg moac){
		synchronized(alarmConfigCache){
			Integer neId = moac.getNeId();
			if(neId != null){
				List<OneMonitoredObjectAlarmCfg> omoacs = alarmConfigCache.get(neId);
				if(omoacs != null){
					omoacs.add(moac);
				}
				
			}
		}
	}
	
	public void removeAlarmConfigs(OneMonitoredObjectAlarmCfg moac){
		synchronized(alarmConfigCache){
			Integer neId = moac.getNeId();
			if(neId != null){
				List<OneMonitoredObjectAlarmCfg> omoacs = alarmConfigCache.get(neId);
				if(omoacs != null){
					omoacs.remove(moac);
				}
			}
		}
	}
	
	//告警属性
	public NeAlarmAttribute getAlarmAttribute(int neId){
		synchronized(alarmAttributeCache){
			return alarmAttributeCache.get(neId);
		}
	}
	
	public void setAlarmAttribute(int neId, NeAlarmAttribute naa){
		synchronized(alarmAttributeCache){
			if(alarmAttributeCache.size() > 15){
				alarmAttributeCache.clear();
			}
			alarmAttributeCache.put(neId, naa);
		}
	}
	
	public void removeAlarmAttribute(int neId){
		synchronized(alarmAttributeCache){
			alarmAttributeCache.remove(neId);
		}
	}

}
