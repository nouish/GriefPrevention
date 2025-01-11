package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class GivePetCommand extends AbstractCommand
{
    public GivePetCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("givepet")
                .requires(playerWithPermission("griefprevention.givepet"))
                .then(argument("target", StringArgumentType.string()).suggests((context, builder) ->
                {
                    List<String> suggestions = new ArrayList<>();

                    for (Player player : Bukkit.getOnlinePlayers())
                    {
                        if (!Objects.equals(player, context.getSource().getSender()))
                        {
                            suggestions.add(player.getName());
                        }
                    }

                    return CommandUtil.suggest(suggestions, builder);
                }).executes(ctx -> {
                    String target = StringArgumentType.getString(ctx, "target");
                    return transferPet(ctx.getSource(), target);
                }))
                .then(literal("cancel").executes(ctx -> cancel(ctx.getSource())))
                .build(),
                "Allows a player to give away a pet he or she tamed.");
    }

    private int transferPet(@NotNull CommandSourceStack source, @NotNull String target)
    {
        Player player = (Player) source.getSender();
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());

        //find the specified player
        OfflinePlayer targetPlayer = plugin().resolvePlayerByName(target);
        if (targetPlayer == null
                || !targetPlayer.isOnline() && !targetPlayer.hasPlayedBefore()
                || targetPlayer.getName() == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.PlayerNotFound2);
            return 0;
        }

        //remember the player's ID for later pet transfer
        playerData.petGiveawayRecipient = targetPlayer;
        //send instructions
        GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.ReadyToTransferPet);
        return Command.SINGLE_SUCCESS;
    }

    private int cancel(CommandSourceStack source)
    {
        Player player = (Player) source.getSender();
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        playerData.petGiveawayRecipient = null;
        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.PetTransferCancellation);
        return Command.SINGLE_SUCCESS;
    }
}
