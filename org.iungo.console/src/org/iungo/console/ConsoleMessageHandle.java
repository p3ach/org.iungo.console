package org.iungo.console;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iungo.core.message.Message;
import org.iungo.core.message.MessageHandle;
import org.iungo.core.message.MessageHandleGo;
import org.iungo.core.result.Result;

public class ConsoleMessageHandle extends MessageHandle {

	/*
	 * Class. 
	 */
	
	private static final Logger logger = LogManager.getLogger(ConsoleMessageHandle.class);

	/*
	 * Instance.
	 */
	
	public ConsoleMessageHandle(final String key, final MessageHandleGo go) {
		super(key, go);
	}

	@Override
	public Result tryHandle(Message message) {
		Result result =  super.tryHandle(message);
		logger.info(result);
		return result;
	}

}
