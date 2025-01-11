package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Vector;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ClaimListCommand extends AbstractCommand
{
    public ClaimListCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        // This differs slightly from the original command because it cannot be executed from console!
        commands.register(literal("adminclaimslist")
                .requires(playerWithPermission("griefprevention.adminclaims"))
                .executes(ctx -> adminClaimList(ctx.getSource()))
                .build(),
                "Lists all administrative claims.");

        commands.register(literal("claimlist")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx -> claimList(ctx.getSource(), (Player) ctx.getSource().getSender()))
                .then(argument("target", ArgumentTypes.player())
                .requires(playerWithPermission("griefprevention.claimslistother")).executes(ctx ->
                {
                    List<Player> targetsList = ctx.getArgument("target", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());

                    if (targetsList.isEmpty())
                    {
                        GriefPrevention.sendMessage((Player) ctx.getSource().getSender(), ChatColor.RED, Messages.PlayerNotFound2);
                        return 0;
                    }

                    return claimList(ctx.getSource(), targetsList.getFirst());
                }))
                .build(),
                "Lists information about a player's claim blocks and claims.",
                List.of("claimslist", "listclaims"));
    }

    private int adminClaimList(@NotNull CommandSourceStack source)
    {
        Player player = (Player) source.getSender();
        //find admin claims
        Vector<Claim> claims = new Vector<>();

        for (Claim claim : plugin().dataStore.getClaims())
        {
            if (claim.ownerID == null)  //admin claim
            {
                claims.add(claim);
            }
        }

        if (!claims.isEmpty())
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.ClaimsListHeader);

            for (Claim claim : claims)
            {
                GriefPrevention.sendMessage(player, ChatColor.YELLOW, GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    private int claimList(@NotNull CommandSourceStack source, @NotNull Player target)
    {
        Player player = (Player) source.getSender();

        if (!player.getUniqueId().equals(target.getUniqueId()))
        {
            if (!player.hasPermission("griefprevention.claimslistother"))
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.ClaimsListNoPermission);
                return 0;
            }
        }

        //load the target player's data
        PlayerData playerData = plugin().dataStore.getPlayerData(target.getUniqueId());
        Vector<Claim> claims = playerData.getClaims();
        GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.StartBlockMath,
            String.valueOf(playerData.getAccruedClaimBlocks()),
            String.valueOf((playerData.getBonusClaimBlocks() + plugin().dataStore.getGroupBonusBlocks(target.getUniqueId()))),
            String.valueOf((playerData.getAccruedClaimBlocks() + playerData.getBonusClaimBlocks() + plugin().dataStore.getGroupBonusBlocks(target.getUniqueId()))));

        if (!claims.isEmpty())
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.ClaimsListHeader);
            for (int i = 0; i < playerData.getClaims().size(); i++)
            {
                Claim claim = playerData.getClaims().get(i);
                GriefPrevention.sendMessage(player, ChatColor.YELLOW, GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()) + plugin().dataStore.getMessage(Messages.ContinueBlockMath, String.valueOf(claim.getArea())));
            }

            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.EndBlockMath, String.valueOf(playerData.getRemainingClaimBlocks()));
        }

        //drop the data we just loaded, if the player isn't online
        if (!target.isOnline())
            plugin().dataStore.clearCachedPlayerData(target.getUniqueId());

        return Command.SINGLE_SUCCESS;
    }
}
