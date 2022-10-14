package com.forgeessentials.commands.server;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.DefaultPermissionLevel;

import org.apache.commons.lang3.StringUtils;

import com.forgeessentials.api.permissions.FEPermissions;
import com.forgeessentials.commands.ModuleCommands;
import com.forgeessentials.core.commands.ParserCommandBase;
import com.forgeessentials.scripting.ScriptArguments;
import com.forgeessentials.util.CommandParserArgs;
import com.forgeessentials.util.output.ChatOutputHandler;

public class CommandServerSettings extends ParserCommandBase
{

    public static List<String> options = Arrays.asList("allowFlight", "allowPVP", "buildLimit", "difficulty", "MotD", "spawnProtection", "gamemode");

    @Override
    public String getPrimaryAlias()
    {
        return "serversettings";
    }

    @Override
    public String[] getDefaultSecondaryAliases()
    {
        return new String[] { "ss" };
    }

    @Override
    public boolean canConsoleUseCommand()
    {
        return true;
    }

    @Override
    public DefaultPermissionLevel getPermissionLevel()
    {
        return DefaultPermissionLevel.OP;
    }

    @Override
    public String getPermissionNode()
    {
        return ModuleCommands.PERM + ".serversettings";
    }

    @OnlyIn(Dist.DEDICATED_SERVER)
    public void doSetProperty(String id, Object value)
    {
        DedicatedServer server = (DedicatedServer) ServerLifecycleHooks.getCurrentServer().getSessionService();
        server.setProperty(id, value);
        server.saveProperties();
    }

    public void setProperty(String id, Object value)
    {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER)
            doSetProperty(id, value);
    }

    @Override
    public void parse(CommandParserArgs arguments) throws CommandException
    {
        if (!FMLEnvironment.dist.isDedicatedServer())
            arguments.error("You can use this command only on dedicated servers");
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (arguments.isEmpty())
        {
            arguments.notify("Options: %s", StringUtils.join(options, ", "));
            return;
        }

        arguments.tabComplete(options);
        String subCmd = arguments.remove().toLowerCase();
        switch (subCmd)
        {
        case "allowflight":
            if (arguments.isEmpty())
                arguments.confirm("Allow flight: %s", Boolean.toString(server.isFlightAllowed()));
            else
            {
                boolean allowFlight = arguments.parseBoolean();
                server.setFlightAllowed(allowFlight);
                setProperty("allow-flight", allowFlight);
                arguments.confirm("Set allow-flight to %s", Boolean.toString(allowFlight));
            }
            break;
        case "allowpvp":
            if (arguments.isEmpty())
                arguments.confirm("Allow PvP: %s", Boolean.toString(server.isPvpAllowed()));
            else
            {
                boolean allowPvP = arguments.parseBoolean();
                server.setPvpAllowed(allowPvP);
                setProperty("pvp", allowPvP);
                arguments.confirm("Set pvp to %s", Boolean.toString(allowPvP));
            }
            break;
        case "buildlimit":
            if (arguments.isEmpty())
                arguments.confirm("Set build limit to %d", server.getMaxBuildHeight());
            else
            {
                int buildLimit = arguments.parseInt(0, Integer.MAX_VALUE);
                server.setMaxBuildHeight(buildLimit);
                setProperty("max-build-height", buildLimit);
                arguments.confirm("Set max-build-height to %d", buildLimit);
            }
            break;
        case "motd":
            if (arguments.isEmpty())
                arguments.confirm("MotD = %s", server.getMotd());
            else
            {
                String motd = ScriptArguments.process(arguments.toString(), null);
                server.getStatus().setDescription(new StringTextComponent(ChatOutputHandler.formatColors(motd)));
                server.setMotd(motd);
                setProperty("motd", motd);
                arguments.confirm("Set MotD to %s", motd);
            }
            break;
        case "spawnprotection":
            if (arguments.isEmpty())
                arguments.confirm("Spawn protection size: %d", server.getSpawnProtectionRadius());
            else
            {
                int spawnSize = arguments.parseInt(0, Integer.MAX_VALUE);
                setProperty("spawn-protection", spawnSize);
                arguments.confirm("Set spawn-protection to %d", spawnSize);
            }
            break;
        case "gamemode":
            if (arguments.isEmpty())
                arguments.confirm("Default gamemode set to %s", server.getDefaultGameType().getName());
            else
            {
                GameType gamemode = GameType.byId(arguments.parseInt());
                server.setDefaultGameType(gamemode);
                setProperty("gamemode", gamemode.ordinal());
                arguments.confirm("Set default gamemode to %s", gamemode.getName());
            }
            break;
        case "difficulty":
            if (arguments.isEmpty())
                arguments.confirm("Difficulty set to %s", server.getWorldData().getDifficulty());
            else
            {
                Difficulty difficulty = Difficulty.byId(arguments.parseInt());
                server.setDifficulty(difficulty, false);
                setProperty("difficulty", difficulty.ordinal());
                arguments.confirm("Set difficulty to %s", difficulty.name());
            }
            break;

        default:
            arguments.error(FEPermissions.MSG_UNKNOWN_SUBCOMMAND, subCmd);
        }
    }

}
