package redempt.cmdmgr;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandManager extends JavaPlugin {
	
	@Override
	public void onEnable() {
		Bukkit.getLogger().info("CmdMgr by Redempt, a utility to make commands easier");
	}
	
}
