package com.nm.server.adapter.base;


public class AdapterDescr
{
	String neType;
	CommProtocol protocol;

	public AdapterDescr(String neType, CommProtocol protocol)
	{
		this.neType = neType;
		this.protocol = protocol;
	}

	@Override
	public boolean equals(Object obj)
	{
		AdapterDescr nad = (AdapterDescr) obj;
		return ((null != nad) && (0 == this.neType.compareTo(nad.neType)) && (0 == this.protocol.compareTo(nad.protocol)));
	}

	@Override
	public int hashCode()
	{
		return this.neType.hashCode() + this.protocol.hashCode();
	}

};