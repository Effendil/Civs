package org.redcastlemedia.multitallented.civs;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.redcastlemedia.multitallented.civs.commands.CivCommand;
import org.redcastlemedia.multitallented.civs.commands.CivsCommand;
import org.redcastlemedia.multitallented.civs.regions.RegionManager;
import org.redcastlemedia.multitallented.civs.regions.effects.ConveyorEffect;
import org.redcastlemedia.multitallented.civs.scheduler.CommonScheduler;
import org.redcastlemedia.multitallented.civs.scheduler.DailyScheduler;
import org.redcastlemedia.multitallented.civs.towns.TownManager;
import org.redcastlemedia.multitallented.civs.update.UpdateUtil;
import org.redcastlemedia.multitallented.civs.util.DebugLogger;
import org.redcastlemedia.multitallented.civs.util.LogInfo;
import org.redcastlemedia.multitallented.civs.util.PlaceHook;
import org.redcastlemedia.multitallented.civs.util.StructureUtil;
import org.reflections.Reflections;

import github.scarsz.discordsrv.DiscordSRV;
import net.Indyuce.mmoitems.MMOItems;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;

public class Civs extends JavaPlugin {

    private HashMap<String, CivCommand> commandList = new HashMap<>();
    public static String NAME = "Civs";
    public static Economy econ;
    public static Permission perm;
    public static MMOItems mmoItems;
    public static DiscordSRV discordSRV;
    private static Civs civs;
    public static Logger logger;

    @Override
    public void onEnable() {
        civs = this;
        logger = Logger.getLogger("Minecraft");
        UpdateUtil.checkUpdate();
        setupDependencies();
        setupEconomy();
        setupPermissions();

        instantiateSingletons();

        initCommands();

        initScheduler();
        civs = this;
        fancyPrintLog();
    }

    @Override
    public void onDisable() {
//        BlockLogger.getInstance().saveBlocks();
        StructureUtil.removeAllBoundingBoxes();
        RegionManager.getInstance().saveAllUnsavedRegions();
        TownManager.getInstance().saveAllUnsavedTowns();
        ConveyorEffect.getInstance().onDisable();
        getLogger().info(LogInfo.DISABLED);
        Bukkit.getScheduler().cancelTasks(this);
    }


    @Override
    public boolean onCommand(CommandSender commandSender, Command command, String message, String[] args) {
        if (args.length < 1) {
            args = new String[1];
            args[0] = "menu";
        }
        if (commandSender instanceof Player && ConfigManager.getInstance().getBlackListWorlds()
                .contains(((Player) commandSender).getWorld().getName())) {
            return true;
        }
        CivCommand civCommand = commandList.get(args[0]);
        if (civCommand == null) {
            commandSender.sendMessage(getPrefix() + "Invalid command " + args[0]);
            return true;
        }
        return civCommand.runCommand(commandSender, command, message, args);
    }

    private void fancyPrintLog() {
        logger.info(LogInfo.INFO);
        logger.info(LogInfo.PH_VOID);

        logger.info(LogInfo.PH_INFO);
        if (econ != null) {
            logger.info(LogInfo.HOOKECON + econ.getName());
        }
        if (perm != null) {
            logger.info(LogInfo.HOOKPERM + perm.getName());
        }
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            logger.info(LogInfo.HOOKCHAT + "PlaceholderAPI");
        }
        if (mmoItems != null) {
            logger.info(LogInfo.HOOKCHAT + "MMOItems");
        }
        if (discordSRV != null) {
            logger.info(LogInfo.HOOKCHAT + "DiscordSRV");
        }
        logger.info(LogInfo.PH_INFO);

        logger.info(LogInfo.PH_VOID);

        logger.info(LogInfo.ENABLED);
    }

    private void initScheduler() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        long timeUntilDay = (86400000 + calendar.getTimeInMillis() - System.currentTimeMillis()) / 50;
        Civs.logger.info(timeUntilDay + " ticks until 00:00");
        DailyScheduler dailyScheduler = new DailyScheduler();
        getServer().getScheduler().scheduleSyncRepeatingTask(this, dailyScheduler, timeUntilDay, 1728000);

        if (ConfigManager.getInstance().isDebugLog()) {
            getServer().getScheduler().scheduleSyncRepeatingTask(this, DebugLogger.timedDebugTask(), 600L, 600L);
        }
        CommonScheduler commonScheduler = new CommonScheduler();
        getServer().getScheduler().scheduleSyncRepeatingTask(this, commonScheduler, 4L, 4L);

        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {

            @Override
            public void run() {
                RegionManager.getInstance().saveNextRegion();
                TownManager.getInstance().saveNextTown();
            }
        }, 20L, 20L);
    }

    private void initCommands() {
        Reflections reflections = new Reflections("org.redcastlemedia.multitallented.civs.commands");
        Set<Class<? extends CivCommand>> commands = reflections.getSubTypesOf(CivCommand.class);
        for (Class<? extends CivCommand> currentCommandClass : commands) {
            try {
                CivCommand currentCommand = currentCommandClass.newInstance();
                for (String key : currentCommandClass.getAnnotation(CivsCommand.class).keys()) {
                    commandList.put(key, currentCommand);
                }
            } catch (Exception e) {

            }
        }
    }

//    private void initListeners() {
//        Bukkit.getPluginManager().registerEvents(new SpellListener(), this);
//        Bukkit.getPluginManager().registerEvents(new AIListener(), this);
//    }

    private void setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            econ = rsp.getProvider();
        }
    }
    private void setupPermissions() {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            perm = permissionProvider.getProvider();
        }
    }
    private void setupDependencies() {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceHook().register();
        }
        if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            mmoItems = MMOItems.plugin;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            discordSRV = DiscordSRV.getPlugin();
        }
//        RegisteredServiceProvider<Chat> chatProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
//        if (chatProvider != null) {
//            chat = chatProvider.getProvider();
//            if (chat != null)
//                System.out.println(Civs.getPrefix() + "Hooked into chat plugin " + chat.getName());
//        }
//        return (chat != null);
    }

    private void instantiateSingletons() {
        Reflections reflections = new Reflections("org.redcastlemedia.multitallented.civs");
        Set<Class<?>> classes = reflections.getTypesAnnotatedWith(CivsSingleton.class);
        List<Class<?>> classList = new ArrayList<>(classes);
        classList.sort(new Comparator<Class<?>>() {
            @Override
            public int compare(Class<?> o1, Class<?> o2) {
                return o1.getAnnotation(CivsSingleton.class).priority().compareTo(o2.getAnnotation(CivsSingleton.class).priority());
            }
        });
        for (Class<?> currentSingleton : classList) {
            try {
                Method method = currentSingleton.getMethod("getInstance");
                if (method != null) {
                    method.invoke(currentSingleton);
                }
            } catch (Exception e) {

            }
        }
    }

    public static Economy getEcon() {
        return econ;
    }
    public static Permission getPerm() {
        return perm;
    }
    public static String getPrefix() {
        return ConfigManager.getInstance().getCivsChatPrefix() + " ";
    }
    public static String getRawPrefix() { return ConfigManager.getInstance().civsChatPrefix + " ";}
    public static synchronized Civs getInstance() {
        return civs;
    }
}
