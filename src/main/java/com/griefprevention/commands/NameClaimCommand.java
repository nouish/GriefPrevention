package com.griefprevention.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.Messages;
import me.ryanhamshire.GriefPrevention.PlayerData;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static net.kyori.adventure.text.Component.text;

@SuppressWarnings("UnstableApiUsage")
public final class NameClaimCommand extends AbstractClaimCommand
{
    private static final int CLAIM_NAME_MIN_LENGTH = 2;
    private static final int CLAIM_NAME_MAX_LENGTH = 40;

    public NameClaimCommand(@NotNull GriefPrevention plugin)
    {
        super(plugin);
    }

    @Override
    public void register(@NotNull Commands commands)
    {
        commands.register(literal("nameclaim")
                .requires(playerWithPermission("griefprevention.name"))
                .executes(ctx -> renameTo(ctx.getSource(), null))
                .then(argument("name", StringArgumentType.greedyString())
                    .suggests((context, builder) ->
                    {
                        List<String> list = new ArrayList<>();
                        claim(context.getSource()).flatMap(Claim::getName).ifPresent(list::add);
                        return CommandUtil.suggest(list, builder);
                    })
                    .executes(ctx -> renameTo(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))
                .build(),
            "Set the name for the current claim.");
    }

    private int renameTo(@NotNull CommandSourceStack context, @Nullable String newName)
    {
        Player player = (Player) context.getSender();

        // Validate new name
        if (newName != null)
        {
            final int len = newName.strip().length();
            if (len < CLAIM_NAME_MIN_LENGTH || len > CLAIM_NAME_MAX_LENGTH)
            {
                GriefPrevention.sendMessage(player, ChatColor.RED, Messages.BadNameInput);
                return 0;
            }
        }

        //which claim is being abandoned?
        Claim claim = claim(context).orElse(null);
        if (claim == null)
        {
            GriefPrevention.sendMessage(player, ChatColor.YELLOW, Messages.DeleteClaimMissing);
            return 0;
        }

        //verify ownership
        if ((claim.isAdminClaim() && !player.hasPermission("griefprevention.adminclaims"))
                || (!claim.isAdminClaim() && !player.getUniqueId().equals(claim.getOwnerID())))
        {
            PlayerData playerData = plugin().dataStore.getPlayerData(player.getUniqueId());
            if (!playerData.ignoreClaims)
            {
                String who = claim.isAdminClaim() ? "the administrators" : claim.getOwnerName();
                String message = plugin().dataStore.getMessage(Messages.OnlyOwnersModifyClaims, who);

                if (player.hasPermission("griefprevention.ignoreclaims"))
                {
                    message = message + " " + plugin().dataStore.getMessage(Messages.IgnoreClaimsAdvertisement);
                }

                GriefPrevention.sendMessage(player, ChatColor.RED, message);
                return 0;
            }
        }

        String oldName = claim.getName().orElse(null);
        claim.setName(newName);

        String message = plugin().dataStore.getMessage(
            Messages.RenameSuccess,
            Objects.requireNonNullElse(newName, "<unnamed>"));
        player.sendMessage(text(message, NamedTextColor.YELLOW));

        if (Objects.equals(oldName, newName))
        {
            logger().info("{} tried changing the name for claim #{}. Nothing changed.", player.getName(), claim.getID());
        }
        else if (newName == null)
        {
            logger().info("{} cleared the name for claim #{}.", player.getName(), claim.getID());
        }
        else
        {
            logger().info("{} set name for claim #{} to: {}.", player.getName(), claim.getID(), newName);
        }

        // IO write on main thread.
        // This is not ideal, but it is consistent with prexisting claim-commands.
        plugin().dataStore.saveClaim(claim);
        return Command.SINGLE_SUCCESS;
    }
}
