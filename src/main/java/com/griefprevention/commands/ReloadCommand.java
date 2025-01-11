package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("UnstableApiUsage")
public final class ReloadCommand extends AbstractCommand
{
    public ReloadCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("gpreload")
                .requires(ctx -> ctx.getSender().hasPermission("griefprevention.reload"))
                .executes(doReload())
                .build(),
                "Reloads Grief Prevention's configuration settings. Does NOT totally reload the entire plugin.");
    }

    public Command<CommandSourceStack> doReload()
    {
        return ctx ->
        {
            var plugin = plugin();
            plugin.loadConfig();
            plugin.dataStore.loadMessages();
            plugin.playerEventHandler.resetPattern();

            if (ctx.getSource().getSender() instanceof Player player)
            {
                GriefPrevention.sendMessage(player, ChatColor.GREEN, "Configuration updated.  If you have updated your Grief Prevention JAR, you still need to /reload or reboot your server.");
                logger().info("{} reloaded {}", player.getName(), plugin.getName());
            }
            else
            {
                GriefPrevention.AddLogEntry("Configuration updated.  If you have updated your Grief Prevention JAR, you still need to /reload or reboot your server.");
            }

            return Command.SINGLE_SUCCESS;
        };
    }
}
