package me.Cynadyde;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class LecternGUI {
	
	public static ItemStack namedItemStack(int amount, Material material, byte subID, String name) {
		ItemStack itemStack = new ItemStack(material, amount, subID);
		ItemMeta meta = itemStack.getItemMeta();
		meta.setDisplayName(name);
		itemStack.setItemMeta(meta);
		return itemStack;
	}

	// Define the items used in the GUI
	public static final ItemStack backButton = namedItemStack(1, Material.ITEM_FRAME, (byte) 0, ChatColor.RED + "" + ChatColor.BOLD + "Last Shelf");
	public static final ItemStack forwardButton = namedItemStack(1, Material.ITEM_FRAME, (byte) 0, ChatColor.GREEN + "" + ChatColor.BOLD + "Next Shelf");
	public static final ItemStack blackBG = namedItemStack(1, Material.STAINED_GLASS_PANE, (byte) 15, " ");
	public static final ItemStack blueBG = namedItemStack(1, Material.STAINED_GLASS_PANE, (byte) 9, " ");
	public static final ItemStack redBG = namedItemStack(1, Material.STAINED_GLASS_PANE, (byte) 14, " ");
	public static final ItemStack purpleBG = namedItemStack(1, Material.STAINED_GLASS_PANE, (byte) 10, " ");

	
	// The make-up of the background of the GUI
	public static final ItemStack[] background = new ItemStack[] {
		blackBG, blueBG, redBG,    redBG,    redBG,    redBG,    redBG,    blueBG, blackBG,
		blackBG, blueBG, purpleBG, purpleBG, purpleBG, purpleBG, purpleBG, blueBG, blackBG,
		blackBG, blueBG, blackBG,  blackBG,  blackBG,  blackBG,  blackBG,  blueBG, blackBG
	};
	
	private static HashMap<String, LecternGUI> lecternViewers = new HashMap<String, LecternGUI>();
	
	public static String[] viewers() {
		return lecternViewers.keySet().toArray(new String[lecternViewers.size()]);
	}
	
	public static LecternGUI getViewer(String playerName) {
		return lecternViewers.get(playerName);
	}
	
	public static void addViewer(Player player, LecternBlock lectern) {
		lecternViewers.put(player.getName(), new LecternGUI(player, lectern));
	}
	
	static void delViewer(String playerName) {
		lecternViewers.remove(playerName);
	}
	
	public static void closeViewer(String playerName) {
		Player player = Bukkit.getPlayer(playerName);
		if (player != null) {
			InventoryCloseEvent event = new InventoryCloseEvent(player.getOpenInventory());
			Bukkit.getPluginManager().callEvent(event);
		}
	}
	
	public static final String titlePrefix = "[Lectern] ";
	
	public static final int slotsPerRow = 9;
	public static final int rows = 3;
	public static final int size = rows * slotsPerRow;
	
	public static final int booksPerShelf = 5;
	public static final int backButtonIndex = (slotsPerRow * 1) + 1;
	public static final int nextButtonIndex = (slotsPerRow * 1) + 7;
	
	private String title;
	private Player player;
	private LecternBlock lectern;
	private Inventory inventory;
	private int maxShelf;
	private int viewedShelf;
	
	private LecternGUI(Player player, LecternBlock lectern) {
		
		this.title = titlePrefix + ((lectern.getDesc().equals(""))? "no description" : lectern.getDesc());
		this.player = player;
		this.lectern = lectern;
		this.inventory = Bukkit.createInventory(player, size, title);
		for (int i = 0; i < size; i++) {
			inventory.setItem(i, background[i]);
		}
		this.maxShelf = (int) Math.ceil((double) lectern.getContents().size() / (double) booksPerShelf);
		this.viewedShelf = 0;
		this.updateDisplay(0);
		player.openInventory(inventory);
	}
	
	public void updateDisplay(int change) {
		
		viewedShelf = Math.max(0, Math.min(viewedShelf + change, maxShelf));
		
		// Get the shelf of books that will be displayed...
		int viewingIndex = viewedShelf * booksPerShelf;
		int startIndex = Math.max(0, Math.min(viewingIndex, lectern.getContents().size() - 1));
		int endIndex = Math.max(startIndex, Math.min(startIndex + booksPerShelf, lectern.getContents().size()));

		List<ItemStack> viewedContents = (endIndex > startIndex)? lectern.getContents().subList(startIndex, endIndex) : new ArrayList<ItemStack>();
		
		// Write in the navigation buttons if they should be displayed...
		inventory.setItem(backButtonIndex, (startIndex > 0)? backButton : background[backButtonIndex]);
		inventory.setItem(nextButtonIndex, (endIndex < lectern.getContents().size())? forwardButton : background[nextButtonIndex]);

		// Write in the shelf's books or their background counterpart...
		for (int i = 0; i < 5; i++) {
			inventory.setItem((slotsPerRow * 1) + 2 + i, (i > viewedContents.size() - 1)? 
					background[(slotsPerRow * 1) + 2 + i] : viewedContents.get(i));
		}
		player.updateInventory();
	}
}
