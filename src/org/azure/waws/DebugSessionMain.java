package org.azure.waws;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.*;

public class DebugSessionMain {

	public static void main(String[] args) {
		Log log = LogFactory.getLog(DebugSessionMain.class);
		CommandLineParser parser = new BasicParser();
		Options options = new Options();
		options.addOption("p", "port", true, "local port (required)");
		options.addOption("s", "server", true, "server (required)");
		options.addOption("u", "user", true, "username (required iff password is specified)");
		options.addOption("w", "pass", true, "password (optional)");
		options.addOption("a", "affinity", true, "affinity cookie value (optional)");
		options.addOption("t", "auto", false, "restart automatically on disconnect/error.");		
		
		try {
			CommandLine commandLine = parser.parse(options, args);
			int port = -1;
			String server = "";
			String username = "";
			String password = "";
			String affinity = "";
			
			if(commandLine.hasOption('p'))
			{
				port = Integer.parseInt(commandLine.getOptionValue('p'));
			}
			else
			{
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("DebugSessionMain", options);
				return;
			}
			
			if(commandLine.hasOption('s'))
			{
				server = commandLine.getOptionValue('s');
			}
			else
			{
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("DebugSessionMain", options);
				return;
			}
			
			if(commandLine.hasOption('u'))
			{
				username = commandLine.getOptionValue('u');
			}
			
			if(commandLine.hasOption('w'))
			{
				password = commandLine.getOptionValue('w');
			}		
			
			if(username.isEmpty() && !password.isEmpty())
			{
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("DebugSessionMain", options);
			}
			
			if(commandLine.hasOption('a'))
			{
				affinity = commandLine.getOptionValue('a');
			}
			
			do
			{
				if(username.isEmpty())
				{
					if(affinity.isEmpty())
					{
						new DebugSession(port, server).startSession();
					}
					else
					{
						new DebugSession(port, server, affinity).startSession();
					}
				}
				else if(affinity.isEmpty())
				{
					new DebugSession(port, server, username, password).startSession();
				}
				else
				{
					new DebugSession(port, server, username, password, affinity).startSession();
				}
			} while(commandLine.hasOption('t'));
			
		} catch (ParseException e) {
			log.error(e);
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("DebugSessionMain", options);
		} catch (Exception e) {
			log.error(e);
		}
	}

}
