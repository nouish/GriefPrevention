package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import me.ryanhamshire.GriefPrevention.ShovelMode;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("UnstableApiUsage")
public final class RestoreNatureCommand extends AbstractCommand
{
    public RestoreNatureCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(
                literal("restorenature")
                        .requires(playerWithPermission("griefprevention.restorenature"))
                        .executes(ctx ->
                        {
                            Player player = (Player) ctx.getSource().getSender();
                            PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
                            playerData.shovelMode = ShovelMode.RestoreNature;
                            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.RestoreNatureActivate);
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                "Switches the shovel tool to restoration mode.",
                List.of("rn"));

        commands.register(
                literal("restorenatureaggressive")
                        .requires(playerWithPermission("griefprevention.restorenatureaggressive"))
                        .executes(ctx ->
                        {
                            Player player = (Player) ctx.getSource().getSender();
                            PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
                            playerData.shovelMode = ShovelMode.RestoreNatureAggressive;
                            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.RestoreNatureAggressiveActivate);
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                "Switches the shovel tool to aggressive restoration mode.",
                List.of("rna"));

        commands.register(
                literal("restorenaturefill")
                        .requires(playerWithPermission("griefprevention.restorenatureaggressive"))
                        .then(argument("fillRadius", IntegerArgumentType.integer(0))
                                .suggests((ctx, builder) -> CommandUtil.suggest(List.of("0", "1", "2"), builder))
                        .executes(ctx ->
                        {
                            Player player = (Player) ctx.getSource().getSender();
                            int fillRadius = IntegerArgumentType.getInteger(ctx, "fillRadius");
                            PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
                            playerData.shovelMode = ShovelMode.RestoreNatureFill;
                            playerData.fillRadius = fillRadius;
                            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.FillModeActive, Integer.toString(playerData.fillRadius));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .build(),
                "Switches the shovel tool to fill mode.",
                List.of("rnf"));
    }
}
