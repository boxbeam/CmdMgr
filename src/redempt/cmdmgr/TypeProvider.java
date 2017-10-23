package redempt.cmdmgr;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Stream;

import org.bukkit.command.CommandSender;

public class TypeProvider<T> {
	
	private String name;
	private Function<String, T> function;
	private Function<CommandSender, List<String>> tab = null;
	
	/**
	 * Make a new TypeProvider, converts a String to another type, so that a listener method can take direct types rather than taking a String and converting to another type in the method. It is recommended that you use lambdas.
	 * @param name The name of the type provider, to be used as a type in the command file.
	 * @param function The function which converts a String to the type this provider is for. Return null if improper usage, the help will be shown.
	 */
	public TypeProvider(String name, Function<String, T> function) {
		this.name = name;
		this.function = function;
	}
	
	/**
	 * Add a tab handler. This will allow the argument to be tab-completed.
	 * @param completions A Function which gives a list of possible completions for the given command sender. Results which do not match the partial argument already typed by the user are omitted automatically.
	 * @return The TypeProvider. This is intended so that you can define a variable and call this method on the same line.
	 */
	public TypeProvider<T> setTab(Function<CommandSender, List<String>> completions) {
		this.tab = completions;
		return this;
	}
	
	/**
	 * Add a tab handler. This will allow the argument to be tab-completed.
	 * @param completions A Function which gives a list of possible completions for the given command sender, in the form of a stream. Results which do not match the partial argument already typed by the user are omitted automatically.
	 * @return The TypeProvider. This is intended so that you can define a variable and call this method on the same line.
	 */
	public TypeProvider<T> setTabStream(Function<CommandSender, Stream<String>> completions) {
		this.tab = correct(completions);
		return this;
	}
	
	private static Function<CommandSender, List<String>> correct(Function<CommandSender, Stream<String>> tab) {
		return (s) -> {
			List<String> list = new ArrayList<>();
			tab.apply(s).forEach(list::add);
			return list;
		};
	}
	
	public T get(String string) {
		return function.apply(string);
	}
	
	public List<String> complete(String partial, CommandSender sender) {
		if (tab == null) {
			return new ArrayList<>();
		}
		List<String> completions = new CopyOnWriteArrayList<>(tab.apply(sender));
		completions.stream().filter((s) -> s == null || !s.toLowerCase().startsWith(partial)).forEach(completions::remove);
		return completions;
	}
	
	public String getName() {
		return name;
	}
	
}
