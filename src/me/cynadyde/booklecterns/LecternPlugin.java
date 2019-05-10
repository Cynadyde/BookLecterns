package me.cynadyde.booklecterns;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.utility.MinecraftReflection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;


/**
 * Main class of the BookLectern plugin.
 */
@SuppressWarnings("WeakerAccess")
public class LecternPlugin extends JavaPlugin implements Listener {

    // Minecraft API & ProtocolLib fail us in 1.13.1...
    private static String version;
    private static boolean useReflection;
    static {
        try {
            version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            int release = Integer.valueOf(version.split("_")[1]);
            useReflection = release >= 13;
        }
        catch (Exception ex) {
            useReflection = false;
        }
    }

    public static final String chatTag =
            ChatColor.DARK_GRAY + "[" + ChatColor.GRAY +
                    "Lecterns" + ChatColor.DARK_GRAY + "]" + ChatColor.RESET + " ";

    public static final String pluginCmd = "lecterns";
    public static final String reloadArg = "reload";

    public static final String usePerms = "lecterns.use";
    public static final String createPerms = "lecterns.create";
    public static final String anyBlockPerms = "lecterns.anyblock";
    public static final String reloadPerms = "lecterns.reload";

    public static boolean lecternChests = true;
    public static boolean nonWrittenBooks = true;
    public static boolean chestAccessNeeded = false;
    public static boolean allContentsShown = false;
    public static boolean quickDisplay = true;

    @Override
    public void onEnable() {

        readConfigVals();

        this.getCommand(pluginCmd).setExecutor(this);
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        for (String playerName : LecternGUI.viewers()) {
            LecternGUI.closeViewer(playerName);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(usePerms)) {
            sender.sendMessage(chatTag + ChatColor.RED + "Insufficient permissions...");
            return true;
        }

        // Command: "/lecterns"
        if (args.length == 0) {
            sender.sendMessage(new String[]{
                    chatTag + ChatColor.GOLD + "Displaying plugin help...",
                    ChatColor.YELLOW + "--------------------------------",
                    ChatColor.AQUA + "Name: " + ChatColor.GRAY + getDescription().getName(),
                    ChatColor.AQUA + "Version: " + ChatColor.GRAY + getDescription().getVersion(),
                    ChatColor.AQUA + "Authors: " + ChatColor.GRAY + StringUtils.join(getDescription().getAuthors(), ", "),
                    ChatColor.AQUA + "Website: " + ChatColor.WHITE + getDescription().getWebsite(),
                    ChatColor.GREEN + "No commands needed! Simply place a sign titled \"" + LecternBlock.signTitle + "\" " +
                            "on an enchantment table and supply the books by placing adjacent chests or item-frames. ",
                    ChatColor.GRAY + "The sign may have a description written on the last three lines. " +
                            "Break the sign to disable the lectern. ",
                    ChatColor.YELLOW + "--------------------------------"
            });
        }

        // Command: "/lecterns reload"
        else if (args[0].equals(reloadArg)) {
            if (!sender.hasPermission(reloadPerms)) {
                sender.sendMessage(chatTag + ChatColor.RED + "Insufficient permissions...");
                return true;
            }
            readConfigVals();
            Bukkit.broadcast(chatTag + ChatColor.YELLOW + sender.getName() +
                    " has reloaded the Lecterns config", reloadPerms);
        }

        return true;
    }

    /**
     * Load options from config for how lecterns work.
     */
    public void readConfigVals() {

        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            saveDefaultConfig();
        }

        this.reloadConfig();

        lecternChests = getConfig().getBoolean("lectern-chests", lecternChests);
        nonWrittenBooks = getConfig().getBoolean("non-written-books", nonWrittenBooks);
        chestAccessNeeded = getConfig().getBoolean("chest-access-needed", chestAccessNeeded);
        allContentsShown = getConfig().getBoolean("all-contents-shown", allContentsShown);
        quickDisplay = getConfig().getBoolean("quick-display", quickDisplay);

        getLogger().info("lectern-chests: " + String.valueOf(lecternChests));
        getLogger().info("non-written-books: " + String.valueOf(nonWrittenBooks));
        getLogger().info("chest-access-needed: " + String.valueOf(chestAccessNeeded));
        getLogger().info("all-contents-shown: " + String.valueOf(allContentsShown));
        getLogger().info("quick-display: " + String.valueOf(quickDisplay));
    }

    /**
     * Open lectern GUIs when bookshelf is clicked.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getPlayer().isSneaking()) {
            return;
        }
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        LecternBlock lectern = LecternBlock.get(event.getClickedBlock());
        if (lectern == null) {
            return;
        }
        event.setCancelled(true);

        if (!event.getPlayer().hasPermission(usePerms)) {
            event.getPlayer().sendMessage(chatTag + ChatColor.RED + "You cannot use lecterns...");
            return;
        }

        if (lectern.getContents().size() == 1 && quickDisplay) {
            openBook(event.getPlayer(), lectern.getContents().get(0));
        }
        else {
            LecternGUI.addViewer(event.getPlayer(), lectern);
        }
    }

    /**
     * Create a lectern when the lectern sign is attached.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignChange(SignChangeEvent event) {

        if (ChatColor.stripColor(event.getLine(0)).equals(LecternBlock.signTitle)) {
            org.bukkit.material.Sign sign = (org.bukkit.material.Sign) event.getBlock().getState().getData();
            Block attachedBlock = event.getBlock().getRelative(sign.getAttachedFace());

            if (!event.getPlayer().hasPermission(createPerms)) {
                event.getPlayer().sendMessage(chatTag + ChatColor.RED + "You cannot create lecterns...");
                return;
            }
            if (!event.getPlayer().hasPermission(anyBlockPerms)) {
                if (!(attachedBlock != null && attachedBlock.getType().equals(Material.ENCHANTMENT_TABLE))) {
                    event.getPlayer().sendMessage(chatTag + ChatColor.RED + "You cannot create a lectern there...");
                    return;
                }
            }
            event.getPlayer().sendMessage(chatTag + ChatColor.GREEN + "Successfully created a lectern!");
            event.setLine(0, LecternBlock.signTag);
        }
    }

    /**
     * Update bookshelf GUIs when inventory is clicked.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {

        if (event.getClickedInventory() != null) {
            if (event.getClickedInventory().getTitle().startsWith(LecternGUI.titlePrefix)) {
                if (event.getWhoClicked() instanceof Player) {

                    Player player = (Player) event.getWhoClicked();
                    ItemStack clickedItem = event.getInventory().getItem(event.getSlot());

                    // User clicked the backwards navigation button in the GUI...
                    if (event.getSlot() == LecternGUI.backButtonIndex &&
                            clickedItem.getType().equals(LecternGUI.backButton.getType())) {
                        LecternGUI.getViewer(player.getName()).updateDisplay(-1);
                    }

                    // User clicked the forwards navigation button in the GUI...
                    else if (event.getSlot() == LecternGUI.nextButtonIndex &&
                            clickedItem.getType().equals(LecternGUI.forwardButton.getType())) {
                        LecternGUI.getViewer(player.getName()).updateDisplay(1);
                    }

                    // User selected a book to read in the GUI...
                    else if (clickedItem.getType().equals(Material.WRITTEN_BOOK)) {
                        player.closeInventory();
                        openBook(player, clickedItem);
                    }
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * Close bookshelf GUIs when inventory is closed.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory() != null) {
            if (event.getInventory().getTitle().startsWith(LecternGUI.titlePrefix)) {
                LecternGUI.delViewer(event.getPlayer().getName());
            }
        }
    }

    /**
     * Opens the book for the player without giving it to them.
     */
    public static void openBook(Player player, ItemStack book) {

        // 0. "MC|BOpen is now "minecraft:book_open"
        // 1. packet.getStrings().write(0, "minecraft:book_open") causes FieldAccessException "no field with type String exists in packet"
        //    Does this mean CUSTOM_PAYLOAD doesn't have that field?
        // 2. Is it possible to create a field for strings in the packet? Can't figure out.
        // 3. ProtocolLib v4.5 has the packet type OPEN_BOOK, which fixes this issue, but cannot find the type for 1.13.
        // 4. packet.getModifier().write(0, "minecraft:book_open") causes IllegalArgumentException
        //    "cannot set MinecraftKey field with String"
        // 5. Same happens when using ProtocolLib's MinecraftKey wrapper... it isn't an actual MinecraftKey obj
//        int slot = player.getInventory().getHeldItemSlot();
//        ItemStack old = player.getInventory().getItem(slot);
//
//        try {
//            ByteBuf buffer = Unpooled.buffer(256);
//            buffer.setByte(0, (byte) 0);
//            buffer.writerIndex(1);
//
//            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.CUSTOM_PAYLOAD);
//            packet.getModifier().writeDefaults();
//            packet.getModifier().write(0, new MinecraftKey("book_open"));
//            packet.getModifier().write(1, MinecraftReflection.getPacketDataSerializer(buffer));
//
//            player.getInventory().setItem(slot, book);
//            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
//
//        }
//        catch (Exception ex) {
//            Bukkit.getLogger().log(Level.SEVERE, "Unable to open book for " + player.getName(), ex);
//        }
//
//        player.getInventory().setItem(slot, old);

        // 6. Another option is to hardcode it for version 1.13.1 specifically.
//        int handSlot = player.getInventory().getHeldItemSlot();
//        ItemStack heldItem = player.getInventory().getItem(handSlot);
//
//        ByteBuf buffer = Unpooled.buffer(256).setByte(0, 0).writerIndex(1);
//        MinecraftKey key = new MinecraftKey("minecraft:book_open");
//        PacketDataSerializer serializer = new PacketDataSerializer(buffer);
//        PacketPlayOutCustomPayload packet = new PacketPlayOutCustomPayload(key, serializer);
//
//        player.getInventory().setItem(handSlot, book);
//        ((CraftPlayer)player).getHandle().playerConnection.sendPacket(packet);
//        player.getInventory().setItem(handSlot, heldItem);


        // 7. Use a bit of reflection to get a MinecraftKey...
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();

        int handSlot = player.getInventory().getHeldItemSlot();
        ItemStack heldItem = player.getInventory().getItem(handSlot);

        try {
            ByteBuf buffer = Unpooled.buffer(256).setByte(0, (byte) 0).writerIndex(1);
            Object serializer = MinecraftReflection.getPacketDataSerializer(buffer);
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.CUSTOM_PAYLOAD);
            packet.getModifier().writeDefaults();

            if (useReflection) {
                Object key = Class.forName("net.minecraft.server." + version + ".MinecraftKey")
                        .getConstructor(String.class).newInstance("minecraft:book_open");
                packet.getModifier().write(0, key);
            }
            else {
                packet.getStrings().write(0, "MC|BOpen");
            }
            packet.getModifier().write(1, serializer);

            player.getInventory().setItem(handSlot, book);
            protocolManager.sendServerPacket(player, packet);
        }
        catch (Exception ex) {
            Bukkit.getLogger().log(Level.SEVERE, "Unable to open book for " + player.getName(), ex);
        }
        player.getInventory().setItem(handSlot, heldItem);

    }
}
