package cn.hairuosky.ximilkanother;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class XiMilkAnotherTabCompleter implements TabCompleter {

    private static final List<String> COMPLETIONS = Arrays.asList("reload");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("ximilkanother")) {
            if (args.length == 1) {
                return StringUtil.copyPartialMatches(args[0], COMPLETIONS, new ArrayList<>());
            }
        }
        return new ArrayList<>();
    }
}
