package com.nm.server.adapter.base.exception;

public class SerialConnectionException extends Exception {

    /**
	 * 
	 */
	private static final long serialVersionUID = 2222493028889110815L;

	/**
     * Constructs a <code>SerialConnectionException</code>
     * with the specified detail message.
     *
     * @param   s   the detail message.
     */
    public SerialConnectionException(String str) {
        super(str);
    }

    /**
     * Constructs a <code>SerialConnectionException</code>
     * with no detail message.
     */
    public SerialConnectionException() {
        super();
    }
}
