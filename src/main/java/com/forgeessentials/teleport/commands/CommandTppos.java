package com.forgeessentials.teleport.commands;

import java.util.HashMap;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.BlockPosArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.server.permission.DefaultPermissionLevel;

import com.forgeessentials.commons.selections.Point;
import com.forgeessentials.commons.selections.WarpPoint;
import com.forgeessentials.core.commands.ForgeEssentialsCommandBuilder;
import com.forgeessentials.core.misc.TeleportHelper;
import com.forgeessentials.teleport.TeleportModule;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

public class CommandTppos extends ForgeEssentialsCommandBuilder
{

    public CommandTppos(boolean enabled)
    {
        super(enabled);
    }

    /**
     * Spawn point for each dimension
     */
    public static HashMap<Integer, Point> spawnPoints = new HashMap<>();

    @Override
    public String getPrimaryAlias()
    {
        return "tppos";
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

    @Override
    public String getPermissionNode()
    {
        return TeleportModule.PERM_TPPOS;
    }

    @Override
    public LiteralArgumentBuilder<CommandSource> setExecution()
    {
        return baseBuilder
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(CommandContext -> execute(CommandContext)
                                )
                        );
    }

    @Override
    public int processCommandPlayer(CommandContext<CommandSource> ctx, Object... params) throws CommandSyntaxException
    {
        ServerPlayerEntity sender = getServerPlayer(ctx.getSource());
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        TeleportHelper.teleport(sender, new WarpPoint(sender.level.dimension(), pos, sender.xRot,
                sender.yRot));
        return Command.SINGLE_SUCCESS;
    }

}
