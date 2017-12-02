package redempt.cmdmgr;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Player;

public class Command {
	
	private String[] names;
	private String permission;
	private String help;
	private String users;
	private CommandArgument[] args;
	private List<Command> children = new ArrayList<>();
	private Method hook;
	private String hookName;
	private String tempName = null;
	private Object listener = null;
	private Map<String, TypeProvider<?>> providers = new HashMap<>();
	private boolean hideSub;
	private int lineNum = 0;
	
	private Command(String[] names, String permission, String help, String users, String hook, boolean hideSub, CommandArgument... args) {
		this.hideSub = hideSub;
		this.names = names;
		this.permission = permission;
		this.help = help;
		this.users = users;
		this.args = args;
		hookName = hook;
		if (hideSub && help == null) {
			this.help = "Subcommands hidden";
		}
	}
	
	private void setTempName(String name) {
		tempName = name;
	}
	
	/**
	 * Gets the children (subcommands) of this command.
	 * Intended for internal use. I don't see why you'd need to use it, but I guess someone might need to sometime.
	 * @return The list of children.
	 */
	public List<Command> getChildren() {
		return children;
	}
	
	public CommandArgument[] getArguments() {
		return args;
	}
	
	public String[] getNames() {
		return names;
	}
	
	/**
	 * Adds a subcommand to a command. Intended for internal use, but go ahead and use it if you want to add children at runtime. Note that children added after the command is registered will not have access to the type providers the command does.
	 * @param command The child command to be added.
	 */
	public void addChild(Command command) {
		children.add(command);
	}
	
	/**
	 * Registers this command. Register type providers before doing this.
	 * @param prefix The prefix at the beginning of the command /prefix:cmdname
	 * @param listener The listener object containing annotated command listener methods
	 */
	public void register(String prefix, Object listener) {
		registerListener(listener);
		try {
			Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
			field.setAccessible(true);
			SimpleCommandMap map = (SimpleCommandMap) field.get(Bukkit.getServer());
			for (String name : names) {
				org.bukkit.command.Command cmd = new org.bukkit.command.Command(name, help != null ? help : "No help provided (CmdMgr)",  "", new ArrayList<String>()) {

					@Override
					public boolean execute(CommandSender sender, String label, String[] args) {
						Command.this.execute(sender, args);
						return false;
					}
					
					@Override
					public List<String> tabComplete(CommandSender sender, String label, String[] args) {
						return Command.this.tabComplete(sender, args);
					}
					
				};
				cmd.setPermission(null);
				map.register(prefix, cmd);
			}
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}
	
	private static String[] removeFirst(String[] args) {
		String[] newArgs = new String[args.length - 1];
		for (int i = 1; i < args.length; i++) {
			newArgs[i - 1] = args[i];
		}
		return newArgs;
	}
	
	private static Object[] getArguments(Command command, String[] args, CommandArgument[] types) {
		if (args.length != types.length && !(types.length != 0 && args.length > types.length && types[types.length - 1].getType() == CommandArgumentType.MULTISTRING)) {
			return null;
		}
		Object[] value = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			Object obj = getType(command, args[i], types[i]);
			if (types[i].getType() == CommandArgumentType.MULTISTRING) {
				Object[] newValue = new Object[i + 1];
				for (int x = 0; x < newValue.length; x++) {
					newValue[x] = value[x];
				}
				String combine = "";
				for (int x = i; x < args.length; x++) {
					combine += args[x] + " ";
				}
				newValue[i] = combine.trim();
				return newValue;
			}
			if (obj == null) {
				return null;
			}
			value[i] = obj;
		}
		return value;
	}
	
	/**
	 * Get the help message for this command.
	 * @return The help message
	 */
	public String getHelp() {
		return help;
	}
	
	/**
	 * Gets the first name specified for this command.
	 * @return The first name
	 */
	public String getPrimaryName() {
		return tempName == null ? names[0] : tempName;
	}
	
	/**
	 * Get the name and arguments as a String.
	 * @return The full name of this command
	 */
	private String getFullName() {
		String names = getPrimaryName();
		names = names.replaceAll("\\|$", "");
		for (CommandArgument arg : args) {
			names += " " + arg.toString(this);
		}
		return names;
	}
	
	private String getFullArgs() {
		String args = "";
		for (CommandArgument arg : this.args) {
			args += " " + arg.toString(this);
		}
		return args.trim();
	}
	
	private String getAliases() {
		if (this.names.length == 1) {
			return getPrimaryName();
		}
		String names = "";
		for (String name : this.names) {
			names += name + "|";
		}
		return names.replaceAll("\\|$", "");
	}
	
	private void showHelp(CommandSender sender) {
		String message = ChatColor.GREEN + "--" + ChatColor.YELLOW + "[ Help for " + getPrimaryName() + " ]" + ChatColor.GREEN + "--\n";
		message += getHelpRecursive("", sender, true);
		sender.sendMessage(message);
	}
	
	private boolean isAllowed(CommandSender sender) {
		return permission == null || sender.hasPermission(permission);
	}
	
	private String getHelpRecursive(String prefix, CommandSender sender, boolean ignoreHidden) {
		for (Command child : children) {
			for (String name : child.getNames()) {
				if (name.equals("_")) {
					child.setTempName(names[0]);
				}
			}
		}
		String help = "";
		if (this.help != null && isAllowed(sender) && !(ignoreHidden && hideSub)) {
			if (tempName != null) {
				help = ChatColor.YELLOW + prefix + getFullArgs() + ChatColor.GREEN + ": " + this.help + "\n";
			} else {
				help = ChatColor.YELLOW + prefix + getFullName() + ChatColor.GREEN + ": " + this.help + "\n";
			}
		}
		if (hideSub && !ignoreHidden) {
			return help;
		}
		for (Command child : children) {
			if (child.getNames()[0].equals("_")) {
				child.setTempName(names[0]);
			}
			help += child.getHelpRecursive(prefix + getAliases() + " ", sender, false) + "\n";
		}
		return help.replaceAll("\n$", "");
	}
	
	private List<String> tabComplete(CommandSender sender, String[] args) {
		if (tempName != null) {
			return null;
		}
		for (Command child : children) {
			if (child.getNames()[0].equals("_")) {
				child.setTempName(names[0]);
			}
		}
		if (!isAllowed(sender)) {
			return null;
		}
		switch (users) {
			case "everyone":
				break;
			case "console":
				if (sender instanceof Player) {
					return null;
				}
				break;
			case "player":
				if (!(sender instanceof Player)) {
					return null;
				}
				break;
		}
		List<String> completions = new CopyOnWriteArrayList<>();
		if (this.args.length >= args.length) {
			CommandArgument arg = this.args[args.length - 1];
			if (arg.getType() == CommandArgumentType.CUSTOM) {
				completions.addAll(arg.getProvider(Command.this).complete(args[args.length - 1].toLowerCase().trim(), sender));
			}
		}
		String finalArg = args[args.length - 1].toLowerCase().trim();
		if (args.length == 1) {
			for (Command command : children) {
				if (command.tempName == null) {
					completions.add(command.getPrimaryName());
				}
				if (!args[args.length - 1].equals("")) {
					Arrays.stream(command.getNames()).filter((s) -> s.toLowerCase().startsWith(finalArg) && !(command.getPrimaryName().toLowerCase().startsWith(finalArg) && !command.getPrimaryName().equals(finalArg)) && !completions.contains(s)).forEach(completions::add);
				}
			}
			completions.stream().filter((s) -> s == null || !s.toLowerCase().startsWith(finalArg)).forEach(completions::remove);
			if (hasName(finalArg)) {
				Arrays.stream(names).forEach(completions::add);
			}
		}
		if (completions.size() > 0) {
			return completions;
		}
		for (Command command : children) {
			if (command.tempName == null && command.hasName(args[0].toLowerCase().trim())) {
				return command.tabComplete(sender, removeFirst(args));
			}
			if (command.tempName != null) {
				return command.tabComplete(sender, args);
			}
		}
		return null;
	}
	
	private boolean hasName(String name) {
		for (String alias : names) {
			if (alias.equals(name)) {
				return true;
			}
		}
		return false;
	}
	
	private boolean execute(CommandSender sender, String[] args) {
		if (!isAllowed(sender)) {
			sender.sendMessage(ChatColor.RED + "You do not have permission to execute that command. " + "(Node: " + permission + ")");
			return false;
		}
		switch (users) {
			case "everyone":
				break;
			case "console":
				if (sender instanceof Player) {
					sender.sendMessage(ChatColor.RED + "This command can only be executed by console!");
					return false;
				}
				break;
			case "player":
				if (!(sender instanceof Player)) {
					sender.sendMessage(ChatColor.RED + "This command can only be exeucted by a player!");
					return false;
				}
				break;
		}
		Object[] values = getArguments(this, args, this.args);
		if (values == null || (args.length == 0 && hook == null)) {
			if (args.length == 0) {
				showHelp(sender);
			} else {
				for (Command child : children) {
					for (String name : child.getNames()) {
						if (name.equals("_")) {
							child.setTempName(names[0]);
							if (!child.execute(sender, args)) {
								continue;
							}
							return true;
						}
						if (name.equals(args[0])) {
							if (child.execute(sender, removeFirst(args))) {
								return true;
							}
						}
					}
				}
				if (tempName != null) {
					return false;
				}
				showHelp(sender);
			}
			return true;
		}
		Object[] newArgs = new Object[values.length + 1];
		newArgs[0] = sender;
		for (int i = 0; i < values.length; i++) {
			newArgs[i + 1] = values[i];
		}
		try {
			hook.invoke(listener, newArgs);
		} catch (IllegalArgumentException e) {
			System.out.println("Could not invoke method hook '" + hookName + "', invalid arguments for method. The arguments should be CommandSender followed by all other argument types.");
			String expectedTypes = "CommandSender, ";
			String argumentTypes = "";
			for (Object o : newArgs) {
				if (o.equals(newArgs[0])) {
					continue;
				}
				expectedTypes += o.getClass().getSimpleName() + ", ";
			}
			expectedTypes = expectedTypes.replaceAll(", $", "");
			for (Parameter param : hook.getParameters()) {
				argumentTypes += param.getType().getSimpleName() + ", ";
			}
			argumentTypes = argumentTypes.replaceAll(", $", "");
			System.out.println("Expected types: " + expectedTypes);
			System.out.println("Found types: " + argumentTypes);
			sender.sendMessage(ChatColor.RED + "An error occurred in executing this command, please check console.");
		} catch (IllegalAccessException | InvocationTargetException | NullPointerException e) {
			System.out.println("Could not invoke method hook '" + hookName + "', the method either does not exist, is not public, the listener is null, or the method errored.");
			sender.sendMessage(ChatColor.RED + "An error occurred in executing this command, please check console.");
			e.printStackTrace();
			return true;
		}
		return true;
	}
	
	private static Object getType(Command command, String arg, CommandArgument type) {
		switch (type.getType()) {
			case INT:
				try {
					return Integer.parseInt(arg);
				} catch (NumberFormatException e) {
					return null;
				}
			case DOUBLE:
				try {
					return Double.parseDouble(arg);
				} catch (NumberFormatException e) {
					return null;
				}
			case STRING:
				return arg;
			case MULTISTRING:
				return arg;
			case CUSTOM:
				Object o = type.getProvider(command).get(arg);
				return o;
			default:
				return null;
		}
	}
	
	private void registerListener(Object listener) {
		this.listener = listener;
		for (Method method : listener.getClass().getDeclaredMethods()) {
			if (method.isAnnotationPresent(CommandHook.class)) {
				CommandHook hook = method.getAnnotation(CommandHook.class);
				if (hook.value().equals(hookName) && !Modifier.isStatic(method.getModifiers())) {
					this.hook = method;
					break;
				}
			}
		}
		for (Command command : children) {
			command.registerListener(listener);
		}
	}
	
	/**
	 * Create a new Command from an InputStream. Use Plugin#getResource to get this stream. See the examplecmd.txt for format.
	 * @param stream The InputStream to read from
	 * @return A Command which represents the information from the stream
	 * @throws IOException
	 */
	public static List<Command> fromStreamMulti(InputStream stream) throws IOException {
		if (stream == null) {
			throw new IllegalArgumentException("Stream cannot be null");
		}
		InputStreamReader read = new InputStreamReader(stream);
		BufferedReader reader = new BufferedReader(read);
		String combine = "";
		String line = "";
		try {
			while ((line = reader.readLine()) != null) {
				combine += line + "\n";
			}	
		} catch (EOFException e) {
		}
		reader.close();
		return fromString(combine);
	}
	
	public static Command fromStream(InputStream stream) throws IOException {
		if (stream == null) {
			throw new IllegalArgumentException("Stream cannot be null");
		}
		InputStreamReader read = new InputStreamReader(stream);
		BufferedReader reader = new BufferedReader(read);
		String combine = "";
		String line = "";
		try {
			while ((line = reader.readLine()) != null) {
				combine += line + "\n";
			}	
		} catch (EOFException e) {
		}
		reader.close();
		return fromStringSingle(combine, 0);
	}
	
	private static Command fromStringSingle(String string, int lineNumber) {
		String[] split = string.split("\n");
		String[] names = null;
		String permission = null;
		String users = null;
		String hook = null;
		String help = null;
		boolean hideSub = false;
		List<Command> children = new ArrayList<>();
		CommandArgument[] args = null;
		int depth = 0;
		for (int lineIter = lineNumber; lineIter < split.length; lineIter++) {
			String line = split[lineIter];
			line = line.trim();
			if (line.endsWith("{")) {
				depth++;
				if (depth == 1) {
					String[] lineSplit = line.split(" ");
					names = lineSplit[0].split(",");
					System.out.println("Loading command '" + names[0] + "'");
					args = new CommandArgument[lineSplit.length - 2];
					for (int i = 1; i < lineSplit.length - 1; i++) {
						String[] argSplit = lineSplit[i].split(":");
						args[i - 1] = new CommandArgument(argSplit[0], argSplit[1]);
					}
				} else if (depth == 2) {
					children.add(fromStringSingle(string, lineIter));
				}
			}
			if (line.equals("}")) {
				depth--;
				if (depth == 0) {
					if (users == null) {
						users = "everyone";
					}
					Command command = new Command(names, permission, help, users, hook, hideSub, args);
					for (Command child : children) {
						command.addChild(child);
					}
					command.lineNum = lineIter;
					return command;
				}
			}
			if (depth == 1) {
				if (line.startsWith("hook ")) {
					hook = line.replaceFirst("hook ", "");
				}
				if (line.startsWith("permission ")) {
					permission = line.replaceFirst("permission ", "");
				}
				if (line.startsWith("users ")) {
					users = line.replaceFirst("users ", "");
				}
				if (line.startsWith("help ")) {
					help = line.replaceFirst("help ", "");
				}
				if (line.startsWith("hidesub")) {	
					hideSub = true;
				}
			}
		}
		return null;
	}
	
	private static List<Command> fromString(String string) {
		List<Command> all = new ArrayList<>();
		Command command;
		int lineNum = 0;
		while ((command = fromStringSingle(string, lineNum)) != null) {
			all.add(command);
			lineNum = command.lineNum + 1;
		}
		return all;
	}
	
	/**
	 * Registers a type provider
	 * @param provider The type provider to register
	 */
	public <T> void registerTypeProvider(TypeProvider<T> provider) {
		System.out.println("Registering type provider '" + provider.getName() + "'");
		registerTypeProviderSilent(provider);
	}
	
	private <T> void registerTypeProviderSilent(TypeProvider<T> provider) {
		System.out.println();
		providers.put(provider.getName(), provider);
		for (Command child : children) {
			child.registerTypeProviderSilent(provider);
		}
	}
	
	public static class CommandArgument {
		
		private String name;
		private String type;
		private TypeProvider<?> provider;
		
		public CommandArgument(String type, String name) {
			this.type = type;
			this.name = name;
		}
		
		public CommandArgumentType getType() {
			try {
				return CommandArgumentType.valueOf(type.toUpperCase().replace("*", ""));
			} catch (IllegalArgumentException e) {
				return CommandArgumentType.CUSTOM;
			}
		}
		
		public TypeProvider<?> getProvider(Command command) {
			if (getType() == CommandArgumentType.CUSTOM) {
				if (provider == null) {
					provider = command.providers.get(type.replace("*", ""));
				}
				return provider;
			}
			return null;
		}
		
		public String getName() {
			return name;
		}
		
		public String toString(Command command) {
			if (type.startsWith("*")) {
				return "<" + name + ">";
			}
			if (getType() == CommandArgumentType.CUSTOM) {
				if (name.equals("_")) {
					return "<" + getProvider(command).getName().toLowerCase() + ">";
				}
				return "<" + getProvider(command).getName().toLowerCase() + ":" + name + ">";
			}
			if (name.equals("_")) {
				return "<" + type.toLowerCase() + ">";
			}
			return "<" + type.toLowerCase() + ":" + name + ">";
		}
		
	}
	
	public static enum CommandArgumentType {
		
		STRING,
		INT,
		DOUBLE,
		MULTISTRING,
		CUSTOM
		
	}
	
}
