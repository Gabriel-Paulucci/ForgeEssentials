package com.forgeessentials.util.selections;

import net.minecraft.commands.CommandSourceStack;

import org.jetbrains.annotations.NotNull;

import com.forgeessentials.api.permissions.DefaultPermissionLevel;
import com.forgeessentials.core.commands.ForgeEssentialsCommandBuilder;
import com.forgeessentials.util.PlayerInfo;
import com.forgeessentials.util.output.ChatOutputHandler;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class CommandDeselect extends ForgeEssentialsCommandBuilder
{

    public CommandDeselect(boolean enabled)
    {
        super(enabled);
    }

    @Override
    public @NotNull String getPrimaryAlias()
    {
        return "SELdesel";
    }

    @Override
    public String @NotNull [] getDefaultSecondaryAliases()
    {
        return new String[] { "/deselect", "/sel" };
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> setExecution()
    {
        return baseBuilder.executes(CommandContext -> execute(CommandContext, "blank"));
    }

    @Override
    public int processCommandPlayer(CommandContext<CommandSourceStack> ctx, String params) throws CommandSyntaxException
    {
        PlayerInfo info = PlayerInfo.get(getServerPlayer(ctx.getSource()).getGameProfile().getId());
        info.setSel1(null);
        info.setSel2(null);
        SelectionHandler.sendUpdate(getServerPlayer(ctx.getSource()));
        ChatOutputHandler.chatConfirmation(ctx.getSource(), "Selection cleared.");
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public boolean canConsoleUseCommand()
    {
        return false;
    }

    @Override
    public DefaultPermissionLevel getPermissionLevel()
    {
        return DefaultPermissionLevel.ALL;
    }
}
