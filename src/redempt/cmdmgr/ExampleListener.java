package redempt.cmdmgr;

import org.bukkit.command.CommandSender;

public class ExampleListener {
	
	@CommandHook("doStuff")
	public void doStuff(CommandSender sender, String stuff) {
		sender.sendMessage(stuff);
	}
	
}
