package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("deprecation")
public final class AbandonClaimCommand extends AbstractClaimCommand
{
    public AbandonClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("abandontoplevelclaim")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx -> abandonClaim(ctx.getSource(), true))
                .build(),
                "Deletes a claim and all its subdivisions.");

        commands.register(literal("abandonclaim")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx -> abandonClaim(ctx.getSource(), false))
                .build(),
                "Deletes a claim.",
                List.of("unclaim", "declaim", "removeclaim", "disclaim"));

        commands.register(literal("abandonallclaims")
                .requires(playerWithPermission("griefprevention.abandonallclaims"))
                .executes(ctx -> abandonAllClaims(ctx.getSource(), false))
                .then(literal("confirm").executes(ctx -> abandonAllClaims(ctx.getSource(), true)))
                .build(),
                "Deletes ALL your claims.");
    }

    private int abandonClaim(@NotNull CommandSourceStack source, boolean deleteTopLevelClaim)
    {
        Player player = (Player) source.getSender();
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        Claim claim = claim(source).orElse(null);

        //which claim is being abandoned?
        if (claim == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.AbandonClaimMissing);
        }

        //verify ownership
        else if (claim.checkPermission(player, ClaimPermission.Edit, null) != null)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.NotYourClaim);
        }

        //warn if has children and we're not explicitly deleting a top level claim
        else if (!claim.children.isEmpty() && !deleteTopLevelClaim)
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.DeleteTopLevelClaim);
            return 0;
        }
        else
        {
            //delete it
            claim.removeSurfaceFluids(null);
            plugin().dataStore.deleteClaim(claim, true, false);

            //if in a creative mode world, restore the claim area
            if (GriefPrevention.instance.creativeRulesApply(claim.getLesserBoundaryCorner()))
            {
                GriefPrevention.AddLogEntry(player.getName() + " abandoned a claim @ " + GriefPrevention.getfriendlyLocationString(claim.getLesserBoundaryCorner()));
                GriefPrevention.sendMessage(player, ChatColor.GOLD, Messages.UnclaimCleanupWarning);
                GriefPrevention.instance.restoreClaim(claim, 20L * 60 * 2);
            }

            //adjust claim blocks when abandoning a top level claim
            if (plugin().config_claims_abandonReturnRatio != 1.0D && claim.parent == null && claim.ownerID.equals(playerData.playerID))
            {
                playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() - (int) Math.ceil((claim.getArea() * (1 - plugin().config_claims_abandonReturnRatio))));
            }

            //tell the player how many claim blocks he has left
            int remainingBlocks = playerData.getRemainingClaimBlocks();
            GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.AbandonSuccess, String.valueOf(remainingBlocks));

            //revert any current visualization
            playerData.setVisibleBoundaries(null);

            playerData.warnedAboutMajorDeletion = false;
        }

        return 1;
    }

    private int abandonAllClaims(@NotNull CommandSourceStack source, boolean confirm)
    {
        Player player = (Player) source.getSender();

        if (!confirm)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.ConfirmAbandonAllClaims);
            return 0;
        }

        //count claims
        PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
        int originalClaimCount = playerData.getClaims().size();

        //check count
        if (originalClaimCount == 0)
        {
            GriefPrevention.sendMessage(player, ChatColor.RED, Messages.YouHaveNoClaims);
            return 0;
        }

        if (plugin().config_claims_abandonReturnRatio != 1.0D)
        {
            //adjust claim blocks
            for (Claim claim : playerData.getClaims())
            {
                playerData.setAccruedClaimBlocks(playerData.getAccruedClaimBlocks() - (int) Math.ceil((claim.getArea() * (1 - plugin().config_claims_abandonReturnRatio))));
            }
        }


        //delete them
        plugin().dataStore.deleteClaimsForPlayer(player.getUniqueId(), false);

        //inform the player
        int remainingBlocks = playerData.getRemainingClaimBlocks();
        GriefPrevention.sendMessage(player, ChatColor.GREEN, Messages.SuccessfulAbandon, Integer.toString(remainingBlocks));

        //revert any current visualization
        playerData.setVisibleBoundaries(null);
        return Command.SINGLE_SUCCESS;
    }
}
