package me.Cynadyde;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class LecternBlock {
	
	public static final String signTitle = "[Lectern]";
	public static final String signTag = ChatColor.DARK_BLUE + "" + ChatColor.BOLD + signTitle;
	public static final String displayArg = "[Display]";
	public static final String displayAllArg = "[Display All]";
	
	public static LecternBlock get(Block block) {
		if (block == null) {
			return null;
		}
		// Using a lectern sign, get the block it is attached to...
		else if (block.getType().equals(Material.WALL_SIGN)) {
			if (((Sign) block.getState()).getLine(0).equals(signTag)) {
				
				org.bukkit.material.Sign sign = (org.bukkit.material.Sign) block.getState().getData();
				Block attacheeBlock = block.getRelative(sign.getAttachedFace());
				return new LecternBlock(attacheeBlock, (Sign) block.getState());
			}
		}
		// Using a given block, get the attached lectern sign...
		else {
			for (BlockFace direction : new BlockFace[] {
					BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
			}) {
				Block relBlock = block.getRelative(direction);
				if (relBlock.getType().equals(Material.WALL_SIGN)) {
					if (((Sign) relBlock.getState()).getLine(0).equals(signTag)) {
						return new LecternBlock(block, (Sign) relBlock.getState());
					}
				}
			}
		}
		return null;
	}
	
	private Block block;
	private String description;
	private List<ItemStack> contents;
	
	private LecternBlock(Block lecternBlock, Sign lecternSign) {
		this.block = lecternBlock;
		this.description = StringUtils.join(Arrays.copyOfRange(lecternSign.getLines(), 1, 3), ' ');
		this.contents = new ArrayList<ItemStack>();
		lecternSign.setLine(0, signTag);
		updateContents();
	}
	
	public String getDesc() {
		return description;
	}
	
	public List<ItemStack> getContents() {
		return contents;
	}

	public void updateContents() {
		
		// Look for books in item frames on all four sides...
		for (BlockFace direction : new BlockFace[] {
			BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
		}) {
			for (Entity entity : block.getWorld().getNearbyEntities(block.getRelative(direction).getLocation().add(0.5, 0.5, 0.5), 0.5, 0.5, 0.5)) {
				if (entity.getType().equals(EntityType.ITEM_FRAME)) {

					ItemFrame itemFrame = (ItemFrame) entity;
					if (itemFrame.getFacing().equals(direction)) {
						if (LecternPlugin.nonWrittenBooks || itemFrame.getItem().getType().equals(Material.WRITTEN_BOOK)) {
							contents.add(itemFrame.getItem());
						}
					}
				}
			}
			
		}
		// Look for chests on all six block faces...
		if (LecternPlugin.lecternChests) {
			for (BlockFace direction : new BlockFace[] {
					BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, 
					BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP
			}) {
				Block relBlock = block.getRelative(direction);
				if (relBlock.getType().equals(Material.CHEST) || relBlock.getType().equals(Material.TRAPPED_CHEST)) {
					Inventory inventory = ((Chest) relBlock.getState()).getInventory();
					
					// Look for any chest arguments...
					List<Integer> argSlots = new ArrayList<Integer>();
					boolean chestAccess = !LecternPlugin.chestAccessNeeded;
					boolean anyItem = LecternPlugin.allContentsShown;
					
					for (int i = 0; i < inventory.getSize(); i++) {
						ItemStack item = inventory.getItem(i);
						if (item != null && item.getType().equals(Material.PAPER)) {
							String argument = item.getItemMeta().getDisplayName();
							if (argument.equals(displayArg)) {
								argSlots.add(i);
								chestAccess = true;
							}
							else if (argument.equals(displayAllArg)) {
								argSlots.add(i);
								chestAccess = true;
								anyItem = true;
							}
						}
					}
					
					if (!chestAccess) {
						continue;
					}
					if (!LecternPlugin.nonWrittenBooks) {
						anyItem = false;
					}
					
					// Get the contents of the chest or double-chest...
					for (int i = 0; i < inventory.getSize(); i++) {
						ItemStack item = inventory.getItem(i);
						if (item != null && !argSlots.contains(i)) {
							if (anyItem || item.getType().equals(Material.WRITTEN_BOOK)) {
								contents.add(item);
							}
						}
					}
				}
			}
		}
	}
}
