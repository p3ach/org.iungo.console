package org.iungo.console;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.iungo.core.argument.Argument;
import org.iungo.core.argument.ArgumentCallback;
import org.iungo.core.argument.CloseConsoleArgument;
import org.iungo.core.argument.CloseIungoArgument;
import org.iungo.core.argument.ConnectIungoArgument;
import org.iungo.core.argument.NewIungoArgument;
import org.iungo.core.argument.EchoRequestArgument;
import org.iungo.core.argument.ShowMapperArgument;
import org.iungo.core.argument.ShowSystemArgument;
import org.iungo.core.concurrent.ConcurrentHashSet;
import org.iungo.core.context.Context;
import org.iungo.core.id.ID;
import org.iungo.core.id.IDFactory;
import org.iungo.core.iungo.Iungo;
import org.iungo.core.lang.SystemUtils;
import org.iungo.core.mapper.Mapper;
import org.iungo.core.message.Message;
import org.iungo.core.message.MessageFactory;
import org.iungo.core.node.Node;
import org.iungo.core.node.NodeContext;
import org.iungo.core.result.Result;
import org.iungo.core.shell.Shell;

public class Console extends Shell {

	/*
	 * Class.
	 */
	
	private static final Logger logger = LogManager.getLogger(Console.class.getName());

	private static final IDFactory idFactory = IDFactory.valueOf(Console.class);

	protected static final ThreadGroup threadGroup = new ThreadGroup(Console.class.getName());
	
	public static volatile Console console;

	protected static Console newConsole() {
		final Context context = new Context();
		/*
		 * Node Context.
		 */
		final NodeContext nodeContext = new NodeContext(context);
		nodeContext.putNodeMapper(new Mapper());
		nodeContext.putNodeIDFactory(IDFactory.valueOf(Console.class));
		/*
		 * Create the Console.
		 */
		Console console = new Console(context);
		new Thread(threadGroup, console, Console.class.getName()).start();
		
		return console;
	}

	public static void main(String[] args) throws InterruptedException {
		logger.info(SystemUtils.getSystem());
		logger.info(Console.console);
		console = newConsole();
	}
	
	/*
	 * Instance.
	 */
	
	protected final ConsoleContext consoleContext;
	
	protected final BufferedReader bufferedReader;

	protected final ConcurrentHashSet<Iungo> iungos = new ConcurrentHashSet<>();
	
	protected final MessageFactory messageIDFactory = new MessageFactory(new IDFactory(idFactory, "Message"));
	
	protected final Runnable read = new Runnable() {
		@Override
		public void run() {
			while (true) {
				try {
					Result result = activeCLI.execute(readLine(activeCLI.getPrompt()));
					if (result.isTrue()) {
						logger.info(String.format("\n%s", result.getText()));
					} else {
						logger.info(result);
					}
				} catch (final Throwable throwable) {
					logger.error(Result.valueOf(throwable));
				}
			}
		}
	};
	
	protected Console(final Context context) {
		super(context);
		
		consoleContext = new ConsoleContext(context);
		
		bufferedReader = newReader();
		
		cli.getArgumentCallbacks().addCallback(CloseConsoleArgument.class, new ArgumentCallback() {
			@Override
			protected Result execute(final Context context, final Argument argument, final Result argumentResult) {
				Result result = argumentResult;
				if (result.isTrue()) {
					result = close();
				}
				return result;
			}
		});
		
		cli.getArgumentCallbacks().addCallback(CloseIungoArgument.class, new ArgumentCallback() {
			@Override
			protected Result execute(final Context context, final Argument argument, final Result argumentResult) {
				Result result = argumentResult;
				if (result.isTrue()) {
					CloseIungoArgument closeIungoArgument = (CloseIungoArgument) argument;
					result = closeIungoArgument.getIungo().tryExecute(context);
					if (result.isTrue()) {
						result = ((Iungo) result.getValue()).close();
					}
				}
				return result;
			}
		});
		
		cli.getArgumentCallbacks().addCallback(ConnectIungoArgument.class, new ArgumentCallback() {
			@Override
			protected Result execute(final Context context, final Argument argument, final Result argumentResult) {
				Result result = argumentResult;
				if (result.isTrue()) {
					ConnectIungoArgument connectIungoArgument = (ConnectIungoArgument) argument;
					result = connectIungoArgument.getIungo().tryExecute(context);
					if (result.isTrue()) {
						activeCLI = ((Iungo) result.getValue()).getCLI();
					}
				}
				Object cli = argumentResult.getValue();
				if (Iungo.class.isAssignableFrom(cli.getClass())) {
					activeCLI = ((Iungo) cli).getCLI();
				}
				return result;
			}
		});
		
		cli.getArgumentCallbacks().addCallback(NewIungoArgument.class, new ArgumentCallback() {
			@Override
			protected Result execute(final Context argumentContext, final Argument argument, final Result argumentResult) {
				final Result result = Iungo.newIungo();
				if (result.isTrue()) {
					mapper.add((Node) result.getValue());
				}
				return result;
			}
		});

		cli.getArgumentCallbacks().addCallback(EchoRequestArgument.class, new ArgumentCallback() {
			@Override
			protected Result execute(final Context argumentContext, final Argument argument, final Result argumentResult) {
				Result result = argumentResult;
				if (argumentResult.isTrue()) {
					final ID to = ID.valueOf(argument.tryExecute(context).getValue());
					final Message message = messageIDFactory.create(getID(), to, Node.NODE_PING_REQUEST_MESSAGE_TYPE);
					messageReplyHandles.add(new ConsoleMessageHandle(message.getID().getID(), pingReplyHandleGo));
					result = mapper.trySendMessage(message);
				}
				return result;
			}
		});
		
		cli.getArgumentCallbacks().addCallback(ShowMapperArgument.class, new ArgumentCallback() {
			@Override
			protected Result execute(final Context context, final Argument argument, final Result argumentResult) {
				return Result.createTrue(mapper.toString());
			}
		});
		
		cli.getArgumentCallbacks().addCallback(ShowSystemArgument.class, new ArgumentCallback() {
			@Override
			protected Result execute(final Context argumentContext, final Argument argument, final Result argumentResult) {
				Result result = argumentResult;
				final StringBuilder text = new StringBuilder(1024);
				for (Iungo iungo : iungos) {
					text.append(String.format("\n%s", Node.stateOf(iungo)));
				}
				text.append(cli.getConfigs());
				result = Result.createTrue(String.format("%s\nConsole:\nCreated [%s]\nIungo\n[%s]", result.getText(), new Date(createdAt), text));
				return result;
			}
		});
		
		final Thread thread = new Thread(threadGroup, read, String.format("%s#in", Console.class.getName()));
		thread.setDaemon(true);
		thread.start();
	}

	// TODO Create our own System.in.read() wrapper.
	protected BufferedReader newReader() {
		return (System.console() == null ? new BufferedReader(new InputStreamReader(System.in)) : null);
	}
	
	public void addIungo(final Iungo iungo) {
		iungos.add(iungo);
	}
	
	public void removeIungo(final Iungo iungo) {
		iungos.remove(iungo);
	}
	
    protected String readLine(String prompt) {
    	String result;
    	if (System.console() == null) {
            System.out.print(prompt);
            try {
                 result = bufferedReader.readLine();
            } catch (IOException e) { 
                //Ignore    
            	result = "";
            }
    	} else {
    		result = System.console().readLine(prompt);
    	}
    	return result;
    }
    
    protected char[] readPassword(final String prompt) {
    	char[] result;
    	if (System.console() == null) {
            System.out.print(prompt);
            try {
                 result = bufferedReader.readLine().toCharArray();
            } catch (IOException e) { 
                //Ignore    
            	result = new char[0];
            }
    	} else {
    		result = System.console().readPassword(prompt);
    	}
    	return result;
    }

	@Override
	public Result close() {
		Result result;
		if (iungos.size() == 0) {
			result = super.close();
		} else {
			result = Result.createFalse("Cannot close.");
		}
		return result;
	}

	@Override
	public String toString() {
		return String.format("%s", consoleContext.getNodeIDFactory());
	}
}
