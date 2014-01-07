package modfixng.fixes;

import java.util.HashMap;
import java.util.List;

import modfixng.main.Config;
import modfixng.main.ModFixNG;
import modfixng.utils.ModFixNGUtils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.GamePhase;

public class RestrictBreakWhileOpen implements Listener {

	private ModFixNG main;
	private Config config;

	public RestrictBreakWhileOpen(ModFixNG main, Config config) {
		this.main = main;
		this.config = config;
		initClientCloseInventoryFixListener();
		initServerCloseInventoryFixListener();
	}


	private HashMap<String,BlockState> playerOpenBlock = new HashMap<String,BlockState>(100);	
	private HashMap<String,Integer> playerOpenBlockInvOpenCheckTask = new HashMap<String,Integer>(100);
	
	@EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
	public void onPlayerOpenedBlock(PlayerInteractEvent e)
	{
		if (!config.restrictBlockBreakWhileOpenEnabled) {return;}

		if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {return;}
		
		final Player player = e.getPlayer();
		final String playername = player.getName();
		
		if (playerOpenBlock.containsKey(playername))
		{
			e.setCancelled(true);
			return;
		}

		final Block b = e.getClickedBlock();
		if (config.restrictBlockBreakWhileOpenIDs.contains(ModFixNGUtils.getIDstring(b)))
		{
			if (playerOpenBlockInvOpenCheckTask.containsKey(playername))
			{
				int taskID = playerOpenBlockInvOpenCheckTask.get(playername);
				Bukkit.getScheduler().cancelTask(taskID);
				playerOpenBlockInvOpenCheckTask.remove(playername);
			}
			int taskID = Bukkit.getScheduler().scheduleSyncDelayedTask(main, new Runnable()
			{
				public void run()
				{
					if (ModFixNGUtils.isInventoryOpen(player))
					{
						playerOpenBlock.put(playername, b.getState());
						playerOpenBlockInvOpenCheckTask.remove(playername);
					}
				}	
			});
			playerOpenBlockInvOpenCheckTask.put(playername, taskID);
		}
		
	}
	
	//remove player from list when he closes inventory
	private void initClientCloseInventoryFixListener()
	{
		main.protocolManager.addPacketListener(
				new PacketAdapter(
						PacketAdapter
						.params(main, PacketType.Play.Client.CLOSE_WINDOW)
						.clientSide()
						.optionManualGamePhase()
						.gamePhase(GamePhase.PLAYING)
						.listenerPriority(ListenerPriority.LOWEST)
				) 
				{
					@Override
					public void onPacketReceiving(PacketEvent e) 
					{
						if (!config.restrictBlockBreakWhileOpenEnabled) {return;}
						
						if (e.getPlayer() == null) {return;}
						
						final String playername = e.getPlayer().getName();
						Bukkit.getScheduler().scheduleSyncDelayedTask(main, new Runnable()
						{
							public void run()
							{
								removeData(playername);
							}
						});
					}
				});
	}
	private void initServerCloseInventoryFixListener()
	{
		main.protocolManager.addPacketListener(
				new PacketAdapter(
						PacketAdapter
						.params(main, PacketType.Play.Server.CLOSE_WINDOW)
						.serverSide()
						.optionManualGamePhase()
						.gamePhase(GamePhase.PLAYING)
						.listenerPriority(ListenerPriority.LOWEST)
				) 
				{
					@Override
					public void onPacketSending(PacketEvent e) 
					{
						if (!config.restrictBlockBreakWhileOpenEnabled) {return;}
						
						final String playername = e.getPlayer().getName();
						if (playerOpenBlock.containsKey(playername) && config.restrictBlockBreakWhileOpenClearDropIfBlockBroken)
						{
							BlockState bs = playerOpenBlock.get(playername);
							Block b = bs.getBlock();
							if (bs.getType() != b.getType())
							{
								clearNearbyDrop(b);
							}
						}
						removeData(playername);
				    }
				});
	}
	@EventHandler(priority=EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent e)
	{
		if (!config.restrictBlockBreakWhileOpenEnabled) {return;}
		
		String playername = e.getPlayer().getName();
		removeData(playername);
	}
	
	private void removeData(String playername)
	{
		playerOpenBlock.remove(playername);
		if (playerOpenBlockInvOpenCheckTask.containsKey(playername))
		{
			int taskID = playerOpenBlockInvOpenCheckTask.get(playername);
			Bukkit.getScheduler().cancelTask(taskID);
			playerOpenBlockInvOpenCheckTask.remove(playername);
		};
	}
	
	//restrict block break while block is open
	@EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
	public void onBlockBreak(BlockBreakEvent e)
	{
		if (!config.restrictBlockBreakWhileOpenEnabled) {return;}
		
		Block brokenblock = e.getBlock();
		for (BlockState bs : playerOpenBlock.values())
		{
			if (bs.getBlock().equals(brokenblock))
			{
				e.setCancelled(true);
				e.getPlayer().sendMessage(ChatColor.RED+"Вы не можете сломать этот блок пока он открыт другим игроком");
				return;
			}
		}
	}
	
	
	//function to clear drops near block
	private void clearNearbyDrop(Block b)
	{
		Entity arrow = b.getWorld().spawnArrow(b.getLocation(), new Vector(0, 0, 0), 0, 0);
		List<Entity> nearbyentities = arrow.getNearbyEntities(3, 3, 3);
		for (Entity entity : nearbyentities)
		{
			if (entity instanceof Item)
			{
				entity.remove();
			}
		}
		arrow.remove();
	}
	
}