package com.nm.server.config.platform;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.persistence.EntityManagerFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.transaction.annotation.Transactional;

import com.nm.base.util.LRUHashMap;
import com.nm.descriptor.AttributeDescriptor;
import com.nm.descriptor.FunctionDescriptor;
import com.nm.server.common.access.DataAccess;
import com.nm.server.common.persist.BaseJpaDao;

@Transactional
public class DataBaseDataAccess implements DataAccess {
	private Log log = LogFactory.getLog(this.getClass());
	static Map<String, BaseJpaDao<Object>> daoMap = new ConcurrentHashMap<String, BaseJpaDao<Object>>();
	static Map<String, Map<String, Field>> classFieldCache = new LRUHashMap<String, Map<String, Field>>(200);
	private EntityManagerFactory entityManagerFactory;
	
	public DataBaseDataAccess(){}

	public void setEntityManagerFactory(EntityManagerFactory emf){
		this.entityManagerFactory = emf;
	}
	

	/**
	 * 新接口
	 */
	
	@Override
	public boolean addDatas(FunctionDescriptor funcDes,
			List<Map<String, Object>> values) {
		// TODO Auto-generated method stub
		if(values == null){
			return false;
		}
		if(funcDes.notBean()){//不是javabean 不用更新数据库
			return false;
		}
		
		try{
			String className = funcDes.getModelClass();
			if(className == null){
				String longName = funcDes.getDomain().toLowerCase()+"."+ funcDes.getName();
				className = "com.nm.models." + longName;
			}else{
				List<AttributeDescriptor> ads = funcDes.getAttributeList();
				for(AttributeDescriptor ad : ads){
					if(ad.getModelField() != null){
						for(Map<String, Object> v : values){
							Object o = v.get(ad.getName());
							if(o != null){
								v.remove(ad.getName());
								v.put(ad.getModelField(), o);
							}
						}
					}
				}
			}
			Class<?> clazz = Class.forName(className);
			return addDatas(clazz, values);
		}catch(Exception e){
			log.error(e, e);
			return false;
		}
	}

	@Override
	public boolean deleteDatas(FunctionDescriptor funcDes,
			List<Map<String, Object>> conditions) {
		try{
			if(funcDes.notBean()){//不是javabean 不用更新数据库
				return false;
			}
			
			String className = funcDes.getModelClass();
			if(className == null){
				String longName = funcDes.getDomain().toLowerCase()+"."+ funcDes.getName();
				className = "com.nm.models." + longName;
			}else{
				List<AttributeDescriptor> ads = funcDes.getAttributeList();
				for(AttributeDescriptor ad : ads){
					if(ad.getModelField() != null){
						for(Map<String, Object> c : conditions){
							Object o = c.get(ad.getName());
							if(o != null){
								c.remove(ad.getName());
								c.put(ad.getModelField(), o);
							}
						}
					}
				}
			}
			log.info(funcDes.getName() + ", className = " + className);
			Class<?> clazz = Class.forName(className);
			
			return deleteDatas(clazz, conditions);
		}catch(Exception e){
			log.error(funcDes.getName(), e);
			return false;
		}
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor funcDes) {
		// TODO Auto-generated method stub
		return getDatas(funcDes, null);
	}

	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor funcDes,
			List<String> fields) {
		// TODO Auto-generated method stub
		return getDatas(funcDes, fields, null);
	}
	
	@Override
	public List<Map<String, Object>> getDatas(FunctionDescriptor funcDes, List<String> fields,
			List<Map<String, Object>> conditions) {
		
		try{
			if(funcDes == null){
				log.error("######funcDes == null");
				return null;
			}
			if(funcDes.notBean()){//不是javabean 不用更新数据库
				return null;
			}
			
			
			String className = funcDes.getModelClass();
			if(className == null){
				String longName = funcDes.getDomain().toLowerCase()+"."+ funcDes.getName();
				className = "com.nm.models." + longName;
			}else{
				List<AttributeDescriptor> ads = funcDes.getAttributeList();
				for(AttributeDescriptor ad : ads){
					if(ad.getModelField() != null){
						for(Map<String, Object> c : conditions){
							Object o = c.get(ad.getName());
							if(o != null){
								c.remove(ad.getName());
								c.put(ad.getModelField(), o);
							}
						}
					}
				}
			}
			Class<?> clazz = Class.forName(className);

			if(fields == null){
				fields = new ArrayList<String>();
				List<AttributeDescriptor> ads = funcDes.getAttributeList();
				if(ads != null){
					for(AttributeDescriptor ad : ads){
						if(ad.getModelField() != null){
							fields.add(ad.getModelField());
						}else{
							fields.add(ad.getName());	
						}
					}
				}
			}else{
				AttributeDescriptor ad = null;
				String f = null;
				List<String> newFields = new ArrayList<String>();
				Iterator<String> it = fields.iterator();
				while(it.hasNext()){
					f = it.next();
					ad = funcDes.getAttribute(f);
					if(ad != null && ad.getModelField() != null){
						it.remove();
						newFields.add(ad.getModelField());
					}
				}
				if(newFields != null && !newFields.isEmpty()){
					fields.addAll(newFields);
				}
			}
			fields.add("id");
			fields.add("neId");
			return getDatas(clazz, fields, conditions);
		}catch(Exception e){
			log.error(e, e);
		}
		return null;
	}

	@Override
	public boolean updateDatas(FunctionDescriptor funcDes,
			Map<String, Object> value, List<Map<String, Object>> conditions) {
		try{
			if(funcDes.notBean()){//不是javabean 不用更新数据库
				return false;
			}
			
			String className = funcDes.getModelClass();
			if(className == null){
				String longName = funcDes.getDomain().toLowerCase()+"."+ funcDes.getName();
				className = "com.nm.models." + longName;
			}else{
				List<AttributeDescriptor> ads = funcDes.getAttributeList();
				for(AttributeDescriptor ad : ads){
					if(ad.getModelField() != null){
						Object o = value.get(ad.getName());
						if(o != null){
							value.remove(ad.getName());
							value.put(ad.getModelField(), o);
						}
						for(Map<String, Object> c : conditions){
							o = c.get(ad.getName());
							if(o != null){
								c.remove(ad.getName());
								c.put(ad.getModelField(), o);
							}
						}
					}
				}
			}
			Class<?> clazz = Class.forName(className);
            this.updateDatas(clazz, value, conditions);
					
		}catch(Exception e){
			log.error(e, e);
			return false;
		}
		return true;
	}


	public boolean addDatas(Class clazz, List<Map<String, Object>> values) {
		// TODO Auto-generated method stub
		try{
			String className = clazz.getSimpleName();
			BaseJpaDao<Object> dao = daoMap.get(className);
			if(dao == null){
				dao = new BaseJpaDao<Object>(clazz);
				dao.setEntityManagerFactory(entityManagerFactory);
				daoMap.put(className, dao);
			}
			
			Map<String, Field> fieldMap = classFieldCache.get(className);
			if(fieldMap == null){
				fieldMap = new HashMap<String, Field>();
				classFieldCache.put(className, fieldMap);
				Field[] fs = clazz.getDeclaredFields();
				if(fs != null){
					for(Field f : fs){
						fieldMap.put(f.getName(), f);
					}
				}
				if (clazz.getSuperclass() != null) {
					fs = clazz.getSuperclass().getDeclaredFields();
					if(fs != null){
						for(Field f : fs){
							fieldMap.put(f.getName(), f);
						}
					}
				}
			}
			
			Object o;
			Field f;
			for(Map<String, Object> m : values){
				o = clazz.newInstance();
				Set<Entry<String, Object>> es = m.entrySet();
				for(Entry<String, Object> e : es){
					f = fieldMap.get(e.getKey());
		            if(f != null){
		            	if(!f.isAccessible()){
		            		f.setAccessible(true);
		            	}
		            	if(e.getValue() != null){
		            		if(f.getType().equals(String.class) && !(e.getValue() instanceof String)){
		            			log.info("#########属性类型f.getType :"+ f.getType());
		            			f.set(o, e.getValue().toString());
		            		}else{
		            			f.set(o, e.getValue());
		            		}
		            	}
		            }else{
		            	log.error(className + "找不到：" + e.getKey());
		            }
		           
				}
				dao.save(o);
			}
		}catch(Exception e){
			log.error(e, e);
			return false;
		}
		
		return true;
	}


	@SuppressWarnings("rawtypes")
	public boolean deleteDatas(Class clazz,
			List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
		String className = clazz.getSimpleName();
		BaseJpaDao<Object> dao = daoMap.get(className);
		if(dao == null){
			dao = new BaseJpaDao<Object>(clazz);
			dao.setEntityManagerFactory(entityManagerFactory);
			daoMap.put(className, dao);
		}
		if(conditions != null){
			for(Map<String, Object> m : conditions){
				dao.deleteBy(m);
			}
		}else{
			StringBuffer sb = new StringBuffer("delete from ");
			sb.append(clazz.getSimpleName()).append(" obj ");
			dao.batchExecute(sb.toString());
		}
		return false;
	}


	public List<Map<String, Object>> getDatas(Class clazz) {
		// TODO Auto-generated method stub
		return getDatas(clazz, null);
	}


	public List<Map<String, Object>> getDatas(Class clazz,
			List<String> fields) {
		// TODO Auto-generated method stub
		return getDatas(clazz, fields, null);
	}


	public List<Map<String, Object>> getDatas(Class clazz,
			List<String> fields, List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
		try{
			String className = clazz.getSimpleName();
			Map<String, Field> fieldMap = classFieldCache.get(className);
			if(fieldMap == null){
				fieldMap = new HashMap<String, Field>();
				classFieldCache.put(className, fieldMap);
				Field[] fs = clazz.getDeclaredFields();
				if(fs != null){
					for(Field f : fs){
						fieldMap.put(f.getName(), f);
					}
				}
				if (clazz.getSuperclass() != null) {
					fs = clazz.getSuperclass().getDeclaredFields();
					if(fs != null){
						for(Field f : fs){
							fieldMap.put(f.getName(), f);
						}
					}
				}
			}


			BaseJpaDao<Object> dao = daoMap.get(className);
			if(dao == null){
				dao = new BaseJpaDao<Object>(clazz);
				dao.setEntityManagerFactory(entityManagerFactory);
				daoMap.put(className, dao);
			}
			List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
			
			List<Object> os = new ArrayList<Object>();
			if(conditions == null){
				os = dao.findAll();
			}else{
				for(Map<String, Object> m : conditions){
					for(String key : m.keySet()){
						if(!fieldMap.containsKey(key)){
							m.remove(key);
						}
					}
					os.addAll(dao.findBy(m));
				}
			}
			
			Field f;
			Map<String, Object> value;
			for(Object o : os){
				value = new HashMap<String, Object>();
				if(fields != null){
					for(String field : fields){
						f = fieldMap.get(field);
						if(f != null){
							if(!f.isAccessible()){
								f.setAccessible(true);
							}
							value.put(field, f.get(o));
						}
					}
				}
				values.add(value);
			}
		
			return values;
		}catch(Exception e){
			log.error(e, e);
		}
		return null;
	}


	public boolean updateDatas(Class clazz, Map<String, Object> values,
			List<Map<String, Object>> conditions) {
		// TODO Auto-generated method stub
		try{
			String className = clazz.getSimpleName();
			BaseJpaDao<Object> dao = daoMap.get(className);
			if(dao == null){
				dao = new BaseJpaDao<Object>(clazz);
				dao.setEntityManagerFactory(entityManagerFactory);
				daoMap.put(className, dao);
			}
			if(values != null){
				List<Object> params = new ArrayList<Object>();
				int index = 1;
				StringBuffer sb = new StringBuffer("update ");
				sb.append(className).append(" o set ");
				boolean first = true;
				Set<Entry<String, Object>> es = values.entrySet();
				for(Entry<String, Object> e : es){
					if(e.getKey().compareTo("id") == 0){
						continue;
					}
					if(!first){
						sb.append(",");
					}else{
						first = false;
					}
					sb.append("o.");
					sb.append(e.getKey());
					sb.append("=?");
					sb.append(index++);
					params.add(e.getValue());
					log.info(e.getKey() + ":" + e.getValue());
				}
				if(conditions != null && !conditions.isEmpty()){
					sb.append(" where ");
					first = true;
					for(Map<String, Object> c : conditions){
						es = c.entrySet();
						if(!first){
							sb.append(" or ");
						}else{
							first = false;
						}
						sb.append(" (");
						boolean first2 = true;
						for(Entry<String, Object> e : es){
							if(!first2){
								sb.append(" and ");
							}else{
								first2 = false;
							}
							sb.append(" o.");
							sb.append(e.getKey());
							sb.append("=?");
							sb.append(index++);
							params.add(e.getValue());
							log.info(e.getKey() + ":" + e.getValue());
						}
						sb.append(") ");
					}
				}
				log.info(sb.toString());
				Integer result = dao.batchExecute(sb.toString(), params.toArray());
			}	
		}catch(Exception e){
			log.error(e, e);
		}
		return false;
	}

}
