package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static io.papermc.paper.command.brigadier.Commands.literal;

public final class ClaimExplosionsCommand extends AbstractClaimCommand
{
    public ClaimExplosionsCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("claimexplosions")
                .requires(playerWithPermission("griefprevention.claims"))
                .executes(ctx ->
                {
                    Player player = (Player) ctx.getSource();
                    Optional<Claim> optionalClaim = claim(ctx.getSource());

                    if (optionalClaim.isEmpty())
                    {
                        GriefPrevention.sendMessage(player, ChatColor.RED, Messages.DeleteClaimMissing);
                        return 0;
                    }

                    Claim claim = optionalClaim.get();
                    Supplier<String> noBuildReason = claim.checkPermission(player, ClaimPermission.Build, null);

                    if (noBuildReason != null)
                    {
                        GriefPrevention.sendMessage(player, ChatColor.RED, noBuildReason.get());
                        return 0;
                    }

                    claim.areExplosivesAllowed = !claim.areExplosivesAllowed;
                    GriefPrevention.sendMessage(player, ChatColor.GREEN,
                            claim.areExplosivesAllowed ? Messages.ExplosivesEnabled : Messages.ExplosivesDisabled);
                    return Command.SINGLE_SUCCESS;
                })
                .build(),
                "Toggles whether explosives may be used in a specific land claim.",
                List.of("claimexplosion"));
    }
}
